package com.smarthome.controller;

import com.smarthome.AppContext;
import com.smarthome.WindowManager;
import com.smarthome.event.DeviceEvent;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.SmartHomeFacade;
import com.smarthome.view.component.Room3DView;
import com.smarthome.view.component.RoomCanvas;
import com.smarthome.view.dialog.AddRoomDialog;
import com.smarthome.view.dialog.LoadModelDialog;
import com.smarthome.view.loader.ModelRegistry;
import com.smarthome.view.loader.ObjLoader;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * Контроллер главного окна.
 * Отвечает за список комнат и переключение режимов просмотра (2D/3D/FPS).
 * Управление устройствами — в DevicePanelController, автоматизация — в AutomationController.
 */
public class MainController {

    @FXML private Room3DView room3DView;
    @FXML private RoomCanvas roomCanvas;
    @FXML private ListView<Room> roomListView;
    @FXML private Label statusLabel;
    @FXML private Label houseInfoLabel;

    private final SmartHomeFacade facade = new SmartHomeFacade();
    private final ObservableList<Room> roomItems = FXCollections.observableArrayList();
    private Room selectedRoom;
    // Флаг подавляет событие room_selected во время программного обновления списка
    private boolean suppressRoomSelectionEvent = false;
    private WindowManager windowManager;

    @FXML
    public void initialize() {
        setupRoomList();
        // Обновляем главное окно при любых событиях в доме
        facade.getEventBus().subscribeAll(event -> Platform.runLater(this::refreshAll));
        updateStatus("Готово");
        refreshAll();
    }

    // === Настройка UI ===

