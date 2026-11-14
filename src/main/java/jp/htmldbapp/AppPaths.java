package jp.htmldbapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * アプリが使うフォルダの場所。
 * <ul>
 *   <li>作業フォルダ（screens/, sql/, drivers/）: システムプロパティ htmldbapp.home、未指定ならカレントディレクトリ</li>
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

    /** screens/ や sql/ が無ければ、JAR 内のサンプルを展開して作成する。 */
    public void ensureWorkspace() throws IOException {
        Files.createDirectories(configDir);
        Files.createDirectories(driversDir());
        extractIfMissing(screensDir(), "samples/screens/", new String[] {"customer.html", "style.css"});
        extractIfMissing(sqlDir(), "samples/sql/",
                new String[] {"initSample.sql", "searchCustomer.sql", "insertCustomer.sql", "deleteCustomer.sql"});
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
