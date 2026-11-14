/*
 * HTML DB App: 画面配線スクリプト（ページ読込完了時にアプリが自動注入します）
 *  - id="btn_xxx" の要素がクリックされたら、name="in_*" の全項目を集めて sql/xxx.sql を実行
 *  - 結果は id="out_xxx" に、メッセージは id="msg" に表示
 *  - data-then="yyy"  : 成功後に sql/yyy.sql も続けて実行（カンマ区切りで複数可）
 *  - data-type="int"  : 入力値の型（int / number / date / datetime / time / bool / string）
 *  - data-confirm="..." : 実行前に確認ダイアログを表示（キャンセルで中止）
 */
(function () {
  if (window.__htmldb) { return; }
  var H = window.__htmldb = {};

  function typeOf(el) {
    var dt = el.getAttribute('data-type');
    if (dt) { return dt; }
    var t = (el.type || '').toLowerCase();
    if (t === 'number') { return 'number'; }
    if (t === 'date') { return 'date'; }
    if (t === 'datetime-local') { return 'datetime'; }
    if (t === 'time') { return 'time'; }
    return null;
  }

  /** name="in_xxx" の項目をすべて集める */
  H.collect = function () {
    var params = {}, types = {};
    var els = document.querySelectorAll('[name^="in_"]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.disabled) { continue; }
      var key = el.name.substring(3);
      var tag = el.tagName.toLowerCase();
      var type = (el.type || '').toLowerCase();
      var v;
      if (type === 'checkbox') {
        if (!(key in params)) { params[key] = null; }
        if (!el.checked) { continue; }
        v = (el.value === 'on') ? 'true' : el.value;
        params[key] = (params[key] == null) ? v : params[key] + ',' + v;
        continue;
      } else if (type === 'radio') {
        if (!(key in params)) { params[key] = null; }
        if (!el.checked) { continue; }
        v = el.value;
      } else if (tag === 'select' && el.multiple) {
        var vals = [];
        for (var j = 0; j < el.options.length; j++) {
          if (el.options[j].selected) { vals.push(el.options[j].value); }
        }
        v = vals.join(',');
      } else if (typeof el.value !== 'undefined') {
        v = el.value;
      } else {
        v = el.textContent;
      }
      params[key] = v;
      var dt = typeOf(el);
      if (dt) { types[key] = dt; }
    }
    return { params: params, types: types };
  };

  /** SQL を実行する（画面の JS から H.run('searchCustomer') のように直接呼ぶことも可能） */
  H.run = function (name, el) {
    var data = H.collect();
    var then = el ? (el.getAttribute('data-then') || '') : '';
    var confirmMsg = el ? el.getAttribute('data-confirm') : null;
    if (confirmMsg && !window.confirm(confirmMsg)) { return; }
    if (el) { el.setAttribute('data-htmldb-busy', 'true'); }
    H.msg(name + ' を実行中...', false);
    window.htmldbBridge.execute(name, JSON.stringify(data), then);
  };

  H.msg = function (text, isError) {
    var m = document.getElementById('msg');
    if (m) {
      m.textContent = text;
      m.className = isError ? 'htmldb-msg-error' : 'htmldb-msg-ok';
    }
  };

  /** Java から呼ばれる: 結果を out_xxx に表示。out_xxx があれば true を返す */
  H.done = function (name, ok, html, message) {
    var btn = document.getElementById('btn_' + name);
    if (btn) { btn.removeAttribute('data-htmldb-busy'); }
    H.msg(message, !ok);
    var out = document.getElementById('out_' + name);
    if (out) { out.innerHTML = html; }
    try {
      document.dispatchEvent(new CustomEvent('htmldb:done', { detail: { name: name, ok: ok, message: message } }));
    } catch (e) { /* 古いエンジン向け */ }
    return !!out;
  };

  // クリックをまとめて受ける（後から追加されたボタンにも効く）。form の submit 遷移も止める
  document.addEventListener('click', function (e) {
    var t = e.target;
    while (t && t !== document) {
      if (t.id && t.id.indexOf('btn_') === 0) {
        e.preventDefault();
        H.run(t.id.substring(4), t);
        return;
      }
      t = t.parentNode;
    }
  }, true);
  document.addEventListener('submit', function (e) { e.preventDefault(); }, true);

  window.htmldbBridge.log('info', '配線完了: ボタン ' + document.querySelectorAll('[id^="btn_"]').length
      + ' 個, 入力項目 ' + document.querySelectorAll('[name^="in_"]').length + ' 個');
})();
