package com.smarthome.service;

import com.smarthome.event.DeviceEvent;
import com.smarthome.event.EventBus;
import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;
import com.smarthome.pattern.behavioral.ApplyStrategyCommand;
import com.smarthome.pattern.behavioral.CommandHistory;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Планировщик автоматизации.
 * Каждые 30 секунд проверяет список расписаний и запускает нужные стратегии.
 * Все действия проходят через CommandHistory — их можно отменить.
 */
public class SchedulerService {

    private final List<ScheduleEntry> entries = new ArrayList<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "automation-scheduler");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> future;
    private boolean running = false;

    public void start(House house, CommandHistory commandHistory, EventBus eventBus) {
        if (running) return;
        running = true;

        future = scheduler.scheduleAtFixedRate(() -> {
            try {
                tick(house, commandHistory, eventBus);
            } catch (Exception e) {
                System.err.println("[Scheduler] Ошибка: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);

        System.out.println("[Scheduler] Запущен");
    }

    public void stop() {
        if (future != null) future.cancel(false);
        scheduler.shutdownNow();
        running = false;
        System.out.println("[Scheduler] Остановлен");
    }

    public void addEntry(ScheduleEntry entry) {
        entries.add(entry);
    }

    public void removeEntry(String entryId) {
        entries.removeIf(e -> e.getId().equals(entryId));
    }

    public void toggleEntry(String entryId) {
        entries.stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .ifPresent(e -> e.setEnabled(!e.isEnabled()));
    }

    public List<ScheduleEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isRunning() { return running; }

    private void tick(House house, CommandHistory commandHistory, EventBus eventBus) {
        LocalTime now   = LocalTime.now();
        DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();

        for (ScheduleEntry entry : entries) {
            if (!entry.shouldFireAt(now, today)) continue;

            List<Room> targets = resolveTargets(entry, house);
            if (targets.isEmpty()) continue;

            ApplyStrategyCommand cmd = new ApplyStrategyCommand(entry.getStrategy(), targets);
            commandHistory.executeCommand(cmd);

            String scopeDesc = entry.getScope() == ScheduleEntry.TargetScope.ALL_ROOMS
                    ? "весь дом" : targets.get(0).getName();

            System.out.printf("[Scheduler] Сработало: «%s» → %s (%s)%n",
                    entry.getLabel(), entry.getStrategy().getName(), scopeDesc);

            eventBus.publish(new DeviceEvent(
                    "schedule_fired",
                    entry.getId(),
                    entry.getStrategy().getName() + " → " + scopeDesc));
        }
    }

    private List<Room> resolveTargets(ScheduleEntry entry, House house) {
        if (entry.getScope() == ScheduleEntry.TargetScope.ALL_ROOMS) {
            return house.getRooms();
        }
        Room room = house.findRoomById(entry.getRoomId());
        return room != null ? List.of(room) : List.of();
    }
}