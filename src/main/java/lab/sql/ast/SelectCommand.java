package lab.sql.ast;

import lab.sql.condition.ComparisonCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Представляет SQL-команду выборки данных.
 */
public class SelectCommand implements SqlCommand {

    private final String tableName;
    private final List<String> columns;
    private final ComparisonCondition condition;

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

    /**
     * Создаёт объект SelectCommand.
     *
     * @param tableName имя таблицы
     * @param columns список столбцов
     * @param condition условие отбора
     */
    public SelectCommand(String tableName, List<String> columns, ComparisonCondition condition) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        this.tableName = tableName.trim();
        this.columns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);
        this.condition = condition;
        validateColumns();
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
     * Возвращает условие отбора.
     * @return условие отбора
     */
    public ComparisonCondition getCondition() {
        return condition;
    }

    /**
     * Проверяет, запрашиваются ли все столбцы таблицы.
     * @return результат операции
     */
    public boolean selectsAllColumns() {
        return columns.isEmpty();
    }

    /**
     * Проверяет наличие условия WHERE.
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasCondition() {
        return condition != null;
    }
}