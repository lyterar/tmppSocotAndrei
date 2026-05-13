package com.smarthome;

import com.smarthome.db.DatabaseService;
import com.smarthome.pattern.behavioral.CommandHistory;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.pattern.structural.SmartHomeFacade;
import com.smarthome.service.AutomationService;
import com.smarthome.service.HouseSaveService;
import com.smarthome.service.EnergyService;
import com.smarthome.service.SchedulerService;
import com.smarthome.service.SensorPollingService;

/**
 * Единая точка доступа к сервисам приложения.
 * Все контроллеры берут сервисы отсюда, а не создают их сами.
 */
public class AppContext {

    private static volatile AppContext instance;

    private final CommandHistory       commandHistory       = new CommandHistory();
    private final AutomationService    automationService    = new AutomationService();
    private final HouseSaveService     saveService          = new HouseSaveService(
            SmartHomeEngine.getInstance().getDeviceFactory());
    private final DatabaseService      database             = new DatabaseService();
    private final SensorPollingService sensorPollingService = new SensorPollingService();
    private final SchedulerService     schedulerService     = new SchedulerService();
    private final EnergyService        energyService        = new EnergyService();
    private final SmartHomeFacade      facade               = new SmartHomeFacade();

    private AppContext() {}

    public static AppContext getInstance() {
        if (instance == null) {
            synchronized (AppContext.class) {
                if (instance == null) {
                    instance = new AppContext();
                }
            }
        }
        return instance;
    }

    public CommandHistory       getCommandHistory()       { return commandHistory; }
    public AutomationService    getAutomationService()    { return automationService; }
    public HouseSaveService     getSaveService()          { return saveService; }
    public DatabaseService      getDatabase()             { return database; }
    public SensorPollingService getSensorPollingService() { return sensorPollingService; }
    public SchedulerService     getSchedulerService()     { return schedulerService; }
    public EnergyService        getEnergyService()        { return energyService; }
    public SmartHomeFacade      getFacade()               { return facade; }

    // Вызывается после загрузки данных из БД
    public void startServices() {
        SmartHomeEngine engine = SmartHomeEngine.getInstance();
        sensorPollingService.start(
                engine.getHouse(),
                engine.getMediator(),
                engine.getEventBus());
        schedulerService.start(
                engine.getHouse(),
                commandHistory,
                engine.getEventBus());
        energyService.start(
                engine.getHouse(),
                engine.getEventBus());
    }

    // Вызывается при закрытии приложения
    public void stopServices() {
        sensorPollingService.stop();
        schedulerService.stop();
        energyService.stop();
    }
}
