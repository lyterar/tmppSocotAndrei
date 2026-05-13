package com.smarthome.service;

import com.smarthome.event.DeviceEvent;
import com.smarthome.event.EventBus;
import com.smarthome.model.device.Device;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Считает энергопотребление по всем включённым устройствам каждые 2 секунды.
 * Результат кладёт в историю и рассылает через EventBus.
 */
public class EnergyService {

    // Мощность устройств в ваттах (примерные реальные значения)
    private static final Map<DeviceType, Double> BASE_WATTS = new EnumMap<>(DeviceType.class);
    static {
        BASE_WATTS.put(DeviceType.LIGHT,      10.0);
        BASE_WATTS.put(DeviceType.THERMOSTAT, 1500.0);
        BASE_WATTS.put(DeviceType.CAMERA,     15.0);
        BASE_WATTS.put(DeviceType.SPEAKER,    30.0);
        BASE_WATTS.put(DeviceType.LOCK,       5.0);
        BASE_WATTS.put(DeviceType.SENSOR,     2.0);
    }

    private static final int HISTORY_SIZE    = 60; // 60 точек × 2 сек = 2 минуты на графике
    private static final int POLL_INTERVAL_S = 2;

    private final Deque<EnergySnapshot> history = new ArrayDeque<>();
    private final Random random = new Random();

    private double peakWatts  = 0;
    private double totalKwh   = 0;
    private long   lastTickMs = System.currentTimeMillis();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "energy-service");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> future;
    private boolean running = false;

    public void start(House house, EventBus eventBus) {
        if (running) return;
        running = true;
        future = scheduler.scheduleAtFixedRate(() -> {
            try {
                tick(house, eventBus);
            } catch (Exception e) {
                System.err.println("[EnergyService] Ошибка: " + e.getMessage());
            }
        }, 0, POLL_INTERVAL_S, TimeUnit.SECONDS);
        System.out.println("[EnergyService] Запущен");
    }

    public void stop() {
        if (future != null) future.cancel(false);
        scheduler.shutdownNow();
        running = false;
    }

    public boolean isRunning() { return running; }

    /** Последние N снимков для LineChart */
    public List<EnergySnapshot> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /** Последний снимок */
    public EnergySnapshot getLatest() {
        synchronized (history) {
            return history.isEmpty() ? null : ((ArrayDeque<EnergySnapshot>) history).peekLast();
        }
    }

    public double getPeakWatts()  { return peakWatts; }
    public double getTotalKwh()   { return totalKwh; }

    // --- Внутренняя логика ---

    private void tick(House house, EventBus eventBus) {
        Map<String, Double> byRoom = new LinkedHashMap<>();
        double total = 0;

        for (Room room : house.getRooms()) {
            double roomWatts = 0;
            for (Device device : room) {
                if (!device.isOn()) continue;
                double w = calcWatts(device);
                roomWatts += w;
            }
            byRoom.put(room.getName(), roomWatts);
            total += roomWatts;
        }

        if (total > peakWatts) peakWatts = total;

        long now = System.currentTimeMillis();
        double elapsedHours = (now - lastTickMs) / 3_600_000.0;
        totalKwh += (total / 1000.0) * elapsedHours;
        lastTickMs = now;

        EnergySnapshot snapshot = new EnergySnapshot(LocalTime.now(), byRoom, total);

        synchronized (history) {
            history.addLast(snapshot);
            while (history.size() > HISTORY_SIZE) history.removeFirst();
        }

        eventBus.publish(new DeviceEvent("energy_tick", "", snapshot));
    }

    private double calcWatts(Device device) {
        double base = BASE_WATTS.getOrDefault(device.getType(), 10.0);

        switch (device.getType()) {
            case LIGHT -> {
                Object brightness = device.getParameters().get("brightness");
                if (brightness != null) {
                    double b = Double.parseDouble(brightness.toString());
                    base = base * (b / 100.0);
                    if (base < 1.0) base = 1.0;
                }
            }
            case THERMOSTAT -> {
                // При низкой температуре термостат почти не греет
                Object target = device.getParameters().get("targetTemp");
                if (target != null && Double.parseDouble(target.toString()) <= 18.0) {
                    base = base * 0.3;
                }
            }
            default -> {}
        }

        // Небольшой разброс чтобы график не был плоским
        double jitter = 0.85 + random.nextDouble() * 0.30;
        return base * jitter;
    }
}