package lab.sql.ast;

import lab.sql.condition.ComparisonCondition;

/**
 * Представляет SQL-команду удаления записей.
 */
public class DeleteCommand implements SqlCommand {
    private final String tableName;
    private final ComparisonCondition condition;

    private static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Table name cannot be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }
        return normalized;
    }

    /**
     * Создаёт объект DeleteCommand.
     *
     * @param tableName имя таблицы
     * @param condition условие отбора
     */
    public DeleteCommand(String tableName, ComparisonCondition condition) {
        this.tableName = normalizeName(tableName);
        this.condition = condition;
    }

    /**
     * Возвращает имя таблицы.
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает условие отбора.
     * @return условие отбора
     */
    public ComparisonCondition getCondition() {
        return condition;
    }

    /**
     * Проверяет наличие условия WHERE.
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasCondition() {
        return condition != null;
    }
}