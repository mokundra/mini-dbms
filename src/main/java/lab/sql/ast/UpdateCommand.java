package lab.sql.ast;

import lab.sql.condition.ComparisonCondition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Представляет SQL-команду изменения записей.
 */
public class UpdateCommand implements SqlCommand {
    private final String tableName;
    private final Map<String, Object> assignments;
    private final ComparisonCondition condition;

    private static String normalizeName(String value){
        if (value==null){
            throw new IllegalArgumentException( "Name cannot be null");
        }
        String normalized = value.trim();

        if (normalized.isEmpty()){
            throw new IllegalArgumentException("Name cannot be empty");
        }

        return normalized;
    }

    private static void validateAssignments(Map<String,Object> assignments){
        if (assignments==null || assignments.isEmpty()){
            throw new IllegalArgumentException("Update must contain at least one assignment");
        }

        for (String columnName : assignments.keySet()){
            if (columnName==null || columnName.isBlank()){
                throw new IllegalArgumentException("Column name cannot be empty");
            }
        }
    }
    private static Map<String, Object> copyAssignments(Map<String,Object> assignments){
        Map<String,Object> result = new LinkedHashMap<>();

        for (Map.Entry<String,Object> entry : assignments.entrySet()){
            result.put(entry.getKey().trim(),entry.getValue());
        }

        return result;
    }

    /**
     * Создаёт объект UpdateCommand.
     *
     * @param tableName имя таблицы
     * @param assignments новые значения столбцов
     * @param condition условие отбора
     */
    public UpdateCommand(String tableName,Map<String,Object> assignments, ComparisonCondition condition){
        validateAssignments(assignments);

        this.tableName=tableName;
        this.assignments=copyAssignments(assignments);
        this.condition=condition;
    }

    /**
     * Возвращает имя таблицы.
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает новые значения столбцов.
     * @return новые значения столбцов
     */
    public Map<String, Object> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    /**
     * Возвращает условие отбора.
     * @return условие отбора
     */
    public ComparisonCondition getCondition() {
        return condition;
    }

    /**
     * Проверяет наличие условия WHERE.
     * @return true, если проверяемое условие выполнено
     */
    public boolean hasCondition() {
        return condition != null;
    }

}