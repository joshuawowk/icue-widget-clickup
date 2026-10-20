#!/usr/bin/env python3
"""Local mock of the ClickUp v2 endpoints the widget uses, for on-device testing without real data.

    python3 tools/mock_clickup.py [port]           # default 8765, token: pk_test_token
    adb reverse tcp:8765 tcp:8765
    adb shell run-as net.jwowk.clickupwidget.debug sh -c \
        'echo http://127.0.0.1:8765/api/v2 > files/api_base_override.txt'

Then connect in the app with the token pk_test_token. Debug builds only (release ignores the override).
Test controls: POST /__fail?mode=401|500|net|off  ·  GET /__state  ·  POST /__reset  ·  POST /__bulk?n=150
(adds n filler tasks so My Tasks spans several 100-task pages). CORS is open, so the iCUE widget can be
pointed at it from a browser page served on localhost.
"""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

TOKEN = "pk_test_token"
TEAM = {"id": "9000", "name": "Mock Workspace"}
USER = {"id": 4242, "username": "Test User", "email": "test@example.com"}

STATUSES = {
    "L1": [("to do", "#87909e", "open", 0), ("in progress", "#4194f6", "custom", 1),
           ("review", "#a875ff", "custom", 2), ("complete", "#6bc950", "closed", 3)],
    "L2": [("Open", "#d3d3d3", "open", "0"), ("In-Progress", "#ff7800", "custom", "1"),
           ("Blocked", "#e50000", "custom", "2"), ("Closed", "#6bc950", "closed", "3")],
    "L3": [("to do", "#87909e", "open", 0), ("doing", "#4194f6", "custom", 1), ("done", "#6bc950", "done", 2)],
}
LISTS = {"L1": "Engineering", "L2": "Ops", "L3": "Inbox"}
PRIO = {"urgent": "#f50000", "high": "#ffcc00", "normal": "#6fddff", "low": "#d8d8d8"}
PRIO_BY_ID = {1: "urgent", 2: "high", 3: "normal", 4: "low"}
TAG_COLORS = {"finance": "#0f5096", "bug": "#e50000", "planning": "#1b9e77", "security": "#7b2cbf", "meeting": "#0f5096"}
REQUESTERS = [("Ariana Carlone", "#9b59b6"), ("Becky Puleo", "#800000"), ("Dana Reyes", "#1abc9c")]
PAGE = 100
DAY = 86_400_000

state_lock = threading.Lock()
fail_mode = "off"


def status_obj(list_id, name):
    for s in STATUSES[list_id]:
        if s[0] == name:
            return {"status": s[0], "color": s[1], "type": s[2], "orderindex": s[3]}
    raise KeyError(name)


def seed():
    now = int(time.time() * 1000)
    rows = [
        # id, name, list, status, due offset days (None), priority, tags, parent
        ("t1", "Quarterly finance report", "L2", "In-Progress", -2, "urgent", ["finance"], None),
        ("t2", "Fix login redirect bug", "L1", "in progress", 0, "high", ["bug"], None),
        ("t3", "Team sync notes", "L3", "to do", 0, "normal", [], None),
        ("t4", "Review pull requests", "L1", "review", 1, None, [], None),
        ("t5", "Plan next sprint", "L1", "to do", 4, "normal", ["planning"], None),
        ("t6", "Renew SSL certificates", "L2", "Open", 10, "low", [], None),
        ("t7", "Security audit", "L2", "In-Progress", None, "urgent", ["security"], None),
        ("t8", "Update onboarding docs", "L3", "doing", None, None, [], None),
        ("t2a", "Reproduce on Android", "L1", "complete", -1, None, [], "t2"),
        ("t2b", "Write regression test", "L1", "in progress", 0, "high", [], "t2"),
        ("t2c", "Ship hotfix", "L1", "to do", 1, None, [], "t2"),
        ("t7a", "Scan open ports", "L2", "Closed", None, None, [], "t7"),
        ("t7b", "Rotate API keys", "L2", "Open", 2, "high", [], "t7"),
    ]
    tasks = {}
    for tid, name, lid, st, due, prio, tags, parent in rows:
        tasks[tid] = {
            "id": tid, "name": name, "list_id": lid, "status": st,
            "due_date": None if due is None else str(now + due * DAY),
            "priority": None if prio is None else {"id": "1", "priority": prio, "color": PRIO[prio]},
            "tags": [{"name": t} for t in tags], "parent": parent,
            "text_content": f"{name}. This is a mock task description used for testing the widget.\n\nSecond paragraph.",
            "created": now - 7 * DAY,
            "fields": {
                "requester": {"t1": 1, "t2": 0, "t7": 2}.get(tid),
                "incident": {"t2": "INC2383904", "t7": "INC2390117"}.get(tid),
                "done_date": str(now + 5 * DAY) if tid in ("t1", "t5") else None,
                "ticket": {"t1": "9", "t6": "42"}.get(tid),
                "notes": "Customer reports the redirect loops after SSO.\nRepro on Android 14." if tid == "t2" else None,
                "progress": 33 if tid == "t2" else 0,
            },
        }
    return tasks


