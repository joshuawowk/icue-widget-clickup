#!/usr/bin/env python3
"""Dev harness: render index.html in headless Chromium against the local mock ClickUp server.

    python3 dev/shoot.py                      # all scenarios -> dev/out/*.png (+ *.dom.html)
    python3 dev/shoot.py list detail-fields   # only the named scenarios

Starts its own mock (android/tools/mock_clickup.py) and static server on free ports. The harness
page is index.html with a <script> injected at the top of <head> that sets the widget settings
and CLICKUP_API_BASE (the dev hook in index.html); an optional per-scenario script then clicks around.
Set BULK=150 to POST /__bulk?n=150 first (exercises multi-page loading).
"""
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'dev', 'out')
CHROMIUM = '/usr/bin/chromium'

# Interaction snippets run inside the page after load (virtual time, so timeouts are cheap).
OPEN_TASK = "setTimeout(function(){var r=document.querySelector('[data-task-id=\"%s\"]');if(r)r.click();},1500);"
CLICK_PRIO = "setTimeout(function(){var b=document.querySelector('#detailPriorities [data-prio=\"%s\"]');if(b)b.click();},3500);"

# Runs at the top of <head>: the mock only has one tag per task, so decorate t1/t2 with many (and
# one long) tags in the task payloads to exercise the "+N" chip and wrapping.
MANY_TAGS = '''(function(){var f=window.fetch;window.fetch=function(u,o){return f(u,o).then(function(r){
 if(!/\\/task(\\?|\\/)/.test(String(u))||(o&&o.method&&o.method!=='GET'))return r;
 return r.clone().json().then(function(j){
  var extra=[{name:'quarter-end-close',tag_bg:'#f9d900'},{name:'a-very-long-tag-name-that-must-truncate-nicely',tag_bg:'#1b9e77'},{name:'ops',tag_bg:'#ff7800'},{name:'urgent-review',tag_bg:'#e50000'},{name:'x',tag_fg:'#fff'}];
  (j.tasks||[j]).forEach(function(t){if(t.id==='t1'||t.id==='t2'){t.tags=(t.tags||[]).concat(extra);}});
  return new Response(JSON.stringify(j),{status:200,headers:{'Content-Type':'application/json'}});});});};})();'''

SCENARIOS = {
    # name: (window size, widget settings, script injected at end of body)
    'list':          ('696x840',  {}, ''),
    'list-small':    ('416x696',  {}, ''),
    'list-wide':     ('1376x696', {}, ''),
    'list-short':    ('840x344',  {}, ''),
    'list-all':      ('696x840',  {'statusDefault': ''}, ''),
    'detail-fields': ('696x840',  {}, OPEN_TASK % 't2'),
    'detail-fields-small': ('416x696', {}, OPEN_TASK % 't2'),
    'detail-wide':   ('1376x696', {}, OPEN_TASK % 't2'),
    'detail-prio':   ('696x840',  {}, OPEN_TASK % 't2' + CLICK_PRIO % 'urgent'),
    'detail-prio-none': ('696x840', {}, OPEN_TASK % 't2' + CLICK_PRIO % 'none'),
    'detail-t1':     ('696x840',  {}, OPEN_TASK % 't1'),
}
# Scenarios that also need MANY_TAGS at the top of <head>.
PRE = {'list-tags': MANY_TAGS, 'list-tags-small': MANY_TAGS, 'detail-tags': MANY_TAGS, 'list-tags-wide': MANY_TAGS}
SCENARIOS['list-tags'] = ('696x840', {}, '')
SCENARIOS['list-tags-small'] = ('416x696', {}, '')
SCENARIOS['list-tags-wide'] = ('1376x696', {}, '')
SCENARIOS['list-tags-xl'] = ('1688x696', {}, '')   # two-column L-H layout
PRE['list-tags-xl'] = MANY_TAGS
SCENARIOS['detail-tags'] =('416x696', {}, OPEN_TASK % 't2')
# Priority round trip (set urgent, back to the list) and a failing PUT (must revert + error toast).
FAIL_PUT = "(function(){var f=window.fetch;window.fetch=function(u,o){if(o&&o.method==='PUT')return Promise.resolve(new Response('{}',{status:500}));return f(u,o);};})();"
BACK = "setTimeout(function(){document.getElementById('backBtn').click();},5500);"
SCENARIOS['prio-roundtrip'] = ('696x840', {'statusDefault': ''},OPEN_TASK % 't4' + CLICK_PRIO % 'urgent' + BACK)
SCENARIOS['prio-fail'] = ('696x840', {}, OPEN_TASK % 't2' + CLICK_PRIO % 'urgent')
PRE['prio-fail'] = FAIL_PUT
# Paging: run as  BULK=150 python3 dev/shoot.py paging  (expects 18 in-progress tasks: 3 seeded + 15 filler)
SCENARIOS['paging'] = ('696x840', {}, '')


