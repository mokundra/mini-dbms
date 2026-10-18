package lab.index;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/**
 * Хранит соответствие целочисленных первичных ключей номерам записей.
 */
public class PrimaryIndex {
    private static final int MAGIC = 0x50494458;

    private static final int HEADER_SIZE = 32;
    private static final int BUCKET_SIZE = 24;

    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;

    private static final int INITIAL_CAPACITY = 101;
    private static final double MAX_LOAD_FACTOR = 0.7;

    private Path filePath;
    private RandomAccessFile file;

    private int capacity;
    private int size;

    private void checkOpen() {
        if (file == null) {
            throw new IllegalStateException("Первичный индекс не открыт");
        }
    }

    private boolean isPrime(int number) {
        if (number < 2) {
            return false;
        }

        if (number == 2) {
            return true;
        }

        if (number % 2 == 0) {
            return false;
        }

        for (int i = 3; (long) i * i <= number; i += 2) {
            if (number % i == 0) {
                return false;
            }
        }
        return true;
    }

    private int nextPrime(int number) {
        if (number <= 2) {
            return 2;
        }
        if (number % 2 == 0) {
            number++;
        }
        while (!isPrime(number)) {
            number += 2;
        }
        return number;
    }

    private long getBucketOffset(int bucketNumber) {
        if (bucketNumber < 0 || bucketNumber >= capacity) {
            throw new IllegalArgumentException("Некорректный номер ячейки");
        }

        return HEADER_SIZE + (long) bucketNumber * BUCKET_SIZE;
    }

    private byte readState(int bucketNumber) throws IOException {
        file.seek(getBucketOffset(bucketNumber));

        return file.readByte();
    }

    private long readKey(int bucketNumber) throws IOException {
        file.seek(getBucketOffset(bucketNumber) + 8);

        return file.readLong();
    }

    private long readRecordNumber(int bucketNumber) throws IOException {
        file.seek(getBucketOffset(bucketNumber) + 16);

        return file.readLong();
    }

    private void writeBucket(int bucketNumber, byte state, long key, long recordNumber) throws IOException {
        file.seek(getBucketOffset(bucketNumber));

        file.writeByte(state);

        for (int i = 0; i < 7; i++) {
            file.writeByte(0);
        }

        file.writeLong(key);
        file.writeLong(recordNumber);
    }

    private void writeHeader() throws IOException {

        file.seek(0);

        file.writeInt(MAGIC);
        file.writeInt(capacity);
        file.writeInt(size);

        while (file.getFilePointer() < HEADER_SIZE) {
            file.writeByte(0);
        }
    }

    private void initializeBuckets() throws IOException {
        for (int i = 0; i < capacity; i++) {
            writeBucket(i, EMPTY, 0, -1);
        }
    }

    private int findBucket(long key) throws IOException {
        int start = HashFunction.hash(key, capacity);

        int step = HashFunction.secondHash(key, capacity);

        for (int i = 0; i < capacity; i++) {

            int bucket = (start + i * step) % capacity;

            byte state = readState(bucket);

            if (state == EMPTY) {
                return -1;
            }

            if (state == OCCUPIED && readKey(bucket) == key) {
                return bucket;
            }
        }
        return -1;
    }

    private int findBucketForInsert(long key) throws IOException {
        int start = HashFunction.hash(key, capacity);

        int step = HashFunction.secondHash(key, capacity);

        int firstDeleted = -1;

        for (int i = 0; i < capacity; i++) {
            int bucket = (start + i * step) % capacity;

            byte state = readState(bucket);

            if (state == OCCUPIED) {
                if (readKey(bucket) == key) {
                    return bucket;
                }
                continue;
            }

            if (state == DELETED) {
                if (firstDeleted == -1) {
                    firstDeleted = bucket;
                }
                continue;
            }

            if (state == EMPTY) {
                if (firstDeleted != -1) {
                    return firstDeleted;
                }
                return bucket;
            }
        }
        return firstDeleted;
    }

    private void insertWithoutResize(long key, long recordNumber) throws IOException {
        int bucket = findBucketForInsert(key);

        if (bucket == -1) {
            throw new IOException("В хеш-таблице нет свободного места");
        }

        byte state = readState(bucket);

        if (state == OCCUPIED) {
            throw new IllegalArgumentException("Запись с ID %d уже существует".formatted(key));
        }

        writeBucket(bucket, OCCUPIED, key, recordNumber);

        size++;

        writeHeader();
    }

