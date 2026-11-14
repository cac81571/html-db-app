package jp.htmldbapp;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import jp.htmldbapp.core.ParamConverter;
import jp.htmldbapp.core.SqlScript;
import org.junit.jupiter.api.Test;

/** SQL 解析とパラメータ型変換のテスト */
class SqlScriptTest {

    @Test
    void パラメータ名を抽出する() {
        String sql = "SELECT * FROM t WHERE a = :a AND b = :b -- :commentParam\n"
                + " AND c = ':notParam' AND d::text = :a /* :blockParam */ AND e = :e_1";
        assertEquals(List.of("a", "b", "e_1"), SqlScript.paramNames(sql));
    }

    @Test
    void 複数文に分割する() {
        String sql = "INSERT INTO t VALUES ('a;b'); -- x;y\nUPDATE t SET v = 1;\n/* ; */\n  ;SELECT 1";
        List<String> s = SqlScript.split(sql);
        assertEquals(3, s.size());
        assertTrue(s.get(0).contains("'a;b'"));
        assertTrue(s.get(2).startsWith("SELECT"));
    }

    @Test
    void 問い合わせか判定する() {
        assertTrue(SqlScript.isQuery("-- comment\n select 1"));
        assertTrue(SqlScript.isQuery("WITH x AS (SELECT 1) SELECT * FROM x"));
        assertTrue(SqlScript.isQuery("(SELECT 1)"));
        assertFalse(SqlScript.isQuery("INSERT INTO t VALUES (1)"));
        assertFalse(SqlScript.isQuery("/* select */ DELETE FROM t"));
    }

    @Test
    void 型変換() {
        assertNull(ParamConverter.convert("", "int"));
        assertNull(ParamConverter.convert("", null));
        assertEquals(42L, ParamConverter.convert("42", "int"));
        assertEquals(new BigDecimal("1.50"), ParamConverter.convert("1.50", "number"));
        assertEquals(Date.valueOf("2025-01-02"), ParamConverter.convert("2025-01-02", "date"));
        assertEquals(Timestamp.valueOf("2025-01-02 03:04:00"), ParamConverter.convert("2025-01-02T03:04", "datetime"));
        assertEquals(Boolean.TRUE, ParamConverter.convert("on", "bool"));
        assertEquals("abc", ParamConverter.convert("abc", "string"));
        assertThrows(IllegalArgumentException.class, () -> ParamConverter.convert("x", "int"));
    }
}
