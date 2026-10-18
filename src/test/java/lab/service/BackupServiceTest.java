package lab.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class BackupServiceTest {
    @TempDir
    Path temp;

    private Path createDatabase() throws IOException {
        Path database = temp.resolve("database");
        Files.createDirectories(database.resolve("tables"));
        Files.createDirectories(database.resolve("indexes"));
        Files.createDirectories(database.resolve("free"));
        Files.writeString(database.resolve("catalog.meta"), "catalog-v1");
        Files.writeString(database.resolve("tables").resolve("students.db"), "rows-v1");
        Files.writeString(database.resolve("indexes").resolve("students_primary.idx"), "index-v1");
        Files.writeString(database.resolve("free").resolve("students.free"), "slots-v1");
        return database;
    }

    @Test
    void createsArchiveContainingDatabaseFiles() throws Exception {
        Path database = createDatabase();
        Path archive = temp.resolve("backups").resolve("snapshot.zip");

        Path result = new BackupService(database).createBackup(archive);

        assertEquals(archive.toAbsolutePath().normalize(), result);
        assertTrue(Files.isRegularFile(archive));

        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            assertNotNull(zip.getEntry("catalog.meta"));
            assertNotNull(zip.getEntry("tables/students.db"));
            assertNotNull(zip.getEntry("indexes/students_primary.idx"));
            assertNotNull(zip.getEntry("free/students.free"));
        }
    }

    @Test
    void restoresFilesAndRemovesChangesMadeAfterBackup() throws Exception {
        Path database = createDatabase();
        Path archive = temp.resolve("backup.zip");
        BackupService service = new BackupService(database);
        service.createBackup(archive);

        Files.writeString(database.resolve("catalog.meta"), "catalog-v2");
        Files.writeString(database.resolve("tables/students.db"), "rows-v2");
        Files.writeString(database.resolve("tables/new_table.db"), "new-data");

        service.restoreBackup(archive);

        assertEquals("catalog-v1", Files.readString(database.resolve("catalog.meta")));
        assertEquals("rows-v1", Files.readString(database.resolve("tables/students.db")));
        assertEquals("index-v1", Files.readString(database.resolve("indexes/students_primary.idx")));
        assertEquals("slots-v1", Files.readString(database.resolve("free/students.free")));
        assertFalse(Files.exists(database.resolve("tables/new_table.db")));
    }

    @Test
    void restoresDatabaseAfterDirectoryWasDeleted() throws Exception {
        Path database = createDatabase();
        Path archive = temp.resolve("backup.zip");
        BackupService service = new BackupService(database);
        service.createBackup(archive);

        try (var paths = Files.walk(database)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.delete(path);
            }
        }

        service.restoreBackup(archive);

        assertEquals("catalog-v1", Files.readString(database.resolve("catalog.meta")));
        assertEquals("rows-v1", Files.readString(database.resolve("tables/students.db")));
    }

    @Test
    void rejectsArchiveInsideDatabaseDirectory() throws Exception {
        Path database = createDatabase();
        BackupService service = new BackupService(database);

        assertThrows(IllegalArgumentException.class,
                () -> service.createBackup(database.resolve("backup.zip")));
        assertThrows(IllegalArgumentException.class,
                () -> service.restoreBackup(database.resolve("backup.zip")));
    }

    @Test
    void rejectsIncompleteArchiveWithoutDamagingOriginalDatabase() throws Exception {
        Path database = createDatabase();
        Path archive = temp.resolve("invalid.zip");

        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("tables/students.db"));
            output.write("changed".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> new BackupService(database).restoreBackup(archive));
        assertEquals("catalog-v1", Files.readString(database.resolve("catalog.meta")));
        assertEquals("rows-v1", Files.readString(database.resolve("tables/students.db")));
    }

    @Test
    void rejectsUnsafeZipEntries() throws Exception {
        Path database = createDatabase();
        Path archive = temp.resolve("unsafe.zip");
        Path outside = temp.resolve("evil.txt");

        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("../evil.txt"));
            output.write("unsafe".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> new BackupService(database).restoreBackup(archive));
        assertFalse(Files.exists(outside));
        assertEquals("catalog-v1", Files.readString(database.resolve("catalog.meta")));
    }
}
