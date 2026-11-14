package jp.htmldbapp.web;

import groovy.json.JsonSlurper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import jp.htmldbapp.AppLog;
import jp.htmldbapp.config.ConnectionFactory;
import jp.htmldbapp.config.ConnectionInfo;
import jp.htmldbapp.core.ExecResult;
import jp.htmldbapp.core.NamingRules;
import jp.htmldbapp.core.ParamConverter;
import jp.htmldbapp.core.ResultRenderer;
import jp.htmldbapp.core.SqlRunner;

/**
 * ボタン押下時の処理本体（JavaFX に依存しないのでテスト可能）。
 * 1. SQL 名から sql/&lt;name&gt;.sql を読む
 * 2. 画面の値（JSON）をパラメータマップに変換（型変換・空文字→NULL）
 * 3. 選択中の接続先で実行し、結果を HTML に変換
 */
public class ActionService {

    /** 実行結果（画面へ返す内容） */
    public static final class Result {
        public final boolean ok;
        public final String html;
        public final String message;
        public final ExecResult exec;

        Result(boolean ok, String html, String message, ExecResult exec) {
            this.ok = ok;
            this.html = html;
            this.message = message;
            this.exec = exec;
        }
    }

    private final Path sqlDir;
    private final ConnectionFactory connectionFactory;
    private final Supplier<ConnectionInfo> activeConnection;
    private final SqlRunner runner = new SqlRunner();

    public ActionService(Path sqlDir, ConnectionFactory connectionFactory, Supplier<ConnectionInfo> activeConnection) {
        this.sqlDir = sqlDir;
        this.connectionFactory = connectionFactory;
        this.activeConnection = activeConnection;
        runner.setWarn(new groovy.lang.Closure<Void>(this) {
            @SuppressWarnings("unused")
            public Void doCall(String msg) {
                AppLog.warn(msg);
                return null;
            }
        });
    }

    public SqlRunner getRunner() { return runner; }

    /**
     * @param sqlName    SQL 名（btn_ を除いた部分）
     * @param screenJson 画面から送られる JSON: {"params":{"customerId":"1"}, "types":{"customerId":"int"}}
     */
    public Result run(String sqlName, String screenJson) {
        try {
            String fileName = NamingRules.sqlFileName(sqlName);
            Path file = sqlDir.resolve(fileName);
            if (!Files.isRegularFile(file)) {
                return error("SQL ファイルが見つかりません: sql/" + fileName, null);
            }
            String sqlText = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> params = parseParams(screenJson);
            AppLog.info("実行: sql/" + fileName + " パラメータ=" + params);

            ConnectionInfo info = activeConnection.get();
            try (Connection con = connectionFactory.open(info)) {
                ExecResult r = runner.execute(con, sqlText, params);
                String msg = sqlName + ": " + r.summary();
                AppLog.info(msg);
                return new Result(true, ResultRenderer.toHtml(r), msg, r);
            }
        } catch (Exception e) {
            return error(sqlName + " の実行でエラー: " + rootMessage(e), e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Object parsed = new JsonSlurper().parseText(json);
        if (!(parsed instanceof Map)) return new LinkedHashMap<>();
        Map<String, Object> root = (Map<String, Object>) parsed;
        Map<String, Object> rawParams = root.containsKey("params")
                ? (Map<String, Object>) root.get("params") : root;
        Map<String, String> types = root.containsKey("types")
                ? (Map<String, String>) root.get("types") : Collections.emptyMap();
        Map<String, Object> named = NamingRules.toSqlParams(rawParams);
        Map<String, String> namedTypes = new LinkedHashMap<>();
        types.forEach((k, v) -> {
            String p = k.startsWith(NamingRules.INPUT_PREFIX) ? NamingRules.paramNameFromInputName(k) : k;
            if (p != null) namedTypes.put(p, v);
        });
        return ParamConverter.convertAll(named, namedTypes);
    }

    private static Result error(String msg, Throwable t) {
        // SQL エラー・入力エラーは原因メッセージだけで十分なのでスタックトレースは出さない
        boolean expected = t == null || t instanceof IOException || t instanceof IllegalArgumentException
                || t instanceof java.sql.SQLException;
        AppLog.error(msg, expected ? null : t);
        String html = "<div class=\"htmldb-error\">" + ResultRenderer.escape(msg) + "</div>";
        return new Result(false, html, msg, null);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t && t.getMessage() == null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }
}
