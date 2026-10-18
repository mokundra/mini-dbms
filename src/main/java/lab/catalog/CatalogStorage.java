package lab.catalog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сохраняет каталог базы данных в файл и восстанавливает его при запуске.
 */
public class CatalogStorage {

    private static final int MAGIC = 0x43415431;

    private CatalogStorage() {
    }


    private static void validatePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Catalog path cannot be null");
        }
    }

    private static void validateCatalog(Catalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }
    }

    private static void writeValue(DataOutputStream output, DataType type, Object value) throws IOException {
        output.writeBoolean(value != null);

        if (value == null) {
            return;
        }

        if (type == DataType.INT) {
            output.writeInt((Integer) value);

        } else if (type == DataType.DOUBLE) {
            output.writeDouble((Double) value);

        } else if (type == DataType.STRING) {
            output.writeUTF((String) value);

        } else if (type == DataType.DATE) {
            output.writeLong(((LocalDate) value).toEpochDay());

        } else if (type == DataType.BOOLEAN) {
            output.writeBoolean((Boolean) value);

        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type);
        }
    }

    private static Object readValue(DataInputStream input, DataType type) throws IOException {
        boolean hasValue = input.readBoolean();

        if (!hasValue) {
            return null;
        }

        if (type == DataType.INT) {
            return input.readInt();

        } else if (type == DataType.DOUBLE) {
            return input.readDouble();

        } else if (type == DataType.STRING) {
            return input.readUTF();

        } else if (type == DataType.DATE) {
            return LocalDate.ofEpochDay(input.readLong());

        } else if (type == DataType.BOOLEAN) {
            return input.readBoolean();
        }

        throw new IllegalArgumentException("Unsupported data type: " + type);
    }

    private static void writeColumn(DataOutputStream output, ColumnDefinition column) throws IOException {
        output.writeUTF(column.getName());
        output.writeUTF(column.getType().name());
        output.writeBoolean(column.isPrimaryKey());
        output.writeBoolean(column.isNullable());
        output.writeBoolean(column.isUnique());
        writeValue(output, column.getType(), column.getDefaultValue());
    }

    private static ColumnDefinition readColumn(DataInputStream input) throws IOException {
        String name = input.readUTF();

        DataType type = DataType.fromString(input.readUTF());

        boolean primaryKey = input.readBoolean();
        boolean nullable = input.readBoolean();
        boolean unique = input.readBoolean();

        Object defaultValue = readValue(input, type);

        return new ColumnDefinition(
                name,
                type,
                primaryKey,
                nullable,
                unique,
                defaultValue
        );
    }

    private static void writeForeignKey(DataOutputStream output, ForeignKeyDefinition foreignKey) throws IOException {
        output.writeUTF(foreignKey.getColumnName());
        output.writeUTF(foreignKey.getReferencedTable());
        output.writeUTF(foreignKey.getReferencedColumn());
    }


    private static ForeignKeyDefinition readForeignKey(DataInputStream input) throws IOException {
        String columnName = input.readUTF();
        String referencedTable = input.readUTF();
        String referencedColumn = input.readUTF();

        return new ForeignKeyDefinition(columnName, referencedTable, referencedColumn);
    }

    private static void writeTable(DataOutputStream output, TableSchema schema) throws IOException {
        output.writeUTF(schema.getTableName());
        output.writeInt(schema.getColumns().size());

        for (ColumnDefinition column : schema.getColumns()) {
            writeColumn(output, column);
        }

        output.writeInt(schema.getForeignKeys().size());

        for (ForeignKeyDefinition foreignKey : schema.getForeignKeys()) {
            writeForeignKey(output, foreignKey);
        }
    }

    private static TableSchema readTable(DataInputStream input) throws IOException {
        String tableName = input.readUTF();

        int columnCount = input.readInt();

        if (columnCount <= 0) {
            throw new IOException("Invalid column count: " + columnCount);
        }

        List<ColumnDefinition> columns = new ArrayList<>();

        for (int i = 0; i < columnCount; i++) {
            columns.add(readColumn(input));
        }

        int foreignKeyCount = input.readInt();

        if (foreignKeyCount < 0) {
            throw new IOException("Invalid foreign key count: " + foreignKeyCount);
        }

        List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();

        for (int i = 0; i < foreignKeyCount; i++) {
            foreignKeys.add(readForeignKey(input));
        }

        return new TableSchema(tableName, columns, foreignKeys);
    }

    private static void writeIndex(DataOutputStream output, IndexDefinition index) throws IOException {
        output.writeUTF(index.getName());
        output.writeUTF(index.getTableName());
        output.writeUTF(index.getColumnName());
        output.writeBoolean(index.isUnique());
        output.writeBoolean(index.isPrimary());
    }

    private static IndexDefinition readIndex(DataInputStream input) throws IOException {
        String name = input.readUTF();
        String tableName = input.readUTF();
        String columnName = input.readUTF();

        boolean unique = input.readBoolean();
        boolean primary = input.readBoolean();

        return new IndexDefinition(
                name,
                tableName,
                columnName,
                unique,
                primary
        );
    }

    private static void writeCatalog(Path path, Catalog catalog) throws IOException {
        try (
                DataOutputStream output = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(path))
                )
        ) {
            output.writeInt(MAGIC);
            output.writeInt(catalog.getTableCount());

            for (TableSchema schema : catalog.getTables()) {
                writeTable(output, schema);
            }

            output.writeInt(catalog.getIndexCount());

            for (IndexDefinition index : catalog.getIndexes()) {
                writeIndex(output, index);
            }
        }
    }

    private static void replaceFile(Path temporaryPath, Path catalogPath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    catalogPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporaryPath,
                    catalogPath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Записывает каталог в двоичный файл.
     *
     * @param path путь к файлу
     * @param catalog каталог таблиц и индексов
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static void save(Path path, Catalog catalog) throws IOException {
        validatePath(path);
        validateCatalog(catalog);

        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");

        try {
            writeCatalog(temporaryPath, catalog);
            replaceFile(temporaryPath, path);
        } catch (Exception exception) {
            Files.deleteIfExists(temporaryPath);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    /**
     * Считывает каталог из файла.
     *
     * @param path путь к файлу
     * @return загруженный каталог
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static Catalog load(Path path) throws IOException {
        validatePath(path);

        if (!Files.exists(path)) {
            return new Catalog();
        }

        try (
                DataInputStream input = new DataInputStream(
                        new BufferedInputStream(Files.newInputStream(path))
                )
        ) {
            int magic = input.readInt();

            if (magic != MAGIC) {
                throw new IOException("Invalid catalog file");
            }

            Catalog catalog = new Catalog();

            int tableCount = input.readInt();

            if (tableCount < 0) {
                throw new IOException("Invalid table count: " + tableCount);
            }

            for (int i = 0; i < tableCount; i++) {
                TableSchema schema = readTable(input);
                catalog.addTable(schema);
            }

            int indexCount = input.readInt();

            if (indexCount < 0) {
                throw new IOException("Invalid index count: " + indexCount);
            }

            for (int i = 0; i < indexCount; i++) {
                IndexDefinition index = readIndex(input);
                catalog.addIndex(index);
            }

            return catalog;

        } catch (IllegalArgumentException | IllegalStateException exception) {

            throw new IOException("Invalid catalog data", exception);
        }
    }

}