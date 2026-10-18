package lab.sql;

import lab.sql.tokenizer.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SqlTokenizerTest {
    private List<Token> tokenize(String sql) {
        return new SqlTokenizer(sql).tokenize();
    }

    @Test
    void selectTokens() {
        List<Token> tokens = tokenize("SELECT * FROM students;");
        assertEquals(TokenType.KEYWORD, tokens.get(0).getType());
        assertEquals("SELECT", tokens.get(0).getValue());
        assertEquals(TokenType.ASTERISK, tokens.get(1).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).getType());
        assertEquals(TokenType.SEMICOLON, tokens.get(4).getType());
        assertEquals(TokenType.END, tokens.get(5).getType());
    }

    @Test
    void insertTokens() {
        List<Token> tokens = tokenize("INSERT INTO students VALUES (1, 'Nikita', 9.5, TRUE);");
        assertEquals(TokenType.NUMBER, tokens.get(5).getType());
        assertEquals("1", tokens.get(5).getValue());
        assertEquals("Nikita", tokens.get(7).getValue());
        assertEquals("9.5", tokens.get(9).getValue());
        assertEquals(TokenType.BOOLEAN, tokens.get(11).getType());
    }

    @Test
    void comparisons() {
        List<Token> tokens = tokenize("SELECT * FROM students WHERE rating >= -8.5;");
        assertEquals(TokenType.GREATER_OR_EQUALS, tokens.get(6).getType());
        assertEquals("-8.5", tokens.get(7).getValue());
    }

    @Test
    void escapedString() {
        List<Token> tokens = tokenize("INSERT INTO students VALUES (1, 'O''Brien');");
        assertEquals("O'Brien", tokens.get(7).getValue());
    }

    @Test
    void invalidCharacter() {
        assertThrows(IllegalArgumentException.class, () -> tokenize("SELECT @ FROM students;"));
    }
}
