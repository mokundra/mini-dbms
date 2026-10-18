package lab.gui;

import lab.engine.DatabaseEngine;
import lab.service.ExcelExportService;
import lab.sql.ast.SqlCommand;
import lab.sql.executor.QueryExecutor;
import lab.sql.executor.QueryResult;
import lab.sql.parser.SqlParser;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Создаёт главное окно программы и связывает элементы интерфейса.
 */
public class MainWindow extends JFrame {
    private static final Color BACKGROUND = new Color(30, 34, 45);

    private static final Color PANEL = new Color(40, 45, 60);

    private final QueryExecutor queryExecutor;
    private final ExcelExportService excelExportService;

    private final SqlEditorPanel editorPanel;
    private final DatabaseTreePanel treePanel;
    private final QueryResultTableModel tableModel;

    private final JLabel resultStatus;

    private QueryResult lastResult;

    private JButton createButton(String text) {
        JButton button = new JButton(text);

        button.setBackground(new Color(75, 110, 210));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);

        return button;
    }

    private void showResult(QueryResult result) {
        lastResult = result;
        if (!result.getColumns().isEmpty()) {
            tableModel.setResult(result);
            resultStatus.setText("Найдено строк: " + result.getRows().size());
        } else {
            tableModel.clear();

            if (result.hasMessage()) {
                resultStatus.setText(result.getMessage());
            } else {
                resultStatus.setText("Изменено строк: " + result.getAffectedRows());
            }
        }

        treePanel.refresh();
        editorPanel.setStatus("Запрос выполнен", false);
    }

    private void executeSql(String sql) {
        try {
            SqlCommand command = new SqlParser(sql).parse();
            QueryResult result = queryExecutor.execute(command);
            showResult(result);

        } catch (IOException | RuntimeException exception) {
            editorPanel.setStatus("Ошибка", true);

            resultStatus.setText("Ошибка выполнения запроса");

            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "SQL Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void exportToExcel() {
        if (lastResult == null || lastResult.getColumns().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Сначала выполните SELECT-запрос");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("result.xlsx"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = chooser.getSelectedFile().toPath();

        if (!path.toString().toLowerCase().endsWith(".xlsx")) {
            path = Path.of(path + ".xlsx");
        }

        try {
            excelExportService.export(lastResult, path);

            JOptionPane.showMessageDialog(this, "Файл сохранён:\n" + path);

        } catch (IOException | RuntimeException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));

        JLabel title = new JLabel("Query Results");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));

        JButton exportButton = createButton("Экспорт в Excel");

        exportButton.addActionListener(event -> exportToExcel());

        JPanel header = new JPanel(new BorderLayout());

        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(exportButton, BorderLayout.EAST);

        JTable table = new JTable(tableModel);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        table.setDefaultRenderer(Object.class, centerRenderer);
        table.setDefaultRenderer(Integer.class, centerRenderer);
        table.setDefaultRenderer(Double.class, centerRenderer);
        table.setDefaultRenderer(Boolean.class, centerRenderer);

        table.setBackground(PANEL);
        table.setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(75, 90, 130));
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(65, 70, 85));
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        table.getTableHeader().setBackground(new Color(55, 65, 85));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));

        JScrollPane scrollPane = new JScrollPane(table);

        scrollPane.getViewport().setBackground(PANEL);

        resultStatus.setForeground(new Color(170, 185, 210));

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(resultStatus, BorderLayout.SOUTH);

        return panel;
    }

    private JSplitPane createLayout() {
        JSplitPane vertical = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                editorPanel,
                createResultPanel()
        );

        vertical.setResizeWeight(0.5);
        vertical.setDividerSize(6);
        vertical.setBorder(null);

        treePanel.setPreferredSize(new Dimension(250, 600));

        JSplitPane horizontal = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                treePanel,
                vertical
        );

        horizontal.setResizeWeight(0.23);
        horizontal.setDividerSize(6);
        horizontal.setBorder(null);

        return horizontal;
    }

    /**
     * Создаёт объект MainWindow.
     *
     * @param databaseEngine движок базы данных
     */
    public MainWindow(DatabaseEngine databaseEngine) {
        if (databaseEngine == null) {
            throw new IllegalArgumentException("Database engine cannot be null");
        }

        queryExecutor = new QueryExecutor(databaseEngine);
        excelExportService = new ExcelExportService();

        tableModel = new QueryResultTableModel();
        resultStatus = new JLabel("Нет результатов");

        editorPanel = new SqlEditorPanel(this::executeSql);

        treePanel = new DatabaseTreePanel(databaseEngine.getCatalog(), editorPanel::setSql);

        setTitle("SQL Console");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        getContentPane().setBackground(BACKGROUND);
        add(createLayout(), BorderLayout.CENTER);
    }
}