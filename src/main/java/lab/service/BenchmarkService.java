package lab.service;

import lab.catalog.ColumnDefinition;
import lab.catalog.TableSchema;
import lab.engine.DatabaseEngine;
import lab.model.Row;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Измеряет время поиска записей с индексом и без него.
 */
public class BenchmarkService {
    private final DatabaseEngine databaseEngine;

    /**
     * Создаёт объект BenchmarkService.
     *
     * @param databaseEngine движок базы данных
     */
    public BenchmarkService(DatabaseEngine databaseEngine) {
        if (databaseEngine == null) {
            throw new IllegalArgumentException("Database engine cannot be null");
        }
        this.databaseEngine = databaseEngine;
    }

    /**
     * Содержит результаты сравнения способов поиска.
     */
    public static class BenchmarkResult {
        private final String tableName;
        private final String columnName;
        private final Object key;
        private final int iterations;
        private final double fullScanTimeMs;
        private final double indexScanTimeMs;
        private final int matchedRows;

        /**
         * Создаёт объект BenchmarkResult.
         *
         * @param tableName имя таблицы
         * @param columnName имя столбца
         * @param key значение ключа
         * @param iterations число повторов измерения
         * @param fullScanTimeMs время полного перебора в миллисекундах
         * @param indexScanTimeMs время поиска через индекс в миллисекундах
         * @param matchedRows число найденных строк
         */
        public BenchmarkResult(
                String tableName,
                String columnName,
                Object key,
                int iterations,
                double fullScanTimeMs,
                double indexScanTimeMs,
                int matchedRows
        ) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.key = key;
            this.iterations = iterations;
            this.fullScanTimeMs = fullScanTimeMs;
            this.indexScanTimeMs = indexScanTimeMs;
            this.matchedRows = matchedRows;
        }

        /**
         * Возвращает имя таблицы.
         * @return имя таблицы
         */
        public String getTableName() {
            return tableName;
        }

        /**
         * Возвращает имя столбца.
         * @return имя столбца
         */
        public String getColumnName() {
            return columnName;
        }

        /**
         * Возвращает ключ.
         * @return ключ
         */
        public Object getKey() {
            return key;
        }

        /**
         * Возвращает число повторений.
         * @return число повторений
         */
        public int getIterations() {
            return iterations;
        }

        /**
         * Возвращает время полного перебора.
         * @return время полного перебора
         */
        public double getFullScanTimeMs() {
            return fullScanTimeMs;
        }

        /**
         * Возвращает время поиска по индексу.
         * @return время поиска по индексу
         */
        public double getIndexScanTimeMs() {
            return indexScanTimeMs;
        }

        /**
         * Возвращает число найденных записей.
         * @return число найденных записей
         */
        public int getMatchedRows() {
            return matchedRows;
        }

        /**
         * Возвращает коэффициент ускорения.
         * @return коэффициент ускорения
         */
        public double getSpeedup() {
            if (indexScanTimeMs == 0) {
                return Double.POSITIVE_INFINITY;
            }
            return fullScanTimeMs / indexScanTimeMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "Benchmark: %s.%s = %s%n" +
                            "Iterations: %d%n" +
                            "Matched rows: %d%n" +
                            "Full scan: %.4f ms%n" +
                            "Index scan: %.4f ms%n" +
                            "Speedup: %.2fx",
                    tableName,
                    columnName,
                    key,
                    iterations,
                    matchedRows,
                    fullScanTimeMs,
                    indexScanTimeMs,
                    getSpeedup()
            );
        }
    }

    private void validateArguments(String tableName, String columnName, Object key, int iterations) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be empty");
        }

        if (key == null) {
            throw new IllegalArgumentException("Benchmark key cannot be null");
        }

        if (iterations <= 0) {
            throw new IllegalArgumentException("Iterations must be positive");
        }

        TableSchema schema = databaseEngine.getCatalog().getTable(tableName);

        ColumnDefinition column = schema.getColumn(columnName).orElseThrow(() ->
                new IllegalArgumentException("Column does not exist: " + columnName)
        );

        if (!column.getType().isCompatible(key)) {
            throw new IllegalArgumentException("Invalid benchmark key type");
        }

        if (databaseEngine.getIndexManager()
                .findSecondaryIndex(tableName, columnName).isEmpty()) {

            throw new IllegalStateException("Secondary index does not exist for column: " + columnName);
        }
    }

    private List<Integer> fullScan(String tableName, String columnName, Object key) throws IOException {
        var storage = databaseEngine.getTableManager().getTable(tableName);

        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < storage.getTotalRecords(); i++) {
            if (!storage.isActive(i)) {
                continue;
            }
            Row row = storage.read(i);

            if (key.equals(row.get(columnName))) {
                result.add(i);
            }
        }
        return result;
    }

    private List<Integer> indexScan(String tableName, String columnName, Object key) throws IOException {
        var index = databaseEngine.getIndexManager()
                .findSecondaryIndex(tableName, columnName).orElseThrow();

        List<Integer> recordNumbers = index.find(key);

        List<Integer> result = new ArrayList<>();

        for (int recordNumber : recordNumbers) {
            Row row = databaseEngine.read(tableName, recordNumber);

            if (row != null && key.equals(row.get(columnName))) {
                result.add(recordNumber);
            }
        }
        return result;
    }

    private void validateResults(List<Integer> fullScanResult, List<Integer> indexScanResult) {
        List<Integer> first = new ArrayList<>(fullScanResult);
        List<Integer> second = new ArrayList<>(indexScanResult);

        Collections.sort(first);
        Collections.sort(second);

        if (!first.equals(second)) {
            throw new IllegalStateException("Index scan and full scan returned different results");
        }
    }

    private double measureFullScan(String tableName, String columnName,
                                   Object key, int iterations) throws IOException {
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();

            fullScan(tableName, columnName, key);
            totalTime += System.nanoTime() - start;
        }

        return totalTime / (double) iterations / 1000000.0;
    }

    private double measureIndexScan(String tableName, String columnName,
                                    Object key, int iterations) throws IOException {
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();

            indexScan(tableName, columnName, key);
            totalTime += System.nanoTime() - start;
        }

        return totalTime / (double) iterations / 1000000.0;
    }

    /**
     * Сравнивает время поиска полным перебором и через индекс.
     *
     * @param tableName имя таблицы
     * @param columnName имя столбца
     * @param key значение ключа
     * @param iterations число повторов измерения
     * @return результат измерений
     * @throws IOException при ошибке чтения или записи файлов
     */
    public BenchmarkResult benchmark(String tableName, String columnName,
            Object key, int iterations) throws IOException {
        validateArguments(tableName, columnName, key, iterations);

        List<Integer> fullScanResult = fullScan(tableName, columnName, key);
        List<Integer> indexScanResult = indexScan(tableName, columnName, key);

        validateResults(fullScanResult, indexScanResult);

        for (int i = 0; i < 5; i++) {
            fullScan(tableName, columnName, key);
            indexScan(tableName, columnName, key);
        }

        double fullScanTime = measureFullScan(tableName, columnName, key, iterations);
        double indexScanTime = measureIndexScan(tableName, columnName, key, iterations);

        return new BenchmarkResult(
                tableName,
                columnName,
                key,
                iterations,
                fullScanTime,
                indexScanTime,
                fullScanResult.size()
        );
    }
}