package lab.index;

import lab.catalog.DataType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Хранит значения индексируемого столбца и номера соответствующих записей.
 */
public class SecondaryIndex implements Index {
    private static final int MAGIC = 0x53494458;

    private final String name;
    private final String tableName;
    private final String columnName;
    private final boolean unique;
    private final DataType dataType;
    private final Path path;
    private final Map<Object, List<Integer>> entries;

    private RandomAccessFile file;
    private boolean closed;

    private SecondaryIndex(
            String name,
            String tableName,
            String columnName,
            boolean unique,
            DataType dataType,
            Path path,
            RandomAccessFile file
    ) {
        this.name = name;
        this.tableName = tableName;
        this.columnName = columnName;
        this.unique = unique;
        this.dataType = dataType;
        this.path = path;
        this.file = file;
        this.entries = new LinkedHashMap<>();
        this.closed = false;
    }

    private static void validateName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
    }

    private static void validatePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Index path cannot be null");
        }
    }

    private static void validateDataType(DataType dataType) {
        if (dataType == null) {
            throw new IllegalArgumentException("Data type cannot be null");
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Index is closed");
        }
    }

    private void validateRecordNumber(int recordNumber) {
        if (recordNumber < 0) {
            throw new IllegalArgumentException("Record number cannot be negative");
        }
    }

    private void validateKey(Object key) {
        if (key == null) {
            throw new IllegalArgumentException("Index key cannot be null");
        }

        if (!dataType.isCompatible(key)) {
            throw new IllegalArgumentException("Invalid index key type. Expected " + dataType);
        }
    }

    private void writeString(RandomAccessFile file, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        file.writeInt(bytes.length);
        file.write(bytes);
    }

    private String readString(RandomAccessFile file) throws IOException {
        int length = file.readInt();
        if (length < 0 || length > file.length() - file.getFilePointer()) {
            throw new IOException("Invalid string length");
        }

        byte[] bytes = new byte[length];
        file.readFully(bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeKey(RandomAccessFile file, Object key) throws IOException {
        if (dataType == DataType.INT) {
            file.writeInt((Integer) key);

        } else if (dataType == DataType.DOUBLE) {
            file.writeDouble((Double) key);

        } else if (dataType == DataType.STRING) {
            writeString(file, (String) key);

        } else if (dataType == DataType.DATE) {
            file.writeLong(((LocalDate) key).toEpochDay());

        } else if (dataType == DataType.BOOLEAN) {
            file.writeBoolean((Boolean) key);
        }
    }

    private Object readKey(RandomAccessFile file) throws IOException {
        if (dataType == DataType.INT) {
            return file.readInt();

        } else if (dataType == DataType.DOUBLE) {
            return file.readDouble();

        } else if (dataType == DataType.STRING) {
            return readString(file);

        } else if (dataType == DataType.DATE) {
            return LocalDate.ofEpochDay(file.readLong());

        } else if (dataType == DataType.BOOLEAN) {
            return file.readBoolean();
        }
        throw new IllegalArgumentException("Unsupported data type: " + dataType);
    }

    private void writeHeader() throws IOException {
        file.writeInt(MAGIC);

        writeString(file, name);
        writeString(file, tableName);
        writeString(file, columnName);

        file.writeBoolean(unique);

        writeString(file, dataType.name());
    }

    private void validateHeader() throws IOException {
        int magic = file.readInt();

        if (magic != MAGIC) {
            throw new IOException("Invalid secondary index file");
        }

        String storedName = readString(file);
        String storedTableName = readString(file);
        String storedColumnName = readString(file);

        boolean storedUnique = file.readBoolean();

        DataType storedDataType = DataType.fromString(readString(file));

        if (!name.equalsIgnoreCase(storedName)
                || !tableName.equalsIgnoreCase(storedTableName)
                || !columnName.equalsIgnoreCase(storedColumnName)
                || unique != storedUnique
                || dataType != storedDataType) {

            throw new IOException("Secondary index metadata mismatch");
        }
    }

    private void saveToDisk() throws IOException {
        file.setLength(0);
        file.seek(0);

        writeHeader();

        file.writeInt(entries.size());

        for (Map.Entry<Object, List<Integer>> entry : entries.entrySet()) {
            writeKey(file, entry.getKey());

            List<Integer> recordNumbers = entry.getValue();

            file.writeInt(recordNumbers.size());

            for (int recordNumber : recordNumbers) {
                file.writeInt(recordNumber);
            }
        }
        file.getFD().sync();
    }

    private void loadFromDisk() throws IOException {
        entries.clear();

        file.seek(0);

        validateHeader();

        int entryCount = file.readInt();

        if (entryCount < 0) {
            throw new IOException("Invalid index entry count");
        }

        for (int i = 0; i < entryCount; i++) {
            Object key = readKey(file);
            int recordCount = file.readInt();

            if (recordCount < 0) {
                throw new IOException("Invalid record count");
            }

            List<Integer> recordNumbers = new ArrayList<>();

            for (int j = 0; j < recordCount; j++) {
                int recordNumber = file.readInt();

                if (recordNumber < 0) {
                    throw new IOException("Invalid record number");
                }

                recordNumbers.add(recordNumber);
            }
            entries.put(key, recordNumbers);
        }
    }

    private List<Integer> copyRecordNumbers(Object key) {
        List<Integer> recordNumbers = entries.get(key);

        if (recordNumbers == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(new ArrayList<>(recordNumbers));
    }

    /**
     * Создаёт файловый вторичный индекс для столбца.
     *
     * @param name имя объекта
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @param unique признак уникальности
     * @param dataType значение параметра dataType
     * @param path путь к файлу
     * @return результат операции
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static SecondaryIndex create(
            String name,
            String tableName,
            String columnName,
            boolean unique,
            DataType dataType,
            Path path
    ) throws IOException {
        validateName(name, "Index name");
        validateName(tableName, "Table name");
        validateName(columnName, "Column name");
        validateDataType(dataType);
        validatePath(path);

        if (Files.exists(path)) {
            throw new IOException("Index file already exists: " + path);
        }

        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        RandomAccessFile file =
                new RandomAccessFile(path.toFile(), "rw");

        SecondaryIndex index =
                new SecondaryIndex(
                        name.trim(),
                        tableName.trim(),
                        columnName.trim(),
                        unique,
                        dataType,
                        path,
                        file
                );

        try {
            index.saveToDisk();
            return index;
        } catch (Exception e) {
            file.close();

            Files.deleteIfExists(path);

            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Открывает ранее созданный вторичный индекс.
     *
     * @param name имя объекта
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @param unique признак уникальности
     * @param dataType значение параметра dataType
     * @param path путь к файлу
     * @return результат операции
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static SecondaryIndex open(
            String name,
            String tableName,
            String columnName,
            boolean unique,
            DataType dataType,
            Path path
    ) throws IOException {
        validateName(name, "Index name");
        validateName(tableName, "Table name");
        validateName(columnName, "Column name");
        validateDataType(dataType);
        validatePath(path);

        if (!Files.exists(path)) {
            throw new IOException("Index file does not exist: " + path);
        }

        RandomAccessFile file =
                new RandomAccessFile(path.toFile(), "rw");

        SecondaryIndex index =
                new SecondaryIndex(
                        name.trim(),
                        tableName.trim(),
                        columnName.trim(),
                        unique,
                        dataType,
                        path,
                        file
                );

        try {
            index.loadFromDisk();
            return index;
        } catch (Exception exception) {
            file.close();

            if (exception instanceof IOException ioException) {
                throw ioException;
            }

            throw exception;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    @Override
    public boolean isUnique() {
        return unique;
    }

    /**
     * Возвращает тип данных.
     * @return тип данных
     */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * Возвращает путь к файлу.
     * @return путь к файлу
     */
    public Path getPath() {
        return path;
    }

    @Override
    public void insert(Object key, int recordNumber) throws IOException {
        checkOpen();
        validateKey(key);
        validateRecordNumber(recordNumber);

        List<Integer> recordNumbers = entries.computeIfAbsent(key, ignored -> new ArrayList<>());

        if (recordNumbers.contains(recordNumber)) {
            return;
        }

        if (unique && !recordNumbers.isEmpty()) {
            throw new IllegalStateException("Duplicate key in unique index: " + key);
        }

        recordNumbers.add(recordNumber);

        try {
            saveToDisk();
        } catch (IOException exception) {
            recordNumbers.remove(Integer.valueOf(recordNumber));
            if (recordNumbers.isEmpty()) {
                entries.remove(key);
            }
            throw exception;
        }
    }

    @Override
    public void remove(Object key, int recordNumber) throws IOException {
        checkOpen();
        validateKey(key);
        validateRecordNumber(recordNumber);

        List<Integer> recordNumbers = entries.get(key);

        if (recordNumbers == null) {
            return;
        }

        int position = recordNumbers.indexOf(recordNumber);
        if (position < 0) {
            return;
        }

        recordNumbers.remove(position);

        if (recordNumbers.isEmpty()) {
            entries.remove(key);
        }

        try {
            saveToDisk();
        } catch (IOException exception) {
            entries.put(key, recordNumbers);
            recordNumbers.add(position, recordNumber);
            throw exception;
        }
    }

    @Override
    public List<Integer> find(Object key) {
        checkOpen();
        validateKey(key);
        return copyRecordNumbers(key);
    }

    @Override
    public boolean contains(Object key) {
        checkOpen();
        validateKey(key);

        List<Integer> recordNumbers = entries.get(key);

        return recordNumbers != null && !recordNumbers.isEmpty();
    }

    @Override
    public void clear() throws IOException {
        checkOpen();
        Map<Object, List<Integer>> previous = new LinkedHashMap<>(entries);
        entries.clear();

        try {
            saveToDisk();
        } catch (IOException exception) {
            entries.putAll(previous);
            throw exception;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        IOException error = null;

        try {
            saveToDisk();
        } catch (IOException exception) {
            error = exception;
        }

        try {
            file.close();
        } catch (IOException exception) {
            if (error == null) {
                error = exception;
            } else {
                error.addSuppressed(exception);
            }
        }

        closed = true;

        if (error != null) {
            throw error;
        }
    }
}


