package com.smarthome.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {

    private final Map<String, List<Consumer<DeviceEvent>>> listeners = new ConcurrentHashMap<>();
    private final List<Consumer<DeviceEvent>> globalListeners = new CopyOnWriteArrayList<>();

    public void subscribe(String eventType, Consumer<DeviceEvent> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void subscribeAll(Consumer<DeviceEvent> listener) {
        globalListeners.add(listener);
    }

    public void unsubscribe(String eventType, Consumer<DeviceEvent> listener) {
        List<Consumer<DeviceEvent>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    public void publish(DeviceEvent event) {
        List<Consumer<DeviceEvent>> specific = listeners.get(event.getType());
        if (specific != null) {
            specific.forEach(l -> l.accept(event));
        }
        globalListeners.forEach(l -> l.accept(event));
    }

    public void clear() {
        listeners.clear();
        globalListeners.clear();
    }

}