TASKS = seed()


def custom_fields(t):
    f = t.get("fields") or {}
    return [
        {"id": "cf-req", "name": "Requester", "type": "drop_down", "value": f.get("requester"),
         "type_config": {"options": [{"id": f"opt{i}", "name": n, "color": c, "orderindex": i}
                                     for i, (n, c) in enumerate(REQUESTERS)]}},
        {"id": "cf-inc", "name": "Incident No.", "type": "short_text", "value": f.get("incident"), "type_config": {}},
        {"id": "cf-done", "name": "Done Date", "type": "date", "value": f.get("done_date"), "type_config": {}},
        {"id": "cf-ticket", "name": "Ticket No", "type": "number", "value": f.get("ticket"),
         "type_config": {"number_format": "en-US"}},
        {"id": "cf-notes", "name": "Incident Description", "type": "text", "value": f.get("notes"), "type_config": {}},
        {"id": "cf-prog", "name": "Progress", "type": "automatic_progress",
         "value": {"percent_complete": f.get("progress", 0)}, "type_config": {"tracking": {"subtasks": True}}},
    ]


def task_json(t, list_null=False):
    return {
        "id": t["id"], "custom_id": None, "name": t["name"], "text_content": t["text_content"],
        "description": t["text_content"], "status": status_obj(t["list_id"], t["status"]),
        "orderindex": "1.0000", "date_created": "1700000000000", "due_date": t["due_date"],
        "priority": t["priority"], "parent": t["parent"],
        "tags": [{"name": g["name"], "tag_fg": "#ffffff", "tag_bg": TAG_COLORS.get(g["name"], "#555555")}
                 for g in t["tags"]],
        "custom_fields": custom_fields(t), "team_id": TEAM["id"],
        "date_created": str(t.get("created", 1700000000000)), "start_date": None, "time_estimate": None,
        "points": None, "creator": {"id": USER["id"], "username": USER["username"]},
        "url": f"https://app.clickup.com/t/{t['id']}",
        "list": None if list_null else {"id": t["list_id"], "name": LISTS[t["list_id"]], "access": True},
        "assignees": [{"id": USER["id"], "username": USER["username"]}],
    }


def is_open(t):
    return status_obj(t["list_id"], t["status"])["type"] not in ("closed", "done")


def page_of(items, q):
    page = int(q.get("page", ["0"])[0])
    chunk = items[page * PAGE:(page + 1) * PAGE]
    return {"tasks": chunk, "last_page": (page + 1) * PAGE >= len(items)}


