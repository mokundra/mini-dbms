package lab.sql.executor;

import lab.model.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Хранит строки результата, число изменённых записей и служебное сообщение.
 */
public class QueryResult {
    private final List<String> columns;
    private final List<Row> rows;
    private final int affectedRows;
    private final String message;

    private static List<String> copyColumns(List<String> columns) {
        if (columns == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(columns);
    }

    private static List<Row> copyRows(List<Row> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }

        List<Row> result = new ArrayList<>();

        for (Row row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("Result row cannot be null");
            }
            result.add(row.copy());
        }

        return result;
    }

    /**
     * Создаёт объект QueryResult.
     *
     * @param columns список столбцов
     * @param rows список строк
     * @param affectedRows количество изменённых записей
     * @param message текст сообщения
     */
    public QueryResult(
            List<String> columns,
            List<Row> rows,
            int affectedRows,
            String message
    ) {
        if (affectedRows < 0) {
            throw new IllegalArgumentException("Affected rows cannot be negative");
        }

        this.columns = copyColumns(columns);
        this.rows = copyRows(rows);
        this.affectedRows = affectedRows;
        this.message = message;
    }

    /**
     * Создаёт результат с набором строк.
     *
     * @param columns список столбцов
     * @param rows список строк
     * @return результат с выбранными строками
     */
    public static QueryResult rows(List<String> columns, List<Row> rows) {
        return new QueryResult(columns, rows, rows == null ? 0 : rows.size(), null);
    }

    /**
     * Создаёт результат команды с количеством изменённых записей.
     *
     * @param affectedRows количество изменённых записей
     * @param message текст сообщения
     * @return результат с количеством изменённых записей
     */
    public static QueryResult affected(int affectedRows, String message) {
        return new QueryResult(
                Collections.emptyList(),
                Collections.emptyList(),
                affectedRows,
                message
        );
    }

    /**
     * Создаёт результат с текстовым сообщением.
     *
     * @param message текст сообщения
     * @return результат с сообщением
     */
    public static QueryResult message(String message) {
        return new QueryResult(
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                message
        );
    }

    /**
     * Возвращает список столбцов.
     * @return список столбцов
     */
    public List<String> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /**
     * Возвращает список записей.
     * @return список записей
     */
    public List<Row> getRows() {
        List<Row> result = new ArrayList<>();

        for (Row row : rows) {
            result.add(row.copy());
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Возвращает количество изменённых записей.
     * @return количество изменённых записей
     */
    public int getAffectedRows() {
        return affectedRows;
    }

    /**
     * Возвращает сообщение.
     * @return сообщение
     */
    public String getMessage() {
        return message;
    }

    /**
     * Проверяет, содержит ли результат строки.
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasRows() {
        return !rows.isEmpty();
    }

    /**
     * Проверяет, содержит ли результат сообщение.
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

}