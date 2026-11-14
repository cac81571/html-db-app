package jp.htmldbapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.htmldbapp.core.ExecResult;
import jp.htmldbapp.core.SqlRunner;
import org.junit.jupiter.api.Test;

/** サンプル CSV編集 の SQL が、CSV の検索と書き戻しまでできること */
class CsvSampleTest {

    @Test
    void 検索して更新追加削除できる() throws Exception {
        Path folder = Path.of("target", "csv-sample", "CSV編集");
        Files.createDirectories(folder);
        Path csv = folder.resolve("items.csv");
        Files.copy(Path.of("CSV編集", "items.csv"), csv, StandardCopyOption.REPLACE_EXISTING);
        String path = csv.toAbsolutePath().toString().replace('\\', '/');

        ExecResult next = run("nextItemId.sql", path, params());
        assertEquals(5, ((Number) next.getRows().get(0).get(0)).intValue());

        ExecResult found = run("searchItem.sql", path, params("name", "ノート", "category", null));
        assertEquals(1, found.getRows().size());
        assertTrue(String.valueOf(found.getRows().get(0)).contains("ノートA5"));

        ExecResult updated = run("updateItem.sql", path, params(
                "id", 1, "itemName", "ノートB5", "itemCategory", "文具", "qty", 15, "note", "横罫"));
        assertEquals(1, updated.getUpdateCount());
        assertTrue(Files.readString(csv, StandardCharsets.UTF_8).contains("ノートB5"));

        ExecResult inserted = run("insertItem.sql", path, params(
                "id", 5, "itemName", "付箋", "itemCategory", "文具", "qty", 30, "note", null));
        assertEquals(1, inserted.getUpdateCount());

        ExecResult all = run("searchItem.sql", path, params("name", null, "category", null));
        assertEquals(5, all.getRows().size());

        ExecResult deleted = run("deleteItem.sql", path, params("id", 5));
        assertEquals(1, deleted.getUpdateCount());
        assertEquals(4, run("searchItem.sql", path, params("name", null, "category", null)).getRows().size());

        SQLException dup = assertThrows(SQLException.class, () -> run("insertItem.sql", path, params(
                "id", 1, "itemName", "重複", "itemCategory", "文具", "qty", 1, "note", null)));
        assertTrue(dup.getMessage().contains("プライマリキー") || dup.getMessage().toLowerCase().contains("primary"));
    }

    private static ExecResult run(String file, String csvPath, Map<String, Object> params) throws Exception {
        String sql = Files.readString(Path.of("CSV編集", "sql", file), StandardCharsets.UTF_8)
                .replace("CSV編集/items.csv", csvPath);
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:csvsample;DB_CLOSE_DELAY=-1", "sa", "")) {
            return new SqlRunner().execute(con, sql, params);
        }
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }
}
