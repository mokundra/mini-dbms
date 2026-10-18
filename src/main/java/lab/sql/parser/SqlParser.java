package lab.sql.parser;

import lab.catalog.ColumnDefinition;
import lab.catalog.DataType;
import lab.catalog.ForeignKeyDefinition;
import lab.sql.ast.CreateIndexCommand;
import lab.sql.ast.CreateTableCommand;
import lab.sql.ast.DeleteCommand;
import lab.sql.ast.DropIndexCommand;
import lab.sql.ast.DropTableCommand;
import lab.sql.ast.ExplainCommand;
import lab.sql.ast.InsertCommand;
import lab.sql.ast.SelectCommand;
import lab.sql.ast.SqlCommand;
import lab.sql.ast.UpdateCommand;
import lab.sql.condition.ComparisonCondition;
import lab.sql.condition.ComparisonOperator;
import lab.sql.tokenizer.SqlTokenizer;
import lab.sql.tokenizer.Token;
import lab.sql.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Преобразует последовательность SQL-токенов в объекты команд.
 */
public class SqlParser {
    private final List<Token> tokens;
    private int position;

    /**
     * Создаёт объект SqlParser.
     *
     * @param sql текст SQL-запроса
     */
    public SqlParser(String sql) {
        SqlTokenizer tokenizer = new SqlTokenizer(sql);

        this.tokens = tokenizer.tokenize();
        this.position = 0;
    }

    private Token current() {
        return tokens.get(position);
    }

    private Token previous() {
        return tokens.get(position - 1);
    }

    private boolean isAtEnd() {
        return current().is(TokenType.END);
    }

    private Token advance() {
        if (!isAtEnd()) {
            position++;
        }

        return previous();
    }

    private boolean check(TokenType type) {
        return current().is(type);
    }

    private boolean checkKeyword(String keyword) {
        return current().isKeyword(keyword);
    }

    private boolean match(TokenType type) {
        if (!check(type)) {
            return false;
        }

        advance();
        return true;
    }

    private boolean matchKeyword(String keyword) {
        if (!checkKeyword(keyword)) {
            return false;
        }

        advance();
        return true;
    }

    private Token expect(TokenType type, String message) {
        if (!check(type)) {
            throw new ParseException(message);
        }

        return advance();
    }

    private Token expectKeyword(String keyword) {
        if (!checkKeyword(keyword)) {
            throw new ParseException("Expected keyword: " + keyword);
        }

        return advance();
    }

    private String expectIdentifier() {
        return expect(TokenType.IDENTIFIER, "Expected identifier").getValue();
    }

    private void expectEnd() {
        match(TokenType.SEMICOLON);
        if (!check(TokenType.END)) {
            throw new ParseException("Unexpected token: " + current().getValue());
        }
    }

