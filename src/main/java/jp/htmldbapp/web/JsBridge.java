package jp.htmldbapp.web;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;

/**
 * JavaScript から呼ばれるブリッジ（window.htmldbBridge として公開）。
 * ※ WebEngine は弱参照でしか保持しないため、呼び出し側（ScreenPanel）でフィールドに強参照を保持すること。
 */
public class JsBridge {

    /** (sqlName, json, then) を受け取るハンドラ */
    public interface Handler {
        void execute(String sqlName, String screenJson, String then);
    }

    private final Handler handler;
    /** (画面ファイル名, 渡す値の JSON) */
    private final BiConsumer<String, String> opener;
    private final BiConsumer<String, String> logger;
    /** (sqlName, screenJson) 自動採番など、結果を画面の表に出さない SQL */
    private final BiConsumer<String, String> scalar;
    /** (sqlName, screenJson) 編集時に登録フォームへ入れる1行 */
    private final BiConsumer<String, String> rowLoad;

    public JsBridge(Handler handler, BiConsumer<String, String> opener, BiConsumer<String, String> logger,
            BiConsumer<String, String> scalar, BiConsumer<String, String> rowLoad) {
        this.handler = handler;
        this.opener = opener;
        this.logger = logger;
        this.scalar = scalar;
        this.rowLoad = rowLoad;
    }

    /** JS: window.htmldbBridge.scalar('nextCustomerId', '{"params":{...}}') — 結果の先頭を JS の autoDone へ返す */
    public void scalar(String sqlName, String screenJson) {
        scalar.accept(sqlName, screenJson == null ? "{}" : screenJson);
    }

    /** JS: window.htmldbBridge.load('loadCustomer', '{"params":{"id":"1"}}') — 先頭行を登録フォームへ */
    public void load(String sqlName, String screenJson) {
        rowLoad.accept(sqlName, screenJson == null ? "{}" : screenJson);
    }

    /** JS: window.htmldbBridge.execute('searchCustomer', '{"params":{...}}', 'otherSql') */
    public void execute(String sqlName, String screenJson, String then) {
        handler.execute(sqlName, screenJson, then == null ? "" : then);
    }

    /** JS: window.htmldbBridge.openScreen('customerForm.html', '{"id":"1","name":"山田"}') */
    public void openScreen(String fileName, String paramsJson) {
        opener.accept(fileName, paramsJson == null ? "{}" : paramsJson);
    }

    /** JS: window.htmldbBridge.log('info', 'message') — 画面側からログ出力 */
    public void log(String level, String message) {
        logger.accept(level, message);
    }

    /** JS: window.htmldbBridge.copy('山田') — 一覧セルの値をクリップボードへ */
    public void copy(String text) {
        String value = text == null ? "" : text;
        SwingUtilities.invokeLater(() -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(value), null));
    }
}
