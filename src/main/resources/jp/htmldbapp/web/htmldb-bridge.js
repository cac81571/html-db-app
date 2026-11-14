/*
 * HTML DB App: 画面配線スクリプト（ページ読込完了時にアプリが自動注入します）
 *  - id="btn_xxx" の要素がクリックされたら、name="in_*" の全項目を集めて sql/xxx.sql を実行
 *  - 結果は id="out_xxx" に、メッセージは id="msg" に表示
 *  - data-then="yyy"  : 成功後に sql/yyy.sql も続けて実行（カンマ区切りで複数可）
 *  - data-type="int"  : 入力値の型（int / number / date / datetime / time / bool / string）
 *  - data-confirm="..." : 実行前に確認ダイアログを表示（キャンセルで中止）
 *  - data-open="画面.html" : その画面を開く（btn_ ではないボタン。SQL は実行しない）
 *  - data-from="out_xxx"   : 開くとき、その結果欄で選択中の行の値を渡す
 *  - data-fields="列名=項目,..." : 結果の列を、同じ画面の name="in_項目" に対応付ける
 *  - data-row-edit="#id"  : 各行の先頭に「編集」。押すと data-fields の対応で入力欄へ入れる
 *  - data-row-delete="名前" : となりに「削除」。その行の値で sql/名前.sql を実行
 *  - data-row-then="名前"  : 行の削除が成功したあとに続けて実行
 *  - data-row-confirm="..." : 行削除の前の確認
 *  - data-clear="#id"      : その要素の中の in_* を空にして、そこへスクロールする。登録モードに戻す
 *  - data-focus="in_xxx"   : data-clear のあと、その入力欄にカーソルを置く
 *  - data-key              : キー項目。ラベルに ＊ を付け、空欄なら登録結果の位置に必須と出す
 *  - data-auto="名前"      : 登録のたびに sql/名前.sql の結果をキーへ入れる。欄は非活性で ＊ は付けない
 *  - data-increment        : DB の identity で採番するキー。欄は非活性で ＊ は付けない。INSERT 後に採番値を入れる
 *  - data-insert="名前"    : 登録モードで実行する sql/名前.sql（ボタン文言は「登録」）
 *  - data-update="名前"    : 更新モードで実行する sql/名前.sql（ボタン文言は「更新」）
 *  - body の data-keep : 別画面へ移る前の入力・結果・選択行を覚え、戻ったときに復元する
 */
