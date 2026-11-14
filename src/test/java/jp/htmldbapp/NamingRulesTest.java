package jp.htmldbapp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.htmldbapp.core.NamingRules;
import org.junit.jupiter.api.Test;

/** ネーミングルール（名前の取り出し・パラメータ対応付け）のテスト */
class NamingRulesTest {

    @Test
    void ボタンidからSQL名を取り出す() {
        assertEquals("searchCustomer", NamingRules.sqlNameFromButtonId("btn_searchCustomer"));
        assertEquals("a_b-1", NamingRules.sqlNameFromButtonId("btn_a_b-1"));
        assertNull(NamingRules.sqlNameFromButtonId("button1"));
        assertNull(NamingRules.sqlNameFromButtonId("btn_"));
        assertNull(NamingRules.sqlNameFromButtonId("btn_../etc/passwd"));
        assertNull(NamingRules.sqlNameFromButtonId(null));
    }

    @Test
    void 入力nameからパラメータ名を取り出す() {
        assertEquals("customerId", NamingRules.paramNameFromInputName("in_customerId"));
        assertNull(NamingRules.paramNameFromInputName("customerId"));
        assertNull(NamingRules.paramNameFromInputName("in_"));
    }

    @Test
    void 出力idとSQLファイル名() {
        assertEquals("out_searchCustomer", NamingRules.outputId("searchCustomer"));
        assertEquals("searchCustomer.sql", NamingRules.sqlFileName("searchCustomer"));
        assertThrows(IllegalArgumentException.class, () -> NamingRules.sqlFileName("../x"));
    }

    @Test
    void 画面値をSQLパラメータに変換する() {
        Map<String, Object> screen = new LinkedHashMap<>();
        screen.put("in_name", "山田");
        screen.put("pref", "東京都");     // in_ なしのキーもそのまま許可
        screen.put("other field", "x");  // 不正な名前は無視
        Map<String, Object> p = NamingRules.toSqlParams(screen);
        assertEquals(Map.of("name", "山田", "pref", "東京都"), p);
    }
}
