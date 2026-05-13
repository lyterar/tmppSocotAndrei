package com.smarthome;

import com.smarthome.db.DatabaseService;
import com.smarthome.mcp.McpServer;
import com.smarthome.mcp.McpTools;
import com.smarthome.mcp.McpPatternReferenceTools;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.room.RoomType;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.SmartHomeFacade;
import com.smarthome.view.loader.ModelRegistry;
import com.smarthome.view.loader.ObjLoader;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Точка входа приложения Smart Home Constructor.
 *
 * Порядок старта:
 * 1. Подключение к PostgreSQL → загрузка комнат/устройств/моделей
 * 2. JavaFX UI
 * 3. MCP сервер (порт 3001)
 *
 * При недоступности БД — graceful degradation: приложение запускается
 * без персистентности, данные теряются при перезапуске.
 */
public class SmartHomeApp extends Application {

    private McpServer mcpServer;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Подключаемся к БД и загружаем сохранённые данные
        initDatabase();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 700);
        primaryStage.setTitle("Smart Home Constructor");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        primaryStage.show();

        startMcpServer();
    }

    private void initDatabase() {
        DatabaseService db = AppContext.getInstance().getDatabase();
        try {
            db.connect();
            // Загружаем комнаты и устройства в движок
            db.loadInto(SmartHomeEngine.getInstance().getHouse(),
                        SmartHomeEngine.getInstance().getDeviceFactory());
            // Загружаем реестр 3D-моделей в фоне — OBJ-файлы читать долго
            loadModelRegistryAsync(db);
        } catch (Exception e) {
            System.err.println("[DB] Не удалось подключиться — работаем без БД: " + e.getMessage());
        }
        // Запускаем фоновые сервисы (опрос датчиков и др.) после загрузки данных
        AppContext.getInstance().startServices();
    }

    /**
     * Загружает URL-адреса 3D-моделей из БД и в фоновом потоке
     * перезагружает OBJ-файлы в ModelRegistry.
     */
    private void loadModelRegistryAsync(DatabaseService db) {
        Map<String, String> entries;
        try {
            entries = db.loadModelRegistry();
        } catch (Exception e) {
            System.err.println("[DB] Не удалось загрузить реестр моделей: " + e.getMessage());
            return;
        }
        if (entries.isEmpty()) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                ObjLoader loader = new ObjLoader();
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    String[] parts = entry.getKey().split(":", 2);
                    if (parts.length != 2) continue;
                    String category = parts[0];
                    String typeName = parts[1];
                    String url      = entry.getValue();
                    try {
                        Group model = loader.loadUrl(url);
                        if ("DEVICE".equals(category)) {
                            ModelRegistry.getInstance().registerForDeviceType(
                                    DeviceType.valueOf(typeName), model);
                        } else if ("ROOM".equals(category)) {
                            ModelRegistry.getInstance().registerForRoomType(
                                    RoomType.valueOf(typeName), model);
                        }
                        System.out.println("[DB] Модель восстановлена: " + category + ":" + typeName);
                    } catch (Exception e) {
                        System.err.println("[DB] Не удалось загрузить модель (" + url + "): "
                                + e.getMessage());
                    }
                }
                return null;
            }
        };
        new Thread(task, "db-model-loader").start();
    }

    private void startMcpServer() {
        try {
            SmartHomeFacade facade = new SmartHomeFacade();
            mcpServer = new McpServer(3001);
            new McpTools(facade).registerAll(mcpServer);
            new McpPatternReferenceTools().registerAll(mcpServer);
            mcpServer.start();
            System.out.println("[MCP] Зарегистрировано инструментов: " + mcpServer.getTools().size());
        } catch (Exception e) {
            System.err.println("Не удалось запустить MCP сервер: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (mcpServer != null) mcpServer.stop();
        AppContext.getInstance().stopServices();
        AppContext.getInstance().getDatabase().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
