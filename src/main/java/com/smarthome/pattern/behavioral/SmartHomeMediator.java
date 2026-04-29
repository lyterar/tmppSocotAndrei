package com.smarthome.pattern.behavioral;

import com.smarthome.model.device.Device;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.room.Room;

/**
 * ПАТТЕРН: Mediator (ConcreteMediator)
 *
 * Конкретный посредник умного дома. Содержит правила,
 * как устройства реагируют друг на друга в пределах комнаты.
 *
 * Правила (можно расширять):
 *   1. Датчик (SENSOR) среагировал → включить все лампы (LIGHT) в комнате.
 *   2. Камера (CAMERA) включена → включить датчик в той же комнате (логика охраны).
 *   3. Замок (LOCK) разблокирован ночью → сигнал в колонку (если есть).
 *
 * Устройства не знают друг о друге — все связи замкнуты на этом классе.
 */
public class SmartHomeMediator implements DeviceMediator {

    @Override
    public void notify(Device sender, Room room, String eventType) {
        if (sender == null || room == null || eventType == null) return;

        switch (eventType) {
            case "motion_detected" -> onMotionDetected(sender, room);
            case "camera_armed"    -> onCameraArmed(sender, room);
            case "lock_unlocked"   -> onLockUnlocked(sender, room);
            default -> { /* неизвестное событие — игнор */ }
        }
    }

    /** Правило 1: датчик движения → включить лампы в комнате */
    private void onMotionDetected(Device sensor, Room room) {
        if (sensor.getType() != DeviceType.SENSOR) return;

        // Используем паттерн Iterator: обход только ламп
        for (Device light : room.filtered(d -> d.getType() == DeviceType.LIGHT)) {
            if (!light.isOn()) light.turnOn();
        }
    }

    /** Правило 2: камера перешла в охрану → активировать датчик */
    private void onCameraArmed(Device camera, Room room) {
        if (camera.getType() != DeviceType.CAMERA) return;

        for (Device sensor : room.filtered(d -> d.getType() == DeviceType.SENSOR)) {
            if (!sensor.isOn()) sensor.turnOn();
        }
    }

    /** Правило 3: замок открыт — колонка проиграет приветствие */
    private void onLockUnlocked(Device lock, Room room) {
        if (lock.getType() != DeviceType.LOCK) return;

        for (Device speaker : room.filtered(d -> d.getType() == DeviceType.SPEAKER)) {
            speaker.turnOn();
            speaker.setParameter("announcement", "Добро пожаловать");
        }
    }
}
