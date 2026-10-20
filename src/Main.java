import javax.swing.tree.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.ExpandVetoException;
import java.util.stream.Stream;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.*;
import javax.swing.event.*;
public class Main {
    private static JFrame window;
    private static JTextArea editor;
    private static Path currentFile;
    private static String savedContent = "";
    private static JTree fileTree;
    private static DefaultTreeModel treeModel;
    private static JTabbedPane tabs;
    private static boolean standaloneMode = false;

    private static final Map<Component, TabData> openTabs = new HashMap<>();
    private static class TabData {
        Path file;
        JTextArea textArea;
        String savedContent;

        TabData(Path file, JTextArea textArea, String savedContent) {
            this.file = file;
            this.textArea = textArea;
            this.savedContent = savedContent;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createWindow);
    }
    private static void createWindow() {
        window = new JFrame("HDBL Code");
        window.setSize(1100, 700);
        window.setLocationRelativeTo(null);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        editor = new JTextArea();
        editor.setFont(new Font("Consolas", Font.PLAIN, 16));
        editor.setBackground(new Color(15, 23, 42));
        editor.setForeground(new Color(134, 239, 172));
        editor.setCaretColor(Color.WHITE);
        editor.setTabSize(4);

        JScrollPane scrollPane = new JScrollPane(editor);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem newFile = new JMenuItem("New");
        JMenuItem openFile = new JMenuItem("Open");
        JMenuItem saveFile = new JMenuItem("Save");
        JMenuItem saveAs = new JMenuItem("Save As");

        newFile.addActionListener(e -> newDocument());
        openFile.addActionListener(e -> openDocument());
        saveFile.addActionListener(e -> saveDocument(false));
        saveAs.addActionListener(e -> saveDocument(true));


        newFile.setAccelerator(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_N,
                        InputEvent.CTRL_DOWN_MASK
                )
        );

