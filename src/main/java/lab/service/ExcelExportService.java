package lab.service;

import lab.sql.executor.QueryResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Экспортирует результаты запросов в формат Excel.
 */
public class ExcelExportService {
    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Сохраняет результат SQL-запроса в файле Excel.
     *
     * @param result результат запроса
     * @param path путь к файлу
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void export(QueryResult result, Path path) throws IOException {
        if (result == null || path == null) {
            throw new IllegalArgumentException("Result and path cannot be null");
        }

        List<String> columns = result.getColumns();

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Result has no columns");
        }

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Results");

            var header = sheet.createRow(0);

            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }

            int rowNumber = 1;

            for (var row : result.getRows()) {
                var excelRow = sheet.createRow(rowNumber++);

                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    writeCell(excelRow.createCell(i), value);
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            try (var output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }
}