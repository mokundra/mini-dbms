package lab.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Хранит схемы таблиц и определения индексов базы данных.
 */
public class Catalog {

    private final Map<String, TableSchema> tables;
    private final Map<String, IndexDefinition> indexes;

    /**
     * Создаёт объект Catalog.
     */
    public Catalog() {
        this.tables = new LinkedHashMap<>();
        this.indexes = new LinkedHashMap<>();
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        String normalized = name.trim().toLowerCase();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        return normalized;
    }

    private TableSchema findTableInternal(String tableName) {
        return tables.get(normalizeName(tableName));
    }

    private IndexDefinition findIndexInternal(String indexName) {
        return indexes.get(normalizeName(indexName));
    }

    private void validateTableDoesNotExist(String tableName) {
        if (containsTable(tableName)) {
            throw new IllegalArgumentException("Table already exists: " + tableName);
        }
    }

    private void validateTableExists(String tableName) {
        if (!containsTable(tableName)) {
            throw new IllegalArgumentException("Table does not exist: " + tableName);
        }
    }

    private void validateIndexDoesNotExist(String indexName) {
        if (containsIndex(indexName)) {
            throw new IllegalArgumentException("Index already exists: " + indexName
            );
        }
    }

    private void validateIndexExists(String indexName) {
        if (!containsIndex(indexName)) {
            throw new IllegalArgumentException("Index does not exist: " + indexName);
        }
    }

    private void validateIndexDefinition(IndexDefinition indexDefinition) {
        if (indexDefinition == null) {
            throw new IllegalArgumentException("Index definition cannot be null");
        }

        validateTableExists(indexDefinition.getTableName());

        TableSchema tableSchema = getTable(indexDefinition.getTableName());

        if (!tableSchema.hasColumn(indexDefinition.getColumnName())) {
            throw new IllegalArgumentException("Column does not exist: "
                            + indexDefinition.getTableName()
                            + "."
                            + indexDefinition.getColumnName()
            );
        }
    }

    private void validateForeignKeys(TableSchema tableSchema) {
        for (ForeignKeyDefinition foreignKey : tableSchema.getForeignKeys()) {

            if (!containsTable(foreignKey.getReferencedTable())) {
                throw new IllegalArgumentException("Referenced table does not exist: " + foreignKey.getReferencedTable());
            }
            TableSchema referencedTable = getTable(foreignKey.getReferencedTable());

            ColumnDefinition localColumn = tableSchema.getColumn(foreignKey.getColumnName()).orElseThrow();

            ColumnDefinition referencedColumn = referencedTable.getColumn(foreignKey.getReferencedColumn())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Referenced column does not exist: "
                                                    + foreignKey.getReferencedTable()
                                                    + "."
                                                    + foreignKey.getReferencedColumn()
                                    )
                            );

            if (localColumn.getType() != referencedColumn.getType()) {
                throw new IllegalArgumentException("Foreign key type mismatch");
            }

            if (!referencedColumn.isPrimaryKey() && !referencedColumn.isUnique()) {
                throw new IllegalArgumentException("Referenced column must be primary key or unique");
            }
        }
    }

    private boolean isTableReferenced(String tableName) {
        for (TableSchema schema : tables.values()) {
            for (ForeignKeyDefinition foreignKey : schema.getForeignKeys()) {
                if (foreignKey.getReferencedTable().equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void removeIndexesForTable(String tableName) {
        indexes.values().removeIf(index ->
                        index.getTableName().equalsIgnoreCase(tableName)
        );
    }

    /**
     * Проверяет наличие таблицы в каталоге.
     *
     * @param tableName имя таблицы
     * @return true, если проверяемое условие выполнено
     */
    public boolean containsTable(String tableName) {
        return findTableInternal(tableName) != null;
    }

    /**
     * Проверяет наличие индекса в каталоге.
     *
     * @param indexName имя индекса
     * @return true, если проверяемое условие выполнено
     */
    public boolean containsIndex(String indexName) {
        return findIndexInternal(indexName) != null;
    }

    /**
     * Возвращает хранилище таблицы.
     *
     * @param tableName имя таблицы
     * @return хранилище таблицы
     */
    public TableSchema getTable(String tableName) {
        validateTableExists(tableName);
        return findTableInternal(tableName);
    }

    /**
     * Возвращает индекс.
     *
     * @param indexName имя индекса
     * @return индекс
     */
    public IndexDefinition getIndex(String indexName) {
        validateIndexExists(indexName);
        return findIndexInternal(indexName);
    }

    /**
     * Ищет индекс по таблице и столбцу.
     *
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @return результат операции
     */
    public Optional<IndexDefinition> findIndex(String tableName, String columnName) {
        String normalizedTableName = normalizeName(tableName);

        String normalizedColumnName = normalizeName(columnName);

        return indexes.values().stream().filter(index ->
                        index.getTableName().equalsIgnoreCase(normalizedTableName)
                                && index.getColumnName()
                                .equalsIgnoreCase(normalizedColumnName)).findFirst();
    }

    /**
     * Возвращает индексы заданной таблицы.
     *
     * @param tableName имя таблицы
     * @return индексы таблицы
     */
    public List<IndexDefinition> getIndexesForTable(String tableName) {
        validateTableExists(tableName);
        List<IndexDefinition> result = new ArrayList<>();

        for (IndexDefinition index : indexes.values()) {
            if (index.getTableName().equalsIgnoreCase(tableName)) {result.add(index);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Регистрирует схему новой таблицы.
     *
     * @param tableSchema схема таблицы
     */
    public void addTable(TableSchema tableSchema) {
        if (tableSchema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        validateTableDoesNotExist(tableSchema.getTableName());

        validateForeignKeys(tableSchema);

        tables.put(normalizeName(tableSchema.getTableName()), tableSchema);
    }

    /**
     * Удаляет таблицу из каталога.
     *
     * @param tableName имя таблицы
     */
    public void removeTable(String tableName) {
        validateTableExists(tableName);
        if (isTableReferenced(tableName)) {
            throw new IllegalStateException("Cannot remove table because it is referenced by a foreign key: " + tableName);
        }

        tables.remove(normalizeName(tableName));
        removeIndexesForTable(tableName);
    }

    /**
     * Регистрирует индекс в каталоге.
     *
     * @param indexDefinition описание индекса
     */
    public void addIndex(IndexDefinition indexDefinition) {
        validateIndexDefinition(indexDefinition);

        validateIndexDoesNotExist(indexDefinition.getName());

        indexes.put(normalizeName(indexDefinition.getName()), indexDefinition);
    }

    /**
     * Удаляет определение индекса из каталога.
     *
     * @param indexName имя индекса
     */
    public void removeIndex(String indexName) {
        validateIndexExists(indexName);
        indexes.remove(normalizeName(indexName));
    }
    /**
     * Возвращает список таблиц.
     * @return список таблиц
     */
    public Collection<TableSchema> getTables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    /**
     * Возвращает список индексов.
     * @return список индексов
     */
    public Collection<IndexDefinition> getIndexes() {
        return Collections.unmodifiableCollection(indexes.values());
    }

    /**
     * Возвращает число таблиц.
     * @return число таблиц
     */
    public int getTableCount() {
        return tables.size();
    }

    /**
     * Возвращает число индексов.
     * @return число индексов
     */
    public int getIndexCount() {
        return indexes.size();
    }
}