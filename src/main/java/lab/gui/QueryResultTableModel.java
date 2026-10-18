package lab.gui;

import lab.model.Row;
import lab.sql.executor.QueryResult;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * Представляет результат SQL-запроса в виде модели таблицы Swing.
 */
public class QueryResultTableModel extends AbstractTableModel {
    private List<String> columns;
    private List<Row> rows;

    /**
     * Создаёт объект QueryResultTableModel.
     */
    public QueryResultTableModel() {
        columns = List.of();
        rows = List.of();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        String columnName = columns.get(columnIndex);

        return row.get(columnName);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        for (Row row : rows) {
            Object value = row.get(columns.get(columnIndex));

            if (value != null) {
                return value.getClass();
            }
        }

        return Object.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * Заполняет модель данными результата запроса.
     *
     * @param result результат запроса
     */
    public void setResult(QueryResult result) {
        if (result == null) {
            clear();
            return;
        }

        columns = List.copyOf(result.getColumns());
        rows = List.copyOf(result.getRows());

        fireTableStructureChanged();
    }

    /**
     * Очищает модель результата.
     */
    public void clear() {
        columns = List.of();
        rows = List.of();
        fireTableStructureChanged();
    }
}