class Handler(BaseHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", self.headers.get("Origin") or "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, PUT, POST, OPTIONS")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, fmt, *args):
        sys.stdout.write("%s %s\n" % (time.strftime("%H:%M:%S"), fmt % args))
        sys.stdout.flush()

    def send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authed(self):
        if fail_mode == "401" or self.headers.get("Authorization") != TOKEN:
            self.send(401, {"err": "Token invalid", "ECODE": "OAUTH_025"})
            return False
        if fail_mode == "500":
            self.send(500, {"err": "Internal error"})
            return False
        if fail_mode == "net":
            self.close_connection = True
            self.connection.close()
            return False
        return True

    def do_POST(self):
        global fail_mode, TASKS
        u = urlparse(self.path)
        q = parse_qs(u.query)
        if u.path == "/__fail":
            fail_mode = q.get("mode", ["off"])[0]
            return self.send(200, {"fail_mode": fail_mode})
        if u.path == "/__reset":
            with state_lock:
                TASKS = seed()
            return self.send(200, {"ok": True})
        if u.path == "/__bulk":
            n = int(q.get("n", ["150"])[0])
            with state_lock:
                base = seed()["t5"]
                for i in range(n):
                    tid = f"bulk{i}"
                    TASKS[tid] = dict(base, id=tid, name=f"Filler task {i}", status="in progress" if i % 10 == 9 else "to do",
                                      due_date=None, priority=None, tags=[], fields={})
            return self.send(200, {"tasks": len(TASKS)})
        if u.path.startswith("/api/v2/"):
            if not self.authed():
                return
            parts = u.path[len("/api/v2/"):].strip("/").split("/")
            length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(length) or b"{}")
            sys.stdout.write(f"   POST body: {body}\n")
            if len(parts) == 3 and parts[0] == "list" and parts[2] == "task" and parts[1] in LISTS:
                if not str(body.get("name", "")).strip():
                    return self.send(400, {"err": "Task name invalid", "ECODE": "INPUT_005"})
                with state_lock:
                    tid = f"n{len(TASKS) + 1}"
                    prio = PRIO_BY_ID.get(body.get("priority"))
                    want = str(body.get("status") or "").lower()
                    names = [st[0] for st in STATUSES[parts[1]]]
                    status = next((n for n in names if n.lower() == want), names[0])
                    TASKS[tid] = {
                        "id": tid, "name": body["name"], "list_id": parts[1], "status": status,
                        "due_date": None if body.get("due_date") is None else str(body["due_date"]),
                        "priority": None if prio is None else {"id": "1", "priority": prio, "color": PRIO[prio]},
                        "tags": [], "parent": None, "text_content": body.get("description", ""),
                        "created": int(time.time() * 1000), "fields": {},
                        "assigned": USER["id"] in (body.get("assignees") or []),
                    }
                    return self.send(200, task_json(TASKS[tid]))
        self.send(404, {"err": "not found"})

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        p = u.path
        if p == "/__state":
            return self.send(200, {k: v["status"] for k, v in TASKS.items()})
        if not p.startswith("/api/v2/"):
            return self.send(404, {"err": "not found"})
        if not self.authed():
            return
        parts = p[len("/api/v2/"):].strip("/").split("/")
        with state_lock:
            if parts == ["user"]:
                return self.send(200, {"user": USER})
            if parts == ["team"]:
                return self.send(200, {"teams": [TEAM]})
            if len(parts) == 3 and parts[0] == "team" and parts[2] == "space":
                return self.send(200, {"spaces": [{"id": "s1", "name": "Work"}]})
            if len(parts) == 3 and parts[0] == "space" and parts[2] == "folder":
                return self.send(200, {"folders": [{"id": "f1", "name": "Projects", "lists": [
                    {"id": "L1", "name": "Engineering"}, {"id": "L2", "name": "Ops"}]}]})
            if len(parts) == 3 and parts[0] == "space" and parts[2] == "list":
                return self.send(200, {"lists": [{"id": "L3", "name": "Inbox"}]})
            if len(parts) == 2 and parts[0] == "list" and parts[1] in LISTS:
                lid = parts[1]
                return self.send(200, {"id": lid, "name": LISTS[lid], "statuses": [
                    {"status": s[0], "color": s[1], "type": s[2], "orderindex": s[3]} for s in STATUSES[lid]]})
            if len(parts) == 3 and parts[0] == "list" and parts[2] == "task":
                lid = parts[1]
                ts = [task_json(t) for t in TASKS.values() if t["list_id"] == lid and t["parent"] is None and is_open(t)]
                return self.send(200, page_of(ts, q))
            if len(parts) == 3 and parts[0] == "team" and parts[2] == "task":
                parent = q.get("parent", [None])[0]
                include_closed = q.get("include_closed", ["false"])[0] == "true"
                subtasks = q.get("subtasks", ["false"])[0] == "true"
                ts = []
                for t in TASKS.values():
                    if parent is not None and t["parent"] != parent:
                        continue
                    if parent is None and t["parent"] is not None and not subtasks:
                        continue
                    if not include_closed and not is_open(t):
                        continue
                    if parent is None and not t.get("assigned", True):
                        continue
                    ts.append(task_json(t))
                return self.send(200, page_of(ts, q))
            if len(parts) == 2 and parts[0] == "task" and parts[1] in TASKS:
                t = TASKS[parts[1]]
                body = task_json(t)
                if q.get("include_subtasks", ["false"])[0] == "true":
                    body["subtasks"] = [task_json(s, list_null=True) for s in TASKS.values() if s["parent"] == t["id"]]
                return self.send(200, body)
        self.send(404, {"err": "Route not found", "ECODE": "APP_001"})

    def do_PUT(self):
        u = urlparse(self.path)
        if not u.path.startswith("/api/v2/") or not self.authed():
            return
        parts = u.path[len("/api/v2/"):].strip("/").split("/")
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length) or b"{}")
        sys.stdout.write(f"   PUT body: {body}\n")
        with state_lock:
            if len(parts) == 2 and parts[0] == "task" and parts[1] in TASKS:
                t = TASKS[parts[1]]
                if "priority" in body:
                    prio = PRIO_BY_ID.get(body["priority"])
                    t["priority"] = None if prio is None else {"id": str(body["priority"]), "priority": prio, "color": PRIO[prio]}
                if "status" in body:
                    names = [s[0].lower() for s in STATUSES[t["list_id"]]]
                    if body["status"].lower() not in names:
                        return self.send(400, {"err": "Status does not exist", "ECODE": "CRTSK_001"})
                    t["status"] = STATUSES[t["list_id"]][names.index(body["status"].lower())][0]
                return self.send(200, task_json(t))
        self.send(404, {"err": "Route not found"})


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    print(f"mock ClickUp on http://127.0.0.1:{port}/api/v2  token={TOKEN}")
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
