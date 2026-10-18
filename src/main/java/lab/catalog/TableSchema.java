package lab.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Описывает структуру таблицы, её столбцы и внешние ключи.
 */
public class TableSchema {
    private final String tableName;
    private final List<ColumnDefinition> columns;
    private final List<ForeignKeyDefinition> foreignKeys;

    /**
     * Создаёт объект TableSchema.
     *
     * @param tableName имя таблицы
     * @param columns список столбцов
     * @param foreignKeys список внешних ключей
     */
    public TableSchema(String tableName, List<ColumnDefinition> columns, List<ForeignKeyDefinition> foreignKeys) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Table must contain at least one column");
        }

        this.tableName = tableName.trim();
        this.columns = new ArrayList<>(columns);
        if (foreignKeys == null) {
            this.foreignKeys = new ArrayList<>();
        } else {
            this.foreignKeys = new ArrayList<>(foreignKeys);
        }

        validateColumns();
        validateForeignKeys();
    }

    /**
     * Создаёт объект TableSchema.
     *
     * @param tableName имя таблицы
     * @param columns список столбцов
     */
    public TableSchema(String tableName, List<ColumnDefinition> columns) {
        this(tableName, columns, Collections.emptyList());
    }

    private void validateColumns() {
        for (int i = 0; i < columns.size(); i++) {
            ColumnDefinition current = columns.get(i);

            if (current == null) {
                throw new IllegalArgumentException("Column cannot be null");
            }
            for (int j = i + 1; j < columns.size(); j++) {
                ColumnDefinition other = columns.get(j);

                if (other != null && current.getName().equalsIgnoreCase(other.getName())) {
                    throw new IllegalArgumentException("Duplicate column name: " + current.getName());
                }
            }
        }

        long primaryKeyCount = columns.stream().filter(ColumnDefinition::isPrimaryKey).count();

        if (primaryKeyCount > 1) {
            throw new IllegalArgumentException("Table cannot contain more than one primary key column");
        }
    }

    private void validateForeignKeys() {
        for (ForeignKeyDefinition foreignKey : foreignKeys) {
            if (foreignKey == null) {
                throw new IllegalArgumentException("Foreign key cannot be null");
            }

            if (!hasColumn(foreignKey.getColumnName())) {
                throw new IllegalArgumentException("Foreign key column does not exist: " + foreignKey.getColumnName());
            }
        }
    }

    /**
     * Проверяет наличие столбца.
     *
     * @param columnName имя столбца
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasColumn(String columnName) {
        return getColumn(columnName).isPresent();
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
    public List<ColumnDefinition> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /**
     * Возвращает список внешних ключей.
     * @return список внешних ключей
     */
    public List<ForeignKeyDefinition> getForeignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    /**
     * Возвращает описание столбца.
     *
     * @param columnName имя столбца
     * @return описание столбца, если он найден
     */
    public Optional<ColumnDefinition> getColumn(String columnName) {
        if (columnName == null) {
            return Optional.empty();
        }

        return columns.stream()
                .filter(column -> column.getName().equalsIgnoreCase(columnName))
                .findFirst();
    }

    /**
     * Возвращает описание первичного ключа.
     * @return описание первичного ключа
     */
    public Optional<ColumnDefinition> getPrimaryKey() {
        return columns.stream().filter(ColumnDefinition::isPrimaryKey).findFirst();
    }

    /**
     * Возвращает число столбцов.
     * @return число столбцов
     */
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TableSchema that)) {
            return false;
        }
        return tableName.equalsIgnoreCase(that.tableName)
                && columns.equals(that.columns)
                && foreignKeys.equals(that.foreignKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                tableName.toLowerCase(),
                columns,
                foreignKeys
        );
    }

    @Override
    public String toString() {
        return "TableSchema{" +
                "tableName='" + tableName + '\'' +
                ",columns=" + columns +
                ",foreignKeys=" + foreignKeys +
                '}';
    }
}