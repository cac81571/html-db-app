package jp.htmldbapp.core

import groovy.transform.CompileStatic

/** SQL テキストの解析ユーティリティ（文の分割・パラメータ名抽出・種別判定）。 */
@CompileStatic
final class SqlScript {

    private SqlScript() {}

    /**
     * セミコロンで文を分割する。文字列リテラル（'...'、"..."）とコメント（--、/* *&#47;）内の ; は無視する。
     * 空の文は除外する。
     */
    static List<String> split(String text) {
        List<String> out = []
        if (text == null) return out
        StringBuilder cur = new StringBuilder()
        int n = text.length()
        int i = 0
        while (i < n) {
            char c = text.charAt(i)
            char next = i + 1 < n ? text.charAt(i + 1) : (char) 0
            if (c == (char) '\'' || c == (char) '"') {
                int j = i + 1
                while (j < n) {
                    if (text.charAt(j) == c) {
                        if (j + 1 < n && text.charAt(j + 1) == c) { j += 2; continue } // '' エスケープ
                        break
                    }
                    j++
                }
                cur.append(text, i, Math.min(j + 1, n))
                i = j + 1
            } else if (c == (char) '-' && next == (char) '-') {
                int j = text.indexOf('\n', i)
                if (j < 0) j = n
                cur.append(text, i, j)
                i = j
            } else if (c == (char) '/' && next == (char) '*') {
                int j = text.indexOf('*/', i + 2)
                j = j < 0 ? n : j + 2
                cur.append(text, i, j)
                i = j
            } else if (c == (char) ';') {
                addIfNotBlank(out, cur.toString())
                cur.setLength(0)
                i++
            } else {
                cur.append(c)
                i++
            }
        }
        addIfNotBlank(out, cur.toString())
        out
    }

    private static void addIfNotBlank(List<String> out, String s) {
        if (stripComments(s).trim()) out << s.trim()
    }

    /** コメントを除去する（文字列リテラルは保持）。 */
    static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder()
        int n = sql.length()
        int i = 0
        while (i < n) {
            char c = sql.charAt(i)
            char next = i + 1 < n ? sql.charAt(i + 1) : (char) 0
            if (c == (char) '\'' || c == (char) '"') {
                int j = i + 1
                while (j < n && sql.charAt(j) != c) j++
                sb.append(sql, i, Math.min(j + 1, n))
                i = j + 1
            } else if (c == (char) '-' && next == (char) '-') {
                int j = sql.indexOf('\n', i)
                i = j < 0 ? n : j
            } else if (c == (char) '/' && next == (char) '*') {
                int j = sql.indexOf('*/', i + 2)
                i = j < 0 ? n : j + 2
                sb.append(' ')
            } else {
                sb.append(c)
                i++
            }
        }
        sb.toString()
    }

    /** 文字列リテラルを空にした SQL（パラメータ抽出用）。 */
    private static String blankLiterals(String sql) {
        sql.replaceAll(/'(?:[^']|'')*'/, "''").replaceAll(/"[^"]*"/, '""')
    }

    /** SQL 中の名前付きパラメータ（:name）を出現順・重複なしで返す。PostgreSQL の :: キャストは除外。 */
    static List<String> paramNames(String sql) {
        String s = blankLiterals(stripComments(sql))
        LinkedHashSet<String> names = new LinkedHashSet<>()
        def m = s =~ /(?<![:\w]):([A-Za-z_]\w*)/
        while (m.find()) names << m.group(1)
        new ArrayList<String>(names)
    }

    /** 結果セットを返す文か（SELECT / WITH / VALUES / SHOW / EXPLAIN など）。 */
    static boolean isQuery(String sql) {
        String s = stripComments(sql).trim().replaceAll(/^\(+/, '').trim()
        String head = (s.split(/\s+/, 2)[0] ?: '').toUpperCase(Locale.ROOT)
        head in ['SELECT', 'WITH', 'VALUES', 'SHOW', 'EXPLAIN', 'TABLE', 'DESC', 'DESCRIBE']
    }
}
