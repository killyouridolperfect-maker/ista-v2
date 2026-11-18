(() => {
const MAX = 1000, LKEY = 'clipnote.v1';
const $ = s => document.querySelector(s);
const S = { items: [], filter: 'all', q: '' };

/* ---------- storage (this device only) ---------- */
function load(){ try { const a = JSON.parse(localStorage.getItem(LKEY)); return Array.isArray(a) ? a : []; } catch(e){ return []; } }
function persist(){ try { localStorage.setItem(LKEY, JSON.stringify(S.items)); } catch(e){ toast('保存容量がいっぱいです。古い履歴を削除してください'); } }
const newId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
function commit(){ persist(); render(); }

/* ---------- actions ---------- */
function addClip(raw, quiet){
  const text = String(raw || '').replace(/\s+$/, '');
  if (!text.trim()){ if (!quiet) toast('空なので保存しませんでした'); return; }
  const dup = S.items.find(x => x.text === text);
  if (dup){ dup.ts = Date.now(); commit(); toast('保存済みのため一番上に移動しました'); return; }
  S.items.push({ id: newId(), text, ts: Date.now(), pin: false });
  const extra = S.items.filter(x => !x.pin).sort((a, b) => a.ts - b.ts);
  while (S.items.length > MAX && extra.length){ const old = extra.shift(); S.items = S.items.filter(x => x !== old); }
  commit(); toast('保存しました');
}

function copyText(text){
  try { return navigator.clipboard.writeText(text).then(() => true, () => fallbackCopy(text)); }
  catch(e){ return Promise.resolve(fallbackCopy(text)); }
}
function fallbackCopy(text){
  const ta = document.createElement('textarea');
  ta.value = text; ta.setAttribute('readonly', ''); ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0';
  document.body.appendChild(ta); ta.select(); ta.setSelectionRange(0, text.length);
  let ok = false; try { ok = document.execCommand('copy'); } catch(e){}
  ta.remove(); return ok;
}

/* ---------- toast ---------- */
let tTimer, undoFn = null;
function toast(msg, undo){
  $('#toastMsg').textContent = msg; undoFn = undo || null; $('#undo').hidden = !undo;
  $('#toast').classList.add('show'); clearTimeout(tTimer);
  tTimer = setTimeout(() => $('#toast').classList.remove('show'), undo ? 5000 : 1800);
}
$('#undo').addEventListener('click', () => { const f = undoFn; undoFn = null; $('#toast').classList.remove('show'); f && f(); });

/* ---------- render ---------- */
const esc = s => s.replace(/[&<>"]/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;' }[c]));
function highlight(text, q){
  if (!q) return esc(text);
  const low = text.toLowerCase(), ql = q.toLowerCase(); let out = '', i = 0, j;
  while ((j = low.indexOf(ql, i)) !== -1){ out += esc(text.slice(i, j)) + '<mark>' + esc(text.slice(j, j + q.length)) + '</mark>'; i = j + q.length; }
  return out + esc(text.slice(i));
}
function dayLabel(ts){
  const d = new Date(ts), n = new Date(), mid = x => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((mid(n) - mid(d)) / 864e5);
  if (diff === 0) return '今日'; if (diff === 1) return '昨日';
  const w = '日月火水木金土'[d.getDay()];
  return (d.getFullYear() === n.getFullYear() ? '' : d.getFullYear() + '年') + (d.getMonth() + 1) + '月' + d.getDate() + '日（' + w + '）';
}
const hhmm = ts => { const d = new Date(ts); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
const svg = p => '<svg viewBox="0 0 24 24" aria-hidden="true">' + p + '</svg>';
const ICON = {
  pin: svg('<path d="M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"/>'),
  edit: svg('<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>'),
  del: svg('<path d="M4 7h16M10 11v6M14 11v6M9 7V4h6v3M6 7l1 13h10l1-13"/>')
};
const open = new Set();

function render(){
  const q = S.q.trim(), ql = q.toLowerCase();
  const all = S.items.slice().sort((a, b) => b.ts - a.ts);
  const shown = all.filter(x => (S.filter === 'all' || x.pin) && (!ql || x.text.toLowerCase().includes(ql)));
  $('#count').textContent = shown.length === all.length ? all.length + '件' : shown.length + ' / ' + all.length + '件';
  const el = $('#list');
  if (!all.length){
    el.innerHTML = '<div class="empty"><h2>保存のしかた</h2><ol>' +
      '<li><b>Androidのおすすめ</b>：他のアプリで文字を選ぶ →「共有」→「クリップ帳」。クリップ帳が開いて自動で保存されます</li>' +
      '<li>または、コピーしてからこのページの「＋ 貼り付け」を押す</li>' +
      '<li>あとは一覧のカードをタップすれば、いつでも再コピーできます</li></ol></div>';
    return;
  }
  if (!shown.length){ el.innerHTML = '<div class="empty"><p>' + (S.filter === 'pin' && !ql ? 'ピン留めはまだありません。カードの ☆ を押すと、ここに集まります。' : '「' + esc(q) + '」を含む履歴はありません。') + '</p></div>'; return; }
  let html = '', last = '';
  for (const it of shown){
    const d = dayLabel(it.ts);
    if (d !== last){ if (last) html += '</div>'; html += '<div class="day">' + d + '</div><div class="list">'; last = d; }
    const long = it.text.length > 120 || it.text.split('\n').length > 4;
    html += '<div class="card' + (it.pin ? ' pinned' : '') + (open.has(it.id) ? ' open' : '') + '" data-id="' + it.id + '">' +
      '<button class="hit" data-a="copy" aria-label="タップでコピー"><div class="txt">' + highlight(it.text, q) + '</div></button>' +
      '<div class="meta"><span class="stamp"><span>' + hhmm(it.ts) + ' · ' + it.text.length + '字</span><span class="done">✓ コピーしました</span></span>' +
      (long ? '<button class="ib more" data-a="open">' + (open.has(it.id) ? 'たたむ' : '全文') + '</button>' : '') +
      '<button class="ib' + (it.pin ? ' on' : '') + '" data-a="pin" aria-label="' + (it.pin ? 'ピンを外す' : 'ピン留め') + '" aria-pressed="' + it.pin + '">' + ICON.pin + '</button>' +
      '<button class="ib" data-a="edit" aria-label="編集">' + ICON.edit + '</button>' +
      '<button class="ib" data-a="del" aria-label="削除">' + ICON.del + '</button></div></div>';
  }
  el.innerHTML = html + '</div>';
}

/* ---------- list interactions ---------- */
$('#list').addEventListener('click', e => {
  const b = e.target.closest('[data-a]'); if (!b) return;
  const card = b.closest('.card'); const it = S.items.find(x => x.id === card.dataset.id); if (!it) return;
  const a = b.dataset.a;
  if (a === 'copy'){
    if (window.getSelection && String(window.getSelection()).length) return;
    copyText(it.text).then(ok => {
      if (ok){
        const c = document.querySelector('.card[data-id="' + it.id + '"]');
        if (c){ c.classList.add('copied'); setTimeout(() => c.classList.remove('copied'), 1600); }
        toast('コピーしました');
      } else { open.add(it.id); render(); toast('自動コピーできませんでした。文字を長押ししてコピーしてください'); }
    });
  } else if (a === 'open'){ open.has(it.id) ? open.delete(it.id) : open.add(it.id); render(); }
  else if (a === 'pin'){ it.pin = !it.pin; commit(); }
  else if (a === 'edit'){ openEditor(it); }
  else if (a === 'del'){
    S.items = S.items.filter(x => x !== it); commit();
    toast('削除しました', () => { S.items.push(it); commit(); });
  }
});

/* ---------- editor ---------- */
let editing = null;
function openEditor(it){ editing = it; $('#editTa').value = it.text; $('#editor').hidden = false; $('#editTa').focus(); }
function closeEditor(){ $('#editor').hidden = true; editing = null; }
$('#editCancel').addEventListener('click', closeEditor);
$('#editor').addEventListener('click', e => { if (e.target === $('#editor')) closeEditor(); });
$('#editSave').addEventListener('click', () => {
  const v = $('#editTa').value.replace(/\s+$/, '');
  if (!v.trim()){ toast('空にはできません。消す場合はゴミ箱を押してください'); return; }
  editing.text = v; closeEditor(); commit(); toast('変更を保存しました');
});
document.addEventListener('keydown', e => { if (e.key === 'Escape' && !$('#editor').hidden) closeEditor(); });

/* ---------- intake ---------- */
$('#pasteBtn').addEventListener('click', async () => {
  try {
    const t = await navigator.clipboard.readText();
    addClip(t);
  } catch(e){
    $('#inbox').focus();
    toast('読み取れませんでした。枠を長押しして「ペースト」を押してください');
  }
});
document.addEventListener('paste', e => {
  const t = e.target;
  if (t && (t.id === 'q' || t.id === 'editTa')) return;
  const text = e.clipboardData && e.clipboardData.getData('text/plain');
  e.preventDefault();
  if (!text){ toast('文字だけ保存できます（画像は保存できません）'); return; }
  $('#inbox').value = ''; syncInbox(); $('#inbox').blur();
  addClip(text);
});
function syncInbox(){
  const ib = $('#inbox'); ib.style.height = 'auto'; ib.style.height = Math.min(ib.scrollHeight, innerHeight * .3) + 'px';
  const has = !!ib.value.trim(); $('#saveTyped').hidden = !has; $('#pasteBtn').hidden = has;
}
$('#inbox').addEventListener('input', syncInbox);
$('#inbox').addEventListener('keydown', e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)){ e.preventDefault(); $('#saveTyped').click(); } });
$('#saveTyped').addEventListener('click', () => { const v = $('#inbox').value; $('#inbox').value = ''; syncInbox(); addClip(v); });

$('#q').addEventListener('input', e => { S.q = e.target.value; render(); });
function setFilter(f){ S.filter = f; $('#fAll').setAttribute('aria-pressed', f === 'all'); $('#fPin').setAttribute('aria-pressed', f === 'pin'); render(); }
$('#fAll').addEventListener('click', () => setFilter('all'));
$('#fPin').addEventListener('click', () => setFilter('pin'));

/* ---------- backup ---------- */
$('#expBtn').addEventListener('click', () => {
  const blob = new Blob([JSON.stringify(S.items, null, 1)], { type: 'application/json' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
  a.download = 'クリップ帳-' + new Date().toISOString().slice(0, 10) + '.json';
  document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000);
});
$('#impBtn').addEventListener('click', () => $('#impFile').click());
$('#impFile').addEventListener('change', async e => {
  const f = e.target.files[0]; if (!f) return;
  try {
    const arr = JSON.parse(await f.text()); if (!Array.isArray(arr)) throw 0;
    const have = new Set(S.items.map(x => x.text)); let n = 0;
    for (const x of arr) if (x && typeof x.text === 'string' && !have.has(x.text)){ S.items.push({ id: newId(), text: x.text, ts: +x.ts || Date.now(), pin: !!x.pin }); have.add(x.text); n++; }
    commit(); toast(n + '件を戻しました');
  } catch(_){ toast('このファイルは読み込めません。クリップ帳で保存したファイルを選んでください'); }
  e.target.value = '';
});

/* ---------- boot ---------- */
S.items = load();
$('#status').className = 'status warn';
$('#statusText').textContent = 'この端末に保存';
// Entry points:
//  - iPhone Shortcut / Back Tap: index.html?add=<text>
//  - Android share sheet (Web Share Target): index.html?text=<text>&url=<url>&title=<title>
const params = new URLSearchParams(location.search);
if (['add', 'text', 'url', 'title'].some(k => params.has(k))){
  let t = params.get('add');
  if (t === null){
    const text = (params.get('text') || '').trim(), url = (params.get('url') || '').trim(), title = (params.get('title') || '').trim();
    t = [text || (url ? '' : title), url && !text.includes(url) ? url : ''].filter(Boolean).join('\n');
  }
  history.replaceState(null, '', location.pathname);
  addClip(t, true);
}
render();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
