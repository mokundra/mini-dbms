package lab.engine;

import lab.catalog.Catalog;
import lab.catalog.ForeignKeyDefinition;
import lab.catalog.IndexDefinition;
import lab.catalog.TableSchema;
import lab.index.PrimaryIndex;
import lab.index.SecondaryIndex;
import lab.model.Row;
import lab.storage.TableStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Управляет таблицами, записями и индексами базы данных.
 */
public class DatabaseEngine {

    private final Path databaseDirectory;
    private final Path tablesDirectory;
    private final Path freeSpaceDirectory;
    private final Path indexesDirectory;

    private final Catalog catalog;
    private final TableManager tableManager;
    private final IndexManager indexManager;

    private boolean closed;

    /**
     * Создаёт объект DatabaseEngine.
     *
     * @param databaseDirectory папка с файлами базы данных
     * @param catalog каталог таблиц и индексов
     * @throws IOException при ошибке чтения или записи файлов
     */
    public DatabaseEngine(Path databaseDirectory, Catalog catalog) throws IOException {
        if (databaseDirectory == null) {
            throw new IllegalArgumentException("Database directory cannot be null");
        }

        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }

        this.databaseDirectory = databaseDirectory;
        this.tablesDirectory = databaseDirectory.resolve("tables");
        this.freeSpaceDirectory = databaseDirectory.resolve("free");
        this.indexesDirectory = databaseDirectory.resolve("indexes");
        this.catalog = catalog;

        Files.createDirectories(databaseDirectory);
        Files.createDirectories(tablesDirectory);
        Files.createDirectories(freeSpaceDirectory);
        Files.createDirectories(indexesDirectory);

        this.tableManager = new TableManager(tablesDirectory, freeSpaceDirectory, catalog);

        this.indexManager = new IndexManager(indexesDirectory);