(function () {
  if (window.__htmldb) { return; }
  var H = window.__htmldb = {};
  /** 結果欄 id → 選択中の行の値（項目名 → 文字列） */
  H.selection = {};
  /** 実行した SQL 名 → 結果を出す out_ の名前（ボタン id の btn_ 以降）。登録と更新で SQL が違っても同じ欄に出す */
  H.outputAlias = {};

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

  function trim(s) {
    return (s || '').replace(/^\s+|\s+$/g, '');
  }

  function tagOf(el) {
    return el && el.tagName ? el.tagName.toLowerCase() : '';
  }

  function ancestor(el, pred) {
    var t = el;
    while (t && t !== document) {
      if (pred(t)) { return t; }
      t = t.parentNode;
    }
    return null;
  }

  /** "顧客ID=id,名前=name" → { 顧客ID: "id", 名前: "name" }。未指定なら null（列名をそのまま項目名にする） */
  function fieldMap(host) {
    var attr = host.getAttribute('data-fields');
    if (attr == null || !trim(attr)) { return null; }
    var map = {};
    var parts = attr.split(',');
    for (var i = 0; i < parts.length; i++) {
      var p = trim(parts[i]);
      var eq = p.indexOf('=');
      if (eq <= 0) { continue; }
      map[trim(p.substring(0, eq))] = trim(p.substring(eq + 1));
    }
    return map;
  }

  function isEmptyRow(tr) {
    var cells = tr.children || [];
    for (var i = 0; i < cells.length; i++) {
      if ((cells[i].className || '').indexOf('htmldb-empty') >= 0) { return true; }
    }
    return false;
  }

  /** data-fields または data-open を持つ、行を包む結果欄 */
  function resultHost(tr) {
    return ancestor(tr, function (n) {
      return n.getAttribute && (n.getAttribute('data-fields') != null || n.getAttribute('data-open') != null);
    });
  }

  function readRow(tr, host) {
    var table = ancestor(tr, function (n) { return tagOf(n) === 'table'; });
    if (!table) { return {}; }
    var ths = table.getElementsByTagName('th');
    var cells = tr.children;
    var map = fieldMap(host);
    var params = {};
    var n = Math.min(ths.length, cells.length);
    for (var i = 0; i < n; i++) {
      var header = trim(ths[i].textContent);
      var key = map ? map[header] : header;
      if (!key) { continue; }
      var nullCell = (cells[i].className || '').indexOf('htmldb-null') >= 0;
      params[key] = nullCell ? '' : trim(cells[i].textContent);
    }
    return params;
  }

  function selectRow(tr, host) {
    var body = tr.parentNode;
    if (body && body.children) {
      for (var i = 0; i < body.children.length; i++) {
        var r = body.children[i];
        r.className = trim((r.className || '').replace(/\s*htmldb-selected\b/g, ''));
      }
    }
    tr.className = trim((tr.className ? tr.className + ' ' : '') + 'htmldb-selected');
    if (host.id) { H.selection[host.id] = readRow(tr, host); }
  }

  /** name="in_xxx" の項目をすべて集める */
  H.collect = function () {
    var params = {}, types = {};
    var els = document.querySelectorAll('[name^="in_"]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.disabled && el.getAttribute('data-key') == null) { continue; }
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
    if (el) {
      el.setAttribute('data-htmldb-busy', 'true');
      if (el.id && el.id.indexOf('btn_') === 0) { H.outputAlias[name] = el.id.substring(4); }
    }
    if (isEntryButton(el)) showEntryMessage(scopeOf(el), name + ' を実行中...', false);
    else H.msg(name + ' を実行中...', false);
    window.htmldbBridge.execute(name, JSON.stringify(data), then);
  };

  /** 別画面を開く。params は遷移先の in_* に入る */
  H.open = function (file, params) {
    window.htmldbBridge.openScreen(file, JSON.stringify(params || {}));
  };

  /** Java から呼ばれる: 渡された値を name="in_項目" にセットする */
  H.apply = function (params) {
    if (!params) { return; }
    for (var key in params) {
      if (!Object.prototype.hasOwnProperty.call(params, key)) { continue; }
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) { continue; }
      var el = document.querySelector('[name="in_' + key + '"]');
      if (!el || typeof el.value === 'undefined') { continue; }
      var text = params[key] == null ? '' : String(params[key]);
      var inputType = (el.type || '').toLowerCase();
      if (inputType === 'date') {
        var m = text.match(/^(\d{4}-\d{2}-\d{2})/);
        if (m) { text = m[1]; }
      } else if (inputType === 'datetime-local') {
        text = text.replace(' ', 'T').substring(0, 16);
      }
      el.value = text;
    }
  };

  /**
   * この画面の状態を JSON 文字列で返す。body に data-keep が無い画面は空文字（覚えない）。
   * Java が画面を離れる直前に呼ぶ。
   */
  H.capture = function () {
    if (!document.body || document.body.getAttribute('data-keep') == null) return '';
    var data = H.collect();
    var outputs = {};
    var nodes = document.querySelectorAll('[id^="out_"]');
    for (var i = 0; i < nodes.length; i++) outputs[nodes[i].id] = nodes[i].innerHTML;
    var msg = document.getElementById('msg');
    return JSON.stringify({
      params: data.params,
      outputs: outputs,
      selection: H.selection,
      scroll: window.pageYOffset || document.documentElement.scrollTop || 0,
      msg: msg ? msg.textContent : '',
      msgError: !!(msg && (msg.className || '').indexOf('htmldb-msg-error') >= 0)
    });
  };

  /** Java から呼ばれる: capture した状態を戻す（結果表の選択色も HTML ごと戻る） */
  H.restore = function (state) {
    if (!state) return;
    H.apply(state.params || {});
    var outputs = state.outputs || {};
    for (var id in outputs) {
      if (!Object.prototype.hasOwnProperty.call(outputs, id)) continue;
      if (!/^out_[A-Za-z0-9_\-]+$/.test(id)) continue;
      var el = document.getElementById(id);
      if (el) {
        el.innerHTML = outputs[id];
        decorateRows(el);
      }
    }
    H.selection = state.selection || {};
    if (state.msg) H.msg(state.msg, !!state.msgError);
    var y = state.scroll || 0;
    if (y) window.scrollTo(0, y);
  };

  H.msg = function (text, isError) {
    var m = document.getElementById('msg');
    if (m) {
      m.textContent = text;
      m.className = isError ? 'htmldb-msg-error' : 'htmldb-msg-ok';
    }
  };

  /** Java から呼ばれる: 結果を out_xxx に表示。out_xxx があれば true を返す */
  H.done = function (name, ok, html, message, generatedKey) {
    var shown = H.outputAlias[name] || name;
    var btn = document.getElementById('btn_' + shown) || document.getElementById('btn_' + name);
    if (btn) { btn.removeAttribute('data-htmldb-busy'); }
    var out = document.getElementById('out_' + shown) || document.getElementById('out_' + name);
    if (!(isEntryButton(btn) && out)) H.msg(message, !ok);
    if (ok) {
      applyGeneratedKey(btn, generatedKey);
      enterUpdateAfterInsert(btn);
    }
    if (out) {
      out.innerHTML = html;
      delete H.selection[out.id];
      decorateRows(out);
    }
    try {
      document.dispatchEvent(new CustomEvent('htmldb:done', { detail: { name: name, ok: ok, message: message } }));
    } catch (e) { /* 古いエンジン向け */ }
    return !!out;
  };

  function rowButton(label, action, cls) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = cls;
    b.textContent = label;
    b.setAttribute('data-row-action', action);
    return b;
  }

  /** data-row-edit / data-row-delete がある結果表の先頭に、行ごとのボタンを足す */
  function decorateRows(out) {
    if (!out || !out.getAttribute) return;
    var editTo = out.getAttribute('data-row-edit');
    var delSql = out.getAttribute('data-row-delete');
    if (editTo == null && !delSql) return;
    var table = out.querySelector('table.htmldb-result');
    if (!table || table.querySelector('th.htmldb-actions')) return;
    var headRow = table.querySelector('thead tr');
    if (headRow) {
      var th = document.createElement('th');
      th.className = 'htmldb-actions';
      th.textContent = '操作';
      headRow.insertBefore(th, headRow.firstChild);
    }
    var rows = table.querySelectorAll('tbody tr');
    for (var i = 0; i < rows.length; i++) {
      var tr = rows[i];
      if (isEmptyRow(tr)) {
        var cell = tr.querySelector('td');
        if (cell) cell.colSpan = (cell.colSpan || 1) + 1;
        continue;
      }
      var td = document.createElement('td');
      td.className = 'htmldb-actions';
      if (editTo != null) td.appendChild(rowButton('編集', 'edit', 'small'));
      if (delSql) td.appendChild(rowButton('削除', 'delete', 'small danger'));
      tr.insertBefore(td, tr.firstChild);
    }
  }

  function onRowAction(button) {
    var tr = ancestor(button, function (n) { return tagOf(n) === 'tr'; });
    var host = ancestor(button, function (n) {
      return n.getAttribute && (n.getAttribute('data-row-edit') != null || n.getAttribute('data-row-delete'));
    });
    if (!tr || !host) return;
    var action = button.getAttribute('data-row-action');
    if (action === 'edit') {
      H.apply(readRow(tr, host));
      var sel = host.getAttribute('data-row-edit') || '';
      if (sel.charAt(0) === '#' || sel.charAt(0) === '.') {
        var form = document.querySelector(sel);
        if (form && form.scrollIntoView) {
          try { form.scrollIntoView({ block: 'nearest' }); }
          catch (err) { form.scrollIntoView(); }
        }
        setEntryMode(form, 'update');
      }
      return;
    }
    if (action !== 'delete') return;
    var sql = host.getAttribute('data-row-delete') || '';
    if (!/^[A-Za-z0-9_\-]+$/.test(sql)) return;
    var confirmMsg = host.getAttribute('data-row-confirm') || 'この行を削除します。よろしいですか？';
    if (!window.confirm(confirmMsg)) return;
    var row = readRow(tr, host);
    var data = H.collect();
    for (var k in row) {
      if (Object.prototype.hasOwnProperty.call(row, k)) data.params[k] = row[k];
    }
    if (row.id) data.types.id = 'int';
    var thenName = host.getAttribute('data-row-then') || '';
    if (thenName && !/^[A-Za-z0-9_\-, ]+$/.test(thenName)) thenName = '';
    H.msg(sql + ' を実行中...', false);
    window.htmldbBridge.execute(sql, JSON.stringify(data), thenName);
  }

  // クリックをまとめて受ける（後から追加されたボタンにも効く）。form の submit 遷移も止める
  document.addEventListener('click', function (e) {
    var rowBtn = ancestor(e.target, function (n) {
      return tagOf(n) === 'button' && n.getAttribute && n.getAttribute('data-row-action');
    });
    if (rowBtn) {
      e.preventDefault();
      onRowAction(rowBtn);
      return;
    }
    var clearBtn = ancestor(e.target, function (n) {
      return tagOf(n) === 'button' && n.getAttribute && n.getAttribute('data-clear') != null;
    });
    if (clearBtn) {
      e.preventDefault();
      var root = document.querySelector(clearBtn.getAttribute('data-clear'));
      if (!root) return;
      var inputs = root.querySelectorAll('[name^="in_"]');
      for (var i = 0; i < inputs.length; i++) {
        if (typeof inputs[i].value !== 'undefined') inputs[i].value = '';
      }
      if (root.scrollIntoView) {
        try { root.scrollIntoView({ block: 'nearest' }); }
        catch (err) { root.scrollIntoView(); }
      }
      var focusName = clearBtn.getAttribute('data-focus') || '';
      if (/^in_[A-Za-z0-9_]+$/.test(focusName)) {
        var focusEl = root.querySelector('[name="' + focusName + '"]');
        if (focusEl && focusEl.focus) focusEl.focus();
      }
      setEntryMode(root, 'insert');
      clearEntryMessage(root);
      return;
    }
    var btn = ancestor(e.target, function (n) { return n.id && n.id.indexOf('btn_') === 0; });
    if (btn) {
      e.preventDefault();
      if (btn.getAttribute('data-htmldb-busy')) return;
      startEntry(btn);
      return;
    }
    var openBtn = ancestor(e.target, function (n) {
      return tagOf(n) === 'button' && n.getAttribute && n.getAttribute('data-open');
    });
    if (openBtn) {
      e.preventDefault();
      var from = openBtn.getAttribute('data-from');
      var params = {};
      if (from) {
        params = H.selection[from];
        if (!params) {
          window.alert('一覧で行を選択してください。');
          return;
        }
      }
      H.open(openBtn.getAttribute('data-open'), params);
      return;
    }
  }, true);

  document.addEventListener('submit', function (e) { e.preventDefault(); }, true);

  function addClass(el, name) {
    if (!el) return;
    var current = ' ' + (el.className || '') + ' ';
    if (current.indexOf(' ' + name + ' ') >= 0) return;
    el.className = trim((el.className || '') + ' ' + name);
  }

  function removeClass(el, name) {
    if (!el) return;
    el.className = trim((el.className || '').replace(new RegExp('(?:^|\\s)' + name + '(?!\\S)', 'g'), ' '));
  }

  function scopeOf(el) {
    return ancestor(el, function (n) { return tagOf(n) === 'fieldset'; }) || document;
  }

  function fieldLabel(el) {
    var row = ancestor(el, function (n) {
      return n.className && String(n.className).indexOf('row') >= 0;
    });
    if (row) {
      var inRow = row.querySelector('label');
      if (inRow) return inRow;
    }
    var prev = el.previousElementSibling;
    while (prev && tagOf(prev) !== 'label') prev = prev.previousElementSibling;
    return prev;
  }

  function keyEmpty(el) {
    var type = (el.type || '').toLowerCase();
    if (type === 'checkbox' || type === 'radio') return !el.checked;
    return !trim(el.value);
  }

  function isIncrement(el) {
    return !!(el && el.getAttribute && el.getAttribute('data-increment') != null);
  }

  /** DB が採番した値を、空欄の data-increment へ入れて更新モードにする */
  function applyGeneratedKey(btn, value) {
    if (value == null || trim(String(value)) === '') return;
    if (!isEntryButton(btn)) return;
    if ((btn.getAttribute('data-mode') || 'insert') === 'update') return;
    var root = scopeOf(btn);
    var keys = root.querySelectorAll('[data-key][data-increment]');
    for (var i = 0; i < keys.length; i++) {
      if (!keyEmpty(keys[i])) continue;
      keys[i].value = String(value);
      return;
    }
  }

  /** 登録に成功し、キーが揃っていれば編集モードにする */
  function enterUpdateAfterInsert(btn) {
    if (!isEntryButton(btn)) return;
    if ((btn.getAttribute('data-mode') || 'insert') === 'update') return;
    var root = scopeOf(btn);
    var keys = root.querySelectorAll('[data-key]');
    if (!keys.length) return;
    for (var i = 0; i < keys.length; i++) {
      if (keyEmpty(keys[i])) return;
    }
    setEntryMode(root, 'update');
  }

  function isAutoKey(el) {
    return isIncrement(el) || autoSqlName(el) !== '';
  }

  /** 登録モードなら手入力キーは入力可、更新モードなら読み取り専用。自動採番は常に非活性 */
  function setEntryMode(root, mode) {
    if (!root || !root.querySelectorAll) return;
    var update = mode === 'update';
    var keys = root.querySelectorAll('[data-key]');
    for (var i = 0; i < keys.length; i++) {
      var el = keys[i];
      markKeyLabel(fieldLabel(el), el);
      addClass(el, 'htmldb-key');
      if (isAutoKey(el)) {
        el.disabled = true;
        if ('readOnly' in el) el.readOnly = false;
        addClass(el, 'htmldb-readonly');
      } else if (update) {
        el.disabled = false;
        if (tagOf(el) === 'select') el.disabled = true;
        else el.readOnly = true;
        addClass(el, 'htmldb-readonly');
      } else {
        el.disabled = false;
        if ('readOnly' in el) el.readOnly = false;
        removeClass(el, 'htmldb-readonly');
      }
    }
    var buttons = root.querySelectorAll('button[data-insert], button[data-update]');
    for (var b = 0; b < buttons.length; b++) {
      buttons[b].setAttribute('data-mode', update ? 'update' : 'insert');
      buttons[b].textContent = update ? '更新' : '登録';
    }
  }

  function sqlForButton(btn) {
    var insertSql = btn.getAttribute('data-insert');
    var updateSql = btn.getAttribute('data-update');
    var name = btn.id.substring(4);
    if (!insertSql && !updateSql) return name;
    var mode = btn.getAttribute('data-mode') || 'insert';
    var sql = mode === 'update' ? (updateSql || insertSql || name) : (insertSql || updateSql || name);
    if (!/^[A-Za-z0-9_\-]+$/.test(sql)) return '';
    return sql;
  }

  /** 手入力のキーだけ、ラベルの末尾に ＊ を付ける。自動採番には付けない。 */
  function markKeyLabel(label, el) {
    if (!label) return;
    var old = label.querySelector('.htmldb-key-mark');
    if (el && isAutoKey(el)) {
      if (old && old.parentNode) old.parentNode.removeChild(old);
      return;
    }
    if (old) return;
    addClass(label, 'htmldb-key');
    var mark = document.createElement('span');
    mark.className = 'htmldb-key-mark';
    mark.textContent = '＊';
    label.appendChild(mark);
  }

  function labelCaption(label) {
    if (!label) return '';
    var text = '';
    for (var n = label.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3) text += n.nodeValue;
    }
    return trim(text);
  }

  function isEntryButton(el) {
    return !!(el && el.getAttribute && (el.getAttribute('data-insert') || el.getAttribute('data-update')));
  }

  /** 登録／更新ボタンに対応する結果欄（登録パネルの上） */
  function entryOutput(root) {
    var btn = root && root.querySelector ? root.querySelector('button[data-insert], button[data-update]') : null;
    if (!btn || !btn.id || btn.id.indexOf('btn_') !== 0) return null;
    return document.getElementById('out_' + btn.id.substring(4));
  }

  function showEntryMessage(root, text, isError) {
    var out = entryOutput(root);
    if (!out) {
      H.msg(text, isError);
      return;
    }
    out.innerHTML = '';
    var div = document.createElement('div');
    div.className = isError ? 'htmldb-error' : 'htmldb-update';
    div.textContent = text;
    out.appendChild(div);
  }

  function clearEntryMessage(root) {
    var out = entryOutput(root);
    if (out) out.innerHTML = '';
  }

  function inInsertMode(root) {
    var btn = root && root.querySelector ? root.querySelector('button[data-insert], button[data-update]') : null;
    if (!btn) return true;
    return (btn.getAttribute('data-mode') || 'insert') !== 'update';
  }

  function autoSqlName(el) {
    var name = el.getAttribute('data-auto');
    return name == null ? '' : trim(name);
  }

  /** 登録のたびに採番し直すキー。欄に値が入っていても手入力とはみなさない */
  function firstAuto(root, filled) {
    if (!inInsertMode(root)) return null;
    var keys = root.querySelectorAll('[data-key][data-auto]');
    for (var i = 0; i < keys.length; i++) {
      if (isIncrement(keys[i])) continue;
      if (!autoSqlName(keys[i]) || filled[keys[i].name]) continue;
      return keys[i];
    }
    return null;
  }

  /** 空のキーがあれば、登録パネルの上にメッセージを出して false。自動採番の空欄は登録時だけ許す */
  function requireKeys(root) {
    var keys = root.querySelectorAll('[data-key]');
    var allowAuto = inInsertMode(root);
    var missing = [];
    var first = null;
    for (var i = 0; i < keys.length; i++) {
      if (!keyEmpty(keys[i])) continue;
      if (allowAuto && (autoSqlName(keys[i]) || isIncrement(keys[i]))) continue;
      var label = fieldLabel(keys[i]);
      var caption = labelCaption(label) || keys[i].name;
      missing.push(caption + 'は必須です');
      if (!first) first = keys[i];
    }
    if (!first) return true;
    showEntryMessage(root, missing.join('\n'), true);
    if (first.focus) first.focus();
    return false;
  }

  /** 登録ボタン。data-auto のキーは毎回採番してから SQL を実行する */
  function startEntry(btn, filled) {
    var sqlName = sqlForButton(btn);
    if (!sqlName) return;
    var root = scopeOf(btn);
    filled = filled || {};
    var key = isEntryButton(btn) ? firstAuto(root, filled) : null;
    if (key) {
      var autoSql = autoSqlName(key);
      if (!/^[A-Za-z0-9_\-]+$/.test(autoSql)) {
        showEntryMessage(root, '自動採番の SQL 名が不正です', true);
        return;
      }
      btn.setAttribute('data-htmldb-busy', 'true');
      showEntryMessage(root, '自動採番しています...', false);
      H.autoPending = { btn: btn, key: key, filled: filled };
      window.htmldbBridge.scalar(autoSql, JSON.stringify(H.collect()));
      return;
    }
    if (isEntryButton(btn) && !requireKeys(root)) return;
    H.run(sqlName, btn);
  }

  /** Java から呼ばれる: 自動採番 SQL の先頭の値をキーへ入れて、登録を続ける */
  H.autoDone = function (value, error) {
    var pending = H.autoPending;
    H.autoPending = null;
    if (!pending || !pending.btn) return;
    var btn = pending.btn;
    btn.removeAttribute('data-htmldb-busy');
    var root = scopeOf(btn);
    if (error) {
      showEntryMessage(root, error, true);
      return;
    }
    if (value == null || trim(String(value)) === '') {
      showEntryMessage(root, '自動採番の結果がありません', true);
      return;
    }
    pending.key.value = String(value);
    var filled = pending.filled || {};
    filled[pending.key.name] = true;
    startEntry(btn, filled);
  };

  var modeButtons = document.querySelectorAll('button[data-insert], button[data-update]');
  for (var m = 0; m < modeButtons.length; m++) setEntryMode(scopeOf(modeButtons[m]), 'insert');

  window.htmldbBridge.log('info', '配線完了: ボタン ' + document.querySelectorAll('[id^="btn_"]').length
      + ' 個, 入力項目 ' + document.querySelectorAll('[name^="in_"]').length
      + ' 個, 画面遷移 ' + document.querySelectorAll('[data-open]').length + ' 個');
})();
