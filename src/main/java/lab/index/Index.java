package lab.index;

import java.io.IOException;
import java.util.List;

/**
 * Задаёт операции поиска и изменения вторичного индекса.
 */
public interface Index {
    /**
     * Возвращает имя.
     * @return имя
     */
    String getName();

    /**
     * Возвращает имя таблицы.
     * @return имя таблицы
     */
    String getTableName();

    /**
     * Возвращает имя столбца.
     * @return имя столбца
     */
    String getColumnName();

    /**
     * Проверяет ограничение уникальности.
     * @return true, если проверяемое условие выполнено
     */
    boolean isUnique();

    /**
     * Добавляет ссылку на запись по указанному ключу.
     *
     * @param key значение ключа
     * @param recordNumber номер записи в файле таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    void insert(Object key, int recordNumber) throws IOException;

    /**
     * Удаляет ссылку на запись из индекса.
     *
     * @param key значение ключа
     * @param recordNumber номер записи в файле таблицы
     * @throws IOException при ошибке чтения или записи файлов
     */
    void remove(Object key, int recordNumber) throws IOException;

    /**
     * Возвращает номера записей с заданным ключом.
     *
     * @param key значение ключа
     * @return список номеров найденных записей
     * @throws IOException при ошибке чтения или записи файлов
     */
    List<Integer> find(Object key) throws IOException;

    /**
     * Проверяет наличие ключа в индексе.
     *
     * @param key значение ключа
     * @return true, если проверяемое условие выполнено
     * @throws IOException при ошибке чтения или записи файлов
     */
    boolean contains(Object key) throws IOException;

    /**
     * Удаляет все записи из индекса.
     * @throws IOException при ошибке чтения или записи файлов
     */
    void clear() throws IOException;

    /**
     * Закрывает файловые ресурсы индекса.
     * @throws IOException при ошибке чтения или записи файлов
     */
    void close() throws IOException;
}