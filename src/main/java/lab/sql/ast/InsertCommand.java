package lab.sql.ast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Представляет SQL-команду вставки одной или нескольких записей.
 */
public class InsertCommand implements SqlCommand {
    private final String tableName;
    private final List<String> columns;
    private final List<List<Object>> rows;

    private void validateColumns() {
        for (String column : columns) {
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("Column name cannot be empty");
            }
        }
        for (int i = 0; i < columns.size(); i++) {
            for (int j = i + 1; j < columns.size(); j++) {
                if (columns.get(i).equalsIgnoreCase(columns.get(j))) {
                    throw new IllegalArgumentException("Duplicate column: " + columns.get(i));
                }
            }
        }
    }

    private void validateRows() {
        if (columns.isEmpty()) {
            return;
        }
        for (List<Object> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException("Values count does not match columns count");
            }
        }
    }


    /**
     * Создаёт объект InsertCommand.
     *
     * @param tableName имя таблицы
     * @param columns список столбцов
     * @param rows список строк
     */
    public InsertCommand(String tableName, List<String> columns, List<List<Object>> rows) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Insert must contain at least one row");
        }

        this.tableName = tableName.trim();

        this.columns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);

        this.rows = new ArrayList<>();

        for (List<Object> row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("Insert row cannot be null");
            }
            this.rows.add(new ArrayList<>(row));
        }
        validateColumns();
        validateRows();
    }

    /**
     * Возвращает имя таблицы.
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
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
    public List<List<Object>> getRows() {
        List<List<Object>> result = new ArrayList<>();

        for (List<Object> row : rows) {
            result.add(Collections.unmodifiableList(row));
        }

        return Collections.unmodifiableList(result);

    }

    /**
     * Проверяет, перечислены ли столбцы в команде INSERT.
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasExplicitColumns() {
        return !columns.isEmpty();
    }

    /**
     * Возвращает количество вставляемых строк.
     * @return количество строк
     */
    public int getRowCount() {
        return rows.size();
    }
}
