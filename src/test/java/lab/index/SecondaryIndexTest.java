package lab.index;

import lab.catalog.DataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SecondaryIndexTest {
    @TempDir Path temp;

    @Test
    void duplicateKeysAndPersistence() throws Exception {
        Path path = temp.resolve("secondary.idx");
        SecondaryIndex index = SecondaryIndex.create("idx_rating", "students", "rating", false, DataType.DOUBLE, path);
        index.insert(9.5, 1);
        index.insert(9.5, 3);
        assertEquals(2, index.find(9.5).size());
        index.remove(9.5, 1);
        index.close();

        SecondaryIndex reopened = SecondaryIndex.open("idx_rating", "students", "rating", false, DataType.DOUBLE, path);
        assertEquals(List.of(3), reopened.find(9.5));
        reopened.close();
    }

    @Test
    void uniqueIndex() throws Exception {
        SecondaryIndex index = SecondaryIndex.create("idx_name", "students", "name", true, DataType.STRING, temp.resolve("unique.idx"));
        try {
            index.insert("Nikita", 0);
            assertThrows(RuntimeException.class, () -> index.insert("Nikita", 1));
        } finally {
            index.close();
        }
    }
}
