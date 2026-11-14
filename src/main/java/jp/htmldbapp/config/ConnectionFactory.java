package jp.htmldbapp.config;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import jp.htmldbapp.AppLog;

/**
 * JDBC 接続を作成する。JAR に同梱したドライバに加え、drivers/ フォルダに置いた JAR からもドライバを読み込む。
 * （DriverManager は別クラスローダのドライバを使えないため、Driver#connect を直接呼ぶ）
 */
public class ConnectionFactory {

    private final Path driversDir;
    private ClassLoader driverLoader;

    public ConnectionFactory(Path driversDir) {
        this.driversDir = driversDir;
    }

    /** drivers/ フォルダの JAR を読み直す。 */
    public synchronized void reloadDrivers() {
        List<URL> urls = new ArrayList<>();
        if (driversDir != null && Files.isDirectory(driversDir)) {
            try (Stream<Path> s = Files.list(driversDir)) {
                s.filter(p -> p.toString().toLowerCase().endsWith(".jar")).forEach(p -> {
                    try {
                        urls.add(p.toUri().toURL());
                        AppLog.info("ドライバ JAR を読み込みました: " + p.getFileName());
                    } catch (IOException e) {
                        AppLog.warn("ドライバ JAR を読み込めません: " + p);
                    }
                });
            } catch (IOException e) {
                AppLog.warn("drivers フォルダを読めません: " + e.getMessage());
            }
        }
        driverLoader = new URLClassLoader(urls.toArray(new URL[0]), ConnectionFactory.class.getClassLoader());
    }

    private synchronized ClassLoader loader() {
        if (driverLoader == null) reloadDrivers();
        return driverLoader;
    }

    public Connection open(ConnectionInfo info) throws SQLException {
        if (info == null) throw new SQLException("接続先が選択されていません");
        if (info.getUrl().isBlank()) throw new SQLException("JDBC URL が空です");
        Properties props = new Properties();
        if (!info.getUser().isEmpty()) props.setProperty("user", info.getUser());
        if (!info.getPassword().isEmpty()) props.setProperty("password", info.getPassword());

        Driver driver = findDriver(info);
        Connection con = driver != null ? driver.connect(info.getUrl(), props) : DriverManager.getConnection(info.getUrl(), props);
        if (con == null) throw new SQLException("この URL を扱える JDBC ドライバがありません: " + info.getUrl());
        return con;
    }

    private Driver findDriver(ConnectionInfo info) throws SQLException {
        ClassLoader cl = loader();
        if (!info.getDriverClass().isBlank()) {
            try {
                Class<?> c = Class.forName(info.getDriverClass().trim(), true, cl);
                return (Driver) c.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new SQLException("JDBC ドライバクラスが見つかりません: " + info.getDriverClass()
                        + "（pom.xml に依存を追加するか、drivers/ フォルダに JAR を置いてください）", e);
            }
        }
        for (Driver d : ServiceLoader.load(Driver.class, cl)) {
            try {
                if (d.acceptsURL(info.getUrl())) return d;
            } catch (SQLException ignore) {
                // 次のドライバへ
            }
        }
        return null; // DriverManager に任せる
    }

    /** 接続テスト。成功時は DB 製品名とバージョンを返す。 */
    public String test(ConnectionInfo info) throws SQLException {
        try (Connection con = open(info)) {
            var md = con.getMetaData();
            return md.getDatabaseProductName() + " " + md.getDatabaseProductVersion();
        }
    }
}
