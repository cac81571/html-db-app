package jp.htmldbapp.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javafx.application.Platform;
import jp.htmldbapp.AppLog;
import jp.htmldbapp.AppPaths;
import jp.htmldbapp.config.ConnectionFactory;
import jp.htmldbapp.config.ConnectionInfo;
import jp.htmldbapp.config.ConnectionStore;
import jp.htmldbapp.web.ActionService;
import jp.htmldbapp.web.ScreenPanel;

/** メインウィンドウ: 接続先選択・画面一覧・HTML 画面・ログ。 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient AppPaths paths;
    private final transient ConnectionStore store;
    private final transient ConnectionFactory factory;
    private final JComboBox<ConnectionInfo> connectionCombo = new JComboBox<>();
    private final DefaultListModel<String> screenModel = new DefaultListModel<>();
    private final JList<String> screenList = new JList<>(screenModel);
    private final JTextArea logArea = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final ScreenPanel screenPanel;
    /** SQL 実行スレッドから参照するため volatile */
    private volatile ConnectionInfo activeConnection;

    public MainFrame(AppPaths paths, ConnectionStore store, ConnectionFactory factory) {
        super("HTML DB App");
        this.paths = paths;
        this.store = store;
        this.factory = factory;

        ActionService service = new ActionService(paths.sqlDir(), factory, () -> activeConnection);
        screenPanel = new ScreenPanel(service, msg -> SwingUtilities.invokeLater(() -> status.setText(msg)));

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(1200, 800));
        setLocationRelativeTo(null);
        setJMenuBar(createMenu());
        add(createToolBar(), BorderLayout.NORTH);
        add(createCenter(), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);

        AppLog.addListener(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));

        refreshConnections();
        refreshScreens();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                screenPanel.shutdown();
                Platform.exit();
                System.exit(0);
            }
        });
    }

    // ---------- 画面構成 ----------

    private JMenuBar createMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("ファイル");
        file.add(item("画面を再読込", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), e -> screenPanel.reload()));
        file.add(item("画面一覧を更新", null, e -> refreshScreens()));
        file.addSeparator();
        file.add(item("選択中の画面を外部エディタで開く", null, e -> editCurrentScreen()));
        file.add(item("screens フォルダを開く", null, e -> openFolder(paths.screensDir())));
        file.add(item("sql フォルダを開く", null, e -> openFolder(paths.sqlDir())));
        file.add(item("drivers フォルダを開く", null, e -> openFolder(paths.driversDir())));
        file.addSeparator();
        file.add(item("終了", null, e -> dispose()));
        bar.add(file);

        JMenu conn = new JMenu("接続");
        conn.add(item("接続先の管理...", null, e -> openConnectionDialog()));
        conn.add(item("選択中の接続先をテスト", null, e -> testActiveConnection()));
        conn.add(item("drivers フォルダを再読込", null, e -> factory.reloadDrivers()));
        bar.add(conn);

        JMenu tools = new JMenu("ツール");
        tools.add(item("ログをクリア", null, e -> logArea.setText("")));
        tools.add(item("ログフォルダを開く", null, e -> openFolder(paths.logFile().getParent())));
        bar.add(tools);

        JMenu help = new JMenu("ヘルプ");
        help.add(item("ネーミングルール", null, e -> showNamingRules()));
        bar.add(help);
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke key, java.awt.event.ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        if (key != null) mi.setAccelerator(key);
        mi.addActionListener(action);
        return mi;
    }

    private JToolBar createToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel(" 接続先: "));
        connectionCombo.setPrototypeDisplayValue(new ConnectionInfo("XXXXXXXXXXXXXXXXXXXXXXXX", "", "", "", ""));
        connectionCombo.setMaximumSize(new Dimension(280, 30));
        connectionCombo.addActionListener(e -> onConnectionSelected());
        tb.add(connectionCombo);
        tb.add(button("管理...", e -> openConnectionDialog()));
        tb.add(button("接続テスト", e -> testActiveConnection()));
        tb.addSeparator();
        tb.add(button("再読込 (F5)", e -> screenPanel.reload()));
        tb.add(button("一覧更新", e -> refreshScreens()));
        return tb;
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    private JSplitPane createCenter() {
        screenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        screenList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && screenList.getSelectedValue() != null) {
                screenPanel.showScreen(paths.screensDir().resolve(screenList.getSelectedValue()));
            }
        });
        screenList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) screenPanel.reload();
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel(" 画面一覧（screens/）"), BorderLayout.NORTH);
        left.add(new JScrollPane(screenList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(220, 400));

        logArea.setEditable(false);
        logArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(400, 140));

        screenPanel.setPreferredSize(new Dimension(900, 560));
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, screenPanel, logScroll);
        right.setResizeWeight(0.8);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        main.setResizeWeight(0.0);
        return main;
    }

    public ScreenPanel getScreenPanel() { return screenPanel; }

    // ---------- 動作 ----------

    private void refreshConnections() {
        String keep = activeConnection != null ? activeConnection.getName() : store.getLastSelected();
        DefaultComboBoxModel<ConnectionInfo> model = new DefaultComboBoxModel<>();
        for (ConnectionInfo c : store.getConnections()) model.addElement(c);
        connectionCombo.setModel(model);
        ConnectionInfo sel = store.find(keep);
        if (sel == null && model.getSize() > 0) sel = model.getElementAt(0);
        connectionCombo.setSelectedItem(sel);
        onConnectionSelected();
    }

    private void onConnectionSelected() {
        activeConnection = (ConnectionInfo) connectionCombo.getSelectedItem();
        if (activeConnection != null && !activeConnection.getName().equals(store.getLastSelected())) {
            store.setLastSelected(activeConnection.getName());
            try {
                store.save();
            } catch (IOException ex) {
                AppLog.error("接続先の保存に失敗しました", ex);
            }
        }
        status.setText(activeConnection == null ? "接続先が未選択です" : "接続先: " + activeConnection.getName());
    }

    private void refreshScreens() {
        String keep = screenList.getSelectedValue();
        screenModel.clear();
        Path dir = paths.screensDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                List<String> names = s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".html") || n.endsWith(".htm");
                }).map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
                names.forEach(screenModel::addElement);
            } catch (IOException e) {
                AppLog.error("画面一覧を読めません", e);
            }
        }
        if (keep != null && screenModel.contains(keep)) screenList.setSelectedValue(keep, true);
        else if (!screenModel.isEmpty() && screenPanel.getCurrentFile() == null) screenList.setSelectedIndex(0);
    }

    private void openConnectionDialog() {
        ConnectionDialog dlg = new ConnectionDialog(this, store, factory);
        dlg.setVisible(true);
        refreshConnections();
    }

    private void testActiveConnection() {
        ConnectionInfo info = activeConnection;
        if (info == null) {
            JOptionPane.showMessageDialog(this, "接続先が選択されていません");
            return;
        }
        status.setText("接続テスト中: " + info.getName());
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return factory.test(info);
            }

            @Override
            protected void done() {
                try {
                    String v = get();
                    AppLog.info("接続成功: " + info.getName() + " (" + v + ")");
                    status.setText("接続成功: " + v);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    AppLog.error("接続失敗: " + info.getName() + " - " + c.getMessage(), null);
                    JOptionPane.showMessageDialog(MainFrame.this, "接続に失敗しました:\n" + c.getMessage(),
                            "接続テスト", JOptionPane.ERROR_MESSAGE);
                    status.setText("接続失敗");
                }
            }
        }.execute();
    }

    private void editCurrentScreen() {
        Path f = screenPanel.getCurrentFile();
        if (f == null) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                Desktop.getDesktop().edit(f.toFile());
            } else {
                Desktop.getDesktop().open(f.toFile());
            }
        } catch (Exception e) {
            AppLog.error("エディタを開けません: " + f, e);
        }
    }

    private void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            AppLog.warn("フォルダを開けません: " + dir + "（" + e.getMessage() + "）");
        }
    }

    private void showNamingRules() {
        String text = String.join("\n",
                "■ ネーミングルール",
                "",
                "ボタン     id=\"btn_名前\"     → sql/名前.sql を実行",
                "入力項目   name=\"in_パラメータ\" → SQL の :パラメータ にバインド",
                "結果表示   id=\"out_名前\"     → SELECT は表、更新系は件数を表示",
                "メッセージ id=\"msg\"          → 成功／エラーメッセージ（任意）",
                "",
                "■ 追加の属性（任意）",
                "data-type=\"int|number|date|datetime|time|bool\" : 入力値の型",
                "data-then=\"名前\"    : 成功後に続けて実行する SQL",
                "data-confirm=\"文言\" : 実行前の確認ダイアログ",
                "",
                "空欄の入力は NULL としてバインドされます。");
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "ネーミングルール", JOptionPane.INFORMATION_MESSAGE);
    }
}
