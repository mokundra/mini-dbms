package lab.catalog;

import java.time.LocalDate;

/**
 * Задаёт типы данных, поддерживаемые таблицами.
 */
public enum DataType {
    INT(Integer.class),
    DOUBLE(Double.class),
    STRING(String.class),
    DATE(LocalDate.class),
    BOOLEAN(Boolean.class);

    private final Class<?> javaClass;

    DataType(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    /**
     * Возвращает соответствующий класс Java.
     * @return соответствующий класс Java
     */
    public Class<?> getJavaClass() {
        return javaClass;
    }

    /**
     * Проверяет, можно ли хранить значение в столбце указанного типа.
     *
     * @param value значение
     * @return true, если проверяемое условие выполнено
     */
    public boolean isCompatible(Object value) {
        return value == null || javaClass.isInstance(value);
    }

    /**
     * Преобразует текстовое имя типа данных в значение перечисления.
     *
     * @param value значение
     * @return элемент перечисления по заданной строке
     */
    public static DataType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Data type cannot be null");
        }
        return switch (value.trim().toUpperCase()) {
            case "INT", "INTEGER" -> INT;
            case "DOUBLE", "FLOAT", "REAL" -> DOUBLE;
            case "STRING", "VARCHAR", "TEXT" -> STRING;
            case "DATE" -> DATE;
            case "BOOLEAN", "BOOL" -> BOOLEAN;
            default -> throw new IllegalArgumentException("Unsupported data type: " + value);
        };
    }
}