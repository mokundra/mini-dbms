package lab.sql;

import lab.catalog.Catalog;
import lab.engine.DatabaseEngine;
import lab.sql.executor.QueryExecutor;
import lab.sql.executor.QueryResult;
import lab.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class QueryExecutorTest {
    @TempDir Path temp;

    private QueryResult sql(QueryExecutor executor, String text) throws Exception {
        return executor.execute(new SqlParser(text).parse());
    }

    @Test
    void insertSelectUpdateDelete() throws Exception {
        DatabaseEngine engine = new DatabaseEngine(temp.resolve("db"), new Catalog());
        try {
            QueryExecutor executor = new QueryExecutor(engine);
            sql(executor, "CREATE TABLE students (id INT PRIMARY KEY, name STRING, rating DOUBLE);");
            sql(executor, "INSERT INTO students VALUES (1, 'Nikita', 9.5);");
            assertEquals(1, sql(executor, "SELECT * FROM students;").getRows().size());
            sql(executor, "UPDATE students SET rating = 10.0 WHERE id = 1;");
            assertEquals(10.0, sql(executor, "SELECT * FROM students WHERE id = 1;").getRows().get(0).get("rating"));
            sql(executor, "DELETE FROM students WHERE id = 1;");
            assertTrue(sql(executor, "SELECT * FROM students;").getRows().isEmpty());
        } finally {
            engine.close();
        }
    }

    @Test
    void dateAndIndexedDouble() throws Exception {
        DatabaseEngine engine = new DatabaseEngine(temp.resolve("db"), new Catalog());
        try {
            QueryExecutor executor = new QueryExecutor(engine);
            sql(executor, "CREATE TABLE events (id INT PRIMARY KEY, hired DATE, salary DOUBLE);");
            sql(executor, "INSERT INTO events VALUES (1, '2026-01-15', 95000.0);");
            assertEquals(1, sql(executor, "SELECT * FROM events WHERE hired = '2026-01-15';").getRows().size());
            sql(executor, "CREATE INDEX idx_salary ON events (salary);");
            assertEquals(1, sql(executor, "SELECT * FROM events WHERE salary = 95000;").getRows().size());
        } finally {
            engine.close();
        }
    }
}
