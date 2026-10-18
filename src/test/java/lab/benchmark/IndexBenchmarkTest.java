package lab.benchmark;

import lab.catalog.Catalog;
import lab.engine.DatabaseEngine;
import lab.service.BenchmarkService;
import lab.sql.executor.QueryExecutor;
import lab.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class IndexBenchmarkTest {
    @TempDir Path temp;

    @Test
    void indexAndFullScanAgree() throws Exception {
        DatabaseEngine engine = new DatabaseEngine(temp.resolve("db"), new Catalog());
        try {
            QueryExecutor executor = new QueryExecutor(engine);
            executor.execute(new SqlParser("CREATE TABLE students (id INT PRIMARY KEY, rating DOUBLE);").parse());
            executor.execute(new SqlParser("INSERT INTO students VALUES (1, 9.5);").parse());
            executor.execute(new SqlParser("INSERT INTO students VALUES (2, 8.0);").parse());
            executor.execute(new SqlParser("CREATE INDEX idx_rating ON students (rating);").parse());
            BenchmarkService service = new BenchmarkService(engine);
            var result = service.benchmark("students", "rating", 9.5, 5);
            assertEquals(1, result.getMatchedRows());
            assertTrue(result.getFullScanTimeMs() >= 0);
            assertTrue(result.getIndexScanTimeMs() >= 0);
        } finally {
            engine.close();
        }
    }
}
