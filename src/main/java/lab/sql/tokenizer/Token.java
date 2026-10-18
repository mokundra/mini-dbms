package lab.sql.tokenizer;

/**
 * Хранит тип и текст одного SQL-токена.
 */
public class Token {
    private final TokenType type;
    private final String value;

    /**
     * Создаёт объект Token.
     *
     * @param type тип
     * @param value значение
     */
    public Token(TokenType type, String value) {
        if (type == null) {
            throw new IllegalArgumentException("Token type cannot be null");
        }
        this.type = type;
        this.value = value;
    }

    /**
     * Возвращает тип.
     * @return тип
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Возвращает значение.
     * @return значение
     */
    public String getValue() {
        return value;
    }

    /**
     * Проверяет тип токена.
     *
     * @param tokenType тип токена
     * @return true, если проверяемое условие выполнено
     */
    public boolean is(TokenType tokenType) {
        return type == tokenType;
    }

    /**
     * Проверяет совпадение токена с ключевым словом.
     *
     * @param keyword значение параметра keyword
     * @return true, если проверяемое условие выполнено
     */
    public boolean isKeyword(String keyword) {
        return type == TokenType.KEYWORD && value != null && value.equalsIgnoreCase(keyword);
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ",value='" + value + '\'' +
                '}';
    }
}