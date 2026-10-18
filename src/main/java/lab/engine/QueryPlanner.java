package lab.engine;

import lab.catalog.Catalog;
import lab.catalog.ColumnDefinition;
import lab.catalog.TableSchema;
import lab.index.SecondaryIndex;
import lab.sql.condition.ComparisonCondition;
import lab.sql.condition.ComparisonOperator;

import java.util.Optional;

/**
 * Выбирает полный перебор или индекс для поиска записей.
 */
public class QueryPlanner {
    /**
     * Перечисляет поддерживаемые способы выполнения поиска.
     */
    public enum PlanType {
        FULL_SCAN,
        PRIMARY_INDEX,
        SECONDARY_INDEX
    }

    /**
     * Описывает выбранный способ выполнения запроса.
     */
    public static class Plan {
        private final PlanType type;
        private final String indexName;

        /**
         * Создаёт объект Plan.
         *
         * @param type тип
         * @param indexName имя индекса
         */
        public Plan(PlanType type, String indexName) {
            if (type == null) {
                throw new IllegalArgumentException("Plan type cannot be null");
            }

            this.type = type;
            this.indexName = indexName;
        }

        /**
         * Возвращает тип.
         * @return тип
         */
        public PlanType getType() {
            return type;
        }

        /**
         * Возвращает имя индекса.
         * @return имя индекса
         */
        public String getIndexName() {
            return indexName;
        }

        /**
         * Проверяет, предусмотрено ли использование индекса.
         * @return результат операции
         */
        public boolean usesIndex() {
            return type != PlanType.FULL_SCAN;
        }

        @Override
        public String toString() {
            if (type == PlanType.FULL_SCAN) {
                return "FULL TABLE SCAN";
            }
            return type + " " + indexName;
        }
    }

    private final Catalog catalog;
    private final IndexManager indexManager;

    /**
     * Создаёт объект QueryPlanner.
     *
     * @param catalog каталог таблиц и индексов
     * @param indexManager менеджер индексов
     */
    public QueryPlanner(Catalog catalog, IndexManager indexManager) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }

        if (indexManager == null) {
            throw new IllegalArgumentException("Index manager cannot be null");
        }
        this.catalog = catalog;
        this.indexManager = indexManager;
    }

    private boolean isEqualityCondition(ComparisonCondition condition) {
        return condition.getOperator() == ComparisonOperator.EQUALS;
    }

    private boolean isPrimaryKeyCondition(TableSchema schema, ComparisonCondition condition) {
        Optional<ColumnDefinition> primaryKey = schema.getPrimaryKey();
        if (primaryKey.isEmpty()) {
            return false;
        }

        return primaryKey.get().getName().equalsIgnoreCase(condition.getColumnName());
    }

    private Optional<SecondaryIndex> findSecondaryIndex(String tableName, String columnName) {
        return indexManager.findSecondaryIndex(tableName, columnName);
    }

    /**
     * Выбирает план поиска с учётом условия и доступных индексов.
     *
     * @param tableName имя таблицы
     * @param condition условие отбора
     * @return выбранный план выполнения поиска
     */
    public Plan plan(String tableName, ComparisonCondition condition) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        if (condition == null) {
            return new Plan(PlanType.FULL_SCAN, null);
        }

        TableSchema schema = catalog.getTable(tableName);

        if (!schema.hasColumn(condition.getColumnName())) {
            throw new IllegalArgumentException("Column does not exist: " + condition.getColumnName());
        }

        if (!isEqualityCondition(condition)) {
            return new Plan(PlanType.FULL_SCAN, null);
        }

        if (isPrimaryKeyCondition(schema, condition) && indexManager.hasPrimaryIndex(tableName)) {
            return new Plan(PlanType.PRIMARY_INDEX, tableName + "_primary");
        }
        Optional<SecondaryIndex> secondaryIndex = findSecondaryIndex(tableName, condition.getColumnName());
        if (secondaryIndex.isPresent()) {
            return new Plan(PlanType.SECONDARY_INDEX, secondaryIndex.get().getName());
        }

        return new Plan(PlanType.FULL_SCAN, null);


    }

}