package com.smarthome.pattern.behavioral;

import com.smarthome.model.device.Device;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * ПАТТЕРН: Iterator (GoF, поведенческий)
 *
 * Последовательный доступ к элементам агрегата без раскрытия
 * его внутреннего представления.
 *
 * Здесь — кастомный итератор с фильтрацией по предикату.
 * В отличие от делегации room.getDevices().iterator(), этот
 * итератор знает правила обхода сам и не зависит от того,
 * в какой коллекции устройства хранятся.
 *
 * Применение:
 *   room.filtered(Device::isOn)   — только включённые
 *   room.filtered(d -> d.getType() == DeviceType.LIGHT)
 *
 * Участники по GoF:
 *   - Aggregate  : Room
 *   - Iterator   : DeviceIterator (этот класс)
 *   - Client     : стратегии автоматизации, сервисы
 */
public class DeviceIterator implements Iterator<Device> {

    private final List<Device> source;
    private final Predicate<Device> filter;
    private int cursor = 0;
    private Device nextCached;
    private boolean nextReady = false;

    public DeviceIterator(List<Device> source, Predicate<Device> filter) {
        this.source = source;
        this.filter = filter != null ? filter : d -> true;
    }

    @Override
    public boolean hasNext() {
        if (nextReady) return true;
        while (cursor < source.size()) {
            Device candidate = source.get(cursor++);
            if (filter.test(candidate)) {
                nextCached = candidate;
                nextReady = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public Device next() {
        if (!hasNext()) {
            throw new NoSuchElementException("Нет устройств, подходящих под фильтр");
        }
        nextReady = false;
        Device result = nextCached;
        nextCached = null;
        return result;
    }
}