        this.closed = false;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Database engine is closed");
        }
    }

    private TableSchema getSchema(String tableName) {
        return catalog.getTable(tableName);
    }

    private TableStorage getStorage(String tableName) {
        return tableManager.getTable(tableName);
    }

    private Object getPrimaryKeyValue(TableSchema schema, Row row) {
        String primaryKeyColumn = schema.getPrimaryKey().orElseThrow(() ->
                new IllegalStateException("Table has no primary key: " + schema.getTableName())
        ).getName();

        Object value = row.get(primaryKeyColumn);

        if (value == null) {
            throw new IllegalArgumentException("Primary key cannot be null");
        }
        return value;
    }

    private int getPrimaryKeyAsInt(TableSchema schema, Row row) {
        Object value = getPrimaryKeyValue(schema, row);

        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException("Primary key must be INT");
        }
        return integer;
    }

    private void insertIntoSecondaryIndexes(String tableName, Row row, int recordNumber) throws IOException {
        for (IndexDefinition definition : catalog.getIndexesForTable(tableName)) {
            if (definition.isPrimary()) {
                continue;
            }

            SecondaryIndex index = indexManager.getSecondaryIndex(definition.getName());

            Object key = row.get(definition.getColumnName());

            if (key != null) {
                index.insert(key, recordNumber);
            }
        }
    }

    private void rollbackSecondaryIndexes(String tableName, Row row, int recordNumber, Exception original) {
        for (IndexDefinition definition : catalog.getIndexesForTable(tableName)) {
            if (definition.isPrimary()) {
                continue;
            }
            Object key = row.get(definition.getColumnName());
            if (key == null) {
                continue;
            }
            try {
                indexManager.getSecondaryIndex(definition.getName()).remove(key, recordNumber);
            } catch (Exception e) {
                original.addSuppressed(e);
            }
        }
    }

    private void restoreSecondaryIndexes(String tableName, Row row, int recordNumber, Exception original) {
        for (IndexDefinition definition : catalog.getIndexesForTable(tableName)) {
            if (definition.isPrimary()) {
                continue;
            }
            Object key = row.get(definition.getColumnName());
            if (key == null) {
                continue;
            }
            try {
                indexManager.getSecondaryIndex(definition.getName()).insert(key, recordNumber);
            } catch (Exception e) {
                original.addSuppressed(e);
            }
        }
    }

    private void removeFromSecondaryIndexes(String tableName, Row row, int recordNumber) throws IOException {
        for (IndexDefinition definition : catalog.getIndexesForTable(tableName)) {
            if (definition.isPrimary()) {
                continue;
            }
            SecondaryIndex index = indexManager.getSecondaryIndex(definition.getName());

            Object key = row.get(definition.getColumnName());

            if (key != null) {
                index.remove(key, recordNumber);
            }
        }
    }

    private void validateReferencedRows(String tableName, Row oldRow, Row newRow) throws IOException {
        for (TableSchema schema : catalog.getTables()) {
            for (ForeignKeyDefinition foreignKey : schema.getForeignKeys()) {
                if (!foreignKey.getReferencedTable().equalsIgnoreCase(tableName)) {
                    continue;
                }

                Object oldValue = oldRow.get(foreignKey.getReferencedColumn());
                Object newValue = newRow == null ? null : newRow.get(foreignKey.getReferencedColumn());

                if (oldValue == null || oldValue.equals(newValue)) {
                    continue;
                }

                TableStorage storage = getStorage(schema.getTableName());

                for (int i = 0; i < storage.getTotalRecords(); i++) {
                    if (!storage.isActive(i)) {
                        continue;
                    }

                    Row row = storage.read(i);

                    if (oldValue.equals(row.get(foreignKey.getColumnName()))) {
                        throw new IllegalStateException(
                                "Record is referenced by foreign key: " + schema.getTableName()
                                        + "." + foreignKey.getColumnName()
                        );
                    }
                }
            }
        }
    }

    private void validateTableCanBeDropped(String tableName) {
        catalog.getTable(tableName);

        for (TableSchema schema : catalog.getTables()) {
            for (ForeignKeyDefinition foreignKey : schema.getForeignKeys()) {
                if (foreignKey.getReferencedTable().equalsIgnoreCase(tableName)) {
                    throw new IllegalStateException(
                            "Cannot drop referenced table: " + tableName
                    );
                }
            }
        }
    }

    /**
     * Возвращает каталог.
     * @return каталог
     */
    public Catalog getCatalog() {
        checkOpen();
        return catalog;
    }

    /**
     * Возвращает менеджер таблиц.
     * @return менеджер таблиц
     */
    public TableManager getTableManager() {
        checkOpen();
        return tableManager;
    }

    /**
     * Возвращает менеджер индексов.
     * @return менеджер индексов
     */
    public IndexManager getIndexManager() {
        checkOpen();
        return indexManager;
    }

    /**
     * Создаёт таблицу и её файловое хранилище.
     *
     * @param schema схема таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void createTable(TableSchema schema) throws IOException {
        checkOpen();

        if (schema.getPrimaryKey().isEmpty()
                || schema.getPrimaryKey().get().getType() != lab.catalog.DataType.INT) {
            throw new IllegalArgumentException("Table must have INT PRIMARY KEY");
        }

        tableManager.createTable(schema);

        if (schema.getPrimaryKey().isPresent()) {
            try {
                indexManager.createPrimaryIndex(schema);
            } catch (Exception e) {
                tableManager.dropTable(schema.getTableName());
                if (e instanceof IOException ioException) {
                    throw ioException;
                }
                throw e;
            }
        }
    }

    /**
     * Открывает существующую таблицу и связанные индексы.
     *
     * @param tableName имя таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void openTable(String tableName) throws IOException {
        checkOpen();

        TableSchema schema = getSchema(tableName);

        tableManager.openTable(tableName);

        try {
            if (schema.getPrimaryKey().isPresent()) {
                indexManager.openPrimaryIndex(schema);
            }
            for (IndexDefinition definition : catalog.getIndexesForTable(tableName)) {
                if (!definition.isPrimary()) {
                    indexManager.openSecondaryIndex(definition, schema);
                }
            }
        } catch (Exception e) {
            tableManager.closeTable(tableName);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Открывает все таблицы, перечисленные в каталоге.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void openAll() throws IOException {
        checkOpen();
        for (TableSchema schema : catalog.getTables()) {
            if (!tableManager.isOpened(schema.getTableName())) {
                openTable(schema.getTableName());
            }
        }
    }

    /**
     * Добавляет запись после проверки ограничений и обновляет индексы.
     *
     * @param tableName имя таблицы
     * @param row запись таблицы
     * @return номер добавленной записи
     * @throws IOException при ошибке чтения или записи файлов
     */
    public int insert(String tableName, Row row) throws IOException {
        checkOpen();
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        TableSchema schema = getSchema(tableName);

        TableStorage storage = getStorage(tableName);

        Row normalizedRow = row.withDefaults(schema);

        int primaryKey = getPrimaryKeyAsInt(schema, normalizedRow);

        PrimaryIndex primaryIndex = indexManager.getPrimaryIndex(tableName);

        if (primaryIndex.contains(primaryKey)) {
            throw new IllegalStateException("Duplicate primary key: " + primaryKey);
        }

        int recordNumber = storage.insert(normalizedRow);

        try {
            primaryIndex.put(primaryKey, recordNumber);
            insertIntoSecondaryIndexes(tableName, normalizedRow, recordNumber);
            return recordNumber;
        } catch (Exception e) {
            rollbackSecondaryIndexes(tableName, normalizedRow, recordNumber, e);
            try {
                if (primaryIndex.contains(primaryKey)) {
                    primaryIndex.remove(primaryKey);
                }
            } catch (Exception rollbackException) {
                e.addSuppressed(rollbackException);
            }
            try {
                storage.delete(recordNumber);
            } catch (Exception rollbackException) {
                e.addSuppressed(rollbackException);
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Читает запись по её номеру в файле таблицы.
     *
     * @param tableName имя таблицы
     * @param recordNumber номер записи в файле таблицы
     * @return прочитанную запись
     * @throws IOException при ошибке чтения или записи файлов
     */
    public Row read(String tableName, int recordNumber) throws IOException {
        checkOpen();
        return getStorage(tableName).read(recordNumber);
    }

    /**
     * Находит запись по первичному ключу.
     *
     * @param tableName имя таблицы
     * @param primaryKey признак первичного ключа
     * @return найденную запись или null при отсутствии ключа
     * @throws IOException при ошибке чтения или записи файлов
     */
    public Row findByPrimaryKey(String tableName, int primaryKey) throws IOException {
        checkOpen();

        PrimaryIndex primaryIndex = indexManager.getPrimaryIndex(tableName);

        long recordNumber = primaryIndex.get(primaryKey);

        if (recordNumber < 0) {
            return null;
        }

        if (recordNumber > Integer.MAX_VALUE) {
            throw new IOException("Record number is too large");
        }

        return getStorage(tableName).read((int) recordNumber);
    }

    /**
     * Возвращает номера записей по значению вторичного индекса.
     *
     * @param indexName имя индекса
     * @param key значение ключа
     * @return номера записей с заданным ключом
     * @throws IOException при ошибке чтения или записи файлов
     */
    public List<Integer> findByIndex(String indexName, Object key) throws IOException {
        checkOpen();
        SecondaryIndex index = indexManager.getSecondaryIndex(indexName);
        return index.find(key);
    }

    /**
     * Заменяет запись и обновляет связанные индексы.
     *
     * @param tableName имя таблицы
     * @param recordNumber номер записи в файле таблицы
     * @param newRow новая версия записи
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void update(String tableName, int recordNumber, Row newRow) throws IOException {
        checkOpen();

        if (newRow == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        TableSchema schema = getSchema(tableName);

        TableStorage storage = getStorage(tableName);

        Row oldRow = storage.read(recordNumber);

        if (oldRow == null) {
            throw new IllegalStateException("Record is deleted: " + recordNumber);
        }

        Row normalizedNewRow = newRow.withDefaults(schema);

        int oldPrimaryKey = getPrimaryKeyAsInt(schema, oldRow);

        int newPrimaryKey = getPrimaryKeyAsInt(schema, normalizedNewRow);

        PrimaryIndex primaryIndex = indexManager.getPrimaryIndex(tableName);

        if (oldPrimaryKey != newPrimaryKey && primaryIndex.contains(newPrimaryKey)) {
            throw new IllegalStateException("Duplicate primary key: " + newPrimaryKey);
        }

        validateReferencedRows(tableName, oldRow, normalizedNewRow);

        try {
            removeFromSecondaryIndexes(tableName, oldRow, recordNumber);
            storage.update(recordNumber, normalizedNewRow);

            if (oldPrimaryKey != newPrimaryKey) {
                primaryIndex.remove(oldPrimaryKey);
                primaryIndex.put(newPrimaryKey, recordNumber);
            }
            insertIntoSecondaryIndexes(tableName, normalizedNewRow, recordNumber);

        } catch (Exception e) {
            rollbackSecondaryIndexes(tableName, normalizedNewRow, recordNumber, e);
            try {
                storage.update(recordNumber, oldRow);
            } catch (Exception rollbackException) {
                e.addSuppressed(rollbackException);
            }

            if (oldPrimaryKey != newPrimaryKey) {
                try {
                    if (primaryIndex.contains(newPrimaryKey)) {
                        primaryIndex.remove(newPrimaryKey);
                    }
                    if (!primaryIndex.contains(oldPrimaryKey)) {
                        primaryIndex.put(oldPrimaryKey, recordNumber);
                    }
                } catch (Exception rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }

            restoreSecondaryIndexes(tableName, oldRow, recordNumber, e);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Удаляет запись и обновляет связанные индексы.
     *
     * @param tableName имя таблицы
     * @param recordNumber номер записи в файле таблицы
     * @return true, если запись удалена
     * @throws IOException при ошибке чтения или записи файлов
     */
    public boolean delete(String tableName, int recordNumber) throws IOException {
        checkOpen();
        TableSchema schema = getSchema(tableName);

        TableStorage storage = getStorage(tableName);

        Row row = storage.read(recordNumber);

        if (row == null) {
            return false;
        }

        int primaryKey = getPrimaryKeyAsInt(schema, row);

        PrimaryIndex primaryIndex = indexManager.getPrimaryIndex(tableName);

        validateReferencedRows(tableName, row, null);

        try {
            removeFromSecondaryIndexes(tableName, row, recordNumber);
            boolean deleted = storage.delete(recordNumber);
            if (!deleted) {
                return false;
            }
            primaryIndex.remove(primaryKey);
            return true;
        } catch (Exception e) {
            insertIntoSecondaryIndexes(tableName, row, recordNumber);

            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Создаёт вторичный индекс и заполняет его существующими записями.
     *
     * @param definition описание индекса
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void createSecondaryIndex(IndexDefinition definition) throws IOException {
        checkOpen();
        if (definition == null) {
            throw new IllegalArgumentException("Index definition cannot be null");
        }
        TableSchema schema = getSchema(definition.getTableName());

        catalog.addIndex(definition);

        try {
            indexManager.createSecondaryIndex(definition, schema);

            TableStorage storage = getStorage(definition.getTableName());

            SecondaryIndex index = indexManager.getSecondaryIndex(definition.getName());

            for (int i = 0; i < storage.getTotalRecords(); i++) {
                if (!storage.isActive(i)) {
                    continue;
                }
                Row row = storage.read(i);

                Object key = row.get(definition.getColumnName());

                if (key != null) {
                    index.insert(key, i);
                }
            }
        } catch (Exception e) {
            catalog.removeIndex(definition.getName());
            if (indexManager.hasSecondaryIndex(definition.getName())) {
                try {
                    indexManager.closeSecondaryIndex(definition.getName());
                } catch (IOException closeException) {
                    e.addSuppressed(closeException);
                }
            }
            try {
                Files.deleteIfExists(indexesDirectory.resolve(definition.getName().trim().toLowerCase() + ".idx"));
            } catch (IOException deleteException) {
                e.addSuppressed(deleteException);
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Удаляет таблицу вместе с её файлами и индексами.
     *
     * @param tableName имя таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void dropTable(String tableName) throws IOException {
        checkOpen();
        validateTableCanBeDropped(tableName);

        List<IndexDefinition> definitions = List.copyOf(catalog.getIndexesForTable(tableName));

        if (indexManager.hasPrimaryIndex(tableName)) {
            indexManager.closePrimaryIndex(tableName);
        }

        for (IndexDefinition definition : definitions) {
            if (!definition.isPrimary() && indexManager.hasSecondaryIndex(definition.getName())) {
                indexManager.closeSecondaryIndex(definition.getName());
            }
        }

        tableManager.dropTable(tableName);

        Files.deleteIfExists(indexesDirectory.resolve(tableName.trim().toLowerCase() + "_primary.idx"));

        for (IndexDefinition definition : definitions) {
            if (!definition.isPrimary()) {
                Files.deleteIfExists(indexesDirectory.resolve(definition.getName().trim().toLowerCase() + ".idx"));
            }
        }
    }

    /**
     * Закрывает открытые файлы таблиц и индексов.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void close() throws IOException {
        if (closed) {
            return;
        }
        IOException error = null;
        try {
            indexManager.closeAll();
        } catch (IOException e) {
            error = e;
        }

        try {
            tableManager.closeAll();
        } catch (IOException e) {
            if (error == null) {
                error = e;
            }
        }
        closed = true;
        if (error != null) {
            throw error;
        }
    }

}