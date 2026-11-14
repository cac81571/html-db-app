package jp.htmldbapp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jp.htmldbapp.ui.MainFrame;
import jp.htmldbapp.web.ScreenPanel;

/**
 * 動作確認用: java -jar htmldbapp.jar --smoke-test
 * サンプル画面（customer.html）で「サンプルテーブルを作成」ボタンを JS からクリックし、
 * data-then で検索まで実行されて結果表が表示されることを確認して終了する（成功: 0 / 失敗: 2）。
 */
final class SmokeTest {

    private SmokeTest() {}

    static void start(MainFrame frame) {
        Thread t = new Thread(() -> {
            int code = 2;
            try {
                ScreenPanel sp = frame.getScreenPanel();
                waitUntil(() -> sp.isReady() && sp.getCurrentFile() != null
                        && Boolean.TRUE.equals(eval(sp, "!!window.__htmldb && !!document.getElementById('btn_initSample')")), 30);
                AppLog.info("[smoke] 画面読込完了: " + sp.getCurrentFile().getFileName());
                eval(sp, "document.getElementById('btn_initSample').click(); true");
                waitUntil(() -> String.valueOf(eval(sp,
                        "document.getElementById('out_searchCustomer').innerHTML")).contains("htmldb-result"), 30);
                String text = String.valueOf(eval(sp, "document.getElementById('out_searchCustomer').innerText"));
                String msg = String.valueOf(eval(sp, "document.getElementById('msg').textContent"));
                AppLog.info("[smoke] msg=" + msg);
                AppLog.info("[smoke] 結果:\n" + text);
                code = text.contains("山田") ? 0 : 2;
                AppLog.info(code == 0 ? "[smoke] OK" : "[smoke] NG");
            } catch (Exception e) {
                AppLog.error("[smoke] 失敗", e);
            }
            System.exit(code);
        }, "smoke-test");
        t.setDaemon(true);
        t.start();
    }

    private static Object eval(ScreenPanel sp, String js) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> ref = new AtomicReference<>();
        sp.eval(js, r -> {
            ref.set(r);
            latch.countDown();
        });
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get();
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, int seconds) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(300);
        }
        throw new IllegalStateException("タイムアウトしました");
    }
}
