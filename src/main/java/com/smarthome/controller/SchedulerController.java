package com.smarthome.controller;

import com.smarthome.AppContext;
import com.smarthome.event.DeviceEvent;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.behavioral.AutomationStrategy;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.SmartHomeFacade;
import com.smarthome.service.ScheduleEntry;
import com.smarthome.service.ScheduleEntry.TargetScope;
import com.smarthome.service.SchedulerService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Контроллер окна планировщика автоматизации.
 * Позволяет создавать расписания: время + дни недели + стратегия + область.
 */
public class SchedulerController {

    @FXML private ListView<ScheduleEntry> scheduleListView;
    @FXML private Spinner<Integer>        hourSpinner;
    @FXML private Spinner<Integer>        minuteSpinner;
    @FXML private ComboBox<AutomationStrategy> strategyCombo;
    @FXML private ComboBox<TargetScope>   scopeCombo;
    @FXML private ComboBox<Room>          roomCombo;
    @FXML private CheckBox cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbSun;
    @FXML private Label statusLabel;

    private final SmartHomeFacade   facade    = AppContext.getInstance().getFacade();
    private final SchedulerService  scheduler = AppContext.getInstance().getSchedulerService();

    private final ObservableList<ScheduleEntry> entryItems  = FXCollections.observableArrayList();
    private final ObservableList<Room>          roomItems   = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupSpinners();
        setupStrategyCombo();
        setupScopeCombo();
        setupRoomCombo();
        setupScheduleList();

        // Обновляем список комнат при изменениях в доме
        SmartHomeEngine.getInstance().getEventBus().subscribeAll(
                e -> Platform.runLater(this::refreshRooms));

        // Обновляем список когда сработало расписание
        SmartHomeEngine.getInstance().getEventBus().subscribe("schedule_fired",
                e -> Platform.runLater(() -> {
                    refreshList();
                    updateStatus("⚡ Сработало: " + e.getData());
                }));

        refreshList();
        refreshRooms();
    }

    // --- Настройка UI ---

    private void setupSpinners() {
        hourSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, LocalTime.now().getHour()));
        minuteSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59,
                        (LocalTime.now().getMinute() / 5 + 1) * 5 % 60));
        hourSpinner.setEditable(true);
        minuteSpinner.setEditable(true);
    }

    private void setupStrategyCombo() {
        strategyCombo.getItems().addAll(
                AppContext.getInstance().getAutomationService().getAvailableStrategies());
        strategyCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(AutomationStrategy s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName());
            }
        });
        strategyCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(AutomationStrategy s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "Выбрать режим" : s.getName());
            }
        });
        if (!strategyCombo.getItems().isEmpty())
            strategyCombo.setValue(strategyCombo.getItems().get(0));
    }

    private void setupScopeCombo() {
        scopeCombo.setItems(FXCollections.observableArrayList(TargetScope.values()));
        scopeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(TargetScope s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null
                        : s == TargetScope.ALL_ROOMS ? "Весь дом" : "Выбранная комната");
            }
        });
        scopeCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(TargetScope s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "Область"
                        : s == TargetScope.ALL_ROOMS ? "Весь дом" : "Выбранная комната");
            }
        });
        scopeCombo.setValue(TargetScope.ALL_ROOMS);
        // Показываем/скрываем roomCombo в зависимости от выбора
        scopeCombo.valueProperty().addListener((obs, o, n) ->
                roomCombo.setDisable(n != TargetScope.SELECTED_ROOM));
        roomCombo.setDisable(true);
    }

    private void setupRoomCombo() {
        roomCombo.setItems(roomItems);
        roomCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Room r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.toString());
            }
        });
        roomCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Room r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? "(не выбрана)" : r.toString());
            }
        });
    }

    private void setupScheduleList() {
        scheduleListView.setItems(entryItems);
        scheduleListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ScheduleEntry e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : e.toString());
            }
        });
    }

    // --- Обработчики кнопок ---

    @FXML
    private void onAddEntry() {
        AutomationStrategy strategy = strategyCombo.getValue();
        if (strategy == null) { showWarning("Выберите режим автоматизации"); return; }

        Set<DayOfWeek> days = getSelectedDays();
        if (days.isEmpty()) { showWarning("Выберите хотя бы один день недели"); return; }

        TargetScope scope = scopeCombo.getValue();
        String roomId = null;
        if (scope == TargetScope.SELECTED_ROOM) {
            Room room = roomCombo.getValue();
            if (room == null) { showWarning("Выберите комнату"); return; }
            roomId = room.getId();
        }

        int hour   = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();
        LocalTime time = LocalTime.of(hour, minute);

        String label = strategy.getName() + " " +
                String.format("%02d:%02d", hour, minute);

        ScheduleEntry entry = new ScheduleEntry(label, time, days, strategy, scope, roomId);
        scheduler.addEntry(entry);
        refreshList();
        updateStatus("✅ Добавлено: " + label);
    }

    @FXML
    private void onRemoveEntry() {
        ScheduleEntry selected = scheduleListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        scheduler.removeEntry(selected.getId());
        refreshList();
        updateStatus("Удалено: " + selected.getLabel());
    }

    @FXML
    private void onToggleEntry() {
        ScheduleEntry selected = scheduleListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        scheduler.toggleEntry(selected.getId());
        refreshList();
        updateStatus(selected.isEnabled() ? "⬜ Отключено" : "✅ Включено");
    }

    @FXML
    private void onSelectAllDays() {
        cbMon.setSelected(true); cbTue.setSelected(true); cbWed.setSelected(true);
        cbThu.setSelected(true); cbFri.setSelected(true);
        cbSat.setSelected(true); cbSun.setSelected(true);
    }

    @FXML
    private void onSelectWeekdays() {
        cbMon.setSelected(true); cbTue.setSelected(true); cbWed.setSelected(true);
        cbThu.setSelected(true); cbFri.setSelected(true);
        cbSat.setSelected(false); cbSun.setSelected(false);
    }

    // --- Внутренние методы ---

    private Set<DayOfWeek> getSelectedDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (cbMon.isSelected()) days.add(DayOfWeek.MONDAY);
        if (cbTue.isSelected()) days.add(DayOfWeek.TUESDAY);
        if (cbWed.isSelected()) days.add(DayOfWeek.WEDNESDAY);
        if (cbThu.isSelected()) days.add(DayOfWeek.THURSDAY);
        if (cbFri.isSelected()) days.add(DayOfWeek.FRIDAY);
        if (cbSat.isSelected()) days.add(DayOfWeek.SATURDAY);
        if (cbSun.isSelected()) days.add(DayOfWeek.SUNDAY);
        return days;
    }

    private void refreshList() {
        entryItems.setAll(scheduler.getEntries());
    }

    private void refreshRooms() {
        roomItems.setAll(facade.getRooms());
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private void showWarning(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }
}