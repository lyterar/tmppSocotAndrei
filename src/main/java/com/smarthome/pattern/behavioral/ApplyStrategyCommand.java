package com.smarthome.pattern.behavioral;

import com.smarthome.model.device.Device;
import com.smarthome.model.room.Room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Применяет стратегию автоматизации к комнатам с возможностью отмены.
 * Перед запуском сохраняет состояние всех устройств — чтобы откатить если нужно.
 */
public class ApplyStrategyCommand implements DeviceCommand {

    private final AutomationStrategy strategy;
    private final List<Room> targetRooms;

    private final Map<String, DeviceSnapshot> snapshot = new HashMap<>();

    public ApplyStrategyCommand(AutomationStrategy strategy, List<Room> targetRooms) {
        this.strategy = strategy;
        this.targetRooms = List.copyOf(targetRooms);
    }

    @Override
    public void execute() {
        snapshot.clear();
        for (Room room : targetRooms) {
            for (Device device : room) {
                snapshot.put(device.getId(), DeviceSnapshot.of(device));
            }
        }
        for (Room room : targetRooms) {
            strategy.execute(room);
        }
    }

    @Override
    public void undo() {
        for (Room room : targetRooms) {
            for (Device device : room) {
                DeviceSnapshot saved = snapshot.get(device.getId());
                if (saved == null) continue;
                if (saved.isOn()) device.turnOn(); else device.turnOff();
                saved.parameters().forEach(device::setParameter);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Автоматизация: " + strategy.getName();
    }

    private record DeviceSnapshot(boolean isOn, Map<String, Object> parameters) {
        static DeviceSnapshot of(Device device) {
            return new DeviceSnapshot(
                    device.isOn(),
                    new HashMap<>(device.getParameters()));
        }
    }
}