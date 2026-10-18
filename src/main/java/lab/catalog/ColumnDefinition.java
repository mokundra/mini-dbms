package lab.catalog;

import java.util.Objects;

/**
 * Описывает столбец таблицы, его тип и ограничения.
 */
public class ColumnDefinition {
    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final boolean nullable;
    private final boolean unique;
    private final Object defaultValue;

    /**
     * Создаёт объект ColumnDefinition.
     *
     * @param name имя объекта
     * @param type тип
     * @param primaryKey признак первичного ключа
     * @param nullable разрешение пустых значений
     * @param unique признак уникальности
     * @param defaultValue значение по умолчанию
     */
    public ColumnDefinition(
            String name,
            DataType type,
            boolean primaryKey,
            boolean nullable,
            boolean unique,
            Object defaultValue
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("Column type cannot be null");
        }

        if (primaryKey && nullable) {
            throw new IllegalArgumentException("Primary key column cannot be nullable");
        }

        if (defaultValue != null && !type.isCompatible(defaultValue)) {
            throw new IllegalArgumentException("Default value is incompatible with column type");
        }

        this.name = name.trim();
        this.type = type;
        this.primaryKey = primaryKey;
        this.nullable = nullable;
        this.unique = unique || primaryKey;
        this.defaultValue = defaultValue;
    }

    /**
     * Создаёт объект ColumnDefinition.
     *
     * @param name имя объекта
     * @param type тип
     */
    public ColumnDefinition(String name, DataType type) {
        this(name, type, false, true, false, null);
    }

    /**
     * Возвращает имя.
     * @return имя
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает тип.
     * @return тип
     */
    public DataType getType() {
        return type;
    }

    /**
     * Проверяет наличие ограничения PRIMARY KEY.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Проверяет возможность хранить null.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Проверяет ограничение уникальности.
     * @return true, если проверяемое условие выполнено
     */
    public boolean isUnique() {
        return unique;
    }

    /**
     * Возвращает значение по умолчанию.
     * @return значение по умолчанию
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Проверяет соответствие значения типу и ограничениям столбца.
     *
     * @param value значение
     */
    public void validateValue(Object value){
        if (value==null){
            if (!nullable){
                throw new IllegalArgumentException( "Column '" + name + "' cannot be null");
            }
            return;
        }
        if (!type.isCompatible(value)){
            throw new IllegalArgumentException("Invalid value type for column '" + name + "'.Expected "
                    + type + ", got " + value.getClass().getSimpleName());
        }

    }

    @Override
    public boolean equals(Object o){
        if (this == o){
            return true;
        }

        if (!(o instanceof ColumnDefinition that)){
            return false;
        }
        return primaryKey == that.primaryKey && nullable == that.nullable
                && unique == that.unique && name.equalsIgnoreCase(that.name)
                && type == that.type && Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name.toLowerCase(),
                type,
                primaryKey,
                nullable,
                unique,
                defaultValue
        );
    }

    @Override
    public String toString() {
        return "ColumnDefinition{" +
                "name='" + name + '\'' +
                ",type=" + type +
                ",primaryKey=" + primaryKey +
                ",nullable=" + nullable +
                ",unique=" + unique +
                ",defaultValue=" + defaultValue + '}';
    }
}

