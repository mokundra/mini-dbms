package lab.sql.executor;

import lab.catalog.ColumnDefinition;
import lab.catalog.DataType;
import lab.catalog.IndexDefinition;
import lab.catalog.TableSchema;
import lab.constraint.ConstraintValidator;
import lab.engine.DatabaseEngine;
import lab.engine.QueryPlanner;
import lab.index.PrimaryIndex;
import lab.model.Row;
import lab.sql.ast.*;
import lab.sql.condition.ComparisonCondition;
import lab.storage.TableStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Выполняет разобранные SQL-команды через движок базы данных.
 */
public class QueryExecutor {
    private final DatabaseEngine databaseEngine;
    private final QueryPlanner queryPlanner;
    private final ConstraintValidator constraintValidator;

    /**
     * Создаёт объект QueryExecutor.
     *
     * @param databaseEngine движок базы данных
     */
    public QueryExecutor(DatabaseEngine databaseEngine) {
        if (databaseEngine == null) {
            throw new IllegalArgumentException("Database engine cannot be null");
        }

        this.databaseEngine = databaseEngine;
        this.queryPlanner = new QueryPlanner(
                databaseEngine.getCatalog(),
                databaseEngine.getIndexManager()
        );
        this.constraintValidator = new ConstraintValidator(databaseEngine);
    }

    private ColumnDefinition getColumn(TableSchema schema, String columnName) {
        return schema.getColumn(columnName).orElseThrow(() ->
                new IllegalArgumentException("Column does not exist: " + columnName));
    }

    private void validateColumns(TableSchema schema, List<String> columns) {
        for (String column : columns) {
            getColumn(schema, column);
        }
    }

    private Integer convertToInt(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }

        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();

            int intValue = number.intValue();

