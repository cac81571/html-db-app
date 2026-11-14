package jp.htmldbapp.core

import groovy.transform.CompileStatic

import java.text.SimpleDateFormat

/** 実行結果を HTML 断片に変換する（out_xxx 要素の innerHTML に入れる）。 */
@CompileStatic
final class ResultRenderer {

    private ResultRenderer() {}

    static String toHtml(ExecResult r) {
        if (!r.query) {
            return "<div class=\"htmldb-update\">${escape(r.summary())}</div>"
        }
        StringBuilder sb = new StringBuilder()
        sb.append('<table class="htmldb-result"><thead><tr>')
        r.columns.each { sb.append('<th>').append(escape(it)).append('</th>') }
        sb.append('</tr></thead><tbody>')
        if (r.rows.isEmpty()) {
            sb.append("<tr><td class=\"htmldb-empty\" colspan=\"${Math.max(r.columns.size(), 1)}\">該当データはありません</td></tr>")
        }
        r.rows.each { List<Object> row ->
            sb.append('<tr>')
            row.each { Object v ->
                if (v == null) sb.append('<td class="htmldb-null">NULL</td>')
                else sb.append('<td>').append(escape(format(v))).append('</td>')
            }
            sb.append('</tr>')
        }
        sb.append('</tbody></table>')
        sb.append("<div class=\"htmldb-count\">${escape(r.summary())}</div>")
        sb.toString()
    }

    static String format(Object v) {
        if (v instanceof java.sql.Timestamp) return new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format((Date) v)
        if (v instanceof BigDecimal) return ((BigDecimal) v).toPlainString()
        String.valueOf(v)
    }

    static String escape(String s) {
        if (s == null) return ''
        s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
         .replace('"', '&quot;').replace("'", '&#39;')
    }
}
