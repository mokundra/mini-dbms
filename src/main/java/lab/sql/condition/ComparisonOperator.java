package lab.sql.condition;

/**
 * Перечисляет операции сравнения, используемые в WHERE.
 */
public enum ComparisonOperator {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_OR_EQUALS(">="),
    LESS_THAN("<"),
    LESS_OR_EQUALS("<=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Возвращает текстовый символ операции.
     * @return текстовый символ операции
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Находит операцию сравнения по текстовому обозначению.
     *
     * @param value значение
     * @return элемент перечисления по заданной строке
     */
    public static ComparisonOperator fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Operator cannot be null");
        }

        String operator = value.trim();

        if (operator.equals("=")) {
            return EQUALS;

        } else if (operator.equals("!=") || operator.equals("<>")) {
            return NOT_EQUALS;

        } else if (operator.equals(">")) {
            return GREATER_THAN;

        } else if (operator.equals(">=")) {
            return GREATER_OR_EQUALS;

        } else if (operator.equals("<")) {
            return LESS_THAN;

        } else if (operator.equals("<=")) {
            return LESS_OR_EQUALS;
        }

        throw new IllegalArgumentException("Unsupported comparison operator: " + value);
    }
}