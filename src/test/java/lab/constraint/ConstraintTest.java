package lab.constraint;

import lab.catalog.Catalog;
import lab.engine.DatabaseEngine;
import lab.sql.executor.QueryExecutor;
import lab.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class ConstraintTest {
    @TempDir Path temp;

    private void sql(QueryExecutor executor, String text) throws Exception {
        executor.execute(new SqlParser(text).parse());
    }

    @Test
    void primaryAndUnique() throws Exception {
        DatabaseEngine engine = new DatabaseEngine(temp.resolve("db"), new Catalog());
        try {
            QueryExecutor executor = new QueryExecutor(engine);
            sql(executor, "CREATE TABLE users (id INT PRIMARY KEY, name STRING UNIQUE);");
            sql(executor, "INSERT INTO users VALUES (1, 'Nikita');");
            assertThrows(Exception.class, () -> sql(executor, "INSERT INTO users VALUES (1, 'Anna');"));
            assertThrows(Exception.class, () -> sql(executor, "INSERT INTO users VALUES (2, 'Nikita');"));
        } finally {
            engine.close();
        }
    }

    @Test
    void foreignKey() throws Exception {
        DatabaseEngine engine = new DatabaseEngine(temp.resolve("db"), new Catalog());
        try {
            QueryExecutor executor = new QueryExecutor(engine);
            sql(executor, "CREATE TABLE departments (id INT PRIMARY KEY);");
            sql(executor, "INSERT INTO departments VALUES (10);");
            sql(executor, "CREATE TABLE employees (id INT PRIMARY KEY, department_id INT, FOREIGN KEY (department_id) REFERENCES departments(id));");
            sql(executor, "INSERT INTO employees VALUES (1, 10);");
            assertThrows(Exception.class, () -> sql(executor, "INSERT INTO employees VALUES (2, 999);"));
            assertThrows(Exception.class, () -> sql(executor, "DELETE FROM departments WHERE id = 10;"));
        } finally {
            engine.close();
        }
    }
}
