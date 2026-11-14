package jp.htmldbapp.web;

import java.util.function.BiConsumer;

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
    private final BiConsumer<String, String> logger;

    public JsBridge(Handler handler, BiConsumer<String, String> logger) {
        this.handler = handler;
        this.logger = logger;
    }

    /** JS: window.htmldbBridge.execute('searchCustomer', '{"params":{...}}', 'otherSql') */
    public void execute(String sqlName, String screenJson, String then) {
        handler.execute(sqlName, screenJson, then == null ? "" : then);
    }

    /** JS: window.htmldbBridge.log('info', 'message') — 画面側からログ出力 */
    public void log(String level, String message) {
        logger.accept(level, message);
    }
}
