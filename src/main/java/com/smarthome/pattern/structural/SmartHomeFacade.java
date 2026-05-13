package com.smarthome.pattern.structural;

import com.smarthome.AppContext;
import com.smarthome.db.DatabaseService;
import com.smarthome.event.DeviceEvent;
import com.smarthome.event.EventBus;
import com.smarthome.model.device.Device;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;
import com.smarthome.model.room.RoomType;
import com.smarthome.pattern.creational.DeviceFactory;
import com.smarthome.pattern.creational.RoomBuilder;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.service.DeviceEnhancementService;

import java.util.List;

/**
 * ПАТТЕРН: Facade
 *
 * Упрощённый API для всей системы умного дома.
 * После каждой мутации автоматически сохраняет данные в PostgreSQL.
 */
public class SmartHomeFacade {

    private final SmartHomeEngine engine;

    public SmartHomeFacade() {
        this.engine = SmartHomeEngine.getInstance();
    }

    /** Всегда создаётся с актуальным домом — корректно работает после загрузки нового дома */
    private DeviceEnhancementService enhancement() {
        return new DeviceEnhancementService(engine.getHouse(), engine.getDeviceFactory());
    }

    private DatabaseService db() {
        return AppContext.getInstance().getDatabase();
    }

    // === Комнаты ===

    public Room createRoom(String name, RoomType type, double x, double y) {
        Room room = new RoomBuilder(name)
                .type(type)
                .position(x, y)
                .build();
        engine.getHouse().addRoom(room);
        db().saveRoom(room);
        engine.getEventBus().publish(new DeviceEvent("room_added", room.getId()));
        return room;
    }

    public void removeRoom(String roomId) {
        Room room = engine.getHouse().findRoomById(roomId);
        if (room != null) {
            engine.getHouse().removeRoom(room);
            db().deleteRoom(roomId); // CASCADE удалит устройства комнаты
            engine.getEventBus().publish(new DeviceEvent("room_removed", roomId));
        }
    }

    public List<Room> getRooms() {
        return engine.getHouse().getRooms();
    }

    // === Устройства ===

    public Device addDeviceToRoom(String roomId, String deviceName, DeviceType type) {
        Room room = engine.getHouse().findRoomById(roomId);
        if (room == null) return null;

        DeviceFactory factory = engine.getDeviceFactory();
        Device device = factory.createDevice(deviceName, type);
        room.addDevice(device);
        db().saveDevice(device, roomId);
        engine.getEventBus().publish(new DeviceEvent("device_added", device.getId()));
        return device;
    }

    public void removeDeviceFromRoom(String roomId, String deviceId) {
        Room room = engine.getHouse().findRoomById(roomId);
        if (room == null) return;

        Device device = room.findDeviceById(deviceId);
        if (device != null) {
            room.removeDevice(device);
            db().deleteDevice(deviceId);
            engine.getEventBus().publish(new DeviceEvent("device_removed", deviceId));
        }
    }

    public void toggleDevice(String deviceId) {
        Device device = engine.getHouse().findDeviceById(deviceId);
        if (device == null) return;

        if (device.isOn()) device.turnOff(); else device.turnOn();
        db().updateDeviceState(device);
        engine.getEventBus().publish(new DeviceEvent("device_toggled", deviceId));
    }

    public void setDeviceParameter(String deviceId, String key, Object value) {
        Device device = engine.getHouse().findDeviceById(deviceId);
        if (device != null) {
            device.setParameter(key, value);
            db().updateDeviceState(device);
            engine.getEventBus().publish(new DeviceEvent("device_param_changed", deviceId));
        }
    }

    public List<Device> getAllDevices() {
        return engine.getHouse().getAllDevices();
    }

    // === Общее ===

    public House getHouse()         { return engine.getHouse(); }
    public EventBus getEventBus()   { return engine.getEventBus(); }

    public String getHouseSummary() {
        House house = engine.getHouse();
        int rooms   = house.getRooms().size();
        int devices = house.getAllDevices().size();
        long active = house.getAllDevices().stream().filter(Device::isOn).count();
        return String.format("%s: %d комнат, %d устройств (%d активных)",
                house.getName(), rooms, devices, active);
    }

    // === Decorator ===

    public void enableLogging(String deviceId) {
        enhancement().enableLogging(deviceId);
        engine.getEventBus().publish(new DeviceEvent("device_logging_enabled", deviceId));
    }

    // === Composite ===

    public Device createDeviceGroup(String roomId, String groupName, DeviceType type,
                                    List<String> deviceIds) {
        Device group = enhancement().createDeviceGroup(roomId, groupName, type, deviceIds);
        if (group != null) {
            db().saveDevice(group, roomId);
            engine.getEventBus().publish(new DeviceEvent("device_added", group.getId()));
        }
        return group;
    }

    // === Proxy ===

    public Device addDeviceLazy(String roomId, String deviceName, DeviceType type) {
        Device device = enhancement().addDeviceLazy(roomId, deviceName, type);
        if (device != null) {
            db().saveDevice(device, roomId);
            engine.getEventBus().publish(new DeviceEvent("device_added", device.getId()));
        }
        return device;
    }

    // === Mediator ===

    public void triggerDeviceEvent(String deviceId, String eventType) {
        for (Room room : engine.getHouse().getRooms()) {
            Device device = room.findDeviceById(deviceId);
            if (device != null) {
                engine.getMediator().notify(device, room, eventType);
                engine.getEventBus().publish(
                        new DeviceEvent("mediator_triggered:" + eventType, deviceId));
                return;
            }
        }
    }
}
