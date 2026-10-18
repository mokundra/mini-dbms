package lab.sql.condition;

import lab.model.Row;

/**
 * Определяет проверку соответствия записи условию запроса.
 */
public interface Condition {

    /**
     * Проверяет, удовлетворяет ли запись условию.
     *
     * @param row запись таблицы
     * @return результат операции
     */
    boolean matches(Row row);

}