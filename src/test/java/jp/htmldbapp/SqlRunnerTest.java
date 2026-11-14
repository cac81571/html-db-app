package jp.htmldbapp;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jp.htmldbapp.core.ExecResult;
import jp.htmldbapp.core.ResultRenderer;
import jp.htmldbapp.core.SqlRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** インメモリ H2 に対する SQL 実行のテスト */
class SqlRunnerTest {

    private Connection con;
    private final SqlRunner runner = new SqlRunner();
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        con = DriverManager.getConnection("jdbc:h2:mem:runner;DB_CLOSE_DELAY=-1", "sa", "");
        runner.setWarn(new groovy.lang.Closure<Void>(this) {
            @SuppressWarnings("unused")
            public Void doCall(String m) {
                warnings.add(m);
                return null;
            }
        });
        runner.execute(con, "DROP TABLE IF EXISTS item; CREATE TABLE item(id INT PRIMARY KEY, name VARCHAR(50), memo VARCHAR(50))", Map.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        con.close();
    }

    @Test
    void 名前付きパラメータでINSERTとSELECT() {
        Map<String, Object> p = new HashMap<>();
        p.put("id", 1L);
        p.put("name", "りんご<&>");
        ExecResult ins = runner.execute(con, "INSERT INTO item(id, name) VALUES (:id, :name)", p);
        assertFalse(ins.isQuery());
        assertEquals(1, ins.getUpdateCount());

        ExecResult sel = runner.execute(con, "SELECT id, name, memo FROM item WHERE name = :name OR id = :id", p);
        assertTrue(sel.isQuery());
        assertEquals(List.of("ID", "NAME", "MEMO"), sel.getColumns());
        assertEquals(1, sel.getRows().size());
        assertEquals("りんご<&>", sel.getRows().get(0).get(1));
        assertNull(sel.getRows().get(0).get(2));

        String html = ResultRenderer.toHtml(sel);
        assertTrue(html.contains("りんご&lt;&amp;&gt;"), "HTML エスケープされること");
        assertTrue(html.contains("htmldb-null"));
    }

    @Test
    void 画面に無いパラメータはNULLで補完して警告() {
        ExecResult r = runner.execute(con, "INSERT INTO item(id, name, memo) VALUES (:id, :name, :memo)",
                Map.of("id", 2L, "name", "みかん"));
        assertEquals(1, r.getUpdateCount());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains(":memo"));
    }

    @Test
    void 同じパラメータを複数回使える_リテラル内のコロンは無視() {
        runner.execute(con, "INSERT INTO item VALUES (1,'a','12:30'),(2,'b',NULL)", Map.of());
        Map<String, Object> p = new HashMap<>();
        p.put("q", null);
        ExecResult r = runner.execute(con,
                "SELECT id FROM item WHERE (CAST(:q AS VARCHAR) IS NULL OR name = CAST(:q AS VARCHAR)) AND COALESCE(memo,'12:30') = '12:30' ORDER BY id", p);
        assertEquals(2, r.getRows().size());
        p.put("q", "b");
        r = runner.execute(con,
                "SELECT id FROM item WHERE (CAST(:q AS VARCHAR) IS NULL OR name = CAST(:q AS VARCHAR)) AND COALESCE(memo,'12:30') = '12:30' ORDER BY id", p);
        assertEquals(1, r.getRows().size());
        assertEquals(2, r.getRows().get(0).get(0));
    }

    @Test
    void 複数文は最後のSELECT結果と合計更新件数() {
        ExecResult r = runner.execute(con,
                "INSERT INTO item(id,name) VALUES (:a,'x'); INSERT INTO item(id,name) VALUES (:b,'y'); SELECT COUNT(*) AS cnt FROM item",
                Map.of("a", 10L, "b", 11L));
        assertTrue(r.isQuery());
        assertEquals(2, r.getUpdateCount());
        assertEquals(3, r.getStatementCount());
        assertEquals(2L, ((Number) r.getRows().get(0).get(0)).longValue());
    }

    @Test
    void 行数上限() {
        runner.setMaxRows(3);
        ExecResult r = runner.execute(con, "SELECT X FROM SYSTEM_RANGE(1, 10)", Map.of());
        assertEquals(3, r.getRows().size());
        assertTrue(r.isTruncated());
    }

    @Test
    void SQLエラーは例外になる() {
        assertThrows(Exception.class, () -> runner.execute(con, "SELECT * FROM no_such_table", Map.of()));
    }
}
