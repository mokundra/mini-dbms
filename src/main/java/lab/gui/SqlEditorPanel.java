package lab.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/**
 * Предоставляет поле ввода SQL и элементы управления выполнением запросов.
 */
public class SqlEditorPanel extends JPanel {
    private final JTextArea editor;
    private final JLabel statusLabel;
    private final Consumer<String> onExecute;

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);

        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));

        return button;
    }

    private void executeQuery() {
        String sql = editor.getText().trim();

        if (sql.isEmpty()) {
            setStatus("Введите SQL-запрос", true);
            return;
        }
        onExecute.accept(sql);
    }

    /**
     * Создаёт объект SqlEditorPanel.
     *
     * @param onExecute обработчик запуска SQL
     */
    public SqlEditorPanel(Consumer<String> onExecute) {
        this.onExecute = onExecute;

        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(30, 34, 45));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("SQL Editor");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        editor = new JTextArea();
        editor.setFont(new Font("Monospaced", Font.PLAIN, 15));
        editor.setBackground(new Color(40, 45, 60));
        editor.setForeground(Color.WHITE);
        editor.setCaretColor(Color.WHITE);
        editor.setMargin(new Insets(12, 12, 12, 12));
        editor.setTabSize(4);

        JScrollPane scrollPane = new JScrollPane(editor);

        JButton executeButton = createButton("Выполнить", new Color(75, 110, 210));
        JButton clearButton = createButton("Очистить", new Color(75, 80, 100));

        executeButton.addActionListener(e -> executeQuery());
        clearButton.addActionListener(e -> editor.setText(""));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);
        buttons.add(executeButton);
        buttons.add(clearButton);

        statusLabel = new JLabel("Готово");
        statusLabel.setForeground(new Color(120, 210, 160));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(buttons, BorderLayout.WEST);
        bottom.add(statusLabel, BorderLayout.EAST);

        editor.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "execute");

        editor.getActionMap().put("execute", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeQuery();
            }
        });

        add(title, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /**
     * Возвращает текст запроса из редактора.
     * @return текст SQL-запроса
     */
    public String getSql() {
        return editor.getText();
    }

    /**
     * Устанавливает текст в редакторе SQL.
     *
     * @param sql текст SQL-запроса
     */
    public void setSql(String sql) {
        editor.setText(sql == null ? "" : sql);
    }

    /**
     * Показывает сообщение о результате выполнения запроса.
     *
     * @param message текст сообщения
     * @param error признак ошибки
     */
    public void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? Color.PINK : new Color(120, 210, 160));
    }

    /**
     * Переносит фокус в поле ввода SQL.
     */
    public void focusEditor() {
        editor.requestFocusInWindow();
    }
}