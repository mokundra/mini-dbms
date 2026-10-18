package lab.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Создаёт ZIP-копии файлов базы данных и восстанавливает их.
 */
public class BackupService {
    private final Path databaseDirectory;

    /**
     * Создаёт объект BackupService.
     *
     * @param databaseDirectory папка с файлами базы данных
     */
    public BackupService(Path databaseDirectory) {
        if (databaseDirectory == null) {
            throw new IllegalArgumentException("Database directory cannot be null");
        }

        this.databaseDirectory = databaseDirectory.toAbsolutePath().normalize();
    }

    private Path validateArchivePath(Path archivePath) {
        if (archivePath == null) {
            throw new IllegalArgumentException("Archive path cannot be null");
        }

        Path path = archivePath.toAbsolutePath().normalize();

        if (path.startsWith(databaseDirectory)) {
            throw new IllegalArgumentException("Archive cannot be inside database directory");
        }
        return path;
    }

    private void validateDatabaseDirectory() throws IOException {
        if (!Files.isDirectory(databaseDirectory)) {
            throw new IOException("Database directory does not exist: " + databaseDirectory);
        }

        if (!Files.isRegularFile(databaseDirectory.resolve("catalog.meta"))) {
            throw new IOException("Catalog file does not exist");
        }
    }

    private void writeArchive(Path temporaryArchive) throws IOException {
        try (
                ZipOutputStream output =
                        new ZipOutputStream(Files.newOutputStream(temporaryArchive),
                                StandardCharsets.UTF_8);
                Stream<Path> paths = Files.walk(databaseDirectory)
        ) {
            List<Path> entries = paths.sorted().toList();

            for (Path path : entries) {
                if (path.equals(databaseDirectory)) {
                    continue;
                }

                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Symbolic links are not supported: " + path);
                }

                String name = databaseDirectory
                        .relativize(path).toString().replace('\\', '/');

                if (Files.isDirectory(path)) {
                    output.putNextEntry(new ZipEntry(name + "/"));
                    output.closeEntry();
                    continue;
                }

                if (!Files.isRegularFile(path)) {
                    throw new IOException("Unsupported file: " + path);
                }

                output.putNextEntry(new ZipEntry(name));

                Files.copy(path, output);

                output.closeEntry();
            }
        }
    }

    private void extractArchive(Path archivePath, Path destination) throws IOException {
        try (
                ZipInputStream input =
                        new ZipInputStream(Files.newInputStream(archivePath),
                                StandardCharsets.UTF_8)
        ) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');

                if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*")) {
                    throw new IOException("Invalid archive entry: " + name);
                }

                Path target = destination.resolve(name).normalize();

                if (!target.startsWith(destination) || target.equals(destination)) {
                    throw new IOException("Unsafe archive entry: " + name);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());

                    try (
                            OutputStream output =
                                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                                            StandardOpenOption.WRITE
                                    )
                    ) {
                        input.transferTo(output);
                    }
                }
                input.closeEntry();
            }
        }
    }

    private void replaceArchive(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    target
            );
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (
                Stream<Path> paths = Files.walk(directory)
        ) {
            List<Path> entries = paths.sorted(Comparator.reverseOrder()).toList();

            for (Path path : entries) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Упаковывает файлы базы данных в ZIP-архив.
     *
     * @param archivePath путь к ZIP-архиву
     * @return путь к созданному архиву
     * @throws IOException при ошибке чтения или записи файлов
     */
    public Path createBackup(Path archivePath) throws IOException {
        Path archive = validateArchivePath(archivePath);

        validateDatabaseDirectory();

        Files.createDirectories(archive.getParent());

        Path temporaryArchive =
                Files.createTempFile(archive.getParent(), "backup-", ".tmp");

        try {
            writeArchive(temporaryArchive);
            replaceArchive(temporaryArchive, archive);
            return archive;
        } finally {
            Files.deleteIfExists(temporaryArchive);
        }
    }

    /**
     * Восстанавливает файлы базы данных из ZIP-архива.
     *
     * @param archivePath путь к ZIP-архиву
     * @throws IOException при ошибке чтения или записи файлов
     */
    public void restoreBackup(Path archivePath) throws IOException {
        Path archive = validateArchivePath(archivePath);

        if (!Files.isRegularFile(archive)) {
            throw new IOException("Backup archive does not exist: " + archive);
        }

        Files.createDirectories(databaseDirectory.getParent());

        Path workingDirectory = Files.createTempDirectory(databaseDirectory.getParent(), "restore-");

        Path restoredDirectory = workingDirectory.resolve("data");

        Path previousDirectory = workingDirectory.resolve("previous");

        boolean preserveWorkingDirectory = false;

        try {
            Files.createDirectory(restoredDirectory);

            extractArchive(archive, restoredDirectory);

            if (!Files.isRegularFile(restoredDirectory.resolve("catalog.meta"))) {
                throw new IOException("Backup does not contain catalog.meta");
            }

            boolean previousDatabaseExists = Files.exists(databaseDirectory);

            if (previousDatabaseExists) {
                moveDirectory(databaseDirectory, previousDirectory);
            }

            try {
                moveDirectory(restoredDirectory, databaseDirectory);
            } catch (IOException exception) {
                if (previousDatabaseExists) {
                    try {
                        moveDirectory(previousDirectory, databaseDirectory);
                    } catch (IOException rollbackException) {
                        preserveWorkingDirectory = true;
                        exception.addSuppressed(rollbackException);
                        throw new IOException("Restore failed. Previous database is at: " + previousDirectory, exception);
                    }
                }

                throw exception;
            }
        } finally {
            if (!preserveWorkingDirectory) {
                deleteDirectory(workingDirectory);
            }
        }
    }


}
