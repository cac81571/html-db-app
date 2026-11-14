package jp.htmldbapp.core

import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import groovy.transform.CompileStatic

import java.sql.Clob
import java.sql.Connection
import java.sql.ResultSetMetaData

/**
 * SQL ファイルの内容を名前付きパラメータ（:name）付きで実行する。
 * 実行には groovy.sql.Sql を使用する。
 */
@CompileStatic
class SqlRunner {

    /** SELECT 結果の最大取得行数 */
    int maxRows = 1000

    /** 警告ログの出力先（UI から差し替え可能） */
    Closure<Void> warn = { String msg -> System.err.println(msg); null } as Closure<Void>

    /**
     * SQL テキスト（複数文可）を実行する。
     * 複数文の場合は順に実行し、最後の SELECT の結果（なければ合計更新件数）を返す。
     *
     * @param connection JDBC 接続（クローズは呼び出し側の責任）
     * @param sqlText    SQL テキスト
     * @param params     パラメータ名 → 値（不足分は NULL として補完）
     */
    ExecResult execute(Connection connection, String sqlText, Map<String, Object> params) {
        long start = System.currentTimeMillis()
        List<String> statements = SqlScript.split(sqlText)
        if (statements.isEmpty()) throw new IllegalArgumentException('SQL が空です')

        Sql sql = new Sql(connection)
        ExecResult result = new ExecResult()
        ExecResult lastQuery = null
        int totalUpdates = 0
        Object generatedKey = null
        for (String stmt : statements) {
            Map<String, Object> bound = bindParams(stmt, params)
            if (SqlScript.isQuery(stmt)) {
                lastQuery = runQuery(sql, stmt, bound)
            } else if (isInsert(stmt)) {
                List<List<Object>> keys = bound.isEmpty() ? sql.executeInsert(stmt) : sql.executeInsert(bound, stmt)
                int count = keys == null ? 0 : keys.size()
                // 採番キーを返さないドライバでも、成功した INSERT は 1 件として数える
                if (count == 0) count = 1
                totalUpdates += count
                Object key = firstGenerated(keys)
                if (key != null) generatedKey = key
            } else {
                int count = bound.isEmpty() ? sql.executeUpdate(stmt) : sql.executeUpdate(bound, stmt)
                totalUpdates += Math.max(count, 0)
            }
        }
        if (lastQuery != null) result = lastQuery
        result.updateCount = totalUpdates
        result.generatedKey = generatedKey
        result.statementCount = statements.size()
        result.elapsedMillis = System.currentTimeMillis() - start
        result
    }

    private static boolean isInsert(String stmt) {
        String s = SqlScript.stripComments(stmt).trim()
        s.toUpperCase(Locale.ROOT).startsWith('INSERT')
    }

    private static Object firstGenerated(List<List<Object>> keys) {
        if (keys == null || keys.isEmpty()) return null
        List<Object> row = keys.get(keys.size() - 1)
        if (row == null || row.isEmpty() || row.get(0) == null) return null
        row.get(0)
    }

    /** 文中で使われているパラメータだけを取り出し、無いものは NULL で補う。 */
    Map<String, Object> bindParams(String stmt, Map<String, Object> params) {
        Map<String, Object> bound = new LinkedHashMap<>()
        for (String name : SqlScript.paramNames(stmt)) {
            if (params != null && params.containsKey(name)) {
                bound.put(name, params.get(name))
            } else {
                warn.call("パラメータ :${name} に対応する画面項目（name=\"in_${name}\"）がありません。NULL をバインドします。".toString())
                bound.put(name, null)
            }
        }
        bound
    }

    private ExecResult runQuery(Sql sql, String stmt, Map<String, Object> bound) {
        ExecResult r = new ExecResult(query: true)
        int limit = maxRows
        Closure meta = { ResultSetMetaData md ->
            for (int i = 1; i <= md.columnCount; i++) r.columns << (md.getColumnLabel(i) ?: md.getColumnName(i))
        }
        Closure row = { GroovyResultSet rs ->
            if (r.rows.size() >= limit) { r.truncated = true; return }
            List<Object> values = []
            for (int i = 0; i < r.columns.size(); i++) values << normalize(rs.getObject(i + 1)) // JDBC は 1 始まり
            r.rows << values
        }
        if (bound.isEmpty()) {
            sql.eachRow(stmt, meta, row)
        } else {
            sql.eachRow(bound, stmt, meta, row)
        }
        r
    }

    private static Object normalize(Object v) {
        if (v instanceof Clob) {
            Clob c = (Clob) v
            return c.getSubString(1L, (int) Math.min(c.length(), 100_000L))
        }
        if (v instanceof byte[]) return "(BLOB ${((byte[]) v).length} bytes)".toString()
        v
    }
}
