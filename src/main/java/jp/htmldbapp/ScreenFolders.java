package jp.htmldbapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * プロジェクトフォルダ直下の画面フォルダ。
 * 各フォルダは HTML と、その中の sql/ に SQL を置く。
 */
public final class ScreenFolders {

    /** 開発用・アプリ用のディレクトリは画面フォルダにしない */
    private static final Set<String> RESERVED = Set.of(
            "src", "target", "drivers", "dist", "screens", "sql", "logs", "test", ".git", ".idea", ".mvn", ".cursor");

    private ScreenFolders() {}

    /** 画面フォルダ名の一覧（名前順）。 */
    public static List<String> list(Path home) throws IOException {
        if (!Files.isDirectory(home)) return List.of();
        Collator collator = Collator.getInstance(Locale.JAPAN);
        try (Stream<Path> s = Files.list(home)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith("."))
                    .filter(n -> !RESERVED.contains(n.toLowerCase(Locale.ROOT)))
                    .sorted(collator::compare)
                    .toList();
        }
    }

    /** 不正ならメッセージ、使えれば null。 */
    public static String validate(String name) {
        if (name == null || name.isBlank()) return "フォルダ名を入力してください";
        String n = name.trim();
        if (n.equals(".") || n.equals("..")) return "その名前は使えません";
        if (n.chars().anyMatch(c -> "\\/:*?\"<>|".indexOf(c) >= 0 || c < ' ')) {
            return "フォルダ名に使えない文字があります";
        }
        if (RESERVED.contains(n.toLowerCase(Locale.ROOT))) return "その名前は予約されています: " + n;
        return null;
    }

    public static Path resolve(Path home, String name) {
        String err = validate(name);
        if (err != null) throw new IllegalArgumentException(err);
        Path dir = home.resolve(name.trim()).normalize();
        Path parent = dir.getParent();
        if (parent == null || !parent.equals(home.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("場所が不正です");
        }
        return dir;
    }

    /** フォルダと空の sql/ を作る。 */
    public static Path create(Path home, String name) throws IOException {
        Path dir = resolve(home, name);
        if (Files.exists(dir)) throw new IOException("すでにあります: " + name.trim());
        Files.createDirectories(dir.resolve("sql"));
        return dir;
    }

    /** 画面フォルダを中身ごと削除する。 */
    public static void delete(Path home, String name) throws IOException {
        Path dir = resolve(home, name);
        if (!Files.isDirectory(dir)) throw new IOException("フォルダがありません: " + name.trim());
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) Files.delete(p);
        }
    }
}
