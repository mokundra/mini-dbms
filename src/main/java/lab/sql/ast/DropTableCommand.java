package lab.sql.ast;

/**
 * Представляет SQL-команду удаления таблицы.
 */
public class DropTableCommand implements SqlCommand {
    private final String tableName;

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
     * Создаёт объект DropTableCommand.
     *
     * @param tableName имя таблицы
     */
    public DropTableCommand(String tableName) {
        this.tableName = normalizeName(tableName);
    }

    /**
     * Возвращает имя таблицы.
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }
}