package lab.storage;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Хранит размер записи, счётчики строк и служебные данные файла таблицы.
 */
public class TableHeader {
    /**
     * Создаёт объект TableHeader.
     *
     * @param recordSize размер одной записи в байтах
     */
    public static final int HEADER_SIZE = 4 * 3 + 8 * 2;

    private int recordSize;
    private int totalRecords;
    private int activeRecords;
    private long createdAt;
    private long updatedAt;

    public TableHeader(int recordSize) {
        if (recordSize <= 0) {
            throw new IllegalArgumentException("Record size must be positive");
        }

        this.recordSize = recordSize;
        this.totalRecords = 0;
        this.activeRecords = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    private void validateFile(RandomAccessFile file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
    }

    private void validateCounters() {
        if (totalRecords < 0) {
            throw new IllegalStateException("Total records cannot be negative");
        }

        if (activeRecords < 0) {
            throw new IllegalStateException("Active records cannot be negative");
        }

        if (activeRecords > totalRecords) {
            throw new IllegalStateException("Active records cannot exceed total records");
        }
    }

    private void touch() {
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Возвращает размер записи.
     * @return размер записи
     */
    public int getRecordSize() {
        return recordSize;
    }

    /**
     * Возвращает общее число мест под записи.
     * @return общее число мест под записи
     */
    public int getTotalRecords() {
        return totalRecords;
    }

    /**
     * Возвращает число активных записей.
     * @return число активных записей
     */
    public int getActiveRecords() {
        return activeRecords;
    }

    /**
     * Возвращает время создания.
     * @return время создания
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Возвращает время последнего изменения.
     * @return время последнего изменения
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Увеличивает число выделенных мест в таблице.
     */
    public void incrementTotalRecords() {
        totalRecords++;
        touch();
    }

    /**
     * Увеличивает число активных записей.
     */
    public void incrementActiveRecords() {
        activeRecords++;
        validateCounters();
        touch();
    }

    /**
     * Уменьшает число активных записей.
     */
    public void decrementActiveRecords() {
        activeRecords--;
        validateCounters();
        touch();
    }

    /**
     * Вычисляет позицию записи в файле таблицы.
     *
     * @param recordNumber номер записи в файле таблицы
     * @return смещение записи в файле
     */
    public long getRecordOffset(int recordNumber) {
        if (recordNumber < 0) {
            throw new IllegalArgumentException("Record number cannot be negative");
        }
        return HEADER_SIZE + (long) recordNumber * recordSize;
    }

    /**
     * Записывает заголовок таблицы в файл.
     *
     * @param file открытый файл
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void write(RandomAccessFile file) throws IOException {
        validateFile(file);
        validateCounters();

        file.seek(0);

        file.writeInt(recordSize);
        file.writeInt(totalRecords);
        file.writeInt(activeRecords);
        file.writeLong(createdAt);
        file.writeLong(updatedAt);
    }

    /**
     * Считывает заголовок таблицы из файла.
     *
     * @param file открытый файл
     * @return заголовок таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public static TableHeader read(RandomAccessFile file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        if (file.length() < HEADER_SIZE) {
            throw new IOException("Invalid table file header");
        }

        file.seek(0);

        int recordSize = file.readInt();
        int totalRecords = file.readInt();
        int activeRecords = file.readInt();
        long createdAt = file.readLong();
        long updatedAt = file.readLong();

        TableHeader header = new TableHeader(recordSize);

        header.totalRecords = totalRecords;
        header.activeRecords = activeRecords;
        header.createdAt = createdAt;
        header.updatedAt = updatedAt;

        header.validateCounters();
        return header;


    }
}