package jp.htmldbapp.core

import groovy.transform.CompileStatic

/** SQL 実行結果。SELECT なら列と行、更新系なら更新件数を持つ。 */
@CompileStatic
class ExecResult {
    /** true: 結果セットあり（SELECT 等） / false: 更新系（INSERT/UPDATE/DELETE/DDL） */
    boolean query
    List<String> columns = []
    List<List<Object>> rows = []
    /** 行数上限で切り捨てた場合 true */
    boolean truncated
    /** 更新件数（複数文の場合は合計） */
    int updateCount
    /** 最後の INSERT が返した採番キー。ドライバが返さないときは null */
    Object generatedKey
    /** 実行した文の数 */
    int statementCount
    long elapsedMillis

    String summary() {
        if (query) {
            return "${rows.size()} 件取得しました${truncated ? '（上限で打ち切り）' : ''}（${elapsedMillis} ms）"
        }
        return "${updateCount} 件更新しました（${statementCount} 文実行, ${elapsedMillis} ms）"
    }
}
