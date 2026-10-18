package lab.engine;

import lab.catalog.Catalog;
import lab.catalog.TableSchema;
import lab.storage.TableStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Управляет файлами таблиц и открытыми хранилищами.
 */
public class TableManager {
    private static final int DEFAULT_RECORD_SIZE = 4096;
    private final Path tablesDirectory;
    private final Path freeSpaceDirectory;
    private final Catalog catalog;
    private final Map<String, TableStorage> storages;

    /**
     * Создаёт объект TableManager.
     *
     * @param tablesDirectory каталог файлов таблиц
     * @param freeSpaceDirectory каталог учёта свободных мест
     * @param catalog каталог таблиц и индексов
     * @throws IOException при ошибке чтения или записи файлов
     */
    public TableManager(
            Path tablesDirectory,
            Path freeSpaceDirectory,
            Catalog catalog
    ) throws IOException {
        if (tablesDirectory == null) {
            throw new IllegalArgumentException("Tables directory cannot be null");
        }

        if (freeSpaceDirectory == null) {
            throw new IllegalArgumentException("Free space directory cannot be null");
        }

        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }

        this.tablesDirectory = tablesDirectory;
        this.freeSpaceDirectory = freeSpaceDirectory;
        this.catalog = catalog;
        this.storages = new HashMap<>();

        Files.createDirectories(tablesDirectory);
        Files.createDirectories(freeSpaceDirectory);
    }

    private String normalizeName(String tableName) {
        if (tableName == null) {
            throw new IllegalArgumentException("Table name cannot be null");
        }

        String normalized = tableName.trim().toLowerCase();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        return normalized;
    }

    private Path getTablePath(String tableName) {
        return tablesDirectory.resolve(normalizeName(tableName) + ".db");
    }

    private Path getFreeSpacePath(String tableName) {
        return freeSpaceDirectory.resolve(normalizeName(tableName) + ".free");
    }

    /**
     * Проверяет открытие таблицы.
     *
     * @param tableName имя таблицы
     * @return true, если проверяемое условие выполнено
     */
    public boolean isOpened(String tableName) {
        return storages.containsKey(normalizeName(tableName));
    }

    private void validateTableNotOpened(String tableName) {
        if (isOpened(tableName)) {
            throw new IllegalStateException("Table is already opened: " + tableName);
        }
    }

    private void validateTableOpened(String tableName) {
        if (!isOpened(tableName)) {
            throw new IllegalStateException("Table is not opened: " + tableName);
        }
    }

    private void validateTableDoesNotExist(String tableName) {
        if (catalog.containsTable(tableName)) {
            throw new IllegalArgumentException("Table already exists: " + tableName);
        }
    }

    private void validateTableExists(String tableName) {
        if (!catalog.containsTable(tableName)) {
            throw new IllegalArgumentException("Table does not exist: " + tableName);
        }
    }

    private TableStorage getStorageInternal(String tableName) {
        return storages.get(normalizeName(tableName));
    }


    private void deleteTableFiles(String tableName) throws IOException {
        Files.deleteIfExists(getTablePath(tableName));
        Files.deleteIfExists(getFreeSpacePath(tableName));
    }

    /**
     * Возвращает хранилище таблицы.
     *
     * @param tableName имя таблицы
     * @return хранилище таблицы
     */
    public TableStorage getTable(String tableName) {
        validateTableOpened(tableName);
        return getStorageInternal(tableName);
    }

    /**
     * Создаёт файловое хранилище для таблицы.
     *
     * @param schema схема таблицы
     * @param recordSize размер одной записи в байтах
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void createTable(TableSchema schema, int recordSize) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        if (recordSize <= 0) {
            throw new IllegalArgumentException("Record size must be positive");
        }

        String tableName = schema.getTableName();

        validateTableDoesNotExist(tableName);
        validateTableNotOpened(tableName);

        catalog.addTable(schema);

        try {
            TableStorage storage = TableStorage.create(
                    getTablePath(tableName),
                    getFreeSpacePath(tableName),
                    schema,
                    recordSize
            );

            storages.put(normalizeName(tableName), storage);
        } catch (Exception e) {
            catalog.removeTable(tableName);
            if (e instanceof IOException ioException) {
                throw ioException;
            }

            throw e;
        }
    }

    /**
     * Создаёт файловое хранилище для таблицы.
     *
     * @param schema схема таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void createTable(TableSchema schema) throws IOException {
        createTable(schema, DEFAULT_RECORD_SIZE);
    }

    /**
     * Открывает файловое хранилище существующей таблицы.
     *
     * @param tableName имя таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void openTable(String tableName) throws IOException {
        validateTableExists(tableName);
        validateTableNotOpened(tableName);

        TableSchema schema = catalog.getTable(tableName);

        TableStorage storage = TableStorage.open(
                getTablePath(tableName),
                getFreeSpacePath(tableName),
                schema
        );

        storages.put(normalizeName(tableName), storage);
    }

    /**
     * Закрывает файловое хранилище таблицы.
     *
     * @param tableName имя таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void closeTable(String tableName) throws IOException {
        validateTableOpened(tableName);
        TableStorage storage = storages.remove(normalizeName(tableName));
        storage.close();
    }

    /**
     * Удаляет файлы таблицы.
     *
     * @param tableName имя таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void dropTable(String tableName) throws IOException {
        validateTableExists(tableName);
        for (TableSchema schema : catalog.getTables()) {
            for (var foreignKey : schema.getForeignKeys()) {
                if (foreignKey.getReferencedTable().equalsIgnoreCase(tableName)) {
                    throw new IllegalStateException("Cannot remove table because it is referenced by a foreign key: " + tableName);
                }
            }
        }
        if (isOpened(tableName)) {
            closeTable(tableName);
        }
        deleteTableFiles(tableName);
        catalog.removeTable(tableName);
    }

    /**
     * Открывает все таблицы из каталога.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void openAll() throws IOException {
        for (TableSchema schema : catalog.getTables()) {
            if (!isOpened(schema.getTableName())) {
                openTable(schema.getTableName());
            }
        }
    }

    /**
     * Возвращает коллекцию открытых таблиц.
     * @return коллекцию открытых таблиц
     */
    public Collection<TableStorage> getOpenedTables() {
        return Collections.unmodifiableCollection(storages.values());
    }

    /**
     * Возвращает число открытых таблиц.
     * @return число открытых таблиц
     */
    public int getOpenedTableCount() {
        return storages.size();
    }

    /**
     * Закрывает все открытые таблицы.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void closeAll() throws IOException {
        IOException error = null;

        for (TableStorage storage : storages.values()) {
            try {
                storage.close();
            } catch (IOException e) {
                if (error == null) {
                    error = e;
                }
            }
        }
        storages.clear();

        if (error != null) {
            throw error;
        }
    }


}