package lab.gui;

import lab.catalog.Catalog;
import lab.catalog.ColumnDefinition;
import lab.catalog.IndexDefinition;
import lab.catalog.TableSchema;

import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Отображает таблицы и их столбцы в графическом интерфейсе.
 */
public class DatabaseTreePanel extends JPanel {
    private final Catalog catalog;
    private final Consumer<String> onTableSelected;

    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel treeModel;
    private final JTree tree;

    private DefaultMutableTreeNode createTableNode(TableSchema schema) {
        DefaultMutableTreeNode tableNode = new DefaultMutableTreeNode(schema.getTableName());
        DefaultMutableTreeNode columnsNode = new DefaultMutableTreeNode("Columns");

        for (ColumnDefinition column : schema.getColumns()) {
            String name = column.getName() + " : " + column.getType();

            if (column.isPrimaryKey()) {
                name += " (PK)";
            } else if (column.isUnique()) {
                name += " (UNIQUE)";
            }
            columnsNode.add(new DefaultMutableTreeNode(name));
        }

        tableNode.add(columnsNode);

        DefaultMutableTreeNode indexesNode = new DefaultMutableTreeNode("Indexes");

        for (IndexDefinition index : catalog.getIndexesForTable(schema.getTableName())) {
            indexesNode.add(
                    new DefaultMutableTreeNode(
                            index.getName() + " (" + index.getColumnName() + ")"
                    )
            );
        }

        if (indexesNode.getChildCount() > 0) {
            tableNode.add(indexesNode);
        }

        DefaultMutableTreeNode foreignKeysNode = new DefaultMutableTreeNode("Foreign Keys");

        for (var foreignKey : schema.getForeignKeys()) {
            foreignKeysNode.add(
                    new DefaultMutableTreeNode(
                            foreignKey.getColumnName()
                                    + " -> "
                                    + foreignKey.getReferencedTable()
                                    + "."
                                    + foreignKey.getReferencedColumn()
                    )
            );
        }

        if (foreignKeysNode.getChildCount() > 0) {
            tableNode.add(foreignKeysNode);
        }
        return tableNode;
    }

    private void selectTable() {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

        if (selected == null || selected.getParent() != root) {
            return;
        }

        String tableName = selected.getUserObject().toString();
        onTableSelected.accept("SELECT * FROM " + tableName + ";");
    }

    /**
     * Создаёт объект DatabaseTreePanel.
     *
     * @param catalog каталог таблиц и индексов
     * @param onTableSelected обработчик выбора таблицы
     */
    public DatabaseTreePanel(Catalog catalog, Consumer<String> onTableSelected) {
        this.catalog = java.util.Objects.requireNonNull(catalog);
        this.onTableSelected = java.util.Objects.requireNonNull(onTableSelected);

        setLayout(new BorderLayout(0, 10));
        setBackground(new Color(30, 34, 45));
        setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));

        JLabel title = new JLabel("Database Explorer");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));

        root = new DefaultMutableTreeNode("Database");
        treeModel = new DefaultTreeModel(root);

        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(new Color(30, 34, 45));
        tree.setForeground(Color.WHITE);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tree.setRowHeight(25);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();

        renderer.setBackgroundNonSelectionColor(new Color(30, 34, 45));
        renderer.setTextNonSelectionColor(Color.WHITE);
        renderer.setBackgroundSelectionColor(new Color(65, 80, 120));
        renderer.setTextSelectionColor(Color.WHITE);
        renderer.setBorderSelectionColor(null);

        tree.setCellRenderer(renderer);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    selectTable();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);

        JButton refreshButton = new JButton("Обновить");
        refreshButton.addActionListener(event -> refresh());

        add(title, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(refreshButton, BorderLayout.SOUTH);

        refresh();
    }

    /**
     * Обновляет дерево таблиц по текущему каталогу.
     */
    public void refresh() {
        root.removeAllChildren();

        for (TableSchema schema : catalog.getTables()) {
            root.add(createTableNode(schema));
        }

        treeModel.reload();

        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}