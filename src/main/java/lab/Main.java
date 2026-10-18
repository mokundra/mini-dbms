package lab;

import lab.catalog.Catalog;
import lab.catalog.CatalogStorage;
import lab.engine.DatabaseEngine;
import lab.gui.MainWindow;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Содержит точку входа в приложение.
 */
public class Main {

    private static void showError(String message, Exception exception) {
        JOptionPane.showMessageDialog(
                null,
                message + ": " + exception.getMessage(),
                "SQL Console",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private static void closeDatabase(DatabaseEngine databaseEngine, Catalog catalog, Path catalogPath) {
        IOException error = null;
        try {
            CatalogStorage.save(catalogPath, catalog);
        } catch (IOException e) {
            error = e;
        }

        try {
            databaseEngine.close();
        } catch (IOException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }

        if (error != null) {
            showError("Ошибка при закрытии базы данных", error);
        }
    }

    private static void startGui(DatabaseEngine databaseEngine, Catalog catalog, Path catalogPath) {
        try {
            MainWindow window = new MainWindow(databaseEngine);
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    closeDatabase(databaseEngine, catalog, catalogPath);
                }
            });

            window.setVisible(true);

        } catch (RuntimeException e) {
            closeDatabase(databaseEngine, catalog, catalogPath);
            showError("Ошибка запуска интерфейса", e);
        }
    }

    /**
     * Выполняет операцию main.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {

        Path databasePath = Path.of("database");
        Path catalogPath = databasePath.resolve("catalog.meta");

        Catalog catalog;
        DatabaseEngine databaseEngine;

        try {
            catalog = CatalogStorage.load(catalogPath);

            databaseEngine = new DatabaseEngine(databasePath, catalog);

            try {
                databaseEngine.openAll();
            } catch (IOException | RuntimeException exception) {
                try {
                    databaseEngine.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }

                throw exception;
            }

        } catch (IOException | RuntimeException exception) {
            showError("Не удалось открыть базу данных", exception);
            return;
        }

        SwingUtilities.invokeLater(() -> startGui(databaseEngine, catalog, catalogPath));
    }
}