package com.smarthome.pattern.behavioral;

import com.smarthome.model.device.Device;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.room.Room;

/**
 * Стратегия «Эко режим»:
 * - Все лампы выключены
 * - Термостат на 18°C
 * - Камеры выключены
 */
public class EcoModeStrategy implements AutomationStrategy {

    @Override
    public String getName() {
        return "Эко режим";
    }

    @Override
    public void execute(Room room) {
        // Демонстрация паттерна Iterator: кастомный итератор с фильтром.
        // Вместо общего for (Device d : room) обходим только нужный тип.
        for (Device light : room.filtered(d -> d.getType() == DeviceType.LIGHT)) {
            light.turnOff();
        }
        for (Device thermo : room.filtered(d -> d.getType() == DeviceType.THERMOSTAT)) {
            thermo.turnOn();
            thermo.setParameter("targetTemp", 18.0);
        }
        for (Device cam : room.filtered(d -> d.getType() == DeviceType.CAMERA)) {
            cam.turnOff();
        }
    }
}
