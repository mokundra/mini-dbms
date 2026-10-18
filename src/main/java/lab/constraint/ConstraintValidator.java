package lab.constraint;

import lab.catalog.ColumnDefinition;
import lab.catalog.ForeignKeyDefinition;
import lab.catalog.IndexDefinition;
import lab.catalog.TableSchema;
import lab.engine.DatabaseEngine;
import lab.model.Row;
import lab.storage.TableStorage;

import java.io.IOException;

/**
 * Проверяет ограничения таблицы перед вставкой и обновлением записей.
 */
public class ConstraintValidator {
    private final DatabaseEngine databaseEngine;

    /**
     * Создаёт объект ConstraintValidator.
     *
     * @param databaseEngine движок базы данных
     */
    public ConstraintValidator(DatabaseEngine databaseEngine) {
        if (databaseEngine == null) {
            throw new IllegalArgumentException("Database engine cannot be null");
        }
        this.databaseEngine = databaseEngine;
    }

    private void validateColumnsExist(TableSchema schema, Row row) {
        for (String columnName : row.getValues().keySet()) {
            if (!schema.hasColumn(columnName)) {
                throw new IllegalArgumentException("Column does not exist: " + columnName);
            }
        }
    }

    private void validateColumnValues(TableSchema schema, Row row) {
        for (ColumnDefinition column : schema.getColumns()) {
            Object value = row.contains(column.getName()) ? row.get(column.getName()) : column.getDefaultValue();
            column.validateValue(value);
        }
    }

    private void validatePrimaryKey(TableSchema schema, Row row, int currentRecordNumber) throws IOException {
        if (schema.getPrimaryKey().isEmpty()) {
            return;
        }

        ColumnDefinition primaryKey = schema.getPrimaryKey().get();
        Object value = row.get(primaryKey.getName());

        if (value == null) {
            throw new IllegalArgumentException("Primary key cannot be null");
        }

        if (!(value instanceof Integer integerValue)) {
            throw new IllegalArgumentException("Primary key must be INT");
        }

        long existingRecordNumber = databaseEngine.getIndexManager()
                .getPrimaryIndex(schema.getTableName()).get(integerValue);

        if (existingRecordNumber >= 0 && existingRecordNumber != currentRecordNumber) {
            throw new IllegalStateException("Duplicate primary key: " + integerValue);
        }
    }

    private void validateUnique(TableSchema schema, Row row, int currentRecordNumber) throws IOException {
        TableStorage storage = databaseEngine.getTableManager().getTable(schema.getTableName());

        for (ColumnDefinition column : schema.getColumns()) {
            if (!column.isUnique() || column.isPrimaryKey()) {
                continue;
            }
            validateUniqueColumn(storage, column.getName(), row.get(column.getName()), currentRecordNumber);
        }

        for (IndexDefinition index : databaseEngine.getCatalog().getIndexesForTable(schema.getTableName())) {
            if (!index.isUnique() || index.isPrimary()) {
                continue;
            }
            validateUniqueColumn(storage, index.getColumnName(), row.get(index.getColumnName()), currentRecordNumber);
        }
    }

    private void validateUniqueColumn(TableStorage storage, String columnName, Object value,
                                      int currentRecordNumber) throws IOException {
        if (value == null) {
            return;
        }

        for (int i = 0; i < storage.getTotalRecords(); i++) {
            if (i == currentRecordNumber || !storage.isActive(i)) {
                continue;
            }

            Row existing = storage.read(i);
            if (existing != null && value.equals(existing.get(columnName))) {
                throw new IllegalStateException("Duplicate unique value: " + columnName + " = " + value);
            }
        }
    }

    private void validateForeignKey(ForeignKeyDefinition foreignKey, Row row) throws IOException {
        Object value = row.get(foreignKey.getColumnName());

        if (value == null) {
            return;
        }

        TableSchema referencedSchema = databaseEngine.getCatalog().getTable(foreignKey.getReferencedTable());
        ColumnDefinition referencedColumn = referencedSchema.getColumn(foreignKey.getReferencedColumn()).orElseThrow();

        if (referencedColumn.isPrimaryKey()) {
            if (!(value instanceof Integer integerValue)) {
                throw new IllegalArgumentException("Foreign key must match referenced primary key type");
            }

            Row referencedRow = databaseEngine.findByPrimaryKey(foreignKey.getReferencedTable(), integerValue);

            if (referencedRow != null) {
                return;
            }
        } else {
            TableStorage storage = databaseEngine.getTableManager().getTable(foreignKey.getReferencedTable());

            for (int i = 0; i < storage.getTotalRecords(); i++) {
                if (!storage.isActive(i)) {
                    continue;
                }

                Row referencedRow = storage.read(i);
                if (referencedRow != null && value.equals(referencedRow.get(foreignKey.getReferencedColumn()))) {
                    return;
                }
            }
        }

        throw new IllegalStateException(
                "Foreign key constraint violation: "
                        + foreignKey.getReferencedTable()
                        + "."
                        + foreignKey.getReferencedColumn()
                        + " = "
                        + value
                        + " does not exist"
        );
    }

    private void validateForeignKeys(TableSchema schema, Row row) throws IOException {
        for (ForeignKeyDefinition foreignKey : schema.getForeignKeys()) {
            validateForeignKey(foreignKey, row);
        }
    }

    /**
     * Проверяет ограничения для новой записи.
     *
     * @param tableName имя таблицы
     * @param row запись таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void validateInsert(String tableName, Row row) throws IOException {
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }
        TableSchema schema = databaseEngine.getCatalog().getTable(tableName);

        validateColumnsExist(schema, row);
        validateColumnValues(schema, row);
        validatePrimaryKey(schema, row, -1);
        validateUnique(schema, row, -1);
        validateForeignKeys(schema, row);
    }

    /**
     * Проверяет ограничения при замене существующей записи.
     *
     * @param tableName имя таблицы
     * @param row запись таблицы
     * @param currentRecordNumber номер изменяемой записи
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void validateUpdate(String tableName, Row row, int currentRecordNumber) throws IOException {
        if (row == null) {
            throw new IllegalArgumentException("Row cannot be null");
        }

        TableSchema schema = databaseEngine.getCatalog().getTable(tableName);

        validateColumnsExist(schema, row);
        validateColumnValues(schema, row);
        validatePrimaryKey(schema, row, currentRecordNumber);
        validateUnique(schema, row, currentRecordNumber);
        validateForeignKeys(schema, row);
    }
}
