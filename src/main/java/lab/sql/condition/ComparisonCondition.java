package lab.sql.condition;

import lab.model.Row;

/**
 * Проверяет условие сравнения значения столбца с заданным значением.
 */
public class ComparisonCondition implements Condition {
    private final String columnName;
    private final ComparisonOperator operator;
    private final Object value;

    /**
     * Создаёт объект ComparisonCondition.
     *
     * @param columnName имя столбца
     * @param operator операция сравнения
     * @param value значение
     */
    public ComparisonCondition(String columnName, ComparisonOperator operator, Object value) {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be empty");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Operator cannot be null");
        }
        this.columnName = columnName.trim();
        this.operator = operator;
        this.value = value;
    }

    private int compareValues(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values");
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> typedComparable = (Comparable<Object>) comparable;
            return typedComparable.compareTo(right);
        }
        throw new IllegalArgumentException("Values cannot be compared: " + left + " and " + right);
    }

    private boolean equalsValues(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return left.equals(right);
    }

    /**
     * Возвращает имя столбца.
     * @return имя столбца
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Возвращает операцию сравнения.
     * @return операцию сравнения
     */
    public ComparisonOperator getOperator() {
        return operator;
    }

    /**
     * Возвращает значение.
     * @return значение
     */
    public Object getValue() {
        return value;
    }

    @Override
    public boolean matches(Row row) {
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        Object rowValue = row.get(columnName);

        if (operator == ComparisonOperator.EQUALS) {
            return equalsValues(rowValue, value);

        } else if (operator == ComparisonOperator.NOT_EQUALS) {
            return !equalsValues(rowValue, value);

        } else if (operator == ComparisonOperator.GREATER_THAN) {
            return compareValues(rowValue, value) > 0;

        } else if (operator == ComparisonOperator.GREATER_OR_EQUALS) {
            return compareValues(rowValue, value) >= 0;

        } else if (operator == ComparisonOperator.LESS_THAN) {
            return compareValues(rowValue, value) < 0;

        } else if (operator == ComparisonOperator.LESS_OR_EQUALS) {
            return compareValues(rowValue, value) <= 0;
        }

        throw new IllegalStateException("Unsupported operator: " + operator);
    }
}

