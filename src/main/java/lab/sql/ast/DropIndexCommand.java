package lab.sql.ast;

/**
 * Представляет SQL-команду удаления индекса.
 */
public class DropIndexCommand implements SqlCommand {
    private final String indexName;

    private static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Index name cannot be null");
        }
        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Index name cannot be empty");
        }
        return normalized;
    }

    /**
     * Создаёт объект DropIndexCommand.
     *
     * @param indexName имя индекса
     */
    public DropIndexCommand(String indexName) {
        this.indexName = normalizeName(indexName);
    }

    /**
     * Возвращает имя индекса.
     * @return имя индекса
     */
    public String getIndexName() {
        return indexName;
    }
}