def free_port():
    s = socket.socket()
    s.bind(('127.0.0.1', 0))
    port = s.getsockname()[1]
    s.close()
    return port


def wait_http(url):
    for _ in range(50):
        try:
            urllib.request.urlopen(url, timeout=1)
            return
        except Exception as e:
            if getattr(e, 'code', None):
                return
            time.sleep(0.1)
    raise RuntimeError('server did not start: ' + url)


def build_harness(path, mock_port, props, script, pre=''):
    html = open(os.path.join(ROOT, 'index.html'), encoding='utf-8').read()
    base = {'apiToken': 'pk_test_token', 'widgetTitle': 'My Tasks', 'statusDefault': 'In-Progress'}
    base.update(props)
    inject = ('<base href="/">\n<script>Object.assign(window,%s);window.CLICKUP_API_BASE="http://127.0.0.1:%d/api/v2";%s</script>\n'
              % (json.dumps(base), mock_port, pre))
    html = html.replace('<head>', '<head>\n' + inject, 1)
    if script:
        html = html.replace('</body>', '<script>' + script + '</script>\n</body>', 1)
    open(path, 'w', encoding='utf-8').write(html)


def post(port, path):
    urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:%d%s' % (port, path), method='POST'))


def main():
    names = sys.argv[1:] or list(SCENARIOS)
    os.makedirs(OUT, exist_ok=True)
    mock_port, web_port = free_port(), free_port()
    procs = [
        subprocess.Popen([sys.executable, os.path.join(ROOT, 'android', 'tools', 'mock_clickup.py'), str(mock_port)],
                         stdout=subprocess.DEVNULL),
        subprocess.Popen([sys.executable, '-m', 'http.server', str(web_port), '--bind', '127.0.0.1'],
                         cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL),
    ]
    try:
        wait_http('http://127.0.0.1:%d/__state' % mock_port)
        wait_http('http://127.0.0.1:%d/index.html' % web_port)
        for name in names:
            size, props, script = SCENARIOS[name]
            post(mock_port, '/__reset')
            if os.environ.get('BULK'):
                post(mock_port, '/__bulk?n=' + os.environ['BULK'])
            page = os.path.join(OUT, name + '.html')
            build_harness(page, mock_port, props, script, PRE.get(name, ''))
            url = 'http://127.0.0.1:%d/dev/out/%s.html' % (web_port, name)
            common = ['--headless=new', '--user-data-dir=' + tempfile.mkdtemp(prefix='shoot-'), '--disable-gpu', '--no-sandbox', '--window-size=' + size.replace('x', ','),
                      '--virtual-time-budget=9000', '--hide-scrollbars']
            png = os.path.join(OUT, name + '.png')
            subprocess.run([CHROMIUM] + common + ['--screenshot=' + png, url], check=True, timeout=90,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            dom = subprocess.run([CHROMIUM] + common + ['--dump-dom', url], check=True, timeout=90,
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout
            open(os.path.join(OUT, name + '.dom.html'), 'wb').write(dom)
            print('wrote', png)
    finally:
        for p in procs:
            p.terminate()


if __name__ == '__main__':
    main()
