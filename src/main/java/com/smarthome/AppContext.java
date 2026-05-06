package com.smarthome;

import com.smarthome.db.DatabaseService;
import com.smarthome.pattern.behavioral.CommandHistory;
import com.smarthome.pattern.creational.SmartHomeEngine;
import com.smarthome.service.AutomationService;
import com.smarthome.service.HouseSaveService;

/**
 * Разделяемые сервисы приложения.
 * Используется всеми контроллерами окон для доступа к общим ресурсам.
 */
public class AppContext {

    private static AppContext instance;

    private final CommandHistory    commandHistory    = new CommandHistory();
    private final AutomationService automationService = new AutomationService();
    private final HouseSaveService  saveService       = new HouseSaveService(
            SmartHomeEngine.getInstance().getDeviceFactory());
    private final DatabaseService   database          = new DatabaseService();

    private AppContext() {}

    public static AppContext getInstance() {
        if (instance == null) instance = new AppContext();
        return instance;
    }

    public CommandHistory    getCommandHistory()    { return commandHistory; }
    public AutomationService getAutomationService() { return automationService; }
    public HouseSaveService  getSaveService()       { return saveService; }
    public DatabaseService   getDatabase()          { return database; }
}
