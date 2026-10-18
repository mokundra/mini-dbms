package lab.sql.ast;

import lab.catalog.ColumnDefinition;
import lab.catalog.ForeignKeyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Представляет SQL-команду создания таблицы.
 */
public class CreateTableCommand implements SqlCommand {
    private final String tableName;
    private final List<ColumnDefinition> columns;
    private final List<ForeignKeyDefinition> foreignKeys;

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

    private static void validateColumns(List<ColumnDefinition> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Table must contain at least one column");
        }

        for (ColumnDefinition column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("Column cannot be null");
            }
        }
    }

    private static List<ColumnDefinition> copyColumns(List<ColumnDefinition> columns) {
        return new ArrayList<>(columns);
    }

    private static List<ForeignKeyDefinition> copyForeignKeys(List<ForeignKeyDefinition> foreignKeys) {
        if (foreignKeys == null) {
            return new ArrayList<>();
        }

        List<ForeignKeyDefinition> result = new ArrayList<>();

        for (ForeignKeyDefinition foreignKey : foreignKeys) {
            if (foreignKey == null) {
                throw new IllegalArgumentException("Foreign key cannot be null");
            }
            result.add(foreignKey);
        }
        return result;
    }

    /**
     * Создаёт объект CreateTableCommand.
     *
     * @param tableName имя таблицы
     * @param columns список столбцов
     * @param foreignKeys список внешних ключей
     */
    public CreateTableCommand(String tableName, List<ColumnDefinition> columns, List<ForeignKeyDefinition> foreignKeys) {
        validateColumns(columns);

        this.tableName = normalizeName(tableName);
        this.columns = copyColumns(columns);
        this.foreignKeys = copyForeignKeys(foreignKeys);
    }

    /**
     * Создаёт объект CreateTableCommand.
     *
     * @param tableName имя таблицы
     * @param columns список столбцов
     */
    public CreateTableCommand(String tableName, List<ColumnDefinition> columns) {
        this(tableName, columns, Collections.emptyList());
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
}