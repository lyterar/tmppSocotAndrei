package com.smarthome.service;

import java.time.LocalTime;
import java.util.Map;

/**
 * Снимок энергопотребления в момент времени.
 * Хранит суммарные Вт по каждой комнате и по дому целиком.
 */
public class EnergySnapshot {

    private final LocalTime time;
    private final Map<String, Double> wattsByRoom; // roomName -> Watts
    private final double totalWatts;

    public EnergySnapshot(LocalTime time, Map<String, Double> wattsByRoom, double totalWatts) {
        this.time        = time;
        this.wattsByRoom = wattsByRoom;
        this.totalWatts  = totalWatts;
    }

    public LocalTime getTime()                    { return time; }
    public Map<String, Double> getWattsByRoom()   { return wattsByRoom; }
    public double getTotalWatts()                 { return totalWatts; }
}