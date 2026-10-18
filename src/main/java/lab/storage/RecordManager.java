package lab.storage;

import lab.catalog.TableSchema;
import lab.model.Row;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Читает, записывает и удаляет записи в файле таблицы.
 */
public class RecordManager {
    private static final int ACTIVE_FLAG_SIZE = 1;
    private static final int LENGTH_SIZE = 4;
    private static final int METADATA_SIZE =
            ACTIVE_FLAG_SIZE + LENGTH_SIZE;

    private final RandomAccessFile file;
    private final TableHeader header;
    private final RecordSerializer serializer;

    /**
     * Создаёт объект RecordManager.
     *
     * @param file открытый файл
     * @param header заголовок файла таблицы
     * @param serializer сериализатор записей
     */
    public RecordManager(RandomAccessFile file, TableHeader header, RecordSerializer serializer){
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        if (header == null) {
            throw new IllegalArgumentException("Table header cannot be null");
        }

        if (serializer == null) {
            throw new IllegalArgumentException("Record serializer cannot be null");
        }

        if (header.getRecordSize() <= METADATA_SIZE) {
            throw new IllegalArgumentException("Record size is too small");
        }

        this.file = file;
        this.header = header;
        this.serializer = serializer;
    }

    private void validateRecordNumber(int recordNumber) {
        if (recordNumber < 0) {
            throw new IllegalArgumentException("Record number cannot be negative");
        }
    }

    private void validateExistingRecordNumber(int recordNumber) {
        validateRecordNumber(recordNumber);
        if (recordNumber >= header.getTotalRecords()) {
            throw new IllegalArgumentException("Record does not exist: " + recordNumber);
        }
    }


    private void validatePayloadSize(byte[] data) {
        int maxPayloadSize = header.getRecordSize() - METADATA_SIZE;
        if (data.length > maxPayloadSize) {
            throw new IllegalArgumentException(
                    "Serialized record is too large.Maximum size: "
                            + maxPayloadSize
                            + ",actual size: "
                            + data.length
            );
        }
    }

    private long getOffset(int recordNumber) {
        return header.getRecordOffset(recordNumber);
    }

    private void clearRemainingSpace(int writtenBytes) throws IOException {

        int remaining = header.getRecordSize() - METADATA_SIZE - writtenBytes;
        for (int i = 0; i < remaining; i++) {
            file.writeByte(0);
        }
    }

    /**
     * Проверяет возможность записи строки в выделенное место файла.
     *
     * @param row запись таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void validateRow(Row row) throws IOException {
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        byte[] data = serializer.serialize(row);
        validatePayloadSize(data);
    }

    /**
     * Проверяет, занята ли запись с указанным номером.
     *
     * @param recordNumber номер записи в файле таблицы
     * @return true, если проверяемое условие выполнено
     * @throws IOException при ошибке чтения или записи файлов
     */
    public boolean isActive(int recordNumber) throws IOException {
        validateExistingRecordNumber(recordNumber);
        file.seek(getOffset(recordNumber));
        return file.readBoolean();
    }

    /**
     * Записывает данные по номеру записи.
     *
     * @param recordNumber номер записи в файле таблицы
     * @param row запись таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void write(int recordNumber, Row row) throws IOException {
        validateRecordNumber(recordNumber);

        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        byte[] data = serializer.serialize(row);

        validatePayloadSize(data);

        file.seek(getOffset(recordNumber));

        file.writeBoolean(true);
        file.writeInt(data.length);
        file.write(data);

        clearRemainingSpace(data.length);
    }

    /**
     * Читает данные записи из файла.
     *
     * @param recordNumber номер записи в файле таблицы
     * @return прочитанную запись
     * @throws IOException при ошибке чтения или записи файлов
     */
    public Row read(int recordNumber) throws IOException {
        validateExistingRecordNumber(recordNumber);

        file.seek(getOffset(recordNumber));

        boolean active = file.readBoolean();

        if (!active) {
            return null;
        }

        int dataLength = file.readInt();

        int maxPayloadSize = header.getRecordSize() - METADATA_SIZE;

        if (dataLength < 0 || dataLength > maxPayloadSize) {
            throw new IOException("Invalid record length: " + dataLength);
        }

        byte[] data = new byte[dataLength];

        file.readFully(data);

        return serializer.deserialize(data);
    }

    /**
     * Помечает запись как удалённую.
     *
     * @param recordNumber номер записи в файле таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void delete(int recordNumber) throws IOException {
        validateExistingRecordNumber(recordNumber);

        file.seek(getOffset(recordNumber));

        file.writeBoolean(false);
        file.writeInt(0);

        clearRemainingSpace(0);
    }

    /**
     * Перезаписывает существующую запись.
     *
     * @param recordNumber номер записи в файле таблицы
     * @param row запись таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void overwrite(int recordNumber, Row row) throws IOException {
        validateExistingRecordNumber(recordNumber);
        if (!isActive(recordNumber)) {
            throw new IllegalStateException("Cannot overwrite deleted record: " + recordNumber);
        }
        write(recordNumber, row);
    }

}