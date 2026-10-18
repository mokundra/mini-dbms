package lab.sql.parser;

/**
 * Сообщает об ошибке синтаксического разбора SQL.
 */
public class ParseException extends RuntimeException {

    /**
     * Создаёт объект ParseException.
     *
     * @param message текст сообщения
     */
    public ParseException(String message) {
        super(message);
    }
    /**
     * Создаёт объект ParseException.
     *
     * @param message текст сообщения
     * @param cause исходная причина ошибки
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}