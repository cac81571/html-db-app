package jp.htmldbapp.web;

import groovy.json.JsonOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import jp.htmldbapp.AppLog;
import netscape.javascript.JSObject;

/**
 * Swing に埋め込む HTML 画面パネル（JFXPanel + WebView）。
 * ページ読込完了時にブリッジ（window.htmldbBridge）と配線用 JS（htmldb-bridge.js）を注入する。
 */
public class ScreenPanel extends JFXPanel {

    private static final long serialVersionUID = 1L;

    private final transient ActionService actionService;
    private final transient Consumer<String> statusSink;
    /** GC 対策: ブリッジへの強参照を必ず保持する */
    private final transient JsBridge bridge;
    private final transient ExecutorService sqlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sql-executor");
        t.setDaemon(true);
        return t;
    });
    private final String injectScript;
    private transient WebEngine engine;
    private transient volatile Path currentFile;
    /** 読込完了＆ブリッジ注入済み（別スレッドから参照するので volatile） */
    private volatile boolean ready;

    public ScreenPanel(ActionService actionService, Consumer<String> statusSink) {
        this.actionService = actionService;
        this.statusSink = statusSink;
        this.bridge = new JsBridge(this::onExecute, (level, msg) -> AppLog.info("[画面] " + level + ": " + msg));
        this.injectScript = loadInjectScript();
        Platform.runLater(this::initFx);
    }

    private static String loadInjectScript() {
        try (InputStream in = ScreenPanel.class.getResourceAsStream("htmldb-bridge.js")) {
            if (in == null) throw new IllegalStateException("htmldb-bridge.js が見つかりません");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void initFx() {
        WebView view = new WebView();
        engine = view.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setOnAlert(ev -> {
            AppLog.info("[alert] " + ev.getData());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ev.getData()));
        });
        // window.confirm（data-confirm）を Swing の確認ダイアログで表示
        engine.setConfirmHandler(message -> {
            AtomicBoolean answer = new AtomicBoolean(false);
            try {
                SwingUtilities.invokeAndWait(() -> answer.set(JOptionPane.showConfirmDialog(this, message, "確認",
                        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION));
            } catch (Exception e) {
                AppLog.error("確認ダイアログの表示に失敗しました", e);
            }
            return answer.get();
        });
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            ready = false;
            if (state == Worker.State.SUCCEEDED) {
                injectBridge();
                ready = true;
            } else if (state == Worker.State.FAILED) {
                AppLog.error("画面の読込に失敗しました: " + engine.getLocation(), engine.getLoadWorker().getException());
            }
        });
        engine.loadContent(welcomeHtml());
        setScene(new Scene(view));
    }

    private void injectBridge() {
        try {
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("htmldbBridge", bridge);
            engine.executeScript(injectScript);
        } catch (RuntimeException e) {
            AppLog.error("ブリッジの注入に失敗しました", e);
        }
    }

    /** 画面ファイルを表示する（Swing スレッドから呼んでよい）。 */
    public void showScreen(Path file) {
        currentFile = file;
        Platform.runLater(() -> {
            AppLog.info("画面を表示: " + file.getFileName());
            engine.load(file.toUri().toString());
        });
    }

    /** 現在の画面を再読込する。 */
    public void reload() {
        if (currentFile != null) showScreen(currentFile);
    }

    public Path getCurrentFile() { return currentFile; }

    /** 画面上で JavaScript を実行し、結果をコールバックで受け取る（FX スレッドで呼ばれる）。 */
    public void eval(String script, Consumer<Object> callback) {
        Platform.runLater(() -> {
            Object r;
            try {
                r = engine.executeScript(script);
            } catch (RuntimeException e) {
                r = e;
            }
            callback.accept(r);
        });
    }

    /** 読込完了してブリッジ注入済みか */
    public boolean isReady() {
        return ready;
    }

    /** JS から呼ばれる（FX スレッド）。SQL は別スレッドで実行して UI を固めない。 */
    private void onExecute(String sqlName, String screenJson, String then) {
        statusSink.accept(sqlName + " を実行中...");
        sqlExecutor.submit(() -> {
            ActionService.Result r = actionService.run(sqlName, screenJson);
            Platform.runLater(() -> render(sqlName, r));
            if (r.ok && !then.isBlank()) {
                // data-then で指定された SQL を同じ画面値で続けて実行
                for (String next : then.split(",")) {
                    String n = next.trim();
                    if (n.isEmpty()) continue;
                    ActionService.Result r2 = actionService.run(n, screenJson);
                    Platform.runLater(() -> render(n, r2));
                    if (!r2.ok) break;
                }
            }
        });
    }

    private void render(String sqlName, ActionService.Result r) {
        try {
            String js = "window.__htmldb && window.__htmldb.done(" + JsonOutput.toJson(sqlName) + ","
                    + r.ok + "," + JsonOutput.toJson(r.html) + "," + JsonOutput.toJson(r.message) + ")";
            Object found = engine.executeScript(js);
            if (r.ok && Boolean.FALSE.equals(found) && r.exec != null && r.exec.isQuery()) {
                AppLog.warn("結果表示用の要素 id=\"out_" + sqlName + "\" が画面にありません");
            }
        } catch (RuntimeException e) {
            AppLog.error("結果の表示に失敗しました", e);
        }
        statusSink.accept(r.message);
    }

    private static String welcomeHtml() {
        return "<html><body style='font-family:sans-serif;color:#555;padding:24px'>"
                + "<h2>HTML DB App</h2><p>左の一覧から画面を選択してください。</p></body></html>";
    }

    public void shutdown() {
        sqlExecutor.shutdownNow();
    }
}