    private void rehash(int newCapacity) throws IOException {
        Path temporaryPath = Files.createTempFile(
                filePath.toAbsolutePath().getParent(), "primary-", ".tmp");

        RandomAccessFile oldFile = file;
        int oldCapacity = capacity;
        int oldSize = size;
        boolean oldClosed = false;

        try {
            try (RandomAccessFile newFile = new RandomAccessFile(temporaryPath.toFile(), "rw")) {
                file = newFile;
                capacity = newCapacity;
                size = 0;

                writeHeader();
                initializeBuckets();

                for (int i = 0; i < oldCapacity; i++) {
                    long oldOffset = HEADER_SIZE + (long) i * BUCKET_SIZE;

                    oldFile.seek(oldOffset);
                    byte state = oldFile.readByte();

                    if (state != OCCUPIED) {
                        continue;
                    }

                    oldFile.skipBytes(7);
                    long key = oldFile.readLong();
                    long recordNumber = oldFile.readLong();

                    insertWithoutResize(key, recordNumber);
                }

                newFile.getFD().sync();
            }

            file = null;
            oldFile.close();
            oldClosed = true;

            try {
                Files.move(temporaryPath, filePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, filePath,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            file = new RandomAccessFile(filePath.toFile(), "rw");

        } catch (IOException | RuntimeException exception) {
            capacity = oldCapacity;
            size = oldSize;

            try {
                if (oldClosed) {
                    file = new RandomAccessFile(filePath.toFile(), "rw");
                } else {
                    file = oldFile;
                }
            } catch (IOException reopenException) {
                file = null;
                exception.addSuppressed(reopenException);
            }

            throw exception;
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private void ensureCapacity() throws IOException {
        double loadFactor = (double) (size + 1) / capacity;

        if (loadFactor > MAX_LOAD_FACTOR) {
            int newCapacity = nextPrime(capacity * 2);

            rehash(newCapacity);
        }
    }

    /**
     * Создаёт новый файл первичного индекса.
     *
     * @param path путь к файлу
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void create(Path path) throws IOException {
        if (file != null) {
            throw new IllegalStateException("Сначала закройте текущий индекс");
        }

        if (Files.exists(path)) {
            throw new IOException("Файл первичного индекса уже существует");
        }

        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        file = new RandomAccessFile(path.toFile(), "rw");

        filePath = path;

        capacity = INITIAL_CAPACITY;
        size = 0;

        writeHeader();
        initializeBuckets();

        file.getFD().sync();
    }

    /**
     * Открывает существующий файл первичного индекса.
     *
     * @param path путь к файлу
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void open(Path path) throws IOException {
        if (file != null) {
            throw new IllegalStateException("Сначала закройте текущий индекс");
        }

        if (!Files.exists(path)) {
            throw new IOException("Файл первичного индекса не существует");
        }

        RandomAccessFile openedFile = new RandomAccessFile(path.toFile(), "rw");
        if (openedFile.length() < HEADER_SIZE) {
            openedFile.close();

            throw new IOException("Некорректный файл первичного индекса");
        }

        int magic = openedFile.readInt();

        if (magic != MAGIC) {
            openedFile.close();
            throw new IOException("Некорректный формат первичного индекса");
        }

        int openedCapacity = openedFile.readInt();

        int openedSize = openedFile.readInt();

        if (openedCapacity <= 1 || openedSize < 0 || openedSize > openedCapacity) {
            openedFile.close();
            throw new IOException("Повреждён заголовок первичного индекса");
        }

        long expectedLength = HEADER_SIZE + (long) openedCapacity * BUCKET_SIZE;

        if (openedFile.length() < expectedLength) {
            openedFile.close();
            throw new IOException("Файл первичного индекса повреждён");
        }

        file = openedFile;
        filePath = path;
        capacity = openedCapacity;
        size = openedSize;

    }

    /**
     * Ищет номер записи по первичному ключу.
     *
     * @param key значение ключа
     * @return номер записи по ключу или -1, если ключ отсутствует
     * @throws IOException при ошибке чтения или записи файлов
     */
    public long get(long key) throws IOException {
        checkOpen();
        int bucket = findBucket(key);

        if (bucket == -1) {
            return -1;
        }

        return readRecordNumber(bucket);
    }

    /**
     * Проверяет наличие первичного ключа.
     *
     * @param key значение ключа
     * @return true, если проверяемое условие выполнено
     * @throws IOException при ошибке чтения или записи файлов
     */
    public boolean contains(long key) throws IOException {
        checkOpen();

        return findBucket(key) != -1;
    }

    /**
     * Связывает первичный ключ с номером записи.
     *
     * @param key значение ключа
     * @param recordNumber номер записи в файле таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void put(long key, long recordNumber) throws IOException {
        checkOpen();

        if (recordNumber < 0) {
            throw new IllegalArgumentException("Номер записи не может быть отрицательным");
        }

        if (contains(key)) {
            throw new IllegalArgumentException("Запись с ID %d уже существует".formatted(key));
        }

        ensureCapacity();

        insertWithoutResize(key, recordNumber);
    }

    /**
     * Удаляет первичный ключ из индекса.
     *
     * @param key значение ключа
     * @return true, если ключ найден и удалён
     * @throws IOException при ошибке чтения или записи файлов
     */
    public boolean remove(long key) throws IOException {
        checkOpen();

        int bucket = findBucket(key);

        if (bucket == -1) {
            return false;
        }

        long recordNumber = readRecordNumber(bucket);

        writeBucket(bucket, DELETED, key, recordNumber);

        size--;
        writeHeader();
        return true;

    }

    /**
     * Очищает первичный индекс.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void clear() throws IOException {
        checkOpen();

        file.setLength(0);

        capacity = INITIAL_CAPACITY;
        size = 0;
        writeHeader();
        initializeBuckets();

        file.getFD().sync();
    }

    /**
     * Закрывает файл первичного индекса.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void close() throws IOException {
        if (file == null) {
            return;
        }
        writeHeader();

        file.getFD().sync();

        file.close();

        file = null;
        filePath = null;

        capacity = 0;
        size = 0;
    }

    /**
     * Возвращает число элементов.
     * @return число элементов
     */
    public int getSize() {
        checkOpen();
        return size;
    }

    /**
     * Возвращает ёмкость индекса.
     * @return ёмкость индекса
     */
    public int getCapacity() {
        checkOpen();
        return capacity;
    }

}
