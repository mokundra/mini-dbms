package lab.storage;

import lab.catalog.ColumnDefinition;
import lab.catalog.DataType;
import lab.catalog.TableSchema;
import lab.model.Row;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Преобразует записи таблицы в массивы байтов и обратно.
 */
public class RecordSerializer {
    private final TableSchema schema;

    /**
     * Создаёт объект RecordSerializer.
     *
     * @param schema схема таблицы
     */
    public RecordSerializer(TableSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        this.schema = schema;
    }

    private void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Invalid string length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeValue(DataOutputStream output, DataType type, Object value) throws IOException {
        if (type == DataType.INT) {
            output.writeInt((Integer) value);
        } else if (type == DataType.DOUBLE) {
            output.writeDouble((Double) value);

        } else if (type == DataType.STRING) {
            writeString(output, (String) value);

        } else if (type == DataType.DATE) {
            output.writeLong(((LocalDate) value).toEpochDay());

        } else if (type == DataType.BOOLEAN) {
            output.writeBoolean((Boolean) value);
        }
    }

    private Object readValue(DataInputStream input, DataType type) throws IOException {
        if (type == DataType.INT) {
            return input.readInt();

        } else if (type == DataType.DOUBLE) {
            return input.readDouble();

        } else if (type == DataType.STRING) {
            return readString(input);

        } else if (type == DataType.DATE) {
            return LocalDate.ofEpochDay(input.readLong());

        } else if (type == DataType.BOOLEAN) {
            return input.readBoolean();
        }
        throw new IllegalArgumentException("Unsupported data type: " + type);
    }

    /**
     * Сериализует значения записи в байтовый массив фиксированного формата.
     *
     * @param row запись таблицы
     * @return байтовое представление записи
     * @throws IOException при ошибке чтения или записи файлов
     */
    public byte[] serialize(Row row) throws IOException {
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        Row normalizedRow = row.withDefaults(schema);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(byteStream);

        for (ColumnDefinition column : schema.getColumns()) {
            Object value = normalizedRow.get(column.getName());

            output.writeBoolean(value != null);

            if (value != null) {
                writeValue(output, column.getType(), value);
            }
        }
        output.flush();

        return byteStream.toByteArray();
    }

    /**
     * Восстанавливает значения записи из байтового массива.
     *
     * @param data массив байтов
     * @return восстановленную запись
     * @throws IOException при ошибке чтения или записи файлов
     */
    public Row deserialize(byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        DataInputStream input = new DataInputStream(byteStream);

        Row row = new Row();

        for (ColumnDefinition column : schema.getColumns()) {
            boolean hasValue = input.readBoolean();
            Object value = null;
            if (hasValue) {
                value = readValue(input, column.getType());
            }
            row.put(column.getName(), value);
        }

        row.validate(schema);
        return row;
    }
}