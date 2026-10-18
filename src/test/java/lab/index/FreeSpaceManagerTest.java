package lab.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class FreeSpaceManagerTest {
    @TempDir Path temp;

    @Test
    void reuseAndReopen() throws Exception {
        Path path = temp.resolve("free.dat");
        FreeSpaceManager manager = new FreeSpaceManager();
        manager.create(path);
        manager.push(5);
        manager.push(8);
        assertEquals(8, manager.pop());
        manager.close();

        FreeSpaceManager reopened = new FreeSpaceManager();
        reopened.open(path);
        assertEquals(5, reopened.pop());
        assertTrue(reopened.isEmpty());
        reopened.close();
    }
}
