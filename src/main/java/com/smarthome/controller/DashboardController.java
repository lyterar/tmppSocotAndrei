package com.smarthome.controller;

import com.smarthome.AppContext;
import com.smarthome.model.device.Device;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.service.EnergyService;
import com.smarthome.service.EnergySnapshot;
import com.smarthome.view.component.EnergyBarChart3D;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Контроллер дашборда.
 * Данные получает от EnergyService через EventBus каждые 2 секунды.
 * Часы и счётчик кВт·ч обновляются отдельным таймером раз в секунду.
 */
public class DashboardController {

    @FXML private Label            clockLabel;
    @FXML private Label            currentWattsLabel;
    @FXML private Label            peakWattsLabel;
    @FXML private Label            kwhLabel;
    @FXML private Label            activeDevicesLabel;
    @FXML private LineChart<String, Number> lineChart;
    @FXML private CategoryAxis     timeAxis;
    @FXML private NumberAxis       wattsAxis;
    @FXML private EnergyBarChart3D barChart3D;
    @FXML private ListView<String> topDevicesList;

    private final EnergyService energyService = AppContext.getInstance().getEnergyService();

    private XYChart.Series<String, Number> lineSeries;
    private final ObservableList<String> topItems = FXCollections.observableArrayList();
    private Timeline clockTimeline;

    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int               MAX_LINE_POINTS = 60;

    @FXML
    public void initialize() {
        setupLineChart();
        topDevicesList.setItems(topItems);

        SmartHomeEngine.getInstance().getEventBus().subscribe("energy_tick",
                event -> Platform.runLater(() -> {
                    if (event.getData() instanceof EnergySnapshot snap) {
                        onEnergyTick(snap);
                    }
                }));

        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        // Если дашборд открыли не сразу — догружаем накопленную историю
        List<EnergySnapshot> history = energyService.getHistory();
        if (!history.isEmpty()) {
            history.forEach(this::onEnergyTick);
        }
    }

    private void onEnergyTick(EnergySnapshot snap) {
        currentWattsLabel.setText(formatWatts(snap.getTotalWatts()));
        peakWattsLabel.setText(formatWatts(energyService.getPeakWatts()));
        kwhLabel.setText(String.format("%.3f кВт·ч", energyService.getTotalKwh()));
        updateActiveDevices();

        String timeLabel = snap.getTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lineSeries.getData().add(new XYChart.Data<>(timeLabel, snap.getTotalWatts()));
        while (lineSeries.getData().size() > MAX_LINE_POINTS) {
            lineSeries.getData().remove(0);
        }

        barChart3D.update(snap.getWattsByRoom());
        updateTopDevices();
    }

    private void setupLineChart() {
        lineSeries = new XYChart.Series<>();
        lineSeries.setName("Вт");
        lineChart.getData().add(lineSeries);
        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(false);
        lineChart.setLegendVisible(false);
    }

    private void updateClock() {
        clockLabel.setText(LocalTime.now().format(TIME_FMT));
        kwhLabel.setText(String.format("%.4f кВт·ч", energyService.getTotalKwh()));
    }

    private void updateActiveDevices() {
        long active = SmartHomeEngine.getInstance().getHouse()
                .getAllDevices().stream().filter(Device::isOn).count();
        activeDevicesLabel.setText(String.valueOf(active));
    }

    private void updateTopDevices() {
        // Собираем все включённые устройства с их мощностью
        List<String> top = new ArrayList<>();
        for (Room room : SmartHomeEngine.getInstance().getHouse().getRooms()) {
            for (Device device : room) {
                if (!device.isOn()) continue;
                // Берём данные из последнего снимка через EnergyService
                double watts = estimateWatts(device);
                String icon = device.getType().getIcon();
                top.add(String.format("%-2s  %-18s  %s  — %s",
                        icon, device.getName(),
                        formatWatts(watts), room.getName()));
            }
        }
        // Сортируем по убыванию мощности (простая сортировка по числу в строке)
        top.sort((a, b) -> {
            double wa = extractWattsFromLabel(a);
            double wb = extractWattsFromLabel(b);
            return Double.compare(wb, wa);
        });

        topItems.setAll(top.stream().limit(5).collect(Collectors.toList()));
    }

    private double estimateWatts(Device device) {
        return switch (device.getType()) {
            case LIGHT -> {
                Object b = device.getParameters().get("brightness");
                yield b != null ? 10.0 * Double.parseDouble(b.toString()) / 100.0 : 10.0;
            }
            case THERMOSTAT -> 1500.0;
            case CAMERA     -> 15.0;
            case SPEAKER    -> 30.0;
            case LOCK       -> 5.0;
            case SENSOR     -> 2.0;
        };
    }

    private double extractWattsFromLabel(String label) {
        try {
            // ищем число перед W или кВт
            int idx = label.indexOf(" W");
            if (idx < 0) idx = label.indexOf(" кВт");
            if (idx < 0) return 0;
            String[] parts = label.substring(0, idx).trim().split(" ");
            return Double.parseDouble(parts[parts.length - 1].replace(",", "."));
        } catch (Exception e) { return 0; }
    }

    private String formatWatts(double watts) {
        if (watts >= 1000) return String.format("%.2f кВт", watts / 1000);
        return String.format("%.0f W", watts);
    }
}