package jp.htmldbapp.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.SwingWorker;
import javafx.application.Platform;
import jp.htmldbapp.AppLog;
import jp.htmldbapp.AppPaths;
import jp.htmldbapp.ScreenFolders;
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
    private final JComboBox<Path> folderCombo = new JComboBox<>();
    private final JTextField treeFilter = new JTextField();
    private final JCheckBox filterFolderBox = filterTarget("フォルダ");
    private final JCheckBox filterHtmlBox = filterTarget("html");
    private final JCheckBox filterSqlBox = filterTarget("sql");
    private final JTree fileTree = new JTree();
    private final JTextArea logArea = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final ScreenPanel screenPanel;
    /** SQL 実行スレッドから参照するため volatile */
    private volatile ConnectionInfo activeConnection;
    /** 画面遷移に合わせてツリーの選択を変えるとき、選択イベントで再読込しない */
    private boolean suppressTreeEvent;
    private boolean suppressFolderEvent;
    /** いま選んでいるプロジェクトフォルダ（直下に画面フォルダがある） */
    private Path activeFolder;
    private JSplitPane contentSplit;
    private JScrollPane logScroll;
    private JButton logToggleButton;
    private JCheckBoxMenuItem logMenuItem;
    private boolean logVisible = true;
    /** ログを閉じる直前の仕切り位置 */
    private int logDividerLocation = -1;

    public MainFrame(AppPaths paths, ConnectionStore store, ConnectionFactory factory) {
        super("HTML DB App");
        this.paths = paths;
        this.store = store;
        this.factory = factory;

        ActionService service = new ActionService(this::currentSqlDir, factory, () -> activeConnection);
        screenPanel = new ScreenPanel(service, msg -> SwingUtilities.invokeLater(() -> status.setText(msg)));
        screenPanel.setOnNavigate(this::syncScreenList);

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
        file.add(item("プロジェクト一覧を更新", null, e -> refreshScreens()));
        file.add(item("プロジェクトを追加...", null, e -> addFolder()));
        file.add(item("選択中のプロジェクトを一覧から外す", null, e -> deleteFolder()));
        file.addSeparator();
        file.add(item("選択中の画面を外部エディタで開く", null, e -> editCurrentScreen()));
        file.add(item("選択中のプロジェクトを開く", null, e -> { if (activeFolder != null) openFolder(activeFolder); }));
        file.add(item("選択中の画面の sql フォルダを開く", null, e -> openCurrentSqlFolder()));
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
        logMenuItem = new JCheckBoxMenuItem("ログを表示");
        logMenuItem.setSelected(true);
        logMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
        logMenuItem.addActionListener(e -> setLogVisible(logMenuItem.isSelected()));
        tools.add(logMenuItem);
        tools.add(item("ログをクリア", null, e -> logArea.setText("")));
        tools.add(item("ログフォルダを開く", null, e -> openFolder(paths.logFile().getParent())));
        tools.addSeparator();
        tools.add(item("サクラエディタのパス...", null, e -> chooseSakuraPath()));
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
        tb.addSeparator();
        logToggleButton = button("ログを隠す", e -> setLogVisible(!logVisible));
        tb.add(logToggleButton);
        return tb;
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    private JSplitPane createCenter() {
        fileTree.setRootVisible(false);
        fileTree.setShowsRootHandles(true);
        fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        fileTree.addTreeSelectionListener(e -> {
            if (suppressTreeEvent) return;
            Path html = htmlForSelection();
            if (html == null) return;
            Path current = screenPanel.getCurrentFile();
            if (current != null && current.equals(html)) return;
            screenPanel.showScreen(html);
        });
        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showTreePopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { showTreePopup(e); }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || e.isPopupTrigger()) return;
                FileNode node = fileNodeAt(e);
                if (node == null) return;
                if (node.kind == Kind.HTML) screenPanel.reload();
                else if (node.kind == Kind.SQL) openInEditor(node.path);
            }
        });
        folderCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        folderCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Path path) setText(projectLabel(path));
                return this;
            }
        });
        folderCombo.addActionListener(e -> {
            if (suppressFolderEvent) return;
            Path dir = (Path) folderCombo.getSelectedItem();
            if (dir == null || dir.equals(activeFolder)) return;
            activeFolder = dir;
            saveLastFolder(dir.toString());
            folderCombo.setToolTipText(dir.toString());
            rebuildTree(true);
        });

        JPanel folderBar = new JPanel(new BorderLayout(4, 4));
        folderBar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        folderBar.add(new JLabel("プロジェクトフォルダ"), BorderLayout.NORTH);
        folderBar.add(folderCombo, BorderLayout.CENTER);
        JPanel folderButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        folderButtons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JButton addProject = button("追加", e -> addFolder());
        addProject.setToolTipText("既存のフォルダを一覧に追加します。フォルダは作りません");
        JButton removeProject = button("削除", e -> deleteFolder());
        removeProject.setToolTipText("一覧から外します。フォルダ自体は削除しません");
        folderButtons.add(addProject);
        folderButtons.add(removeProject);
        folderBar.add(folderButtons, BorderLayout.SOUTH);

        treeFilter.setToolTipText("選んだ対象の名前の一部。Esc で解除");
        treeFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onFilterChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onFilterChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onFilterChanged(); }
        });
        treeFilter.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearFilter");
        treeFilter.getActionMap().put("clearFilter", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                treeFilter.setText("");
            }
        });
        JPanel filterTargets = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        filterTargets.add(new JLabel("絞り込み"));
        filterTargets.add(filterFolderBox);
        filterTargets.add(filterHtmlBox);
        filterTargets.add(filterSqlBox);
        JPanel filterBar = new JPanel(new BorderLayout(4, 4));
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
        filterBar.add(filterTargets, BorderLayout.NORTH);
        filterBar.add(treeFilter, BorderLayout.CENTER);

        JPanel treeButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        treeButtons.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        treeButtons.add(button("全て展開", e -> {
            Object root = fileTree.getModel().getRoot();
            if (root != null) expandAll(fileTree, new TreePath(root));
        }));
        treeButtons.add(button("全て折りたたむ", e -> collapseAll()));

        JPanel folderPanel = new JPanel(new BorderLayout());
        folderPanel.add(folderBar, BorderLayout.NORTH);
        JPanel filterAndTree = new JPanel(new BorderLayout());
        filterAndTree.add(filterBar, BorderLayout.NORTH);
        filterAndTree.add(treeButtons, BorderLayout.SOUTH);
        folderPanel.add(filterAndTree, BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout());
        left.add(folderPanel, BorderLayout.NORTH);
        left.add(new JScrollPane(fileTree), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(260, 400));

        logArea.setEditable(false);
        logArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(400, 140));

        screenPanel.setPreferredSize(new Dimension(900, 560));
        contentSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, screenPanel, logScroll);
        contentSplit.setResizeWeight(0.8);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, contentSplit);
        main.setResizeWeight(0.0);
        return main;
    }

    public ScreenPanel getScreenPanel() { return screenPanel; }

    /** 下のログ欄を開く、または閉じる。閉じている間もログは記録され、開くと続きが見える。 */
    private void setLogVisible(boolean visible) {
        if (contentSplit == null || visible == logVisible) {
            logVisible = visible;
            syncLogControls();
            return;
        }
        if (!visible) {
            logDividerLocation = contentSplit.getDividerLocation();
            contentSplit.setBottomComponent(null);
        } else {
            contentSplit.setBottomComponent(logScroll);
            contentSplit.setResizeWeight(0.8);
            int height = contentSplit.getHeight();
            int loc = logDividerLocation;
            if (height > 0) {
                int max = height - contentSplit.getDividerSize() - 48;
                if (loc < 80 || loc > max) loc = Math.max(80, height - 160);
            }
            int place = loc;
            SwingUtilities.invokeLater(() -> contentSplit.setDividerLocation(place));
        }
        logVisible = visible;
        syncLogControls();
    }

    private void syncLogControls() {
        if (logMenuItem != null) logMenuItem.setSelected(logVisible);
        if (logToggleButton != null) logToggleButton.setText(logVisible ? "ログを隠す" : "ログを表示");
    }

    /** 画面内の遷移に合わせて、プロジェクトとツリーの選択を移動する。 */
    private void syncScreenList(Path file) {
        if (file == null) return;
        Path project = projectOf(file);
        if (project != null && !project.equals(activeFolder)) {
            suppressFolderEvent = true;
            try {
                folderCombo.setSelectedItem(project);
                if (project.equals(folderCombo.getSelectedItem())) {
                    activeFolder = project;
                    saveLastFolder(project.toString());
                    folderCombo.setToolTipText(project.toString());
                }
            } finally {
                suppressFolderEvent = false;
            }
            if (activeFolder != null && activeFolder.equals(project)) rebuildTree(false);
        }
        suppressTreeEvent = true;
        try {
            selectNode(file);
        } finally {
            suppressTreeEvent = false;
        }
    }

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
        List<Path> projects;
        try {
            projects = readProjects();
        } catch (IOException e) {
            AppLog.error("プロジェクトフォルダを読めません", e);
            projects = List.of();
        }
        Path keep = activeFolder;
        if (keep == null) {
            String saved = readLastFolder();
            if (saved != null && !saved.isBlank()) {
                Path savedPath = Path.of(saved).toAbsolutePath().normalize();
                if (projects.contains(savedPath)) keep = savedPath;
            }
        }
        suppressFolderEvent = true;
        try {
            folderCombo.removeAllItems();
            projects.forEach(folderCombo::addItem);
            if (keep != null && projects.contains(keep)) folderCombo.setSelectedItem(keep);
            else if (folderCombo.getItemCount() > 0) folderCombo.setSelectedIndex(0);
            activeFolder = (Path) folderCombo.getSelectedItem();
            folderCombo.setToolTipText(activeFolder == null ? null : activeFolder.toString());
        } finally {
            suppressFolderEvent = false;
        }
        Path current = screenPanel.getCurrentFile();
        boolean openFirst = current == null || activeFolder == null || !current.startsWith(activeFolder);
        rebuildTree(openFirst);
    }

    /** 表示中の HTML がある画面フォルダの sql/。 */
    private Path currentSqlDir() {
        Path file = screenPanel.getCurrentFile();
        if (file != null && file.getParent() != null) return file.getParent().resolve("sql");
        return null;
    }

    private void openCurrentSqlFolder() {
        Path dir = currentSqlDir();
        if (dir != null) openFolder(dir);
    }

    private void rebuildTree(boolean openFirstHtml) {
        DefaultMutableTreeNode root;
        if (activeFolder == null) {
            root = new DefaultMutableTreeNode(new FileNode(null, "(プロジェクトなし)", Kind.FOLDER));
        } else {
            root = new DefaultMutableTreeNode(
                    new FileNode(activeFolder, projectLabel(activeFolder), Kind.FOLDER));
            String query = filterQuery();
            addProjectChildren(root, activeFolder, query);
            if (!query.isEmpty() && root.getChildCount() == 0) {
                root.add(new DefaultMutableTreeNode(new FileNode(null, "(該当なし)", Kind.FOLDER)));
            }
        }
        suppressTreeEvent = true;
        try {
            fileTree.setModel(new DefaultTreeModel(root));
            if (filterQuery().isEmpty()) collapseAll();
            else expandAll(fileTree, new TreePath(root));
            Path current = screenPanel.getCurrentFile();
            if (!openFirstHtml && current != null) {
                selectNode(current);
            } else if (openFirstHtml) {
                DefaultMutableTreeNode html = firstHtml(root);
                if (html != null) fileTree.setSelectionPath(new TreePath(html.getPath()));
            }
        } finally {
            suppressTreeEvent = false;
        }
        if (openFirstHtml) {
            FileNode node = selectedNode();
            if (node != null && node.kind == Kind.HTML) {
                Path current = screenPanel.getCurrentFile();
                if (current == null || !current.equals(node.path)) screenPanel.showScreen(node.path);
            }
        }
    }

    private void addFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("プロジェクトフォルダを追加");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (activeFolder != null && Files.isDirectory(activeFolder)) {
            chooser.setCurrentDirectory(activeFolder.toFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        Path dir = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            JOptionPane.showMessageDialog(this, "フォルダが見つかりません:\n" + dir,
                    "プロジェクトの追加", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            List<Path> projects = new ArrayList<>(readProjects());
            if (projects.contains(dir)) {
                JOptionPane.showMessageDialog(this, "すでに追加されています:\n" + dir,
                        "プロジェクトの追加", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            projects.add(dir);
            writeProjects(projects);
            activeFolder = dir;
            saveLastFolder(dir.toString());
            refreshScreens();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "プロジェクトの追加", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteFolder() {
        if (activeFolder == null) {
            JOptionPane.showMessageDialog(this, "プロジェクトフォルダが選択されていません");
            return;
        }
        String name = projectLabel(activeFolder);
        int ok = JOptionPane.showConfirmDialog(this,
                name + " を一覧から外します。\nフォルダ自体は削除しません。よろしいですか？",
                "プロジェクトを一覧から外す", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            List<Path> projects = new ArrayList<>(readProjects());
            Path removing = activeFolder.toAbsolutePath().normalize();
            projects.remove(removing);
            writeProjects(projects);
            clearLastFolderIf(removing.toString());
            activeFolder = null;
            refreshScreens();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "プロジェクトを一覧から外す", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 設定が無ければ sample_project を1件だけ返す。ファイルがあればその内容だけ（空なら空）。 */
    private List<Path> readProjects() throws IOException {
        Path file = projectsFile();
        if (!Files.isRegularFile(file)) return List.of(paths.sampleProjectDir());
        List<Path> projects = new ArrayList<>();
        for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\\R")) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#")) continue;
            Path dir = Path.of(text).toAbsolutePath().normalize();
            if (Files.isDirectory(dir) && !projects.contains(dir)) projects.add(dir);
        }
        return projects;
    }

    private void writeProjects(List<Path> projects) throws IOException {
        Files.createDirectories(paths.configDir());
        StringBuilder body = new StringBuilder();
        for (Path project : projects) {
            body.append(project.toAbsolutePath().normalize()).append('\n');
        }
        Files.writeString(projectsFile(), body.toString(), StandardCharsets.UTF_8);
    }

    private Path projectsFile() {
        return paths.configDir().resolve("projects.txt");
    }

    /** ファイルを含む、一覧にあるプロジェクト。 */
    private Path projectOf(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        Path best = null;
        for (int i = 0; i < folderCombo.getItemCount(); i++) {
            Path project = folderCombo.getItemAt(i);
            if (project == null || !abs.startsWith(project)) continue;
            if (best == null || project.getNameCount() > best.getNameCount()) best = project;
        }
        return best;
    }

    /** 同名が複数あるときは親フォルダも付けて区別する。 */
    private String projectLabel(Path path) {
        Path name = path.getFileName();
        String label = name == null ? path.toString() : name.toString();
        for (int i = 0; i < folderCombo.getItemCount(); i++) {
            Path other = folderCombo.getItemAt(i);
            if (other == null || other.equals(path) || other.getFileName() == null) continue;
            if (other.getFileName().toString().equals(label) && path.getParent() != null) {
                return label + " (" + path.getParent() + ")";
            }
        }
        return label;
    }

    private void onFilterChanged() {
        if (activeFolder == null && fileTree.getModel().getRoot() == null) return;
        rebuildTree(false);
    }

    /** 絞り込み欄の文字列。空なら全部。チェックした対象だけ名前を比べ、大文字小文字は区別しない。 */
    private String filterQuery() {
        return treeFilter.getText().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean nameMatches(String name, String query) {
        return query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query);
    }

    /** 絞り込み文字が空なら全部。文字があるときは、その種類が対象のときだけ名前で残す。 */
    private static boolean fileMatches(Path file, String query, boolean byName) {
        return query.isEmpty() || (byName && nameMatches(file.getFileName().toString(), query));
    }

    private JCheckBox filterTarget(String label) {
        JCheckBox box = new JCheckBox(label, true);
        box.setMargin(new Insets(0, 0, 0, 0));
        box.setToolTipText(label + " の名前を絞り込み対象にする");
        box.addActionListener(e -> onFilterChanged());
        return box;
    }

    /** フォルダのチェックがオンのときだけ、フォルダ名を絞り込み対象にする。 */
    private boolean folderMatches(String name, String query) {
        return filterFolderBox.isSelected() && nameMatches(name, query);
    }

    private static void addHtmlNodes(DefaultMutableTreeNode parent, Path dir, String query, boolean byName) {
        if (!Files.isDirectory(dir)) return;
        Collator collator = Collator.getInstance(Locale.JAPAN);
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> Files.isRegularFile(p) && isHtml(p) && fileMatches(p, query, byName))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), collator))
                    .forEach(p -> parent.add(new DefaultMutableTreeNode(
                            new FileNode(p, p.getFileName().toString(), Kind.HTML))));
        } catch (IOException e) {
            AppLog.error("HTML を読めません: " + dir, e);
        }
    }

    private static void addSqlNodes(DefaultMutableTreeNode parent, Path dir, String query, boolean byName) {
        if (!Files.isDirectory(dir)) return;
        Collator collator = Collator.getInstance(Locale.JAPAN);
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> Files.isRegularFile(p) && isSql(p) && fileMatches(p, query, byName))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), collator))
                    .forEach(p -> parent.add(new DefaultMutableTreeNode(
                            new FileNode(p, p.getFileName().toString(), Kind.SQL))));
        } catch (IOException e) {
            AppLog.error("SQL を読めません: " + dir, e);
        }
    }

    private static boolean isHtml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    private static boolean isSql(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql");
    }

    private static DefaultMutableTreeNode firstHtml(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof FileNode file && file.kind == Kind.HTML) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = firstHtml((DefaultMutableTreeNode) node.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static void expandAll(JTree tree, TreePath path) {
        tree.expandPath(path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        for (int i = 0; i < node.getChildCount(); i++) {
            expandAll(tree, path.pathByAddingChild(node.getChildAt(i)));
        }
    }

    /**
     * プロジェクト直下からツリーを作る。ルート自体は出さない。
     * 画面フォルダ（HTML や sql/ があるフォルダ）も、それをまとめるフォルダも、何階層でもたどる。
     */
    private void addProjectChildren(DefaultMutableTreeNode root, Path project, String query) {
        try {
            for (String name : ScreenFolders.list(project)) {
                Path dir = project.resolve(name);
                boolean folderHit = folderMatches(name, query);
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileNode(dir, name, Kind.FOLDER));
                addScreenTree(node, dir, folderHit ? "" : query);
                if (query.isEmpty() || folderHit || node.getChildCount() > 0) root.add(node);
            }
        } catch (IOException e) {
            AppLog.error("画面フォルダを読めません: " + project, e);
        }
    }

    private void addScreenTree(DefaultMutableTreeNode parent, Path dir, String query) {
        try {
            for (String name : ScreenFolders.list(dir)) {
                Path childDir = dir.resolve(name);
                boolean folderHit = folderMatches(name, query);
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FileNode(childDir, name, Kind.FOLDER));
                addScreenTree(child, childDir, folderHit ? "" : query);
                if (query.isEmpty() || folderHit || child.getChildCount() > 0) parent.add(child);
            }
        } catch (IOException e) {
            AppLog.error("画面フォルダを読めません: " + dir, e);
        }
        int folders = parent.getChildCount();
        addHtmlNodes(parent, dir, query, filterHtmlBox.isSelected());
        boolean hasHtml = parent.getChildCount() > folders;
        Path sqlDir = dir.resolve("sql");
        if (!hasHtml && !Files.isDirectory(sqlDir)) return;
        boolean sqlHit = folderMatches("sql", query);
        DefaultMutableTreeNode sql = new DefaultMutableTreeNode(new FileNode(sqlDir, "sql", Kind.SQL_DIR));
        addSqlNodes(sql, sqlDir, sqlHit ? "" : query, filterSqlBox.isSelected());
        if (query.isEmpty() || sqlHit || sql.getChildCount() > 0) parent.add(sql);
    }

    /** 見えている画面フォルダだけを閉じる。非表示のプロジェクトルートは開いたままにする。 */
    private void collapseAll() {
        Object root = fileTree.getModel().getRoot();
        if (!(root instanceof DefaultMutableTreeNode node)) return;
        TreePath rootPath = new TreePath(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collapseChildren(rootPath.pathByAddingChild(node.getChildAt(i)));
        }
        fileTree.expandPath(rootPath);
    }

    private void collapseChildren(TreePath path) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        for (int i = 0; i < node.getChildCount(); i++) {
            collapseChildren(path.pathByAddingChild(node.getChildAt(i)));
        }
        fileTree.collapsePath(path);
    }

    private void selectNode(Path path) {
        if (fileTree.getModel().getRoot() == null) return;
        DefaultMutableTreeNode found = findNode((DefaultMutableTreeNode) fileTree.getModel().getRoot(), path);
        if (found == null) return;
        TreePath treePath = new TreePath(found.getPath());
        fileTree.setSelectionPath(treePath);
        fileTree.scrollPathToVisible(treePath);
    }

    private static DefaultMutableTreeNode findNode(DefaultMutableTreeNode node, Path path) {
        if (node.getUserObject() instanceof FileNode file && path.equals(file.path)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNode((DefaultMutableTreeNode) node.getChildAt(i), path);
            if (found != null) return found;
        }
        return null;
    }

    /** 選択中の HTML。画面フォルダなら、その中の先頭の HTML。 */
    private Path htmlForSelection() {
        TreePath path = fileTree.getSelectionPath();
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode treeNode)) return null;
        if (!(treeNode.getUserObject() instanceof FileNode node) || node.path == null) return null;
        if (node.kind == Kind.HTML) return node.path;
        if (node.kind != Kind.FOLDER) return null;
        Path current = screenPanel.getCurrentFile();
        if (current != null && current.startsWith(node.path) && isHtml(current)) return current;
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
            if (child.getUserObject() instanceof FileNode file && file.kind == Kind.HTML && file.path != null) {
                return file.path;
            }
        }
        return null;
    }

    private FileNode selectedNode() {
        TreePath path = fileTree.getSelectionPath();
        if (path == null) return null;
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof FileNode node ? node : null;
    }

    private Path lastFolderFile() {
        return paths.configDir().resolve("last-folder.txt");
    }

    private void saveLastFolder(String name) {
        try {
            Files.createDirectories(paths.configDir());
            Files.writeString(lastFolderFile(), name, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AppLog.warn("フォルダの記憶に失敗しました: " + e.getMessage());
        }
    }

    private String readLastFolder() {
        try {
            Path file = lastFolderFile();
            if (Files.isRegularFile(file)) return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            AppLog.warn("前回のフォルダを読めません: " + e.getMessage());
        }
        return null;
    }

    private void clearLastFolderIf(String name) {
        if (!name.equals(readLastFolder())) return;
        try {
            Files.deleteIfExists(lastFolderFile());
        } catch (IOException e) {
            AppLog.warn("フォルダの記憶を消せません: " + e.getMessage());
        }
    }

    private enum Kind { FOLDER, HTML, SQL_DIR, SQL }

    private static final class FileNode {
        final Path path;
        final String label;
        final Kind kind;

        FileNode(Path path, String label, Kind kind) {
            this.path = path;
            this.label = label;
            this.kind = kind;
        }

        @Override
        public String toString() {
            return label;
        }
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
        if (f != null) openInEditor(f);
    }

    /** ファイルの上で右クリックしたら、エディタで開くメニューを出す。 */
    private void showTreePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        FileNode node = fileNodeAt(e);
        if (node == null || node.path == null) return;
        TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) fileTree.setSelectionPath(path);
        JPopupMenu menu = new JPopupMenu();
        if (node.kind == Kind.FOLDER || node.kind == Kind.SQL_DIR) {
            JMenuItem open = new JMenuItem("フォルダを開く");
            open.addActionListener(ev -> openFolder(node.path));
            menu.add(open);
        } else if (node.kind == Kind.HTML || node.kind == Kind.SQL) {
            JMenuItem external = new JMenuItem("外部エディタで開く");
            external.addActionListener(ev -> openInEditor(node.path));
            JMenuItem sakura = new JMenuItem("サクラエディタで開く");
            sakura.addActionListener(ev -> openInSakura(node.path));
            menu.add(external);
            menu.add(sakura);
        } else {
            return;
        }
        // マウスを離した直後に出すと、カーソル下の項目がそのまま実行される
        int x = e.getX();
        int y = e.getY();
        SwingUtilities.invokeLater(() -> menu.show(fileTree, x, y));
    }

    private FileNode fileNodeAt(MouseEvent e) {
        TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return null;
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof FileNode node ? node : null;
    }

    private void openInEditor(Path file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                Desktop.getDesktop().edit(file.toFile());
            } else {
                Desktop.getDesktop().open(file.toFile());
            }
        } catch (Exception e) {
            AppLog.error("エディタを開けません: " + file, e);
            JOptionPane.showMessageDialog(this, "エディタを開けません:\n" + e.getMessage(),
                    "外部エディタ", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path sakuraExe;
    private boolean sakuraSearched;

    private void openInSakura(Path file) {
        Path configured = readSakuraPath();
        if (configured != null && !Files.isRegularFile(configured)) {
            JOptionPane.showMessageDialog(this,
                    "設定されたサクラエディタが見つかりません:\n" + configured
                            + "\nツール → サクラエディタのパス で選び直してください。",
                    "サクラエディタ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path exe = configured != null ? configured : findSakura();
        if (exe == null) {
            JOptionPane.showMessageDialog(this,
                    "サクラエディタ (sakura.exe) が見つかりません。\n"
                            + "ツール → サクラエディタのパス で sakura.exe を指定してください。",
                    "サクラエディタ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            new ProcessBuilder(exe.toString(), file.toAbsolutePath().toString()).start();
            AppLog.info("サクラエディタで開く: " + file.getFileName());
        } catch (IOException e) {
            AppLog.error("サクラエディタを開けません: " + file, e);
            JOptionPane.showMessageDialog(this, "サクラエディタを開けません:\n" + e.getMessage(),
                    "サクラエディタ", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path sakuraPathFile() {
        return paths.configDir().resolve("sakura-path.txt");
    }

    /** 設定済みならそのパス。未設定なら null。 */
    private Path readSakuraPath() {
        try {
            Path file = sakuraPathFile();
            if (!Files.isRegularFile(file)) return null;
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return null;
            return Path.of(text);
        } catch (IOException e) {
            AppLog.warn("サクラエディタのパスを読めません: " + e.getMessage());
            return null;
        }
    }

    private void chooseSakuraPath() {
        Path saved = readSakuraPath();
        String initial = saved != null ? saved.toString() : "";
        if (initial.isEmpty()) {
            Path detected = findSakura();
            if (detected != null) initial = detected.toString();
        }
        JTextField field = new JTextField(initial, 36);
        JButton browse = new JButton("参照...");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("sakura.exe を選択");
            chooser.setFileFilter(new FileNameExtensionFilter("実行ファイル (*.exe)", "exe"));
            String current = field.getText().trim();
            if (!current.isEmpty()) {
                Path path = Path.of(current);
                Path dir = Files.isDirectory(path) ? path : path.getParent();
                if (dir != null && Files.isDirectory(dir)) chooser.setCurrentDirectory(dir.toFile());
                if (Files.isRegularFile(path)) chooser.setSelectedFile(path.toFile());
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JPanel row = new JPanel(new BorderLayout(4, 4));
        row.add(field, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("sakura.exe の場所。空欄で保存すると自動検出に戻ります。"), BorderLayout.NORTH);
        panel.add(row, BorderLayout.CENTER);
        String[] options = {"保存", "自動検出に戻す", "キャンセル"};
        int choice = JOptionPane.showOptionDialog(this, panel, "サクラエディタのパス",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == 1) {
            clearSakuraPath();
            return;
        }
        if (choice != 0) return;
        String text = field.getText().trim();
        if (text.isEmpty()) {
            clearSakuraPath();
            return;
        }
        Path exe = Path.of(text);
        if (!Files.isRegularFile(exe)) {
            JOptionPane.showMessageDialog(this, "ファイルが見つかりません:\n" + exe,
                    "サクラエディタのパス", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Files.createDirectories(paths.configDir());
            Files.writeString(sakuraPathFile(), exe.toAbsolutePath().toString(), StandardCharsets.UTF_8);
            AppLog.info("サクラエディタのパスを保存しました: " + exe.toAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "保存できませんでした:\n" + ex.getMessage(),
                    "サクラエディタのパス", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearSakuraPath() {
        try {
            Files.deleteIfExists(sakuraPathFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "設定を消せませんでした:\n" + e.getMessage(),
                    "サクラエディタのパス", JOptionPane.ERROR_MESSAGE);
            return;
        }
        sakuraSearched = false;
        sakuraExe = null;
        AppLog.info("サクラエディタのパスを自動検出に戻しました");
    }

    /** 設定が無いときの探索。インストール先、見つからなければ PATH 上の sakura.exe。 */
    private Path findSakura() {
        if (sakuraSearched) return sakuraExe;
        sakuraSearched = true;
        String[] roots = {
                System.getenv("ProgramFiles(x86)"),
                System.getenv("ProgramFiles"),
                System.getenv("LOCALAPPDATA")
        };
        for (String root : roots) {
            if (root == null || root.isBlank()) continue;
            Path exe = Path.of(root, "sakura", "sakura.exe");
            if (Files.isRegularFile(exe)) return sakuraExe = exe;
        }
        Path local = Path.of(System.getenv("LOCALAPPDATA") == null ? "" : System.getenv("LOCALAPPDATA"),
                "Programs", "sakura", "sakura.exe");
        if (System.getenv("LOCALAPPDATA") != null && Files.isRegularFile(local)) return sakuraExe = local;
        return sakuraExe = sakuraOnPath();
    }

    private static Path sakuraOnPath() {
        try {
            Process process = new ProcessBuilder("where", "sakura").redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset());
            if (process.waitFor() != 0) return null;
            for (String line : out.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                Path exe = Path.of(trimmed);
                if (Files.isRegularFile(exe)) return exe;
            }
        } catch (Exception e) {
            AppLog.warn("サクラエディタを PATH から探せません: " + e.getMessage());
        }
        return null;
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
                "data-open=\"画面.html\" : その画面を開く（SQL は実行しない）",
                "data-from=\"out_名前\"  : 一覧で選択した行の値を渡して開く",
                "data-fields=\"列名=項目\" : 結果の列を同じ画面の in_項目 に対応付ける",
                "data-row-key=\"列名=項目\" : 一覧から編集・削除へ渡すキー",
                "data-row-load=\"名前\" : 編集時に実行する SELECT。列別名が登録欄に入る",
                "data-row-edit=\"#id\" : 各行の先頭に「編集」（登録フォーム）",
                "data-row-delete=\"名前\" : 各行に「削除」",
                "data-keep（body）     : 離れる前の入力・結果・選択を覚え、戻ると復元",
                "                       F5 の再読込では破棄する",
                "",
                "空欄の入力は NULL としてバインドされます。");
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "ネーミングルール", JOptionPane.INFORMATION_MESSAGE);
    }
}
