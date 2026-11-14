package jp.htmldbapp;

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
                waitUntil(() -> sp.isReady() && sp.getCurrentFile() != null
                        && Boolean.TRUE.equals(eval(sp, "!!window.__htmldb && !!document.getElementById('btn_initSample')")), 30);
                AppLog.info("[smoke] 画面読込完了: " + sp.getCurrentFile().getFileName());
                eval(sp, "document.querySelector('#customerForm button[data-insert]').click(); true");
                waitUntil(() -> {
                    String place = String.valueOf(eval(sp,
                            "(function(){var o=document.getElementById('out_saveCustomer');"
                                    + "var id=document.querySelector('[name=\"in_id\"]');"
                                    + "var beside=id&&id.nextElementSibling;"
                                    + "var besideErr=(beside&&(beside.className||'').indexOf('htmldb-key-error')>=0)?beside.textContent:'';"
                                    + "var msg=document.getElementById('msg');"
                                    + "var above=(o&&o.nextElementSibling&&o.nextElementSibling.id==='customerForm')?'above':'';"
                                    + "return above+'|'+(o?o.textContent:'')+'|beside='+besideErr+'|top='+(msg?msg.textContent:'');})()"));
                    String label = String.valueOf(eval(sp,
                            "(function(){var lb=document.querySelector('#customerForm .row label');return lb?lb.textContent:'';})()"));
                    return place.startsWith("above|顧客IDは必須です|beside=|top=")
                            && !place.substring(place.indexOf("|top=")).contains("必須")
                            && label.contains("＊");
                }, 30);
                AppLog.info("[smoke] 必須: 登録パネルの上だけ");
                eval(sp, "document.getElementById('btn_initSample').click(); true");
                waitUntil(() -> String.valueOf(eval(sp,
                        "document.getElementById('out_searchCustomer').innerHTML")).contains("htmldb-result"), 30);
                String text = String.valueOf(eval(sp, "document.getElementById('out_searchCustomer').innerText"));
                String msg = String.valueOf(eval(sp, "document.getElementById('msg').textContent"));
                AppLog.info("[smoke] msg=" + msg);
                AppLog.info("[smoke] 結果:\n" + text);
                if (text.contains("山田")) {
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
                                        + "return (b?b.textContent:'')+'|'+(id&&id.readOnly);})()"));
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
                                        + "return (b?b.textContent:'')+'|'+(id&&id.readOnly);})()"));
                        return "||".equals(formValue[0]) && "登録|false".equals(mode);
                    }, 30);
                    AppLog.info("[smoke] クリア: 登録欄を空にしました");
                    eval(sp, "(function(){document.querySelector('[name=\"in_id\"]').value='1';"
                            + "document.querySelector('[name=\"in_newName\"]').value='重複';"
                            + "document.querySelector('#customerForm button[data-insert]').click();return true;})()");
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

    private static void waitUntil(java.util.function.BooleanSupplier cond, int seconds) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(300);
        }
        throw new IllegalStateException("タイムアウトしました");
    }
}
