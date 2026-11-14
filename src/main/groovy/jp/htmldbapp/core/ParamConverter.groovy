package jp.htmldbapp.core

import groovy.transform.CompileStatic

import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 画面から来た文字列値を SQL パラメータ用の型に変換する。
 * 型は HTML の data-type 属性（または input type="number" / "date" など）で指定する。
 * <ul>
 *   <li>空文字 → NULL</li>
 *   <li>int / integer / long → Long</li>
 *   <li>number / decimal / numeric → BigDecimal</li>
 *   <li>date → java.sql.Date（yyyy-MM-dd）</li>
 *   <li>datetime / timestamp → java.sql.Timestamp（yyyy-MM-ddTHH:mm[:ss] / yyyy-MM-dd HH:mm[:ss]）</li>
 *   <li>time → java.sql.Time（HH:mm[:ss]）</li>
 *   <li>bool / boolean → Boolean</li>
 *   <li>string / 指定なし → String</li>
 * </ul>
 */
@CompileStatic
final class ParamConverter {

    private ParamConverter() {}

    static Object convert(Object value, String type) {
        if (value == null) return null
        String s = value.toString()
        if (s.isEmpty()) return null
        String t = (type ?: 'string').toLowerCase(Locale.ROOT).trim()
        try {
            switch (t) {
                case ['int', 'integer', 'long', 'bigint']:
                    return Long.valueOf(s.trim())
                case ['number', 'decimal', 'numeric', 'double', 'float']:
                    return new BigDecimal(s.trim())
                case 'date':
                    return Date.valueOf(s.trim())
                case ['datetime', 'datetime-local', 'timestamp']:
                    String v = s.trim().replace(' ', 'T')
                    if (v.length() == 16) v += ':00'
                    return Timestamp.valueOf(LocalDateTime.parse(v))
                case 'time':
                    String v = s.trim()
                    if (v.length() == 5) v += ':00'
                    return Time.valueOf(v)
                case ['bool', 'boolean']:
                    return s.trim().toLowerCase(Locale.ROOT) in ['true', '1', 'on', 'yes']
                default:
                    return s
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("値「${s}」を型 ${t} に変換できません", e)
        }
    }

    /** パラメータマップ全体を変換する。types に無いものは文字列（空文字は NULL）。 */
    static Map<String, Object> convertAll(Map<String, ?> values, Map<String, String> types) {
        Map<String, Object> out = new LinkedHashMap<>()
        values?.each { String k, Object v -> out.put(k, convert(v, types?.get(k))) }
        out
    }
}