            if (doubleValue != intValue) {
                throw new IllegalArgumentException("Value cannot be converted to INT: " + value);
            }
            return intValue;
        }
        throw new IllegalArgumentException("Value cannot be converted to INT: " + value);
    }

    private Double convertToDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Value cannot be converted to DOUBLE: " + value);
    }

    private String convertToString(Object value) {
        if (value instanceof String string) {
            return string;
        }

        throw new IllegalArgumentException("Value cannot be converted to STRING: " + value);
    }

    private LocalDate convertToDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }

        if (value instanceof String string) {
            try {
                return LocalDate.parse(string);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid DATE value: " + value);
            }
        }

        throw new IllegalArgumentException("Value cannot be converted to DATE: " + value);
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Value cannot be converted to BOOLEAN: " + value);
    }


    private Object convertValue(Object value, DataType type) {
        if (value == null) {
            return null;
        }

        if (type == DataType.INT) {
            return convertToInt(value);
        } else if (type == DataType.DOUBLE) {
            return convertToDouble(value);
        } else if (type == DataType.STRING) {
            return convertToString(value);
        } else if (type == DataType.DATE) {
            return convertToDate(value);
        } else if (type == DataType.BOOLEAN) {
            return convertToBoolean(value);
        }

        throw new IllegalArgumentException("Unsupported data type: " + type);
    }


    private Object convertValueForColumn(TableSchema schema, String columnName, Object value) {
        ColumnDefinition column = getColumn(schema, columnName);
        Object converted = convertValue(value, column.getType());
        column.validateValue(converted);
        return converted;
    }

    private ComparisonCondition normalizeCondition(String tableName, ComparisonCondition condition) {
        if (condition == null) {
            return null;
        }
        TableSchema schema = databaseEngine.getCatalog().getTable(tableName);
        ColumnDefinition column = getColumn(schema, condition.getColumnName());
        Object value = convertValue(condition.getValue(), column.getType());

        return new ComparisonCondition(condition.getColumnName(), condition.getOperator(), value);
    }

    private Row createRow(TableSchema schema, List<String> columns, List<Object> values) {
        Row row = new Row();

        if (columns.isEmpty()) {
            if (values.size() != schema.getColumnCount()) {
                throw new IllegalArgumentException("Values count does not match table columns count");
            }

            for (int i = 0; i < schema.getColumnCount(); i++) {
                ColumnDefinition column = schema.getColumns().get(i);
                Object value = convertValue(values.get(i), column.getType());

                row.put(column.getName(), value);
            }
            return row;
        }

        if (columns.size() != values.size()) {
            throw new IllegalArgumentException("Values count does not match columns count");
        }

        validateColumns(schema, columns);
        for (int i = 0; i < columns.size(); i++) {
            String columnName = columns.get(i);

            Object value = convertValueForColumn(schema, columnName, values.get(i));

            row.put(columnName, value);

        }
        return row;

    }

    private Row mergeRow(TableSchema schema, Row oldRow, Map<String, Object> assignments) {
        Row result = oldRow.copy();

        for (Map.Entry<String, Object> entry : assignments.entrySet()) {
            String columnName = entry.getKey();
            Object value = convertValueForColumn(schema, columnName, entry.getValue());
            result.put(columnName, value);
        }

        return result;
    }

    private Row projectRow(Row row, List<String> columns) {
        Row result = new Row();

        for (String column : columns) {
            result.put(column, row.get(column));
        }

        return result;
    }

    private List<Integer> fullScanRecordNumbers(String tableName) throws IOException {
        TableStorage storage = databaseEngine.getTableManager().getTable(tableName);
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < storage.getTotalRecords(); i++) {
            if (storage.isActive(i)) {
                result.add(i);
            }
        }

        return result;
    }

    private List<Integer> findCandidateRecordNumbers(
            String tableName, ComparisonCondition condition) throws IOException {
        if (condition == null) {
            return fullScanRecordNumbers(tableName);
        }

        QueryPlanner.Plan plan = queryPlanner.plan(tableName, condition);

        if (plan.getType() == QueryPlanner.PlanType.FULL_SCAN) {
            return fullScanRecordNumbers(tableName);
        }

        if (plan.getType() == QueryPlanner.PlanType.PRIMARY_INDEX) {

            if (!(condition.getValue() instanceof Integer key)) {
                return fullScanRecordNumbers(tableName);
            }

            PrimaryIndex primaryIndex =
                    databaseEngine.getIndexManager().getPrimaryIndex(tableName);

            long recordNumber = primaryIndex.get(key);
            List<Integer> result = new ArrayList<>();

            if (recordNumber >= 0) {
                if (recordNumber > Integer.MAX_VALUE) {
                    throw new IOException("Record number is too large");
                }
                result.add((int) recordNumber);
            }
            return result;
        }
        return databaseEngine.findByIndex(plan.getIndexName(), condition.getValue());
    }

    private boolean matches(Row row, ComparisonCondition condition) {
        return condition == null || condition.matches(row);
    }

    private QueryResult executeInsert(InsertCommand command) throws IOException {
        TableSchema schema = databaseEngine.getCatalog().getTable(command.getTableName());
        int inserted = 0;
        for (List<Object> values : command.getRows()) {
            Row row = createRow(schema, command.getColumns(), values);
            Row normalized = row.withDefaults(schema);

            constraintValidator.validateInsert(command.getTableName(), normalized);
            databaseEngine.insert(command.getTableName(), normalized);

            inserted++;
        }
        return QueryResult.affected(inserted, inserted + " row(s) inserted");
    }

    private QueryResult executeSelect(SelectCommand command) throws IOException {
        TableSchema schema = databaseEngine.getCatalog().getTable(command.getTableName());

        List<String> resultColumns;

        if (command.selectsAllColumns()) {
            resultColumns = new ArrayList<>();

            for (ColumnDefinition column : schema.getColumns()) {
                resultColumns.add(column.getName());
            }
        } else {
            validateColumns(schema, command.getColumns());
            resultColumns = new ArrayList<>(command.getColumns());
        }

        if (command.hasCondition()) {
            getColumn(schema, command.getCondition().getColumnName());
        }

        ComparisonCondition condition = normalizeCondition(command.getTableName(), command.getCondition());

        List<Integer> candidates = findCandidateRecordNumbers(command.getTableName(),
                condition);
        List<Row> rows = new ArrayList<>();

        for (int recordNumber : candidates) {
            Row row = databaseEngine.read(command.getTableName(), recordNumber);

            if (row == null || !matches(row, condition)) {
                continue;
            }
            rows.add(projectRow(row, resultColumns));
        }
        return QueryResult.rows(resultColumns, rows);
    }

    private QueryResult executeUpdate(UpdateCommand command) throws IOException {
        TableSchema schema = databaseEngine.getCatalog().getTable(command.getTableName());

        for (String columnName : command.getAssignments().keySet()) {
            getColumn(schema, columnName);
        }

        if (command.hasCondition()) {
            getColumn(schema, command.getCondition().getColumnName());
        }

        ComparisonCondition condition = normalizeCondition(command.getTableName(), command.getCondition());

        List<Integer> candidates = findCandidateRecordNumbers(command.getTableName(),
                condition);

        int updated = 0;

        for (int recordNumber : candidates) {
            Row oldRow = databaseEngine.read(command.getTableName(), recordNumber);

            if (oldRow == null || !matches(oldRow, condition)) {
                continue;
            }

            Row newRow = mergeRow(schema, oldRow, command.getAssignments());

            constraintValidator.validateUpdate(command.getTableName(), newRow, recordNumber);
            databaseEngine.update(command.getTableName(), recordNumber, newRow);

            updated++;
        }
        return QueryResult.affected(updated, updated + " row(s) updated");
    }

    private QueryResult executeDelete(DeleteCommand command) throws IOException {
        TableSchema schema = databaseEngine.getCatalog().getTable(command.getTableName());

        if (command.hasCondition()) {
            getColumn(schema, command.getCondition().getColumnName());
        }
        ComparisonCondition condition = normalizeCondition(command.getTableName(), command.getCondition());

        List<Integer> candidates = findCandidateRecordNumbers(command.getTableName(),
                condition);

        int deleted = 0;

        for (int recordNumber : candidates) {
            Row row = databaseEngine.read(command.getTableName(), recordNumber);

            if (row == null || !matches(row, condition)) {
                continue;
            }

            if (databaseEngine.delete(command.getTableName(), recordNumber)) {
                deleted++;
            }
        }
        return QueryResult.affected(deleted, deleted + " row(s) deleted");
    }

    private QueryResult executeCreateTable(CreateTableCommand command) throws IOException {
        TableSchema schema = new TableSchema(command.getTableName(),
                command.getColumns(), command.getForeignKeys());
        databaseEngine.createTable(schema);
        return QueryResult.message("Table created: " + command.getTableName());
    }

    private QueryResult executeDropTable(DropTableCommand command) throws IOException {
        databaseEngine.dropTable(command.getTableName());
        return QueryResult.message("Table dropped: " + command.getTableName());
    }

    private QueryResult executeCreateIndex(CreateIndexCommand command) throws IOException {
        IndexDefinition definition =
                new IndexDefinition(
                        command.getIndexName(),
                        command.getTableName(),
                        command.getColumnName(),
                        command.isUnique(),
                        false
                );

        databaseEngine.createSecondaryIndex(definition);
        return QueryResult.message("Index created: " + command.getIndexName());
    }

    private QueryResult executeDropIndex(DropIndexCommand command) throws IOException {

        IndexDefinition definition = databaseEngine.getCatalog().
                getIndex(command.getIndexName());

        if (definition.isPrimary()) {
            throw new IllegalStateException("Primary index cannot be dropped");
        }
        if (databaseEngine.getIndexManager().hasSecondaryIndex(command.getIndexName())) {
            var index = databaseEngine.getIndexManager()
                    .getSecondaryIndex(command.getIndexName());

            var path = index.getPath();
            databaseEngine.getIndexManager().closeSecondaryIndex(command.getIndexName());
            Files.deleteIfExists(path);
        }
        databaseEngine.getCatalog().removeIndex(command.getIndexName());

        return QueryResult.message("Index dropped: " + command.getIndexName());
    }

    private QueryResult executeExplain(ExplainCommand command) {
        SelectCommand select = command.getSelectCommand();
        ComparisonCondition condition = normalizeCondition(select.getTableName(), select.getCondition());
        QueryPlanner.Plan plan = queryPlanner.plan(select.getTableName(), condition);

        return QueryResult.message(plan.toString());
    }

    /**
     * Выполняет переданную SQL-команду и формирует результат.
     *
     * @param command SQL-команда
     * @return результат операции
     * @throws IOException при ошибке чтения или записи файлов
     */
    public QueryResult execute(SqlCommand command) throws IOException {
        if (command == null) {
            throw new IllegalArgumentException("SQL command cannot be null");
        }

        if (command instanceof InsertCommand insert) {
            return executeInsert(insert);
        }

        if (command instanceof SelectCommand select) {
            return executeSelect(select);
        }

        if (command instanceof UpdateCommand update) {
            return executeUpdate(update);
        }

        if (command instanceof DeleteCommand delete) {
            return executeDelete(delete);
        }

        if (command instanceof CreateTableCommand createTable) {
            return executeCreateTable(createTable);
        }

        if (command instanceof DropTableCommand dropTable) {
            return executeDropTable(dropTable);
        }

        if (command instanceof CreateIndexCommand createIndex) {
            return executeCreateIndex(createIndex);
        }

        if (command instanceof DropIndexCommand dropIndex) {
            return executeDropIndex(dropIndex);
        }

        if (command instanceof ExplainCommand explain) {
            return executeExplain(explain);
        }

        throw new IllegalArgumentException("Unsupported SQL command: " + command.getClass().getSimpleName());
    }
}