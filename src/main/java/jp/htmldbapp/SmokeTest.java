package jp.htmldbapp;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jp.htmldbapp.ui.MainFrame;
import jp.htmldbapp.web.ScreenPanel;

/**
 * 動作確認用: java -jar htmldbapp.jar --smoke-test
 * サンプル画面（customer.html）で「サンプルテーブルを作成」ボタンを JS からクリックし、
 * data-then で検索まで実行されて結果表が表示されること、行の「編集」で下の登録欄に値が入ることを確認して終了する
 * （成功: 0 / 失敗: 2）。
 */
final class SmokeTest {

    private SmokeTest() {}

    static void start(MainFrame frame) {
        Thread t = new Thread(() -> {
            int code = 2;
            String[] formValue = { "" };
            try {
                ScreenPanel sp = frame.getScreenPanel();
                Path customer = Path.of("sample_project", "顧客管理", "customer.html").toAbsolutePath().normalize();
                waitUntil(sp::isReady, 30);
                if (sp.getCurrentFile() == null || !customer.equals(sp.getCurrentFile().toAbsolutePath().normalize())) {
                    sp.showScreen(customer);
                }
                waitUntil(() -> sp.isReady() && sp.getCurrentFile() != null
                        && customer.equals(sp.getCurrentFile().toAbsolutePath().normalize())
                        && Boolean.TRUE.equals(eval(sp, "!!window.__htmldb && !!document.getElementById('btn_initSample')")), 30);
                AppLog.info("[smoke] 画面読込完了: " + sp.getCurrentFile().getFileName());
                eval(sp, "document.getElementById('btn_initSample').click(); true");
                waitUntil(() -> String.valueOf(eval(sp,
                        "document.getElementById('out_searchCustomer').innerHTML")).contains("htmldb-result"), 30);
                String text = String.valueOf(eval(sp, "document.getElementById('out_searchCustomer').innerText"));
                String msg = String.valueOf(eval(sp, "document.getElementById('msg').textContent"));
                AppLog.info("[smoke] msg=" + msg);
                AppLog.info("[smoke] 結果:\n" + text);
                if (text.contains("山田")) {
                    String savedClip = clipboardText();
                    boolean restoreClip = false;
                    try {
                        eval(sp, "document.querySelector('#out_searchCustomer table.htmldb-result tbody tr td:nth-child(3)').click(); true");
                        restoreClip = true;
                        waitUntil(() -> "山田 太郎".equals(clipboardText()), 10);
                        String copyMsg = String.valueOf(eval(sp, "document.getElementById('msg').textContent"));
                        if (copyMsg.contains("コピーしました")) {
                            throw new IllegalStateException("コピーメッセージが出ています: " + copyMsg);
                        }
                        AppLog.info("[smoke] セルコピー: 山田 太郎");
                    } finally {
                        if (restoreClip) setClipboard(savedClip == null ? "" : savedClip);
                    }
                    String mark = String.valueOf(eval(sp,
                            "(function(){var lb=document.querySelector('#customerForm .row label');return lb?lb.textContent:'';})()"));
                    if (mark.contains("＊")) throw new IllegalStateException("自動採番に ＊ が付いています: " + mark);
                    eval(sp, "(function(){document.querySelector('[name=\"in_newName\"]').value='自動 採番';"
                            + "document.querySelector('#customerForm button[data-insert]').click();return true;})()");
                    waitUntil(() -> {
                        String id = String.valueOf(eval(sp, "document.querySelector('[name=\"in_id\"]').value"));
                        String list = String.valueOf(eval(sp, "document.getElementById('out_searchCustomer').innerText"));
                        String mode = String.valueOf(eval(sp,
                                "document.querySelector('#customerForm button[data-insert]').textContent"));
                        return id.matches("\\d+") && list.contains(id) && list.contains("自動 採番") && "更新".equals(mode);
                    }, 30);
                    AppLog.info("[smoke] 自動採番: 空欄の顧客IDに番号を入れて登録");
                    eval(sp, "document.querySelector('#out_searchCustomer button[data-row-action=\"edit\"]').click(); true");
                    waitUntil(() -> {
                        formValue[0] = String.valueOf(eval(sp,
                                "(function(){function g(n){var e=document.querySelector('[name=\"'+n+'\"]');return e?e.value:'';}"
                                        + "return g('in_id')+'|'+g('in_newName')+'|'+g('in_newPref')+'|'+g('in_email')+'|'+g('in_joinedOn');})()"));
                        String list = String.valueOf(eval(sp,
                                "document.getElementById('out_searchCustomer') ? document.getElementById('out_searchCustomer').innerText : ''"));
                        String mode = String.valueOf(eval(sp,
                                "(function(){var b=document.querySelector('#customerForm button[data-insert]');"
                                        + "var id=document.querySelector('[name=\"in_id\"]');"
                                        + "return (b?b.textContent:'')+'|'+(id&&id.disabled);})()"));
                        return "1|山田 太郎|東京都|taro@example.com|2024-04-01".equals(formValue[0])
                                && list.contains("山田") && "更新|true".equals(mode);
                    }, 30);
                    AppLog.info("[smoke] 編集: " + formValue[0]);
                    eval(sp, "document.querySelector('#customerForm button[data-update]').click(); true");
                    waitUntil(() -> {
                        String place = String.valueOf(eval(sp,
                                "(function(){var o=document.getElementById('out_saveCustomer');"
                                        + "var n=o&&o.nextElementSibling;return (n?n.id:'')+'|'+(o?o.textContent:'');})()"));
                        return place.startsWith("customerForm|") && place.contains("件更新しました");
                    }, 30);
                    AppLog.info("[smoke] 更新メッセージ: 登録パネルの上");
                    eval(sp, "document.querySelector('#customerForm button[data-clear]').click(); true");
                    waitUntil(() -> {
                        formValue[0] = String.valueOf(eval(sp,
                                "(function(){function g(n){var e=document.querySelector('[name=\"'+n+'\"]');return e?e.value:'';}"
                                        + "return g('in_id')+'|'+g('in_newName')+'|'+g('in_newPref');})()"));
                        String mode = String.valueOf(eval(sp,
                                "(function(){var b=document.querySelector('#customerForm button[data-insert]');"
                                        + "var id=document.querySelector('[name=\"in_id\"]');"
                                        + "return (b?b.textContent:'')+'|'+(id&&id.disabled);})()"));
                        return "||".equals(formValue[0]) && "登録|true".equals(mode);
                    }, 30);
                    AppLog.info("[smoke] クリア: 登録欄を空にしました");
                    eval(sp, "document.querySelector('#customerForm button[data-insert]').click(); true");
                    waitUntil(() -> {
                        String place = String.valueOf(eval(sp,
                                "(function(){var o=document.getElementById('out_saveCustomer');"
                                        + "var msg=document.getElementById('msg');"
                                        + "return (o?o.textContent:'')+'|top='+(msg?msg.textContent:'');})()"));
                        int split = place.indexOf("|top=");
                        return split > 0 && place.substring(0, split).contains("の実行でエラー")
                                && !place.substring(split).contains("の実行でエラー");
                    }, 30);
                    AppLog.info("[smoke] 登録エラー: 登録パネルの上だけ");
                    eval(sp, "document.querySelector('#out_searchCustomer button[data-row-action=\"edit\"]').click(); true");
                    waitUntil(() -> "1|山田 太郎".equals(String.valueOf(eval(sp,
                            "(function(){function g(n){var e=document.querySelector('[name=\"'+n+'\"]');return e?e.value:'';}"
                                    + "return g('in_id')+'|'+g('in_newName');})()"))), 30);
                    eval(sp, "(function(){window.confirm=function(){return true;};"
                            + "document.querySelector('#out_searchCustomer button[data-row-action=\"delete\"]').click();return true;})()");
                    waitUntil(() -> {
                        String form = String.valueOf(eval(sp,
                                "(function(){function g(n){var e=document.querySelector('[name=\"'+n+'\"]');return e?e.value:'';}"
                                        + "var b=document.querySelector('#customerForm button[data-insert]');"
                                        + "return g('in_id')+'|'+g('in_newName')+'|'+(b?b.textContent:'');})()"));
                        String list = String.valueOf(eval(sp, "document.getElementById('out_searchCustomer').innerText"));
                        return "||登録".equals(form) && !list.contains("山田");
                    }, 30);
                    AppLog.info("[smoke] 削除: 登録欄を空にしました");
                    Path items = Path.of("sample_project", "CSV編集", "items.html").toAbsolutePath().normalize();
                    sp.showScreen(items);
                    waitUntil(() -> sp.isReady() && sp.getCurrentFile() != null
                            && items.equals(sp.getCurrentFile().toAbsolutePath().normalize())
                            && Boolean.TRUE.equals(eval(sp, "!!document.getElementById('btn_searchItem')")), 30);
                    eval(sp, "document.getElementById('btn_searchItem').click(); true");
                    waitUntil(() -> String.valueOf(eval(sp,
                            "document.getElementById('out_searchItem').innerText")).contains("ノートA5"), 30);
                    AppLog.info("[smoke] CSV検索: ノートA5");
                    Path identity = Path.of("sample_project", "顧客管理", "identity.html").toAbsolutePath().normalize();
                    sp.showScreen(identity);
                    waitUntil(() -> sp.isReady() && sp.getCurrentFile() != null
                            && identity.equals(sp.getCurrentFile().toAbsolutePath().normalize())
                            && Boolean.TRUE.equals(eval(sp, "!!document.getElementById('btn_initTicket')")), 30);
                    eval(sp, "document.getElementById('btn_initTicket').click(); true");
                    waitUntil(() -> String.valueOf(eval(sp,
                            "document.getElementById('out_searchTicket').innerHTML")).contains("htmldb-result"), 30);
                    eval(sp, "(function(){document.querySelector('[name=\"in_title\"]').value='採番メモ';"
                            + "document.querySelector('#ticketForm button[data-insert]').click();return true;})()");
                    waitUntil(() -> {
                        String state = String.valueOf(eval(sp,
                                "(function(){var id=document.querySelector('[name=\"in_id\"]');"
                                        + "var b=document.querySelector('#ticketForm button[data-insert]');"
                                        + "var list=document.getElementById('out_searchTicket').innerText;"
                                        + "var lb=document.querySelector('#ticketForm .row label');"
                                        + "return (id&&id.value)+'|'+(id&&id.disabled)+'|'+(b&&b.textContent)+'|'+(lb?lb.textContent:'')+'|'+list;})()"));
                        int bar = state.indexOf('|');
                        return bar > 0 && state.substring(0, bar).matches("\\d+")
                                && state.contains("|true|更新|番号|") && state.contains("採番メモ");
                    }, 30);
                    AppLog.info("[smoke] DB採番: 番号を入力せず登録");
                    code = 0;
                }
                AppLog.info(code == 0 ? "[smoke] OK" : "[smoke] NG");
            } catch (Exception e) {
                AppLog.error("[smoke] 失敗 form=" + formValue[0], e);
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

    private static String clipboardText() {
        try {
            var cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return String.valueOf(cb.getData(DataFlavor.stringFlavor));
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private static void setClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException ignore) {
            // 他のアプリがクリップボードを開いているときは戻さない
        }
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
