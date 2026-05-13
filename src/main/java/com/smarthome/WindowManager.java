package com.smarthome;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Управляет вторичными окнами приложения.
 * Каждое окно открывается/скрывается кнопкой-переключателем в главном окне.
 */
public class WindowManager {

    private final Stage mainStage;
    private Stage devicePanelStage;
    private Stage automationStage;
    private Stage logStage;
    private Stage schedulerStage;
    private Stage dashboardStage;

    public WindowManager(Stage mainStage) {
        this.mainStage = mainStage;
    }

    public void toggleDevicePanel() {
        if (devicePanelStage == null) {
            devicePanelStage = createStage("/fxml/device_panel.fxml", "Устройства", 280, 430);
        }
        toggleStage(devicePanelStage, 0);
    }

    public void toggleAutomation() {
        if (automationStage == null) {
            automationStage = createStage("/fxml/automation.fxml", "Автоматизация", 280, 370);
        }
        toggleStage(automationStage, 450);
    }

    public void toggleLog() {
        if (logStage == null) {
            logStage = createStage("/fxml/log.fxml", "Журнал логирования", 420, 500);
        }
        if (logStage != null) toggleStageBelow(logStage);
    }

    public void toggleScheduler() {
        if (schedulerStage == null) {
            schedulerStage = createStage("/fxml/scheduler.fxml", "Расписание автоматизации", 340, 560);
        }
        toggleStage(schedulerStage, 150);
    }

    public void toggleDashboard() {
        if (dashboardStage == null) {
            dashboardStage = createStage("/fxml/dashboard.fxml", "⚡ Энергопотребление", 520, 680);
        }
        toggleStage(dashboardStage, 0);
    }

    private void toggleStage(Stage stage, double yOffset) {
        if (stage.isShowing()) {
            stage.hide();
        } else {
            positionStage(stage, yOffset);
            stage.show();
        }
    }

    private void toggleStageBelow(Stage stage) {
        if (stage.isShowing()) {
            stage.hide();
        } else {
            stage.setX(mainStage.getX());
            stage.setY(mainStage.getY() + mainStage.getHeight() + 10);
            stage.show();
        }
    }

    private Stage createStage(String fxmlPath, String title, int minWidth, int minHeight) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(scene);
            stage.setMinWidth(minWidth);
            stage.setMinHeight(minHeight);
            return stage;
        } catch (Exception e) {
            System.err.println("Не удалось открыть окно " + title + ": " + e.getMessage());
            return null;
        }
    }

    private void positionStage(Stage stage, double yOffset) {
        double x = mainStage.getX() + mainStage.getWidth() + 10;
        double y = mainStage.getY() + yOffset;
        // Если окно не умещается справа — ставим слева
        if (x + stage.getMinWidth() > getScreenWidth()) {
            x = mainStage.getX() - stage.getMinWidth() - 10;
        }
        stage.setX(Math.max(0, x));
        stage.setY(Math.max(0, y));
    }

    private double getScreenWidth() {
        return javafx.stage.Screen.getPrimary().getBounds().getWidth();
    }
}
