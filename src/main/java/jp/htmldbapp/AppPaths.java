package jp.htmldbapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * アプリが使うフォルダの場所。
 * <ul>
 *   <li>作業フォルダ（画面フォルダ/ と drivers/）: システムプロパティ htmldbapp.home、未指定ならカレントディレクトリ</li>
 *   <li>設定フォルダ（connections.json, secret.key, logs/）: システムプロパティ htmldbapp.config、未指定なら ~/.htmldbapp</li>
 * </ul>
 */
public final class AppPaths {

    private final Path home;
    private final Path configDir;

    public AppPaths(Path home, Path configDir) {
        this.home = home.toAbsolutePath().normalize();
        this.configDir = configDir.toAbsolutePath().normalize();
    }

    public static AppPaths fromSystem() {
        String h = System.getProperty("htmldbapp.home");
        String c = System.getProperty("htmldbapp.config");
        Path home = h != null ? Paths.get(h) : Paths.get("").toAbsolutePath();
        Path config = c != null ? Paths.get(c) : Paths.get(System.getProperty("user.home"), ".htmldbapp");
        return new AppPaths(home, config);
    }

    public Path home() { return home; }
    public Path screensDir() { return home.resolve("screens"); }
    public Path sqlDir() { return home.resolve("sql"); }
    public Path driversDir() { return home.resolve("drivers"); }
    public Path configDir() { return configDir; }
    public Path connectionsFile() { return configDir.resolve("connections.json"); }
    public Path keyFile() { return configDir.resolve("secret.key"); }
    public Path logFile() { return configDir.resolve("logs").resolve("app.log"); }

    /** 画面フォルダが無ければサンプル「顧客管理」を作る。古い screens/ と sql/ があればそこへコピーする。 */
    public void ensureWorkspace() throws IOException {
        Files.createDirectories(configDir);
        Files.createDirectories(driversDir());
        Path sample = home.resolve("顧客管理");
        if (Files.isDirectory(sample)) return;
        if (Files.isDirectory(screensDir()) || Files.isDirectory(sqlDir())) {
            Files.createDirectories(sample.resolve("sql"));
            copyFiles(screensDir(), sample);
            copyFiles(sqlDir(), sample.resolve("sql"));
            AppLog.info("screens/ と sql/ を 顧客管理/ にコピーしました");
            return;
        }
        extractIfMissing(sample, "samples/顧客管理/", new String[] {"customer.html", "style.css"});
        extractIfMissing(sample.resolve("sql"), "samples/顧客管理/sql/",
                new String[] {"initSample.sql", "searchCustomer.sql", "saveCustomer.sql",
                        "insertCustomer.sql", "updateCustomer.sql", "deleteCustomer.sql"});
    }

    private static void copyFiles(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) return;
        Files.createDirectories(to);
        try (var s = Files.list(from)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path dest = to.resolve(p.getFileName());
                if (!Files.exists(dest)) Files.copy(p, dest);
            }
        }
    }

    private void extractIfMissing(Path dir, String resourcePrefix, String[] files) throws IOException {
        if (Files.isDirectory(dir)) return;
        Files.createDirectories(dir);
        for (String f : files) {
            try (InputStream in = AppPaths.class.getClassLoader().getResourceAsStream(resourcePrefix + f)) {
                if (in != null) {
                    Files.copy(in, dir.resolve(f));
                }
            }
        }
        AppLog.info("サンプルを展開しました: " + dir);
    }
}
