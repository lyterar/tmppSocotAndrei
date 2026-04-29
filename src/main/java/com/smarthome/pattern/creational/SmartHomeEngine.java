package com.smarthome.pattern.creational;

import com.smarthome.model.house.House;
import com.smarthome.event.EventBus;
import com.smarthome.pattern.behavioral.DeviceMediator;
import com.smarthome.pattern.behavioral.SmartHomeMediator;

/**
 * ПАТТЕРН: Singleton
 *
 * Единственный экземпляр движка умного дома.
 * Хранит текущий дом, фабрику, шину событий и посредника.
 */
public class SmartHomeEngine {

    private static SmartHomeEngine instance;

    private House house;
    private final DeviceFactory deviceFactory;
    private final EventBus eventBus;
    private final DeviceMediator mediator;

    private SmartHomeEngine() {
        this.house = new House("Мой дом");
        this.deviceFactory = new DeviceFactory();
        this.eventBus = new EventBus();
        this.mediator = new SmartHomeMediator();
    }

    public static SmartHomeEngine getInstance() {
        if (instance == null) {
            instance = new SmartHomeEngine();
        }
        return instance;
    }

    /** Для тестов — сброс синглтона */
    public static void reset() {
        instance = null;
    }

    public House getHouse() { return house; }
    public void setHouse(House house) { this.house = house; }

    public DeviceFactory getDeviceFactory() { return deviceFactory; }
    public EventBus getEventBus() { return eventBus; }
    public DeviceMediator getMediator() { return mediator; }
}
