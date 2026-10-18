package lab.cli;

import lab.sql.ast.SqlCommand;
import lab.sql.executor.QueryExecutor;
import lab.sql.executor.QueryResult;
import lab.sql.parser.ParseException;
import lab.sql.parser.SqlParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Предоставляет консоль для выполнения SQL-команд.
 */
public class SqlConsole {
    private final QueryExecutor queryExecutor;
    private final Scanner scanner;

    /**
     * Создаёт объект SqlConsole.
     *
     * @param queryExecutor исполнитель SQL-команд
     */
    public SqlConsole(QueryExecutor queryExecutor) {
        if (queryExecutor == null) {
            throw new IllegalArgumentException("Query executor cannot be null");
        }
        this.queryExecutor = queryExecutor;
        this.scanner = new Scanner(System.in);
    }

    private boolean isExitCommand(String input) {
        String normalized = input.trim();

        return normalized.equalsIgnoreCase("exit")
                || normalized.equalsIgnoreCase("quit")
                || normalized.equalsIgnoreCase("\\q");
    }

    private boolean isCompleteSql(String sql) {
        return sql.trim().endsWith(";");
    }

    private String readCommand() {
        StringBuilder builder = new StringBuilder();

        while (true) {
            if (builder.isEmpty()) {
                System.out.print("db> ");
            } else {
                System.out.print("... ");
            }
            if (!scanner.hasNextLine()) {
                return "exit";
            }

            String line = scanner.nextLine();

            if (builder.isEmpty() && isExitCommand(line)) {
                return line;
            }

            builder.append(line).append(' ');

            if (isCompleteSql(builder.toString())) {
                return builder.toString().trim();
            }
        }
    }

    private int calculateColumnWidth(String column, List<String> values) {
        int width = column.length();

        for (String value : values) {
            if (value != null) {
                width = Math.max(width, value.length());
            }
        }
        return width;
    }

    private String repeat(char value, int count) {
        return String.valueOf(value).repeat(Math.max(0, count));
    }

    private List<Integer> calculateWidths(QueryResult result) {
        List<Integer> widths = new ArrayList<>();

        for (String column : result.getColumns()) {
            List<String> values = new ArrayList<>();

            for (var row : result.getRows()) {
                Object value = row.get(column);
                values.add(value == null ? "NULL" : String.valueOf(value));
            }
            widths.add(calculateColumnWidth(column, values));
        }
        return widths;
    }

    private void printSeparator(List<Integer> widths) {
        System.out.print("+");

        for (int width : widths) {
            System.out.print(repeat('-', width + 2));
            System.out.print("+");
        }
        System.out.println();
    }

    private void printHeader(QueryResult result, List<Integer> widths) {
        System.out.print("|");

        for (int i = 0; i < result.getColumns().size(); i++) {
            String column = result.getColumns().get(i);
            System.out.printf(" %-" + widths.get(i) + "s |", column);
        }
        System.out.println();
    }

    private void printRows(QueryResult result, List<Integer> widths) {
        for (var row : result.getRows()) {
            System.out.print("|");

            for (int i = 0; i < result.getColumns().size(); i++) {
                String column = result.getColumns().get(i);
                Object value = row.get(column);

                String text = value == null ? "NULL" : String.valueOf(value);
                System.out.printf(" %-" + widths.get(i) + "s |", text);
            }
            System.out.println();
        }
    }

    private void printTable(QueryResult result) {
        List<Integer> widths = calculateWidths(result);

        printSeparator(widths);

        printHeader(result, widths);

        printSeparator(widths);

        printRows(result, widths);

        printSeparator(widths);

        System.out.println(result.getRows().size() + " row(s)");
    }

    private void printResult(QueryResult result) {
        if (result.hasRows()) {
            printTable(result);
            return;
        }

        if (result.hasMessage()) {
            System.out.println(result.getMessage());
            return;
        }
        System.out.println(result.getAffectedRows() + " row(s) affected");
    }

    private void executeSql(String sql) {
        try {
            SqlParser parser = new SqlParser(sql);

            SqlCommand command = parser.parse();

            QueryResult result = queryExecutor.execute(command);
            printResult(result);

        } catch (ParseException exception) {
            System.out.println("SQL syntax error: " + exception.getMessage());

        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Error: " + exception.getMessage());

        } catch (IOException exception) {
            System.out.println("I/O error: " + exception.getMessage());
        }
    }

    /**
     * Запускает цикл чтения и выполнения SQL-команд.
     */
    public void run() {
        System.out.println("SQL Console");
        System.out.println("Type EXIT, QUIT or \\q to stop.");

        while (true) {
            String input = readCommand();

            if (isExitCommand(input)) {
                break;
            }

            if (input.isBlank()) {
                continue;
            }
            executeSql(input);
        }
        System.out.println("Console closed.");
    }

}