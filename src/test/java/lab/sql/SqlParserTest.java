package lab.sql;

import lab.sql.parser.SqlParser;
import lab.sql.ast.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqlParserTest {
    @Test
    void select() {
        SelectCommand command = assertInstanceOf(SelectCommand.class,
                new SqlParser("SELECT name FROM students WHERE id = 1;").parse());
        assertEquals("students", command.getTableName());
        assertEquals("name", command.getColumns().get(0));
        assertNotNull(command.getCondition());
    }

    @Test
    void insert() {
        InsertCommand command = assertInstanceOf(InsertCommand.class,
                new SqlParser("INSERT INTO students VALUES (1, 'Nikita');").parse());
        assertEquals(1, command.getRows().size());
        assertEquals(1, command.getRows().get(0).get(0));
    }

    @Test
    void createTable() {
        CreateTableCommand command = assertInstanceOf(CreateTableCommand.class,
                new SqlParser("CREATE TABLE students (id INT PRIMARY KEY, name STRING);").parse());
        assertEquals("students", command.getTableName());
        assertEquals(2, command.getColumns().size());
    }

    @Test
    void invalidSql() {
        assertThrows(RuntimeException.class, () -> new SqlParser("HELLO WORLD;").parse());
    }
}
