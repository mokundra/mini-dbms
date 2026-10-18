package lab.catalog;

import java.util.Objects;

/**
 * Содержит параметры первичного или вторичного индекса.
 */
public class IndexDefinition {
    private final String name;
    private final String tableName;
    private final String columnName;
    private final boolean unique;
    private final boolean primary;

    /**
     * Создаёт объект IndexDefinition.
     *
     * @param name имя объекта
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @param unique признак уникальности
     * @param primary значение параметра primary
     */
    public IndexDefinition(
            String name,
            String tableName,
            String columnName,
            boolean unique,
            boolean primary
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Index name cannot be empty");
        }

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be empty");
        }

        this.name = name.trim();
        this.tableName = tableName.trim();
        this.columnName = columnName.trim();
        this.unique = unique || primary;
        this.primary = primary;
    }

    /**
     * Создаёт объект IndexDefinition.
     *
     * @param name имя объекта
     * @param tableName имя таблицы
     * @param columnName имя столбца
     */
    public IndexDefinition(String name, String tableName, String columnName) {
        this(name, tableName, columnName, false, false);
    }

    /**
     * Возвращает имя.
     * @return имя
     */
    public String getName() {
        return name;
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

    /**
     * Проверяет заданное условие.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isPrimary() {
        return primary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IndexDefinition that)) {
            return false;
        }

        return unique == that.unique
                && primary == that.primary
                && name.equalsIgnoreCase(that.name)
                && tableName.equalsIgnoreCase(that.tableName)
                && columnName.equalsIgnoreCase(that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name.toLowerCase(),
                tableName.toLowerCase(),
                columnName.toLowerCase(),
                unique,
                primary
        );
    }

    @Override
    public String toString() {
        return "IndexDefinition{" +
                "name='" + name + '\'' +
                ",tableName='" + tableName + '\'' +
                ",columnName='" + columnName + '\'' +
                ",unique=" + unique +
                ",primary=" + primary +
                '}';
    }
}