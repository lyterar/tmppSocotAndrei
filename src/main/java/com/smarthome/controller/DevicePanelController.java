package com.smarthome.controller;

import com.smarthome.AppContext;
import com.smarthome.event.DeviceEvent;
import com.smarthome.model.device.Device;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.behavioral.CommandHistory;
import com.smarthome.pattern.behavioral.ToggleDeviceCommand;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.SmartHomeFacade;
import com.smarthome.view.dialog.AddDeviceDialog;
import com.smarthome.view.dialog.CreateGroupDialog;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

/**
 * Контроллер окна управления устройствами.
 * Комната выбирается прямо здесь через ComboBox — выбор синхронизируется
 * с главным окном через событие "room_selected" в EventBus.
 */
public class DevicePanelController {

    @FXML private ComboBox<Room> roomCombo;
    @FXML private ListView<Device> deviceListView;
    @FXML private Label houseInfoLabel;

    private final SmartHomeFacade facade = new SmartHomeFacade();
    private final CommandHistory commandHistory = AppContext.getInstance().getCommandHistory();
    private final ObservableList<Room> roomItems = FXCollections.observableArrayList();
    private final ObservableList<Device> deviceItems = FXCollections.observableArrayList();
    // Подавляет публикацию room_selected при программном обновлении ComboBox
    private boolean suppressComboEvent = false;

    @FXML
    public void initialize() {
        setupRoomCombo();
        setupDeviceList();

        // Синхронизируем выбор комнаты с главным окном
        SmartHomeEngine.getInstance().getEventBus().subscribe("room_selected",
                event -> Platform.runLater(() -> syncRoomSelection(event.getTargetId())));

        // Обновляем данные при любом изменении в доме
        SmartHomeEngine.getInstance().getEventBus().subscribeAll(
                event -> Platform.runLater(this::refresh));

        refresh();
    }

    private void setupRoomCombo() {
        roomCombo.setItems(roomItems);
        roomCombo.setPromptText("Выберите комнату");
        roomCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Room room, boolean empty) {
                super.updateItem(room, empty);
                setText(empty || room == null ? null : room.toString());
            }
        });
        roomCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Room room, boolean empty) {
                super.updateItem(room, empty);
                setText(empty || room == null ? "Выберите комнату" : room.toString());
            }
        });
        // При ручном выборе в ComboBox — уведомляем все окна
        roomCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressComboEvent) {
                String roomId = newVal != null ? newVal.getId() : "";
                SmartHomeEngine.getInstance().getEventBus().publish(
                        new DeviceEvent("room_selected", roomId));
            }
        });
    }

    private void setupDeviceList() {
        deviceListView.setItems(deviceItems);
        deviceListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        deviceListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Device device, boolean empty) {
                super.updateItem(device, empty);
                setText(empty || device == null ? null : device.toString());
            }
        });
    }

    // Вызывается из EventBus когда другое окно изменило выбор комнаты
    private void syncRoomSelection(String roomId) {
        suppressComboEvent = true;
        try {
            Room room = roomId != null && !roomId.isEmpty()
                    ? facade.getHouse().findRoomById(roomId) : null;
            roomCombo.setValue(room);
        } finally {
            suppressComboEvent = false;
        }
        refreshDevices();
    }

    private void refresh() {
        suppressComboEvent = true;
        try {
            Room current = roomCombo.getValue();
            roomItems.setAll(facade.getRooms());
            // Восстанавливаем выбор после setAll (может сбросить)
            if (current != null) {
                Room updated = facade.getHouse().findRoomById(current.getId());
                roomCombo.setValue(updated);
            }
        } finally {
            suppressComboEvent = false;
        }
        refreshDevices();
        houseInfoLabel.setText(facade.getHouseSummary());
    }

    private void refreshDevices() {
        Room selected = roomCombo.getValue();
        if (selected != null) {
            Room updated = facade.getHouse().findRoomById(selected.getId());
            deviceItems.setAll(updated != null ? updated.getDevices() : List.of());
        } else {
            deviceItems.clear();
        }
    }

    @FXML
    private void onAddDevice() {
        Room room = roomCombo.getValue();
        if (room == null) {
            showWarning("Сначала выберите комнату");
            return;
        }
        new AddDeviceDialog().showAndWait().ifPresent(result ->
                facade.addDeviceToRoom(room.getId(), result.name(), result.type()));
    }

    @FXML
    private void onRemoveDevice() {
        Room room = roomCombo.getValue();
        Device device = deviceListView.getSelectionModel().getSelectedItem();
        if (device == null || room == null) return;
        facade.removeDeviceFromRoom(room.getId(), device.getId());
    }

    @FXML
    private void onToggleDevice() {
        Device device = deviceListView.getSelectionModel().getSelectedItem();
        if (device == null) return;
        commandHistory.executeCommand(new ToggleDeviceCommand(device));
        SmartHomeEngine.getInstance().getEventBus().publish(
                new DeviceEvent("device_toggled", device.getId()));
    }

    @FXML
    private void onEnableLogging() {
        Device device = deviceListView.getSelectionModel().getSelectedItem();
        if (device == null) {
            showWarning("Сначала выберите устройство");
            return;
        }
        facade.enableLogging(device.getId());
    }

    @FXML
    private void onCreateGroup() {
        Room room = roomCombo.getValue();
        if (room == null) {
            showWarning("Сначала выберите комнату");
            return;
        }
        Room updated = facade.getHouse().findRoomById(room.getId());
        if (updated == null || updated.getDevices().isEmpty()) {
            showWarning("В комнате нет устройств для объединения");
            return;
        }
        new CreateGroupDialog(updated.getDevices()).showAndWait().ifPresent(result ->
                facade.createDeviceGroup(room.getId(),
                        result.groupName(), result.groupType(), result.deviceIds()));
    }

    private void showWarning(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }
}
