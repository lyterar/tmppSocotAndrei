package com.smarthome.service;

import com.smarthome.pattern.behavioral.AutomationStrategy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Одна запись в расписании: когда, что и где запустить.
 */
public class ScheduleEntry {

    public enum TargetScope { ALL_ROOMS, SELECTED_ROOM }

    private final String id;
    private String label;
    private LocalTime time;
    private Set<DayOfWeek> days;
    private AutomationStrategy strategy;
    private TargetScope scope;
    private String roomId; // null если применяется ко всему дому
    private boolean enabled;

    public ScheduleEntry(String label, LocalTime time, Set<DayOfWeek> days,
                         AutomationStrategy strategy, TargetScope scope, String roomId) {
        this.id       = UUID.randomUUID().toString().substring(0, 8);
        this.label    = label;
        this.time     = time;
        this.days     = EnumSet.copyOf(days);
        this.strategy = strategy;
        this.scope    = scope;
        this.roomId   = roomId;
        this.enabled  = true;
    }

    public boolean shouldFireAt(LocalTime now, DayOfWeek today) {
        if (!enabled) return false;
        if (!days.contains(today)) return false;
        return now.getHour() == time.getHour() && now.getMinute() == time.getMinute();
    }

    @Override
    public String toString() {
        String dayStr = days.size() == 7 ? "Ежедневно"
                : days.size() == 5 && !days.contains(DayOfWeek.SATURDAY) && !days.contains(DayOfWeek.SUNDAY)
                    ? "Пн–Пт" : formatDays();
        String scopeStr = scope == TargetScope.ALL_ROOMS ? "весь дом" : "комната";
        return String.format("%s  %02d:%02d  [%s]  %s  — %s",
                enabled ? "✅" : "⬜",
                time.getHour(), time.getMinute(),
                dayStr, strategy.getName(), scopeStr);
    }

    private String formatDays() {
        StringBuilder sb = new StringBuilder();
        DayOfWeek[] order = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};
        String[] names = {"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
        for (int i = 0; i < order.length; i++) {
            if (days.contains(order[i])) {
                if (sb.length() > 0) sb.append(",");
                sb.append(names[i]);
            }
        }
        return sb.toString();
    }

    // --- Getters / Setters ---
    public String getId()                      { return id; }
    public String getLabel()                   { return label; }
    public void setLabel(String label)         { this.label = label; }
    public LocalTime getTime()                 { return time; }
    public void setTime(LocalTime time)        { this.time = time; }
    public Set<DayOfWeek> getDays()            { return days; }
    public void setDays(Set<DayOfWeek> days)   { this.days = EnumSet.copyOf(days); }
    public AutomationStrategy getStrategy()    { return strategy; }
    public void setStrategy(AutomationStrategy s) { this.strategy = s; }
    public TargetScope getScope()              { return scope; }
    public void setScope(TargetScope scope)    { this.scope = scope; }
    public String getRoomId()                  { return roomId; }
    public void setRoomId(String roomId)       { this.roomId = roomId; }
    public boolean isEnabled()                 { return enabled; }
    public void setEnabled(boolean enabled)    { this.enabled = enabled; }
}