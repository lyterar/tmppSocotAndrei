package com.smarthome.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventBus {

    private final Map<String, List<Consumer<DeviceEvent>>> listeners = new HashMap<>();
    private final List<Consumer<DeviceEvent>> globalListeners = new ArrayList<>();

    public void subscribe(String eventType, Consumer<DeviceEvent> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    public void subscribeAll(Consumer<DeviceEvent> listener) {
        globalListeners.add(listener);
    }

    public void unsubscribe(String eventType, Consumer<DeviceEvent> listener) {
        List<Consumer<DeviceEvent>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void publish(DeviceEvent event) {
        List<Consumer<DeviceEvent>> specific = listeners.get(event.getType());
        if (specific != null) {
            for (Consumer<DeviceEvent> listener : specific) {
                listener.accept(event);
            }
        }
        for (Consumer<DeviceEvent> listener : globalListeners) {
            listener.accept(event);
        }
    }

    public void clear() {
        listeners.clear();
        globalListeners.clear();
    }
}