    private Object parseValue() {
        Token token = current();

        if (match(TokenType.STRING)) {
            return token.getValue();
        }

        if (match(TokenType.NUMBER)) {
            String value = token.getValue();

            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                }
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new ParseException("Invalid number: " + value, exception);
            }
        }

        if (match(TokenType.BOOLEAN)) {
            return Boolean.parseBoolean(token.getValue());
        }

        if (checkKeyword("NULL")) {
            advance();
            return null;
        }

        throw new ParseException("Expected value");
    }

    private DataType parseDataType() {
        if (!check(TokenType.KEYWORD)) {
            throw new ParseException("Expected data type");
        }

        String value = current().getValue();

        try {
            DataType type = DataType.fromString(value);
            advance();
            return type;
        } catch (IllegalArgumentException exception) {
            throw new ParseException("Unsupported data type: " + value, exception);
        }
    }

    private ComparisonOperator parseComparisonOperator() {
        Token token = current();

        if (match(TokenType.EQUALS)) {
            return ComparisonOperator.EQUALS;
        }

        if (match(TokenType.NOT_EQUALS)) {
            return ComparisonOperator.NOT_EQUALS;
        }

        if (match(TokenType.GREATER_THAN)) {
            return ComparisonOperator.GREATER_THAN;
        }

        if (match(TokenType.GREATER_OR_EQUALS)) {
            return ComparisonOperator.GREATER_OR_EQUALS;
        }

        if (match(TokenType.LESS_THAN)) {
            return ComparisonOperator.LESS_THAN;
        }

        if (match(TokenType.LESS_OR_EQUALS)) {
            return ComparisonOperator.LESS_OR_EQUALS;
        }

        throw new ParseException("Expected comparison operator: " + token.getValue());
    }

    private ComparisonCondition parseCondition() {
        String columnName = expectIdentifier();
        ComparisonOperator operator = parseComparisonOperator();
        Object value = parseValue();

        return new ComparisonCondition(columnName, operator, value);
    }

    private List<String> parseColumnList() {
        List<String> columns = new ArrayList<>();
        columns.add(expectIdentifier());

        while (match(TokenType.COMMA)) {
            columns.add(expectIdentifier());
        }

        return columns;
    }

    private List<Object> parseValueList() {
        expect(TokenType.LEFT_PARENTHESIS, "Expected '('");
        List<Object> values = new ArrayList<>();
        values.add(parseValue());

        while (match(TokenType.COMMA)) {
            values.add(parseValue());
        }

        expect(TokenType.RIGHT_PARENTHESIS, "Expected ')'");

        return values;
    }

    private List<List<Object>> parseRows() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(parseValueList());

        while (match(TokenType.COMMA)) {
            rows.add(parseValueList());
        }

        return rows;
    }

    private Map<String, Object> parseAssignments() {
        Map<String, Object> assignments = new LinkedHashMap<>();

        while (true) {
            String columnName = expectIdentifier();
            expect(TokenType.EQUALS, "Expected '='");
            Object value = parseValue();

            if (assignments.containsKey(columnName)) {
                throw new ParseException("Duplicate assignment: " + columnName);
            }

            assignments.put(columnName, value);

            if (!match(TokenType.COMMA)) {
                break;
            }
        }

        return assignments;
    }

    private ColumnDefinition parseColumnDefinition() {
        String columnName = expectIdentifier();
        DataType type = parseDataType();

        boolean primaryKey = false;
        boolean unique = false;

        if (matchKeyword("PRIMARY")) {
            expectKeyword("KEY");

            primaryKey = true;
            unique = true;
        } else if (matchKeyword("UNIQUE")) {
            unique = true;
        }

        return new ColumnDefinition(
                columnName,
                type,
                primaryKey,
                !primaryKey,
                unique,
                null
        );
    }

    private ForeignKeyDefinition parseForeignKey() {
        expectKeyword("FOREIGN");
        expectKeyword("KEY");

        expect(TokenType.LEFT_PARENTHESIS, "Expected '(' after FOREIGN KEY");

        String columnName = expectIdentifier();

        expect(TokenType.RIGHT_PARENTHESIS, "Expected ')' after foreign key column");

        expectKeyword("REFERENCES");

        String referencedTable = expectIdentifier();

        expect(TokenType.LEFT_PARENTHESIS, "Expected '(' after referenced table");

        String referencedColumn = expectIdentifier();

        expect(TokenType.RIGHT_PARENTHESIS, "Expected ')' after referenced column");

        return new ForeignKeyDefinition(columnName, referencedTable, referencedColumn);
    }

    private InsertCommand parseInsert() {
        expectKeyword("INTO");
        String tableName = expectIdentifier();
        List<String> columns = new ArrayList<>();

        if (match(TokenType.LEFT_PARENTHESIS)) {
            columns = parseColumnList();

            expect(TokenType.RIGHT_PARENTHESIS, "Expected ')'");
        }

        expectKeyword("VALUES");

        List<List<Object>> rows = parseRows();

        expectEnd();

        return new InsertCommand(tableName, columns, rows);
    }

    private SelectCommand parseSelect() {
        List<String> columns = new ArrayList<>();

        if (!match(TokenType.ASTERISK)) {
            columns = parseColumnList();
        }

        expectKeyword("FROM");

        String tableName = expectIdentifier();

        ComparisonCondition condition = null;

        if (matchKeyword("WHERE")) {
            condition = parseCondition();
        }

        expectEnd();

        return new SelectCommand(tableName, columns, condition);
    }

    private UpdateCommand parseUpdate() {
        String tableName = expectIdentifier();
        expectKeyword("SET");
        Map<String, Object> assignments = parseAssignments();
        ComparisonCondition condition = null;

        if (matchKeyword("WHERE")) {
            condition = parseCondition();
        }

        expectEnd();

        return new UpdateCommand(tableName, assignments, condition);
    }

    private DeleteCommand parseDelete() {
        expectKeyword("FROM");
        String tableName = expectIdentifier();
        ComparisonCondition condition = null;

        if (matchKeyword("WHERE")) {
            condition = parseCondition();
        }

        expectEnd();

        return new DeleteCommand(tableName, condition);
    }

    private CreateTableCommand parseCreateTable() {
        String tableName = expectIdentifier();
        expect(TokenType.LEFT_PARENTHESIS, "Expected '(' after table name");
        List<ColumnDefinition> columns = new ArrayList<>();
        List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();

        while (!check(TokenType.RIGHT_PARENTHESIS)) {
            if (checkKeyword("FOREIGN")) {
                foreignKeys.add(parseForeignKey());
            } else {
                columns.add(parseColumnDefinition());
            }
            if (!match(TokenType.COMMA)) {
                break;
            }
        }

        expect(TokenType.RIGHT_PARENTHESIS, "Expected ')' after table definition");

        expectEnd();

        return new CreateTableCommand(tableName, columns, foreignKeys);
    }

    private CreateIndexCommand parseCreateIndex(boolean unique) {
        String indexName = expectIdentifier();

        expectKeyword("ON");

        String tableName = expectIdentifier();

        expect(TokenType.LEFT_PARENTHESIS, "Expected '(' after table name");

        String columnName = expectIdentifier();

        expect(TokenType.RIGHT_PARENTHESIS, "Expected ')' after column name");

        expectEnd();

        return new CreateIndexCommand(indexName, tableName, columnName, unique);
    }

    private SqlCommand parseCreate() {
        if (matchKeyword("TABLE")) {
            return parseCreateTable();
        }
        boolean unique = matchKeyword("UNIQUE");

        if (matchKeyword("INDEX")) {
            return parseCreateIndex(unique);
        }

        throw new ParseException("Expected TABLE or INDEX after CREATE");
    }

    private DropTableCommand parseDropTable() {
        String tableName = expectIdentifier();
        expectEnd();
        return new DropTableCommand(tableName);
    }

    private DropIndexCommand parseDropIndex() {
        String indexName = expectIdentifier();
        expectEnd();
        return new DropIndexCommand(indexName);
    }

    private SqlCommand parseDrop() {
        if (matchKeyword("TABLE")) {
            return parseDropTable();
        }

        if (matchKeyword("INDEX")) {
            return parseDropIndex();
        }
        throw new ParseException("Expected TABLE or INDEX after DROP");
    }

    private ExplainCommand parseExplain() {
        expectKeyword("SELECT");
        SelectCommand selectCommand = parseSelect();
        return new ExplainCommand(selectCommand);
    }

    /**
     * Разбирает SQL и возвращает соответствующий объект команды.
     * @return объект разобранной SQL-команды
     */
    public SqlCommand parse() {
        if (matchKeyword("INSERT")) {
            return parseInsert();
        }

        if (matchKeyword("SELECT")) {
            return parseSelect();
        }

        if (matchKeyword("UPDATE")) {
            return parseUpdate();
        }

        if (matchKeyword("DELETE")) {
            return parseDelete();
        }

        if (matchKeyword("CREATE")) {
            return parseCreate();
        }

        if (matchKeyword("DROP")) {
            return parseDrop();
        }

        if (matchKeyword("EXPLAIN")) {
            return parseExplain();
        }

        throw new ParseException("Unsupported SQL command: " + current().getValue());
    }
}