#!/usr/bin/env node
/**
 * Build the .icuewidget from a clean staging copy, using the official CLI.
 *
 * `icuewidget package .` zips everything under the directory that is not a dotfile, so run
 * from the repo root it would sweep in android/, dev/, README and LICENSE. Only the files
 * the widget loads belong in the package; this stages exactly those, then runs
 * `icuewidget validate` and `icuewidget package` on the staging directory.
 *
 * Usage:  npm run package        (needs the CLI: npm install -g icuewidget-cli)
 * Output: dist/ClickUp-Tasks-<version>.icuewidget   (gitignored)
 * Override the CLI binary with ICUEWIDGET_CLI.
 */
const { execFileSync } = require('child_process');
const fs = require('fs'), os = require('os'), path = require('path');

const WIDGET = path.resolve(__dirname, '..');
const SHIP = [
  'index.html',
  'manifest.json',
  'translation.json',
  'styles/ClickUpTasks.css',
  'resources/icon.png',
  'resources/icon.svg',
  'common/tools/media_viewer/MediaViewer.js',
  'common/tools/media_viewer/MediaViewer.css',
  'common/LICENSE.txt'
];

const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'icue-pkg-'));
try {
  for (const rel of SHIP) {
    const src = path.join(WIDGET, rel);
    if (!fs.existsSync(src)) { console.error('missing: ' + rel); process.exit(1); }
    const dst = path.join(stage, rel);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.copyFileSync(src, dst);
  }

  const version = JSON.parse(fs.readFileSync(path.join(WIDGET, 'manifest.json'), 'utf8')).version;
  const out = path.join(WIDGET, 'dist', 'ClickUp-Tasks-' + version + '.icuewidget');
  fs.mkdirSync(path.dirname(out), { recursive: true });

  const cli = process.env.ICUEWIDGET_CLI || 'icuewidget';
  execFileSync(cli, ['validate', stage], { stdio: 'inherit' });
  execFileSync(cli, ['package', stage, '-o', out], { stdio: 'inherit' });
  console.log('\npackaged: ' + out);
} finally {
  fs.rmSync(stage, { recursive: true, force: true });
}
