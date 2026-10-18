package lab.storage;

import lab.catalog.TableSchema;
import lab.index.FreeSpaceManager;
import lab.model.Row;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Обеспечивает файловое хранение таблицы и повторное использование свободных мест.
 */
public class TableStorage {
    private final Path tablePath;
    private final Path freeSpacePath;
    private final TableSchema schema;

    private final RandomAccessFile file;
    private final TableHeader header;
    private final RecordSerializer serializer;
    private final RecordManager recordManager;
    private final FreeSpaceManager freeSpaceManager;

    private boolean closed;

    private TableStorage(
            Path tablePath,
            Path freeSpacePath,
            TableSchema schema,
            RandomAccessFile file,
            TableHeader header,
            FreeSpaceManager freeSpaceManager
    ) {
        this.tablePath = tablePath;
        this.freeSpacePath = freeSpacePath;
        this.schema = schema;
        this.file = file;
        this.header = header;
        this.serializer = new RecordSerializer(schema);
        this.recordManager = new RecordManager(file, header, serializer);
        this.freeSpaceManager = freeSpaceManager;
        this.closed = false;
    }

    private static void validatePath(Path path, String name) {
        if (path == null) {
            throw new IllegalArgumentException(name + " path cannot be null");
        }
    }

    private static void validateSchema(TableSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }
    }

    private static void validateRecordSize(int recordSize) {
        if (recordSize <= 0) {
            throw new IllegalArgumentException("Record size must be positive");
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Table storage is closed");
        }
    }

    private void validateRecordNumber(int recordNumber) {
        if (recordNumber < 0 || recordNumber >= header.getTotalRecords()) {
            throw new IllegalArgumentException("Record does not exist: " + recordNumber);
        }
    }

    private int getFreeRecordNumber() throws IOException {
        long recordNumber = freeSpaceManager.pop();

        if (recordNumber == -1) {
            return -1;
        }

        if (recordNumber > Integer.MAX_VALUE) {
            throw new IOException("Record number is too large");
        }
        return (int) recordNumber;
    }

    private void saveHeader() throws IOException {
        header.write(file);
        file.getFD().sync();
    }

    private int insertIntoFreeSlot(int recordNumber, Row row) throws IOException {
        try {
            recordManager.write(recordNumber, row);

            header.incrementActiveRecords();
            saveHeader();

            return recordNumber;
        } catch (Exception exception) {
            freeSpaceManager.push(recordNumber);

            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private int insertAtEnd(Row row) throws IOException {
        int recordNumber = header.getTotalRecords();

        recordManager.write(recordNumber, row);

        header.incrementTotalRecords();
        header.incrementActiveRecords();

        saveHeader();

        return recordNumber;
    }

    /**
     * Создаёт файлы таблицы и учёта свободных мест.
     *
     * @param tablePath путь к файлу таблицы
     * @param freeSpacePath путь к файлу свободных мест
     * @param schema схема таблицы
     * @param recordSize размер одной записи в байтах
     * @return результат операции
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static TableStorage create(
            Path tablePath,
            Path freeSpacePath,
            TableSchema schema,
            int recordSize
    ) throws IOException {
        validatePath(tablePath, "Table");
        validatePath(freeSpacePath, "Free space");
        validateSchema(schema);
        validateRecordSize(recordSize);

        if (Files.exists(tablePath)) {
            throw new IOException("Table file already exists: " + tablePath);
        }

        Path parent = tablePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        RandomAccessFile file = new RandomAccessFile(tablePath.toFile(), "rw");
        FreeSpaceManager freeSpaceManager = new FreeSpaceManager();

        try {
            TableHeader header = new TableHeader(recordSize);

            header.write(file);
            file.getFD().sync();

            freeSpaceManager.create(freeSpacePath);

            return new TableStorage(
                    tablePath,
                    freeSpacePath,
                    schema,
                    file,
                    header,
                    freeSpaceManager
            );
        } catch (Exception exception) {
            file.close();

            try {
                freeSpaceManager.close();
            } catch (Exception ignored) {
            }

            Files.deleteIfExists(tablePath);
            Files.deleteIfExists(freeSpacePath);

            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    /**
     * Открывает существующие файлы таблицы.
     *
     * @param tablePath путь к файлу таблицы
     * @param freeSpacePath путь к файлу свободных мест
     * @param schema схема таблицы
     * @return результат операции
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static TableStorage open(Path tablePath, Path freeSpacePath, TableSchema schema) throws IOException {
        validatePath(tablePath, "Table");
        validatePath(freeSpacePath, "Free space");
        validateSchema(schema);

        if (!Files.exists(tablePath)) {
            throw new IOException("Table file does not exist: " + tablePath);
        }

        if (!Files.exists(freeSpacePath)) {
            throw new IOException("Free space file does not exist: " + freeSpacePath);
        }

        RandomAccessFile file = new RandomAccessFile(tablePath.toFile(), "rw");
        FreeSpaceManager freeSpaceManager = new FreeSpaceManager();

        try {
            TableHeader header = TableHeader.read(file);

            freeSpaceManager.open(freeSpacePath);

            return new TableStorage(
                    tablePath,
                    freeSpacePath,
                    schema,
                    file,
                    header,
                    freeSpaceManager
            );
        } catch (Exception exception) {
            file.close();

            try {
                freeSpaceManager.close();
            } catch (Exception ignored) {
            }

            if (exception instanceof IOException ioException) {
                throw ioException;
            }

            throw exception;
        }
    }

    /**
     * Добавляет запись в таблицу, используя свободное место при наличии.
     *
     * @param row запись таблицы
     * @return номер добавленной записи
     * @throws IOException при ошибке чтения или записи файлов
     */
    public int insert(Row row) throws IOException {
        checkOpen();
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        row.validate(schema);
        recordManager.validateRow(row);
        int freeRecordNumber = getFreeRecordNumber();

        if (freeRecordNumber != -1) {
            return insertIntoFreeSlot(freeRecordNumber, row);
        }

        return insertAtEnd(row);
    }

    /**
     * Читает запись таблицы по номеру.
     *
     * @param recordNumber номер записи в файле таблицы
     * @return прочитанную запись
     * @throws IOException при ошибке чтения или записи файлов
     */
    public Row read(int recordNumber) throws IOException {
        checkOpen();
        validateRecordNumber(recordNumber);

        return recordManager.read(recordNumber);
    }

    /**
     * Обновляет запись таблицы по номеру.
     *
     * @param recordNumber номер записи в файле таблицы
     * @param row запись таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void update(int recordNumber, Row row) throws IOException {
        checkOpen();
        validateRecordNumber(recordNumber);

        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }
        if (!recordManager.isActive(recordNumber)) {
            throw new IllegalStateException("Cannot update deleted record: " + recordNumber);
        }

        row.validate(schema);
        recordManager.validateRow(row);

        recordManager.overwrite(recordNumber, row);

        saveHeader();
    }

    /**
     * Удаляет запись таблицы и освобождает место в файле.
     *
     * @param recordNumber номер записи в файле таблицы
     * @return true, если запись удалена
     * @throws IOException при ошибке чтения или записи файлов
     */
    public boolean delete(int recordNumber) throws IOException {
        checkOpen();
        validateRecordNumber(recordNumber);

        if (!recordManager.isActive(recordNumber)) {
            return false;
        }
        Row oldRow = recordManager.read(recordNumber);
        recordManager.delete(recordNumber);
        try {
            freeSpaceManager.push(recordNumber);
            header.decrementActiveRecords();
            saveHeader();
            return true;
        } catch (Exception e) {
            recordManager.write(recordNumber, oldRow);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Проверяет, является ли запись активной.
     *
     * @param recordNumber номер записи в файле таблицы
     * @return true, если проверяемое условие выполнено
     * @throws IOException при ошибке чтения или записи файлов
     */
    public boolean isActive(int recordNumber) throws IOException {
        checkOpen();
        validateRecordNumber(recordNumber);
        return recordManager.isActive(recordNumber);
    }

    /**
     * Возвращает общее число мест под записи.
     * @return общее число мест под записи
     */
    public int getTotalRecords() {
        checkOpen();
        return header.getTotalRecords();
    }

    /**
     * Возвращает число активных записей.
     * @return число активных записей
     */
    public int getActiveRecords() {
        checkOpen();
        return header.getActiveRecords();
    }

    /**
     * Возвращает размер записи.
     * @return размер записи
     */
    public int getRecordSize() {
        checkOpen();
        return header.getRecordSize();
    }

    /**
     * Возвращает схему таблицы.
     * @return схему таблицы
     */
    public TableSchema getSchema() {
        checkOpen();
        return schema;
    }

    /**
     * Возвращает путь к файлу таблицы.
     * @return путь к файлу таблицы
     */
    public Path getTablePath() {
        return tablePath;
    }

    /**
     * Возвращает путь к файлу свободных мест.
     * @return путь к файлу свободных мест
     */
    public Path getFreeSpacePath() {
        return freeSpacePath;
    }

    /**
     * Закрывает файловые ресурсы таблицы.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void close() throws IOException {
        if (closed) {
            return;
        }

        IOException error = null;

        try {
            saveHeader();
        } catch (IOException e) {
            error = e;
        }

        try {
            freeSpaceManager.close();
        } catch (IOException e) {
            if (error == null) {
                error = e;
            }
        }

        try {
            file.close();
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