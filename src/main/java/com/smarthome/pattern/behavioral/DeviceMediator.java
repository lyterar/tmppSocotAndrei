package com.smarthome.pattern.behavioral;

import com.smarthome.model.device.Device;
import com.smarthome.model.room.Room;

/**
 * ПАТТЕРН: Mediator (GoF, поведенческий)
 *
 * Инкапсулирует правила взаимодействия между объектами,
 * чтобы они не ссылались друг на друга напрямую.
 *
 * В умном доме устройства «разговаривают» не между собой,
 * а через Mediator: датчик движения не знает о лампах,
 * он просто уведомляет посредника о событии,
 * а тот решает — кого включить.
 *
 * Участники по GoF:
 *   - Mediator         : DeviceMediator (этот интерфейс)
 *   - ConcreteMediator : SmartHomeMediator
 *   - Colleague        : Device (не ссылается на других напрямую)
 */
public interface DeviceMediator {

    /**
     * Устройство-коллега уведомляет посредника о своём событии.
     *
     * @param sender    устройство-источник события
     * @param room      комната, в которой произошло событие
     * @param eventType строковый тип события, например "turned_on", "motion_detected"
     */
    void notify(Device sender, Room room, String eventType);
}
