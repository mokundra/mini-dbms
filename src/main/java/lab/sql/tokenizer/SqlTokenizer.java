package lab.sql.tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Разбивает SQL-текст на токены.
 */
public class SqlTokenizer {
    private static final Set<String> KEYWORDS = Set.of(
            "SELECT",
            "FROM",
            "WHERE",
            "INSERT",
            "INTO",
            "VALUES",
            "UPDATE",
            "SET",
            "DELETE",
            "CREATE",
            "TABLE",
            "DROP",
            "INDEX",
            "UNIQUE",
            "ON",
            "PRIMARY",
            "KEY",
            "FOREIGN",
            "REFERENCES",
            "EXPLAIN",
            "INT",
            "INTEGER",
            "DOUBLE",
            "FLOAT",
            "REAL",
            "STRING",
            "VARCHAR",
            "TEXT",
            "DATE",
            "BOOLEAN",
            "BOOL",
            "NULL"
    );

    private final String sql;
    private int position;

    /**
     * Создаёт объект SqlTokenizer.
     *
     * @param sql текст SQL-запроса
     */
    public SqlTokenizer(String sql){
        if (sql == null){
            throw new IllegalArgumentException("SQL cannot be null");
        }

        this.sql = sql;
        this.position = 0;
    }


    private boolean isAtEnd() {
        return position >= sql.length();
    }

    private char currentChar() {
        return sql.charAt(position);
    }

    private char peekChar(){
        if (position+1>=sql.length()){
            return '\0';
        }

        return sql.charAt(position+1);
    }

    private void advance(){
        position++;
    }

    private void skipWhitespace(){
        while (!isAtEnd() && Character.isWhitespace(currentChar())){
            advance();
        }
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private Token readIdentifierOrKeyword(){
        int start=position;

        while (!isAtEnd() && isIdentifierPart(currentChar())){
            advance();
        }

        String value = sql.substring(start,position);

        String upper = value.toUpperCase(Locale.ROOT);

        if (upper.equals("TRUE") || upper.equals("FALSE")){
            return new Token(TokenType.BOOLEAN,upper);
        }

        if (KEYWORDS.contains(upper)){
            return new Token(TokenType.KEYWORD,upper);
        }
        return new Token(TokenType.IDENTIFIER,value);
    }

    private Token readNumber() {
        int start = position;
        boolean hasDot = false;

        if (currentChar() == '-') {
            advance();
        }

        while (!isAtEnd()) {
            char current = currentChar();

            if (Character.isDigit(current)) {
                advance();
                continue;
            }

            if (current == '.' && !hasDot) {
                hasDot = true;
                advance();
                continue;
            }

            break;
        }

        String value = sql.substring(start, position);

        if (value.equals("-") || value.equals(".") || value.equals("-.")) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }

        return new Token(TokenType.NUMBER, value);
    }

    private Token readString() {
        advance();

        StringBuilder builder = new StringBuilder();

        while (!isAtEnd()) {
            char current = currentChar();

            if (current == '\'') {
                if (peekChar() == '\'') {
                    builder.append('\'');
                    advance();
                    advance();
                    continue;
                }

                advance();

                return new Token(TokenType.STRING, builder.toString());
            }
            builder.append(current);
            advance();
        }
        throw new IllegalArgumentException("Unterminated string literal");
    }

    private Token readOperator() {
        char current = currentChar();

        if (current == '=') {
            advance();
            return new Token(TokenType.EQUALS, "=");
        }

        if (current == '!') {
            if (peekChar() != '=') {
                throw new IllegalArgumentException("Invalid operator: !");
            }
            advance();
            advance();
            return new Token(TokenType.NOT_EQUALS, "!=");
        }

        if (current == '>') {
            if (peekChar() == '=') {
                advance();
                advance();
                return new Token(TokenType.GREATER_OR_EQUALS, ">=");
            }
            advance();
            return new Token(TokenType.GREATER_THAN, ">");
        }

        if (current == '<') {
            if (peekChar() == '=') {
                advance();
                advance();
                return new Token(TokenType.LESS_OR_EQUALS, "<=");
            }

            if (peekChar() == '>') {
                advance();
                advance();
                return new Token(TokenType.NOT_EQUALS, "<>");
            }
            advance();
            return new Token(TokenType.LESS_THAN, "<");
        }

        throw new IllegalArgumentException("Unsupported operator: " + current);
    }

    private Token nextToken() {
        skipWhitespace();
        if (isAtEnd()) {
            return new Token(TokenType.END, null);
        }

        char current = currentChar();

        if (isIdentifierStart(current)) {
            return readIdentifierOrKeyword();
        }

        if (Character.isDigit(current) || current == '-' && Character.isDigit(peekChar())) {
            return readNumber();
        }

        if (current == '\'') {
            return readString();
        }

        if (current == '=' || current == '!' || current == '>' || current == '<') {return readOperator();
        }

        advance();

        if (current == ',') {
            return new Token(TokenType.COMMA, ",");

        } else if (current == '(') {
            return new Token(TokenType.LEFT_PARENTHESIS, "(");

        } else if (current == ')') {
            return new Token(TokenType.RIGHT_PARENTHESIS, ")");

        } else if (current == ';') {
            return new Token(TokenType.SEMICOLON, ";");

        } else if (current == '*') {
            return new Token(TokenType.ASTERISK, "*");
        }

        throw new IllegalArgumentException("Unexpected character: " + current);
    }

    /**
     * Разбивает исходный SQL-текст на последовательность токенов.
     * @return список распознанных токенов
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (true) {
            Token token = nextToken();
            tokens.add(token);
            if (token.is(TokenType.END)) {
                break;
            }
        }
        return tokens;
    }
}