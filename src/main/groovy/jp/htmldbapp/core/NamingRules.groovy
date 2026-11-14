package jp.htmldbapp.core

import groovy.transform.CompileStatic

/**
 * 画面項目と SQL を結び付けるネーミングルール。
 *
 * <ul>
 *   <li>ボタン   : id="btn_&lt;name&gt;"    → sql/&lt;name&gt;.sql を実行</li>
 *   <li>入力項目 : name="in_&lt;param&gt;" → SQL の名前付きパラメータ :&lt;param&gt; にバインド</li>
 *   <li>結果表示 : id="out_&lt;name&gt;"   → 実行結果（表 or 更新件数）を表示</li>
 *   <li>メッセージ: id="msg"            → 成功／エラーメッセージを表示（任意）</li>
 * </ul>
 */
@CompileStatic
final class NamingRules {

    public static final String BUTTON_PREFIX = 'btn_'
    public static final String INPUT_PREFIX  = 'in_'
    public static final String OUTPUT_PREFIX = 'out_'
    public static final String MESSAGE_ID    = 'msg'

    /** SQL 名として許可する文字（パストラバーサル防止のため英数字・_・- のみ） */
    private static final String NAME_PATTERN = /[A-Za-z0-9_\-]+/

    private NamingRules() {}

    /** ボタン id（btn_xxx）から SQL 名（xxx）を取り出す。ルール外なら null。 */
    static String sqlNameFromButtonId(String id) {
        stripPrefix(id, BUTTON_PREFIX)
    }

    /** 入力項目の name（in_xxx）からパラメータ名（xxx）を取り出す。ルール外なら null。 */
    static String paramNameFromInputName(String name) {
        stripPrefix(name, INPUT_PREFIX)
    }

    /** SQL 名から結果表示要素の id（out_xxx）を作る。 */
    static String outputId(String sqlName) {
        OUTPUT_PREFIX + sqlName
    }

    /** SQL 名から SQL ファイル名（xxx.sql）を作る。 */
    static String sqlFileName(String sqlName) {
        requireValidName(sqlName) + '.sql'
    }

    static boolean isValidName(String name) {
        name != null && name ==~ NAME_PATTERN
    }

    static String requireValidName(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("SQL 名に使えない文字が含まれています: ${name}")
        }
        name
    }

    private static String stripPrefix(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) return null
        String rest = value.substring(prefix.length())
        isValidName(rest) ? rest : null
    }

    /**
     * 画面から受け取った {name: value} のマップ（キーは in_ 付き／なしどちらでも可）を
     * SQL パラメータ名をキーにしたマップへ変換する。ルール外のキーは無視する。
     */
    static Map<String, Object> toSqlParams(Map<String, ?> screenValues) {
        Map<String, Object> result = new LinkedHashMap<>()
        screenValues?.each { String k, Object v ->
            String p = k.startsWith(INPUT_PREFIX) ? paramNameFromInputName(k) : (isValidName(k) ? k : null)
            if (p != null) result.put(p, v)
        }
        result
    }
}
