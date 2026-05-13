package com.smarthome.controller;

import com.smarthome.AppContext;
import com.smarthome.event.DeviceEvent;
import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.behavioral.ApplyStrategyCommand;
import com.smarthome.pattern.behavioral.AutomationStrategy;
import com.smarthome.pattern.behavioral.CommandHistory;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.SmartHomeFacade;
import com.smarthome.service.AutomationService;
import com.smarthome.service.HouseSaveService;

import java.util.List;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * Контроллер окна автоматизации.
 * Управляет стратегиями автоматизации, историей команд и сохранением/загрузкой.
 */
public class AutomationController {

    @FXML private ComboBox<AutomationStrategy> automationCombo;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private Label statusLabel;

    private final SmartHomeFacade facade = AppContext.getInstance().getFacade();
    private final CommandHistory commandHistory = AppContext.getInstance().getCommandHistory();
    private final AutomationService automationService = AppContext.getInstance().getAutomationService();
    private final HouseSaveService saveService = AppContext.getInstance().getSaveService();
    private Room selectedRoom;

    @FXML
    public void initialize() {
        setupAutomationCombo();

        SmartHomeEngine.getInstance().getEventBus().subscribe("room_selected",
                event -> Platform.runLater(() ->
                        selectedRoom = facade.getHouse().findRoomById(event.getTargetId())));

        // Обновляем кнопки undo/redo при любом событии (включая toggle из другого окна)
        SmartHomeEngine.getInstance().getEventBus().subscribeAll(
                event -> Platform.runLater(this::updateUndoRedo));

        updateUndoRedo();
    }

    private void setupAutomationCombo() {
        automationCombo.getItems().addAll(automationService.getAvailableStrategies());
        automationCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AutomationStrategy s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName());
            }
        });
        automationCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AutomationStrategy s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "Выбрать режим" : s.getName());
            }
        });
    }

    @FXML
    private void onApplyAutomation() {
        AutomationStrategy strategy = automationCombo.getValue();
        if (strategy == null) {
            showWarning("Выберите режим автоматизации");
            return;
        }
        List<Room> targets;
        String statusMsg;
        if (selectedRoom != null) {
            targets = List.of(selectedRoom);
            statusMsg = strategy.getName() + " применён к " + selectedRoom.getName();
        } else {
            targets = facade.getHouse().getRooms();
            statusMsg = strategy.getName() + " применён ко всему дому";
        }
        // Оборачиваем стратегию в команду — теперь поддерживает Undo/Redo
        commandHistory.executeCommand(new ApplyStrategyCommand(strategy, targets));
        updateStatus(statusMsg);
        SmartHomeEngine.getInstance().getEventBus().publish(
                new DeviceEvent("automation_applied", ""));
    }

    @FXML
    private void onUndo() {
        if (commandHistory.undo()) {
            SmartHomeEngine.getInstance().getEventBus().publish(new DeviceEvent("undo", ""));
            updateUndoRedo();
            updateStatus("Отменено");
        }
    }

    @FXML
    private void onRedo() {
        if (commandHistory.redo()) {
            SmartHomeEngine.getInstance().getEventBus().publish(new DeviceEvent("redo", ""));
            updateUndoRedo();
            updateStatus("Повторено");
        }
    }

    @FXML
    private void onSaveHouse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить дом");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        chooser.setInitialFileName("my_house.json");
        File file = chooser.showSaveDialog(automationCombo.getScene().getWindow());
        if (file != null) {
            try {
                saveService.save(facade.getHouse(), file.toPath());
                updateStatus("Сохранено: " + file.getName());
            } catch (Exception e) {
                showWarning("Ошибка сохранения: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onLoadHouse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Загрузить дом");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showOpenDialog(automationCombo.getScene().getWindow());
        if (file != null) {
            try {
                House house = saveService.load(file.toPath());
                SmartHomeEngine.getInstance().setHouse(house);
                SmartHomeEngine.getInstance().getEventBus().publish(
                        new DeviceEvent("house_loaded", ""));
                updateStatus("Загружено: " + file.getName());
            } catch (Exception e) {
                showWarning("Ошибка загрузки: " + e.getMessage());
            }
        }
    }

    private void updateUndoRedo() {
        undoButton.setDisable(!commandHistory.canUndo());
        redoButton.setDisable(!commandHistory.canRedo());
        undoButton.setTooltip(new Tooltip(commandHistory.getUndoDescription()));
        redoButton.setTooltip(new Tooltip(commandHistory.getRedoDescription()));
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private void showWarning(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }
}
