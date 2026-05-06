package com.smarthome.controller;

import com.smarthome.model.device.Device;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.LoggingDeviceDecorator;
import com.smarthome.pattern.structural.SmartHomeFacade;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

/**
 * Контроллер окна журнала логирования.
 * Показывает все записи от устройств с включённым логированием (Decorator).
 * При открытии загружает исторические записи, затем слушает новые в реальном времени.
 */
public class LogController {

    @FXML private TextArea logArea;

    private final SmartHomeFacade facade = new SmartHomeFacade();

    @FXML
    public void initialize() {
        logArea.setEditable(false);
        logArea.setWrapText(true);

        // Загружаем исторические записи из уже существующих декораторов
        for (Device device : facade.getHouse().getAllDevices()) {
            if (device.getDriver() instanceof LoggingDeviceDecorator decorator) {
                for (String entry : decorator.getLog()) {
                    logArea.appendText(entry + "\n");
                }
            }
        }

        if (logArea.getText().isEmpty()) {
            logArea.setText("Нет записей. Включите логирование для устройства в панели \"Устройства\".\n");
        }

        // Подписываемся на новые записи в реальном времени
        SmartHomeEngine.getInstance().getEventBus().subscribe("device_log",
                event -> Platform.runLater(() -> {
                    if (event.getData() != null) {
                        // Убираем placeholder-текст при первой реальной записи
                        if (logArea.getText().startsWith("Нет записей")) {
                            logArea.clear();
                        }
                        logArea.appendText(event.getData().toString() + "\n");
                        // Прокручиваем вниз к последней записи
                        logArea.setScrollTop(Double.MAX_VALUE);
                    }
                }));
    }

    @FXML
    private void onClear() {
        logArea.clear();
    }
}
