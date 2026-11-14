package jp.htmldbapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jp.htmldbapp.config.ConnectionFactory;
import jp.htmldbapp.config.ConnectionInfo;
import jp.htmldbapp.web.ActionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** サンプル画面と同じ流れ（JSON → パラメータ → sql/*.sql 実行 → HTML）を、同梱の sql/ フォルダで検証する */
class ActionServiceTest {

    @TempDir
    Path tmp;

    @Test
    void サンプルSQLで初期化_検索_登録_削除() {
        ConnectionInfo h2 = new ConnectionInfo("test", "jdbc:h2:mem:sample;DB_CLOSE_DELAY=-1", "sa", "", "");
        ActionService svc = new ActionService(Paths.get("顧客管理/sql"), new ConnectionFactory(tmp), () -> h2);

        ActionService.Result init = svc.run("initSample", "{}");
        assertTrue(init.ok, init.message);

        ActionService.Result next = svc.run("nextCustomerId", "{}");
        assertTrue(next.ok, next.message);
        assertEquals(5, ((Number) next.exec.getRows().get(0).get(0)).intValue());

        // 空欄（NULL）なら全件
        ActionService.Result all = svc.run("searchCustomer", "{\"params\":{\"name\":\"\",\"pref\":\"\"},\"types\":{}}");
        assertTrue(all.ok, all.message);
        assertEquals(4, all.exec.getRows().size());
        assertTrue(all.html.contains("<th>顧客ID</th>"));

        // 部分一致 + 都道府県
        ActionService.Result tokyo = svc.run("searchCustomer", "{\"params\":{\"name\":\"\",\"pref\":\"東京都\"}}");
        assertEquals(2, tokyo.exec.getRows().size());

        // 登録。日付は date 型
        ActionService.Result ins = svc.run("insertCustomer",
                "{\"params\":{\"id\":\"5\",\"newName\":\"伊藤 健\",\"newPref\":\"福岡県\",\"email\":\"\",\"joinedOn\":\"2026-11-14\"},"
                        + "\"types\":{\"id\":\"int\",\"joinedOn\":\"date\"}}");
        assertTrue(ins.ok, ins.message);
        assertEquals(1, ins.exec.getUpdateCount());
        assertTrue(ins.html.contains("1 件更新しました"));

        ActionService.Result ito = svc.run("searchCustomer", "{\"params\":{\"name\":\"伊藤\",\"pref\":\"\"}}");
        assertEquals(1, ito.exec.getRows().size());
        assertEquals(5, ((Number) ito.exec.getRows().get(0).get(0)).intValue());

        // 顧客ID をキーに上書き
        ActionService.Result upd = svc.run("updateCustomer",
                "{\"params\":{\"id\":\"5\",\"newName\":\"伊藤 次郎\",\"newPref\":\"福岡県\",\"email\":\"jiro@example.com\",\"joinedOn\":\"2026-11-14\"},"
                        + "\"types\":{\"id\":\"int\",\"joinedOn\":\"date\"}}");
        assertTrue(upd.ok, upd.message);
        assertEquals(1, upd.exec.getUpdateCount());

        ActionService.Result renamed = svc.run("searchCustomer", "{\"params\":{\"name\":\"次郎\",\"pref\":\"\"}}");
        assertEquals(1, renamed.exec.getRows().size());
        assertEquals("伊藤 次郎", renamed.exec.getRows().get(0).get(1));

        ActionService.Result del = svc.run("deleteCustomer", "{\"params\":{\"id\":\"5\"},\"types\":{\"id\":\"int\"}}");
        assertEquals(1, del.exec.getUpdateCount());
    }

    @Test
    void エラー時はokがfalseでメッセージを返す() throws Exception {
        Files.writeString(tmp.resolve("bad.sql"), "SELECT * FROM no_such_table");
        ConnectionInfo h2 = new ConnectionInfo("test", "jdbc:h2:mem:err", "sa", "", "");
        ActionService svc = new ActionService(tmp, new ConnectionFactory(tmp), () -> h2);

        ActionService.Result r = svc.run("bad", "{}");
        assertFalse(r.ok);
        assertTrue(r.message.contains("NO_SUCH_TABLE"), r.message);
        assertTrue(r.html.contains("htmldb-error"));

        ActionService.Result missing = svc.run("nothing", "{}");
        assertFalse(missing.ok);
        assertTrue(missing.message.contains("見つかりません"));

        ActionService.Result traversal = svc.run("../bad", "{}");
        assertFalse(traversal.ok);

        ActionService.Result noConn = new ActionService(tmp, new ConnectionFactory(tmp), () -> null).run("bad", "{}");
        assertFalse(noConn.ok);
        assertTrue(noConn.message.contains("接続先が選択されていません"));
    }
}
