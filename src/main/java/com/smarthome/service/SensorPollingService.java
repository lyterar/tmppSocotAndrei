package com.smarthome.service;

import com.smarthome.model.device.Device;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.behavioral.DeviceMediator;
import com.smarthome.event.EventBus;
import com.smarthome.event.DeviceEvent;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Каждые 5 секунд проверяет показания всех включённых датчиков.
 * Если датчик сработал — уведомляет медиатора, тот решает что делать.
 */
public class SensorPollingService {

    private static final int POLL_INTERVAL_SECONDS = 5;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sensor-polling");
                t.setDaemon(true); // не блокирует завершение приложения
                return t;
            });

    private ScheduledFuture<?> future;
    private boolean running = false;

    public void start(House house, DeviceMediator mediator, EventBus eventBus) {
        if (running) return;
        running = true;

        future = scheduler.scheduleAtFixedRate(() -> {
            try {
                pollAll(house, mediator, eventBus);
            } catch (Exception e) {
                System.err.println("[SensorPolling] Ошибка опроса: " + e.getMessage());
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        System.out.println("[SensorPolling] Запущен, интервал " + POLL_INTERVAL_SECONDS + "с");
    }

    public void stop() {
        if (future != null) future.cancel(false);
        scheduler.shutdownNow();
        running = false;
        System.out.println("[SensorPolling] Остановлен");
    }

    public boolean isRunning() {
        return running;
    }

    private void pollAll(House house, DeviceMediator mediator, EventBus eventBus) {
        for (Room room : house.getRooms()) {
            for (Device device : room) {
                if (device.getType() == DeviceType.SENSOR && device.isOn()) {
                    checkSensor(device, room, mediator, eventBus);
                }
            }
        }
    }

    private void checkSensor(Device device, Room room,
                              DeviceMediator mediator, EventBus eventBus) {
        Map<String, Object> params = device.getParameters();
        Object rawType  = params.get("sensorType");
        Object rawValue = params.get("value");
        if (rawType == null || rawValue == null) return;

        String sensorType = rawType.toString();
        double value;
        try {
            value = Double.parseDouble(rawValue.toString());
        } catch (NumberFormatException e) {
            return;
        }

        String eventType = detectEvent(sensorType, value);
        if (eventType != null) {
            System.out.printf("[SensorPolling] %s в «%s»: %s=%.2f → %s%n",
                    device.getName(), room.getName(), sensorType, value, eventType);
            mediator.notify(device, room, eventType);
            eventBus.publish(new DeviceEvent(eventType, device.getId(),
                    String.format("%s=%.2f", sensorType, value)));
        }
    }

    // Пороговые значения взяты из типичных норм для жилых помещений
    private String detectEvent(String sensorType, double value) {
        return switch (sensorType) {
            case "motion"      -> value > 0.5  ? "motion_detected"   : null;
            case "temperature" -> value > 27.0 ? "high_temperature"  : null;
            case "humidity"    -> value > 65.0 ? "high_humidity"     : null;
            default            -> null;
        };
    }
}