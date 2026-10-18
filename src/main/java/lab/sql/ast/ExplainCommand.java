package lab.sql.ast;

/**
 * Представляет запрос на вывод плана выполнения SELECT.
 */
public class ExplainCommand implements SqlCommand {
    private final SelectCommand selectCommand;

    /**
     * Создаёт объект ExplainCommand.
     *
     * @param selectCommand SQL-команда SELECT
     */
    public ExplainCommand(SelectCommand selectCommand) {
        if (selectCommand == null) {
            throw new IllegalArgumentException("Select command cannot be null");
        }
        this.selectCommand = selectCommand;
    }

    /**
     * Возвращает команду SELECT.
     * @return команду SELECT
     */
    public SelectCommand getSelectCommand() {
        return selectCommand;
    }
}