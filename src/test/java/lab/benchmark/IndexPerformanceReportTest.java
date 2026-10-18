package lab.benchmark;

import lab.catalog.Catalog;
import lab.engine.DatabaseEngine;
import lab.service.BenchmarkService;
import lab.sql.executor.QueryExecutor;
import lab.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexPerformanceReportTest {
    @TempDir
    Path temp;

    private void runCase(int rowCount) throws Exception {
        DatabaseEngine engine = new DatabaseEngine(temp.resolve("db-" + rowCount), new Catalog());

        try {
            QueryExecutor executor = new QueryExecutor(engine);
            executor.execute(new SqlParser("CREATE TABLE benchmark (id INT PRIMARY KEY, rating DOUBLE);").parse());

            for (int id = 1; id <= rowCount; id++) {
                String sql = "INSERT INTO benchmark VALUES (" + id + ", " + (id % 100) + ".0);";
                executor.execute(new SqlParser(sql).parse());
            }

            executor.execute(new SqlParser("CREATE INDEX idx_benchmark_rating ON benchmark (rating);").parse());

            var result = new BenchmarkService(engine).benchmark("benchmark", "rating", 42.0, 15);
            assertEquals(rowCount / 100, result.getMatchedRows());

            System.out.printf(Locale.US,
                    "| %,d | %.4f | %.4f | %.2f |%n",
                    rowCount, result.getFullScanTimeMs(), result.getIndexScanTimeMs(), result.getSpeedup());
        } finally {
            engine.close();
        }
    }

    @Test
    void printPerformanceReport() throws Exception {
        System.out.println("| Number of rows | Full scan, ms | Index scan, ms | Speedup | ");
        System.out.println("|---------------:|--------------:|---------------:|--------:|");
        runCase(1000);
        runCase(5000);
    }
}
