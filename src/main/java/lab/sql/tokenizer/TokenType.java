package lab.sql.tokenizer;

/**
 * Перечисляет типы токенов SQL-подобного языка.
 */
public enum TokenType {
    KEYWORD,
    IDENTIFIER,
    NUMBER,
    STRING,
    BOOLEAN,
    DATE,
    COMMA,
    LEFT_PARENTHESIS,
    RIGHT_PARENTHESIS,
    SEMICOLON,
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_OR_EQUALS,
    LESS_THAN,
    LESS_OR_EQUALS,
    ASTERISK,
    END
}