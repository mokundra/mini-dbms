package lab.index;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Хранит свободные номера записей для повторного использования места в файле.
 */
public class FreeSpaceManager {
    private static final int MAGIC = 0x46524545;

    private static final int HEADER_SIZE = 32;
    private static final int SLOT_SIZE = 8;

    private Path filePath;
    private RandomAccessFile file;

    private long size;

    private void checkOpen() {
        if (file == null) {
            throw new IllegalStateException("Файл свободных записей не открыт");
        }
    }

    private long getSlotOffset(long pos) {
        if (pos < 0) {
            throw new IllegalArgumentException("Позиция не может быть отрицательной");
        }

        return HEADER_SIZE + pos * SLOT_SIZE;
    }

    private void writeHeader() throws IOException {
        file.seek(0);

        file.writeInt(MAGIC);
        file.writeLong(size);

        while (file.getFilePointer() < HEADER_SIZE) {
            file.writeByte(0);
        }
    }

    /**
     * Создаёт файл со списком свободных записей.
     *
     * @param path путь к файлу
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void create(Path path) throws IOException {
        if (file != null) {
            throw new IllegalStateException("Сначала закройте текущий файл свободных записей");
        }

        if (Files.exists(path)) {
            throw new IOException("Файл свободных записей уже существует");
        }
        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        file = new RandomAccessFile(path.toFile(), "rw");

        filePath = path;
        size = 0;

        writeHeader();

        file.getFD().sync();
    }

    /**
     * Открывает ранее созданный файл свободных записей.
     *
     * @param path путь к файлу
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void open(Path path) throws IOException {
        if (file != null) {
            throw new IllegalStateException("Сначала закройте текущий файл свободных записей");
        }

        if (!Files.exists(path)) {
            throw new IOException("Файл свободных записей не существует");
        }

        RandomAccessFile openedFile = new RandomAccessFile(path.toFile(), "rw");

        if (openedFile.length() < HEADER_SIZE) {
            throw new IOException("Некорректный файл свободных записей");
        }

        int magic = openedFile.readInt();

        if (magic != MAGIC) {
            openedFile.close();

            throw new IOException("Некорректный формат файла свободных записей");
        }

        long openedSize = openedFile.readLong();

        if (openedSize < 0) {
            openedFile.close();
            throw new IOException("Повреждён файл свободных записей");
        }

        long expectedLength = HEADER_SIZE + openedSize * SLOT_SIZE;

        if (openedFile.length() < expectedLength) {
            throw new IOException("Файл свободных записей повреждён");
        }

        file = openedFile;
        filePath = path;
        size = openedSize;
    }

    /**
     * Добавляет номер удалённой записи в список свободных мест.
     *
     * @param recordNumber номер записи в файле таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void push(long recordNumber) throws IOException {
        checkOpen();

        if (recordNumber < 0) {
            throw new IllegalArgumentException("Номер записи не может быть отрицательным");
        }

        file.seek(getSlotOffset(size));
        file.writeLong(recordNumber);
        size++;
        writeHeader();
    }

    /**
     * Извлекает номер свободной записи.
     * @return номер свободной записи или -1, если свободных мест нет
     * @throws IOException при ошибке чтения или записи файлов
     */
    public long pop() throws IOException {
        checkOpen();

        if (size == 0) {
            return -1;
        }

        long pos = size - 1;

        file.seek(getSlotOffset(pos));

        long recordNumber = file.readLong();

        size--;

        writeHeader();

        return recordNumber;
    }

    /**
     * Проверяет отсутствие элементов.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isEmpty() {
        checkOpen();
        return size == 0;
    }

    /**
     * Возвращает число элементов.
     * @return число элементов
     */
    public long getSize() {
        checkOpen();
        return size;
    }

    /**
     * Очищает список свободных записей.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void clear() throws IOException{
        checkOpen();

        file.setLength(0);
        size=0;

        writeHeader();
        file.getFD().sync();
    }

    /**
     * Закрывает файл свободных записей.
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void close() throws IOException{
        if (file==null){
            return;
        }

        writeHeader();

        file.getFD().sync();

        file.close();
        file=null;
        filePath=null;
        size=0;
    }
}
