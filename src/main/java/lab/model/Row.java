package lab.model;

import lab.catalog.ColumnDefinition;
import lab.catalog.TableSchema;

import java.util.*;

/**
 * Представляет запись таблицы как набор значений столбцов.
 */
public class Row {
    private final Map<String, Object> values;

    /**
     * Создаёт объект Row.
     */
    public Row() {
        this.values = new LinkedHashMap<>();
    }

    private String normalizeColumnName(String columnName) {
        if (columnName == null) {
            throw new IllegalArgumentException("Column name cannot be null");
        }

        String normalized = columnName.trim().toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be empty");
        }
        return normalized;
    }

    private void validateColumnExists(TableSchema schema, String columnName) {
        if (!schema.hasColumn(columnName)) {
            throw new IllegalArgumentException("Column does not exist: " + columnName);
        }
    }

    private void validateValue(TableSchema schema, String columnName, Object value) {
        ColumnDefinition column = schema.getColumn(columnName).orElseThrow();

        column.validateValue(value);
    }

    private Object getValueOrDefault(ColumnDefinition column) {
        String columnName = normalizeColumnName(column.getName());

        if (values.containsKey(columnName)) {
            return values.get(columnName);
        }

        return column.getDefaultValue();
    }

    /**
     * Устанавливает значение столбца в записи.
     *
     * @param columnName имя столбца
     * @param value значение
     */
    public void put(String columnName, Object value) {
        String normalized = normalizeColumnName(columnName);
        values.put(normalized, value);
    }

    /**
     * Возвращает значение столбца или null, если его нет.
     *
     * @param columnName имя столбца
     * @return значение по ключу или null, если оно отсутствует
     */
    public Object get(String columnName) {
        return values.get(normalizeColumnName(columnName));
    }

    /**
     * Получает значение существующего столбца.
     *
     * @param columnName имя столбца
     * @return значение указанного столбца
     */
    public Object getRequired(String columnName) {
        String normalized = normalizeColumnName(columnName);

        if (!values.containsKey(normalized)) {
            throw new IllegalArgumentException("Column value is missing: " + columnName);
        }

        return values.get(normalized);
    }

    /**
     * Проверяет наличие столбца в записи.
     *
     * @param columnName имя столбца
     * @return true, если проверяемое условие выполнено
     */
    public boolean contains(String columnName) {
        return values.containsKey(normalizeColumnName(columnName));
    }

    /**
     * Удаляет значение столбца.
     *
     * @param columnName имя столбца
     * @return удалённое значение
     */
    public Object remove(String columnName) {
        return values.remove(normalizeColumnName(columnName));
    }

    /**
     * Возвращает количество элементов.
     * @return результат операции
     */
    public int size() {
        return values.size();
    }

    /**
     * Проверяет отсутствие элементов.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Возвращает значения столбцов.
     * @return значения столбцов
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Проверяет запись на соответствие схеме таблицы.
     *
     * @param schema схема таблицы
     */
    public void validate(TableSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        for (String columnName : values.keySet()) {
            validateColumnExists(schema, columnName);
        }

        for (ColumnDefinition column : schema.getColumns()) {
            Object value = getValueOrDefault(column);
            validateValue(schema, column.getName(), value);
        }
    }

    /**
     * Создаёт запись с подставленными значениями по умолчанию.
     *
     * @param schema схема таблицы
     * @return запись с подставленными значениями по умолчанию
     */
    public Row withDefaults(TableSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Table schema cannot be null");
        }

        Row result = new Row();

        for (ColumnDefinition column : schema.getColumns()) {
            Object value = getValueOrDefault(column);
            result.put(column.getName(), value);
        }
        result.validate(schema);
        return result;
    }

    /**
     * Создаёт объект Row.
     *
     * @param values набор значений
     */
    public Row(Map<String, Object> values) {
        if (values == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }

        this.values = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Создаёт независимую копию записи.
     * @return копию записи
     */
    public Row copy() {
        return new Row(values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Row row)) {
            return false;
        }

        return values.equals(row.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "Row{" +
                "values=" + values +
                '}';
    }


}