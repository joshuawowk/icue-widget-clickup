// Sanity checks for the shared task-field formatting spec (dates assume TZ=UTC).  Run:  TZ=UTC node dev/test_fields.js
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
const start = html.indexOf('function fmtFieldDate');
const end = html.indexOf('function renderFields');
if (start < 0 || end < 0) throw new Error('markers not found');
const languageCode = 'en-US';
const formatTaskFields = new Function('languageCode', html.slice(start, end) + '; return formatTaskFields;')(languageCode);

const opts = [{ id: 'cad9', name: 'Ariana Carlone', color: '#9b59b6', orderindex: 0 }, { id: 'b2', name: 'Becky', color: '#800000', orderindex: 1 }];
const cf = (type, value, type_config) => ({ type, name: type + '-f', value, type_config: type_config || {} });
const t = {
  assignees: [{ username: 'Josh' }, { username: 'Ann' }],
  start_date: '1792051200000', time_estimate: 5400000, points: 3, date_created: '1792051200000', creator: { username: 'Josh' },
  custom_fields: [
    cf('drop_down', 0, { options: opts }), cf('drop_down', 'b2', { options: opts }), cf('drop_down', 9, { options: opts }),
    cf('date', '1792051200000'), cf('short_text', 'INC2383904'), cf('number', '9'), cf('currency', 12.5, { currency_type: 'USD' }),
    cf('text', '  Good day,\n\nthere  '), cf('text', '   '), cf('checkbox', true), cf('checkbox', false),
    cf('labels', ['cad9', 'b2'], { options: [{ id: 'cad9', label: 'One' }, { id: 'b2', name: 'Two' }] }),
    cf('users', [{ username: 'A' }, { username: 'B' }]), cf('tasks', [{ name: 'T1' }]),
    cf('emoji', 3, { count: 5 }), cf('automatic_progress', { percent_complete: 33 }), cf('automatic_progress', { percent_complete: 0 }),
    cf('manual_progress', { percent_completed: 50 }), cf('location', { formatted_address: '1 Main St' }),
    cf('formula', 42), cf('attachment', [{}, {}]), cf('url', null), cf('weird', 'plain'), cf('weird', { a: 1 }), cf('text', []),
  ],
};
const got = formatTaskFields(t).map(f => f.name + '=' + f.value + (f.color ? '@' + f.color : ''));
const want = [
  'Assignees=Josh, Ann', 'Start date=Thu, Oct 15, 2026', 'Time estimate=1h 30m', 'Sprint points=3', 'Created=Thu, Oct 15, 2026 by Josh',
  'drop_down-f=Ariana Carlone@#9b59b6', 'drop_down-f=Becky@#800000', 'date-f=Thu, Oct 15, 2026', 'short_text-f=INC2383904', 'number-f=9',
  'currency-f=USD 12.5', 'text-f=Good day,\n\nthere', 'checkbox-f=Yes', 'labels-f=One, Two', 'users-f=A, B', 'tasks-f=T1', 'emoji-f=★★★☆☆',
  'automatic_progress-f=33%', 'manual_progress-f=50%', 'location-f=1 Main St', 'formula-f=42', 'attachment-f=2 files', 'weird-f=plain',
];
let bad = 0;
want.forEach((w, i) => { if (got[i] !== w) { bad++; console.log('MISMATCH', i, JSON.stringify(got[i]), 'want', JSON.stringify(w)); } });
if (got.length !== want.length) { bad++; console.log('length', got.length, 'want', want.length, got); }
const est = formatTaskFields({ time_estimate: 2700000 }).map(f => f.value).concat(formatTaskFields({ time_estimate: 7200000 }).map(f => f.value));
if (est.join('|') !== '45m|2h') { bad++; console.log('estimate', est); }
console.log(bad ? 'FAIL' : 'ok (' + got.length + ' fields)');
process.exit(bad ? 1 : 0);
