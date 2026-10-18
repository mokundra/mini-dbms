package lab.engine;

import lab.catalog.ColumnDefinition;
import lab.catalog.IndexDefinition;
import lab.catalog.TableSchema;
import lab.index.PrimaryIndex;
import lab.index.SecondaryIndex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Открывает и закрывает файловые индексы таблиц.
 */
public class IndexManager {
    private final Path indexesDirectory;
    private final Map<String, PrimaryIndex> primaryIndexes;
    private final Map<String, SecondaryIndex> secondaryIndexes;

    /**
     * Создаёт объект IndexManager.
     *
     * @param indexesDirectory каталог файлов индексов
     */
    public IndexManager(Path indexesDirectory) {
        if (indexesDirectory == null) {
            throw new IllegalArgumentException("Indexes directory cannot be null");
        }

        this.indexesDirectory = indexesDirectory;
        this.primaryIndexes = new HashMap<>();
        this.secondaryIndexes = new HashMap<>();
    }

    private String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        String normalized = value.trim().toLowerCase();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        return normalized;
    }

    private String primaryKey(String tableName) {
        return normalize(tableName);
    }

    private String secondaryKey(String indexName) {
        return normalize(indexName);
    }

    private Path getPrimaryIndexPath(String tableName) {
        return indexesDirectory.resolve(normalize(tableName) + "_primary.idx");
    }


    private Path getSecondaryIndexPath(String indexName) {
        return indexesDirectory.resolve(normalize(indexName) + ".idx");
    }

    private ColumnDefinition getPrimaryKeyColumn(TableSchema schema) {
        return schema.getPrimaryKey().orElseThrow(() ->
                new IllegalArgumentException("Table has no primary key: " + schema.getTableName())
        );
    }

    private ColumnDefinition getIndexedColumn(TableSchema schema, String columnName) {
        return schema.getColumn(columnName).orElseThrow(() ->
                new IllegalArgumentException("Column does not exist: " + columnName)
        );
    }

    private void validatePrimaryKeyType(ColumnDefinition column) {
        if (column.getType() != lab.catalog.DataType.INT) {
            throw new IllegalArgumentException("PrimaryIndex supports only INT primary keys");
        }
    }

    private void validateSecondaryIndexNotOpened(String indexName) {
        if (secondaryIndexes.containsKey(secondaryKey(indexName))) {
            throw new IllegalStateException("Secondary index is already opened: " + indexName);
        }
    }

    private void validatePrimaryIndexNotOpened(String tableName) {
        if (primaryIndexes.containsKey(primaryKey(tableName))) {
            throw new IllegalStateException("Primary index is already opened for table: " + tableName);
        }
    }

    void createPrimaryIndex(TableSchema schema) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        validatePrimaryIndexNotOpened(schema.getTableName());

        ColumnDefinition primaryKeyColumn = getPrimaryKeyColumn(schema);

        validatePrimaryKeyType(primaryKeyColumn);

        PrimaryIndex index = new PrimaryIndex();

        Path path = getPrimaryIndexPath(schema.getTableName());

        index.create(path);

        primaryIndexes.put(primaryKey(schema.getTableName()), index);

    }

    /**
     * Открывает первичный индекс таблицы или создаёт его при необходимости.
     *
     * @param schema схема таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void openPrimaryIndex(TableSchema schema) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        validatePrimaryIndexNotOpened(schema.getTableName());

        ColumnDefinition primaryKeyColumn = getPrimaryKeyColumn(schema);

        validatePrimaryKeyType(primaryKeyColumn);

        PrimaryIndex index = new PrimaryIndex();

        Path path = getPrimaryIndexPath(schema.getTableName());

        index.open(path);

        primaryIndexes.put(primaryKey(schema.getTableName()), index);
    }

    void createSecondaryIndex(IndexDefinition definition, TableSchema schema) throws IOException {
        if (definition == null) {
            throw new IllegalArgumentException("Index definition cannot be null");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        validateSecondaryIndexNotOpened(definition.getName());
        if (!definition.getTableName().equalsIgnoreCase(schema.getTableName())) {
            throw new IllegalArgumentException("Index table does not match schema");
        }

        ColumnDefinition column = getIndexedColumn(schema, definition.getColumnName());

        Path path = getSecondaryIndexPath(definition.getName());

        SecondaryIndex index = SecondaryIndex.create(
                definition.getName(),
                definition.getTableName(),
                definition.getColumnName(),
                definition.isUnique(),
                column.getType(),
                path
        );

        secondaryIndexes.put(secondaryKey(definition.getName()), index);
    }

    void openSecondaryIndex(IndexDefinition definition, TableSchema schema) throws IOException {
        if (definition == null) {
            throw new IllegalArgumentException("Index definition cannot be null");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        validateSecondaryIndexNotOpened(definition.getName());
        if (!definition.getTableName().equalsIgnoreCase(schema.getTableName())) {
            throw new IllegalArgumentException("Index table does not match schema");
        }

        ColumnDefinition column = getIndexedColumn(schema, definition.getColumnName());

        Path path = getSecondaryIndexPath(definition.getName());

        SecondaryIndex index = SecondaryIndex.open(
                definition.getName(),
                definition.getTableName(),
                definition.getColumnName(),
                definition.isUnique(),
                column.getType(),
                path
        );

        secondaryIndexes.put(secondaryKey(definition.getName()), index);
    }

    /**
     * Возвращает открытый первичный индекс таблицы.
     *
     * @param tableName имя таблицы
     * @return первичный индекс
     */
    public PrimaryIndex getPrimaryIndex(String tableName) {
        PrimaryIndex index = primaryIndexes.get(primaryKey(tableName));

        if (index == null) {
            throw new IllegalStateException("Primary index is not opened for table: " + tableName);
        }
        return index;
    }

    /**
     * Возвращает открытый вторичный индекс по имени.
     *
     * @param indexName имя индекса
     * @return вторичный индекс
     */
    public SecondaryIndex getSecondaryIndex(String indexName) {
        SecondaryIndex index = secondaryIndexes.get(secondaryKey(indexName));

        if (index == null) {
            throw new IllegalStateException("Secondary index is not opened: " + indexName);
        }

        return index;
    }

    /**
     * Ищет вторичный индекс для указанного столбца.
     *
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @return результат операции
     */
    public Optional<SecondaryIndex> findSecondaryIndex(String tableName, String columnName) {
        for (SecondaryIndex index : secondaryIndexes.values()) {
            if (index.getTableName().equalsIgnoreCase(tableName) && index.getColumnName().equalsIgnoreCase(columnName)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    /**
     * Проверяет наличие первичного индекса.
     *
     * @param tableName имя таблицы
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasPrimaryIndex(String tableName) {
        return primaryIndexes.containsKey(primaryKey(tableName));
    }

    /**
     * Проверяет наличие вторичного индекса.
     *
     * @param indexName имя индекса
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasSecondaryIndex(String indexName) {
        return secondaryIndexes.containsKey(secondaryKey(indexName));
    }

    /**
     * Закрывает первичный индекс таблицы.
     *
     * @param tableName имя таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void closePrimaryIndex(String tableName) throws IOException {
        String key = primaryKey(tableName);
        PrimaryIndex index = primaryIndexes.get(key);

        if (index != null) {
            index.close();
            primaryIndexes.remove(key);
        }
    }

    /**
     * Закрывает вторичный индекс.
     *
     * @param indexName имя индекса
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void closeSecondaryIndex(String indexName) throws IOException {
        String key = secondaryKey(indexName);
        SecondaryIndex index = secondaryIndexes.get(key);

        if (index != null) {
            index.close();
            secondaryIndexes.remove(key);
        }
    }

    /**
     * Закрывает все открытые индексы.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void closeAll() throws IOException {
        IOException error = null;

        for (String tableName : new java.util.ArrayList<>(primaryIndexes.keySet())) {
            try {
                closePrimaryIndex(tableName);
            } catch (IOException exception) {
                if (error == null) {
                    error = exception;
                } else {
                    error.addSuppressed(exception);
                }
            }
        }

        for (String indexName : new java.util.ArrayList<>(secondaryIndexes.keySet())) {
            try {
                closeSecondaryIndex(indexName);
            } catch (IOException exception) {
                if (error == null) {
                    error = exception;
                } else {
                    error.addSuppressed(exception);
                }
            }
        }

        if (error != null) {
            throw error;
        }
    }
}
