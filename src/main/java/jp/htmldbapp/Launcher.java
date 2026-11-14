package jp.htmldbapp;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Font;
import java.util.Arrays;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javafx.application.Platform;
import jp.htmldbapp.config.ConnectionFactory;
import jp.htmldbapp.config.ConnectionStore;
import jp.htmldbapp.config.CryptoUtil;
import jp.htmldbapp.ui.MainFrame;

/**
 * エントリポイント。
 * ※ fat JAR から JavaFX を起動するため、javafx.application.Application を継承しないクラスを main にしている。
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        AppPaths paths = AppPaths.fromSystem();
        AppLog.setLogFile(paths.logFile());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> AppLog.error("予期しないエラー（" + t.getName() + "）", e));

        // 最後のウィンドウを閉じても JavaFX ランタイムを終了させない（JFXPanel を再生成できるように）
        Platform.setImplicitExit(false);

        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
                UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(Font.PLAIN, 13f));
                paths.ensureWorkspace();
                CryptoUtil crypto = new CryptoUtil(paths.keyFile());
                ConnectionStore store = new ConnectionStore(paths.connectionsFile(), crypto);
                store.load();
                ConnectionFactory factory = new ConnectionFactory(paths.driversDir());
                MainFrame frame = new MainFrame(paths, store, factory);
                frame.setVisible(true);
                AppLog.info("起動しました（作業フォルダ: " + paths.home() + "）");
                if (Arrays.asList(args).contains("--smoke-test")) {
                    SmokeTest.start(frame);
                }
            } catch (Exception e) {
                AppLog.error("起動に失敗しました", e);
                JOptionPane.showMessageDialog(null, "起動に失敗しました:\n" + e, "HTML DB App", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
