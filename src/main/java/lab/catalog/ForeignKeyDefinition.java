package lab.catalog;

import java.util.Objects;

/**
 * Описывает внешний ключ и ссылку на столбец другой таблицы.
 */
public class ForeignKeyDefinition {
    private final String columnName;
    private final String referencedTable;
    private final String referencedColumn;

    /**
     * Создаёт объект ForeignKeyDefinition.
     *
     * @param columnName имя столбца
     * @param referencedTable имя таблицы, на которую ссылается внешний ключ
     * @param referencedColumn имя столбца, на который ссылается внешний ключ
     */
    public ForeignKeyDefinition(String columnName, String referencedTable, String referencedColumn) {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Foreign key column name cannot be empty");
        }

        if (referencedTable == null || referencedTable.isBlank()) {
            throw new IllegalArgumentException("Referenced table name cannot be empty");
        }

        if (referencedColumn == null || referencedColumn.isBlank()) {
            throw new IllegalArgumentException("Referenced column name cannot be empty");
        }

        this.columnName = columnName.trim();
        this.referencedTable = referencedTable.trim();
        this.referencedColumn = referencedColumn.trim();
    }

    /**
     * Возвращает имя столбца.
     * @return имя столбца
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Возвращает имя целевой таблицы.
     * @return имя целевой таблицы
     */
    public String getReferencedTable() {
        return referencedTable;
    }

    /**
     * Возвращает имя целевого столбца.
     * @return имя целевого столбца
     */
    public String getReferencedColumn() {
        return referencedColumn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ForeignKeyDefinition that)) {
            return false;
        }

        return columnName.equalsIgnoreCase(that.columnName)
                && referencedTable.equalsIgnoreCase(that.referencedTable)
                && referencedColumn.equalsIgnoreCase(that.referencedColumn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                columnName.toLowerCase(),
                referencedTable.toLowerCase(),
                referencedColumn.toLowerCase()
        );
    }

    @Override
    public String toString() {
        return "ForeignKeyDefinition{" +
                "columnName='" + columnName + '\'' +
                ",referencedTable='" + referencedTable + '\'' +
                ",referencedColumn='" + referencedColumn + '\'' +
                '}';
    }
}