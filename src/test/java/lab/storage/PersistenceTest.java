package lab.storage;

import lab.catalog.Catalog;
import lab.catalog.CatalogStorage;
import lab.engine.DatabaseEngine;
import lab.sql.executor.QueryExecutor;
import lab.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class PersistenceTest {
    @TempDir Path temp;

    @Test
    void reopenDatabase() throws Exception {
        Path databasePath = temp.resolve("database");
        Path catalogPath = databasePath.resolve("catalog.meta");
        Catalog catalog = new Catalog();
        DatabaseEngine first = new DatabaseEngine(databasePath, catalog);
        try {
            QueryExecutor executor = new QueryExecutor(first);
            executor.execute(new SqlParser("CREATE TABLE students (id INT PRIMARY KEY, name STRING);").parse());
            executor.execute(new SqlParser("INSERT INTO students VALUES (1, 'Nikita');").parse());
            CatalogStorage.save(catalogPath, catalog);
        } finally {
            first.close();
        }

        Catalog loaded = CatalogStorage.load(catalogPath);
        DatabaseEngine second = new DatabaseEngine(databasePath, loaded);
        try {
            second.openAll();
            QueryExecutor executor = new QueryExecutor(second);
            var result = executor.execute(new SqlParser("SELECT * FROM students;").parse());
            assertEquals(1, result.getRows().size());
            assertEquals("Nikita", result.getRows().get(0).get("name"));
        } finally {
            second.close();
        }
    }
}
