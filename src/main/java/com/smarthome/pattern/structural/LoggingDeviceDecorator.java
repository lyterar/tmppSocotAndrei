package com.smarthome.pattern.structural;

import com.smarthome.event.DeviceEvent;
import com.smarthome.model.device.DeviceDriver;
import com.smarthome.model.device.DeviceType;
import com.smarthome.pattern.creational.SmartHomeEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ПАТТЕРН: Decorator
 *
 * Оборачивает любой DeviceDriver, добавляя логирование всех действий.
 * Каждая запись публикуется в EventBus (тип "device_log") — окно журнала
 * подписывается на этот тип и отображает записи в реальном времени.
 */
public class LoggingDeviceDecorator implements DeviceDriver {

    private final DeviceDriver wrapped;
    private final String deviceName;
    private final List<String> log = new ArrayList<>();
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public LoggingDeviceDecorator(DeviceDriver wrapped, String deviceName) {
        this.wrapped = wrapped;
        this.deviceName = deviceName;
    }

    @Override
    public void turnOn() {
        addLog("включено");
        wrapped.turnOn();
    }

    @Override
    public void turnOff() {
        addLog("выключено");
        wrapped.turnOff();
    }

    @Override
    public boolean isOn() {
        return wrapped.isOn();
    }

    @Override
    public Map<String, Object> getParameters() {
        return wrapped.getParameters();
    }

    @Override
    public void setParameter(String key, Object value) {
        addLog("параметр " + key + " = " + value);
        wrapped.setParameter(key, value);
    }

    @Override
    public boolean isConnected() {
        return wrapped.isConnected();
    }

    @Override
    public DeviceType getDeviceType() {
        return wrapped.getDeviceType();
    }

    public List<String> getLog() {
        return new ArrayList<>(log);
    }

    public List<String> getRecentLog(int count) {
        int start = Math.max(0, log.size() - count);
        return new ArrayList<>(log.subList(start, log.size()));
    }

    private void addLog(String action) {
        String time = LocalDateTime.now().format(FORMAT);
        String entry = "[" + time + "] " + deviceName + " (" + getDeviceType().getDisplayName() + "): " + action;
        log.add(entry);
        System.out.println(entry);
        // Публикуем запись для окна журнала
        SmartHomeEngine.getInstance().getEventBus().publish(
                new DeviceEvent("device_log", deviceName, entry));
    }
}