    private void setupRoomList() {
        roomListView.setItems(roomItems);
        roomListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Room room, boolean empty) {
                super.updateItem(room, empty);
                setText(empty || room == null ? null : room.toString());
            }
        });
        roomListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> onRoomSelected(newVal));
    }

    // === Обработчики комнат ===

    @FXML
    private void onAddRoom() {
        new AddRoomDialog().showAndWait().ifPresent(result -> {
            facade.createRoom(result.name(), result.type(),
                    50 + roomItems.size() * 30, 50 + roomItems.size() * 30);
            updateStatus("Комната добавлена: " + result.name());
        });
    }

    @FXML
    private void onRemoveRoom() {
        Room room = roomListView.getSelectionModel().getSelectedItem();
        if (room == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить комнату \"" + room.getName() + "\"?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                facade.removeRoom(room.getId());
                updateStatus("Комната удалена");
            }
        });
    }

    // === Переключение режимов вида ===

    @FXML
    private void onSwitch2D() {
        if (room3DView.isFpsMode()) room3DView.exitFpsMode();
        room3DView.setVisible(false);
        roomCanvas.setVisible(true);
        roomCanvas.drawHouse(facade.getHouse());
        updateStatus("Режим: 2D план");
    }

    @FXML
    private void onSwitch3D() {
        if (room3DView.isFpsMode()) room3DView.exitFpsMode();
        roomCanvas.setVisible(false);
        room3DView.setVisible(true);
        room3DView.drawHouse(facade.getHouse());
        updateStatus("Режим: 3D — ЛКМ вращение, ПКМ панорама, колёсико zoom, перетащи устройство мышью");
    }

    @FXML
    private void onSwitchFps() {
        Room room = roomListView.getSelectionModel().getSelectedItem();
        if (room == null) {
            showWarning("Выберите комнату для входа в FPS режим");
            return;
        }
        roomCanvas.setVisible(false);
        room3DView.setVisible(true);
        room3DView.drawHouse(facade.getHouse());
        room3DView.enterFpsMode(room);
        updateStatus("FPS: " + room.getName() + " | WASD движение, мышь взгляд, ESC выход");
    }

    // === Управление дополнительными окнами ===

    @FXML
    private void onToggleDevicePanel() {
        getWindowManager().toggleDevicePanel();
    }

    @FXML
    private void onToggleAutomation() {
        getWindowManager().toggleAutomation();
    }

    @FXML
    private void onToggleLog() {
        getWindowManager().toggleLog();
    }

    @FXML
    private void onLoadObjModel() {
        LoadModelDialog dialog = new LoadModelDialog(
                (Stage) room3DView.getScene().getWindow());
        dialog.showAndWait().ifPresent(result -> {
            String url = result.url();
            String modelName = url.substring(url.lastIndexOf('/') + 1);
            updateStatus("Загрузка модели...");

            Task<Group> task = new Task<>() {
                @Override
                protected Group call() throws Exception {
                    return new ObjLoader().loadUrl(url);
                }
            };
            task.setOnSucceeded(e -> {
                Group model = task.getValue();
                room3DView.setVisible(true);
                roomCanvas.setVisible(false);

                if (result.deviceType() != null) {
                    ModelRegistry.getInstance().registerForDeviceType(result.deviceType(), model);
                    AppContext.getInstance().getDatabase()
                            .saveModelEntry("DEVICE", result.deviceType().name(), url);
                    room3DView.drawHouse(facade.getHouse());
                    updateStatus("Модель «" + modelName + "» → все устройства типа «"
                            + result.deviceType().getDisplayName() + "»");
                } else if (result.roomType() != null) {
                    ModelRegistry.getInstance().registerForRoomType(result.roomType(), model);
                    AppContext.getInstance().getDatabase()
                            .saveModelEntry("ROOM", result.roomType().name(), url);
                    room3DView.drawHouse(facade.getHouse());
                    updateStatus("Модель «" + modelName + "» → все комнаты типа «"
                            + result.roomType().getDisplayName() + "»");
                } else {
                    room3DView.addExternalModel(model);
                    updateStatus("Модель загружена в сцену: " + modelName);
                }
            });
            task.setOnFailed(e -> {
                updateStatus("Ошибка загрузки: " + task.getException().getMessage());
                new Alert(Alert.AlertType.ERROR,
                        "Не удалось загрузить модель:\n" + task.getException().getMessage())
                        .showAndWait();
            });
            new Thread(task, "obj-loader").start();
        });
    }

    @FXML
    private void onClearObjModels() {
        ModelRegistry.getInstance().clearAll();
        room3DView.clearAllModels(facade.getHouse());
        AppContext.getInstance().getDatabase().clearModelRegistry();
        updateStatus("Все модели удалены");
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

    // === Внутренние методы ===

    private WindowManager getWindowManager() {
        if (windowManager == null) {
            windowManager = new WindowManager((Stage) room3DView.getScene().getWindow());
        }
        return windowManager;
    }

    private void onRoomSelected(Room room) {
        if (suppressRoomSelectionEvent) return;
        selectedRoom = room;
        if (room != null) room3DView.highlightRoom(room);
        // Уведомляем другие окна через EventBus
        String roomId = room != null ? room.getId() : "";
        SmartHomeEngine.getInstance().getEventBus().publish(
                new DeviceEvent("room_selected", roomId));
    }

    private void refreshAll() {
        suppressRoomSelectionEvent = true;
        try {
            roomItems.setAll(facade.getRooms());
            if (selectedRoom != null) {
                Room updated = facade.getHouse().findRoomById(selectedRoom.getId());
                selectedRoom = updated;
                if (updated != null) {
                    roomListView.getSelectionModel().select(updated);
                    room3DView.highlightRoom(updated);
                } else {
                    roomListView.getSelectionModel().clearSelection();
                }
            }
        } finally {
            suppressRoomSelectionEvent = false;
        }
        if (!room3DView.isFpsMode()) room3DView.drawHouse(facade.getHouse());
        if (roomCanvas.isVisible()) roomCanvas.drawHouse(facade.getHouse());
        houseInfoLabel.setText(facade.getHouseSummary());
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private void showWarning(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }
}