        openFile.setAccelerator(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_O,
                        InputEvent.CTRL_DOWN_MASK
                )
        );

        saveFile.setAccelerator(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_S,
                        InputEvent.CTRL_DOWN_MASK
                )
        );



        fileMenu.add(newFile);
        fileMenu.add(openFile);
        fileMenu.add(saveFile);
        fileMenu.add(saveAs);

        menuBar.add(fileMenu);
        window.setJMenuBar(menuBar);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("No folder open");

        treeModel = new DefaultTreeModel(root);
        fileTree = new JTree(treeModel);
        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                var treePath = fileTree.getPathForLocation(e.getX(), e.getY());
                if (treePath == null) {
                    return;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                Object value = node.getUserObject();
                if (value instanceof Path path && Files.isRegularFile(path)) {
                    JOptionPane.showMessageDialog(window, "Opening: " + path.getFileName());
                    openFilesFromTree(path);
                }
            }
        });
        fileTree.setBackground(new Color(30, 41, 59));
        fileTree.setForeground(Color.WHITE);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackgroundNonSelectionColor(new Color(30, 41, 59));

        renderer.setTextNonSelectionColor(Color.WHITE);
        fileTree.setCellRenderer(renderer);
        fileTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                loadFolder(node);
                if (standaloneMode && node == treeModel.getRoot()) {
                    return;
                }
            }
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
            }
        });
        JPanel explorerPanel = new JPanel(new BorderLayout());

        JButton openFolder = new JButton("Open Folder");

        openFolder.addActionListener(e -> chooseFolder());

        explorerPanel.add(openFolder, BorderLayout.NORTH);

        explorerPanel.add(new JScrollPane(fileTree), BorderLayout.CENTER);
        tabs = new JTabbedPane();
        tabs.addChangeListener(e -> {
            TabData selected = openTabs.get(tabs.getSelectedComponent());
            if (selected != null) {
                editor = selected.textArea;
                currentFile = selected.file;
                savedContent = selected.savedContent;
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, tabs);
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerSize(7);

        JButton saveButton = new JButton("Save");

        saveButton.addActionListener(e -> {
            saveDocument(false);
        });

        JPanel toolbar = new JPanel(
                new FlowLayout(FlowLayout.LEFT)
        );

        toolbar.add(saveButton);
        JButton createButton = new JButton("Create");
        createButton.addActionListener(e -> createEntry());
        toolbar.add(createButton);
        JButton renameButton = new JButton("Rename");
        JButton deleteButton = new JButton("Delete");
        renameButton.addActionListener(e -> renameEntry());
        deleteButton.addActionListener(e -> deleteEntry());
        toolbar.add(renameButton);
        toolbar.add(deleteButton);
        toolbar.setBackground(new Color(30, 41, 59));
        styleButton(saveButton);
        styleButton(createButton);
        styleButton(renameButton);
        styleButton(deleteButton);
        deleteButton.setBackground(new Color(153, 27, 27));


        window.add(toolbar, BorderLayout.NORTH);


        window.add(splitPane, BorderLayout.CENTER);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.25);

        window.add(splitPane, BorderLayout.CENTER);

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmCloseAllTabs()) {
                    window.dispose();
                }
            }
        });
        window.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(280);
        });

    }
    private static void styleButton(JButton button) {
        button.setBackground(new Color(37, 99, 235));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    private static void openFilesFromTree(Path path) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component component = tabs.getComponentAt(i);
            TabData existing = openTabs.get(component);
            if (existing != null && path.equals(existing.file)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
        try {
            if (Files.size(path) > 5_000_000) {
                JOptionPane.showMessageDialog(window, "File is too large");
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            for (byte b : bytes) {
                if (b == 0) {
                    JOptionPane.showMessageDialog(window, "Not a text file");
                    return;
                }
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            JTextArea newEditor = new JTextArea(content);

            newEditor.setFont(editor.getFont());
            newEditor.setBackground(editor.getBackground());
            newEditor.setForeground(editor.getForeground());
            newEditor.setCaretColor(Color.WHITE);
            newEditor.setTabSize(4);

            JScrollPane newScrollPane = new JScrollPane(newEditor);
            openTabs.put(newScrollPane, new TabData(path, newEditor, content));
            watchTab(newScrollPane, newEditor);
            tabs.addTab(path.getFileName().toString(), newScrollPane);
            addCloseButton(newScrollPane);
            tabs.setSelectedComponent(newScrollPane);
            window.setTitle("HDBL Code - " + path.getFileName());
        } catch (IOException error) {
            JOptionPane.showMessageDialog(window, "Could not open file: " + error.getMessage());
        }
    }
    private static void chooseFolder() {
        JFileChooser chooser = new JFileChooser();

        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path folder = chooser.getSelectedFile().toPath();
        DefaultMutableTreeNode root = createFolderNode(folder);
        standaloneMode = false;
        treeModel.setRoot(root);
        loadFolder(root);
        fileTree.expandRow(0);
    }
    private static DefaultMutableTreeNode createFolderNode(Path path) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(path);
        if (Files.isDirectory(path)) {
            node.add(new DefaultMutableTreeNode("Loading..."));
        }
        return node;
    }
    private static void loadFolder(DefaultMutableTreeNode node) {
        Object value = node.getUserObject();
        if (!(value instanceof Path folder)) {
            return;
        }
        if (!Files.isDirectory(folder)) {
            return;
        }
        node.removeAllChildren();

        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> !path.getFileName().toString().equals(".git")).sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) {
                    return aDir ? -1 : 1;
                }
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).forEach(path -> node.add(createFolderNode(path)));
            treeModel.nodeStructureChanged(node);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(window, "Could not read folder: " + e.getMessage());
        }
    }
    private static boolean confirmDiscard() {
        TabData selected = openTabs.get(tabs.getSelectedComponent());
        if (selected == null) {
            return true;
        }
        if (selected.textArea.getText().equals(selected.savedContent)) {
            return true;
        }
        String[] options = {
                "Save",
                "Discard",
                "Cancel"
        };
        int choice = JOptionPane.showOptionDialog(window, "You have unsaved changes.\nWhat would you like to do?", "HDBL Code - Unsaved Changes", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            saveDocument(false);
            return selected.textArea.getText().equals(selected.savedContent);

        }
        if (choice == 1) {
            return true;
        }
        return false;
    }
    private static void newDocument() {
        if(!confirmDiscard()) return;

        editor.setText("");
        savedContent = "";
        currentFile = null;
        window.setTitle("HDBL Code - Untitled");
    }
    private static void openDocument() {
        if(!confirmDiscard()) return;

        JFileChooser chooser = new JFileChooser();

        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath();

        try {
            String content = Files.readString(selected, StandardCharsets.UTF_8);

            editor.setText(content);
            savedContent = content;
            currentFile = selected;
            window.setTitle("HDBL Code - " + selected.getFileName());

        } catch (IOException error) {
            JOptionPane.showMessageDialog(window, "Could not open file: " + error.getMessage());
        }
    }
    private static void saveDocument(boolean saveAs) {
        Path destination = currentFile;

        if (saveAs || destination == null) {
            JFileChooser chooser = new JFileChooser();

            if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            destination = chooser.getSelectedFile().toPath();

            if (Files.exists(destination)) {
                int result = JOptionPane.showConfirmDialog(window, "Replace existing file?",  "HDBL Code", JOptionPane.YES_NO_OPTION);
                if (result != JOptionPane.YES_OPTION) return;

            }
        }
        try {
            Files.writeString(destination, editor.getText(), StandardCharsets.UTF_8);

            currentFile = destination;
            savedContent = editor.getText();
            TabData selected = openTabs.get(tabs.getSelectedComponent());
            if (selected != null) {
                selected.file = destination;
                selected.savedContent = editor.getText();
                updateTabTitle(tabs.getSelectedComponent());

                tabs.setTitleAt(tabs.getSelectedIndex(), destination.getFileName().toString());
            }

            window.setTitle("HDBL Code - " + destination.getFileName());

        }catch (IOException error) {
            JOptionPane.showMessageDialog(window, "Save failed: " + error.getMessage());
        }
    }
    private static void updateTabTitle(Component component) {
        TabData data = openTabs.get(component);
        if (data == null) return;
        int index = tabs.indexOfComponent(component);
        if (index == -1) return;
        boolean unsaved = !data.textArea.getText().equals(data.savedContent);
        String name = data.file == null
                ? "Untitled"
                : data.file.getFileName().toString();
        tabs.setTitleAt(index, name + (unsaved ? " ●" : ""));
        Component tabHeader =  tabs.getTabComponentAt(index);
        if (tabHeader instanceof JPanel header) {
            if (header.getComponent(0) instanceof JLabel label) {
                label.setText(name + (unsaved ? " ●" : ""));
            }
        }
    }
    private static void watchTab(Component component, JTextArea textArea) {
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTabTitle(component);
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTabTitle(component);
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateTabTitle(component);
            }
        });
    }
    private static boolean confirmCloseAllTabs() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setSelectedIndex(i);
            if (!confirmDiscard()) {
                return false;
            }
        }
        return true;
    }
    private static void addCloseButton(Component component) {
        int index = tabs.indexOfComponent(component);
        if (index == -1) return;
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        header.setOpaque(false);
        JLabel title = new JLabel(tabs.getTitleAt(index));
        JButton close = new JButton("x");
        close.setFocusable(false);
        close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        close.setContentAreaFilled(false);
        close.addActionListener(e -> closeTab(component));
        header.add(title);
        header.add(close);
        tabs.setTabComponentAt(index, header);
    }
    private static void closeTab(Component component) {
        if (tabs.indexOfComponent(component) == -1) {
            return;
        }
        tabs.setSelectedComponent(component);
        if (!confirmDiscard()) {
            return;
        }
        openTabs.remove(component);
        tabs.remove(component);
    }
    private static void createEntry() {
     DefaultMutableTreeNode node = (DefaultMutableTreeNode)fileTree.getLastSelectedPathComponent();
     if (node == null) {
         node = (DefaultMutableTreeNode) treeModel.getRoot();
     }
     Object value = node.getUserObject();
     Path path;

     if(value instanceof Path selectedPath) {
         path = selectedPath;
         if (!Files.isDirectory(path)) {
             node = (DefaultMutableTreeNode) node.getParent();
             if (node == null) return;
             path = (Path) node.getUserObject();
         }
     } else {
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Choose where to create your first file");
         chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
         int result = chooser.showOpenDialog(window);
         if (result != JFileChooser.APPROVE_OPTION) {
             return;
         }
         path = chooser.getSelectedFile().toPath();
        standaloneMode = true;
        node = new  DefaultMutableTreeNode(path);
        treeModel.setRoot(node);
     }
     String[] options = {"File", "Directory"};
     int choice = JOptionPane.showOptionDialog(window, "What do you want to create?", "HDBL Code - Create", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
     if (choice == JOptionPane.CLOSED_OPTION) {
         return;
     }
     String name = JOptionPane.showInputDialog(window, choice == 0 ? "File name (example: hello.html):" : "Directory name:");
     if (name == null) return;
     name = name.trim();
 if (name.isEmpty() || name.equals(".") || name.equals("..") || name.matches(".*[\\\\\\\\/:*?\\\"<>|].*") || name.endsWith(".")) {
     JOptionPane.showMessageDialog(window, "Invalid name.");
     return;
 }
 try {
     Path created = path.resolve(name);
     if (Files.exists(created)) {
         JOptionPane.showMessageDialog(window, "File already exists.");
         return;
     }

     if (choice == 0) {
         Files.createFile(created);
     } else {
         Files.createDirectory(created);
     }
     if (standaloneMode && node == treeModel.getRoot()) {
         node.add(createFolderNode(created));
         treeModel.nodeStructureChanged(node);
     } else {
         loadFolder(node);
     }
     fileTree.expandPath(new TreePath(node.getPath()));
     if (choice == 0) {
         openFilesFromTree(created);
     }
 } catch (IOException error){
     JOptionPane.showMessageDialog(window, "Created failed: " + error.getMessage());
 }
    }

    private static void renameEntry() {
        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode)
                        fileTree.getLastSelectedPathComponent();

        if (node == null || node.getParent() == null) {
            JOptionPane.showMessageDialog(
                    window,
                    "Select a file or folder inside the project."
            );
            return;
        }

        if (!(node.getUserObject() instanceof Path oldPath)) {
            return;
        }

        String name = JOptionPane.showInputDialog(
                window,
                "Enter the new name:",
                oldPath.getFileName().toString()
        );

        if (name == null) return;
        name = name.trim();

        if (!isValidEntryName(name)) {
            JOptionPane.showMessageDialog(
                    window, "Invalid name."
            );
            return;
        }

        if (hasUnsavedTabs(oldPath)) {
            JOptionPane.showMessageDialog(
                    window,
                    "Save your changes before renaming."
            );
            return;
        }

        Path newPath = oldPath.resolveSibling(name);

        if (oldPath.equals(newPath)) return;

        if (Files.exists(newPath)) {
            JOptionPane.showMessageDialog(
                    window, "That name already exists."
            );
            return;
        }

        try {
            Files.move(oldPath, newPath);

            // Update paths stored in the explorer.
            Enumeration<?> nodes = node.depthFirstEnumeration();

            while (nodes.hasMoreElements()) {
                DefaultMutableTreeNode child =
                        (DefaultMutableTreeNode)
                                nodes.nextElement();

                if (child.getUserObject() instanceof Path path) {
                    if (path.startsWith(oldPath)) {
                        child.setUserObject(
                                newPath.resolve(
                                        oldPath.relativize(path)
                                )
                        );
                    }
                }
            }

            treeModel.nodeStructureChanged(node);

            // Update files already open in tabs.
            for (Map.Entry<Component, TabData> entry
                    : openTabs.entrySet()) {

                TabData data = entry.getValue();

                if (data.file != null &&
                        data.file.startsWith(oldPath)) {

                    data.file = newPath.resolve(
                            oldPath.relativize(data.file)
                    );

                    updateTabTitle(entry.getKey());
                }
            }

            TabData selected = openTabs.get(
                    tabs.getSelectedComponent()
            );

            if (selected != null) {
                currentFile = selected.file;
            }

        } catch (IOException error) {
            JOptionPane.showMessageDialog(
                    window,
                    "Rename failed: " + error.getMessage()
            );
        }
    }

    private static void deleteEntry() {
        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode)
                        fileTree.getLastSelectedPathComponent();

        if (node == null || node.getParent() == null) {
            JOptionPane.showMessageDialog(
                    window,
                    "Select a file or folder inside the project."
            );
            return;
        }

        if (!(node.getUserObject() instanceof Path path)) {
            return;
        }

        if (hasUnsavedTabs(path)) {
            JOptionPane.showMessageDialog(
                    window,
                    "Save your changes before deleting."
            );
            return;
        }

        boolean isDirectory = Files.isDirectory(path);

        String message = "Move to Recycle Bin?\n\n"
                + path.getFileName();

        if (isDirectory) {
            message +=
                    "\n\nThis includes all files and subfolders.";
        }

        int answer = JOptionPane.showConfirmDialog(
                window,
                message,
                "HDBL Code - Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (answer != JOptionPane.YES_OPTION) return;

        try {
            if (!Desktop.isDesktopSupported() ||
                    !Desktop.getDesktop().isSupported(
                            Desktop.Action.MOVE_TO_TRASH)) {

                JOptionPane.showMessageDialog(
                        window,
                        "Recycle Bin is not supported here."
                );
                return;
            }

            boolean deleted = Desktop.getDesktop()
                    .moveToTrash(path.toFile());

            if (!deleted) {
                JOptionPane.showMessageDialog(
                        window, "Could not move to Recycle Bin."
                );
                return;
            }

            // Close tabs belonging to deleted files.
            for (Component component :
                    new ArrayList<>(openTabs.keySet())) {

                TabData data = openTabs.get(component);

                if (data != null &&
                        data.file != null &&
                        data.file.startsWith(path)) {

                    openTabs.remove(component);
                    tabs.remove(component);
                }
            }

            // Remove the element from the explorer.
            treeModel.removeNodeFromParent(node);

            TabData selected = openTabs.get(
                    tabs.getSelectedComponent()
            );

            if (selected != null) {
                editor = selected.textArea;
                currentFile = selected.file;
                savedContent = selected.savedContent;
            } else {
                currentFile = null;
                savedContent = "";
            }

        } catch (Exception error) {
            JOptionPane.showMessageDialog(
                    window,
                    "Delete failed: " + error.getMessage()
            );
        }
    }

    private static boolean isValidEntryName(String name) {
        return !name.isBlank()
                && !name.equals(".")
                && !name.equals("..")
                && !name.endsWith(".")
                && !name.matches(".*[\\\\/:*?\"<>|].*");
    }

    private static boolean hasUnsavedTabs(Path target) {
        for (TabData data : openTabs.values()) {
            if (data.file != null &&
                    data.file.startsWith(target) &&
                    !data.textArea.getText()
                            .equals(data.savedContent)) {
                return true;
            }
        }

        return false;
    }


}
