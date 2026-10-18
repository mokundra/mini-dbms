package lab.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class PrimaryIndexTest {
    @TempDir Path temp;

    @Test
    void insertFindDeleteAndReopen() throws Exception {
        Path path = temp.resolve("primary.idx");
        PrimaryIndex index = new PrimaryIndex();
        index.create(path);
        for (int i = 0; i < 250; i++) {
            index.put(i, i + 1000);
        }
        assertEquals(250, index.getSize());
        assertEquals(1123, index.get(123));
        assertTrue(index.remove(123));
        assertFalse(index.contains(123));
        index.close();

        PrimaryIndex reopened = new PrimaryIndex();
        reopened.open(path);
        assertFalse(reopened.contains(123));
        assertEquals(1249, reopened.get(249));
        reopened.close();
    }
}
