package lab.sql.ast;

/**
 * Представляет SQL-команду создания индекса.
 */
public class CreateIndexCommand implements SqlCommand {
    private final String indexName;
    private final String tableName;
    private final String columnName;
    private final boolean unique;

    private static String normalizeName(
            String value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }

        return normalized;
    }

    /**
     * Создаёт объект CreateIndexCommand.
     *
     * @param indexName имя индекса
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @param unique признак уникальности
     */
    public CreateIndexCommand(String indexName, String tableName, String columnName, boolean unique) {
        this.indexName = normalizeName(indexName, "Index name");
        this.tableName = normalizeName(tableName, "Table name");
        this.columnName = normalizeName(columnName, "Column name");
        this.unique = unique;
    }

    /**
     * Создаёт объект CreateIndexCommand.
     *
     * @param indexName имя индекса
     * @param tableName имя таблицы
     * @param columnName имя столбца
     */
    public CreateIndexCommand(String indexName, String tableName, String columnName) {
        this(indexName, tableName, columnName, false);
    }

    /**
     * Возвращает имя индекса.
     * @return имя индекса
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Возвращает имя таблицы.
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает имя столбца.
     * @return имя столбца
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Проверяет ограничение уникальности.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isUnique() {
        return unique;
    }
}