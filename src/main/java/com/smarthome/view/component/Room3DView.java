package com.smarthome.view.component;

import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Основная 3D сцена приложения.
 *
 * Два режима:
 *  - Orbit (по умолчанию): вид сверху, вращение мышью вокруг центра, zoom колёсиком.
 *    Устройства можно перетаскивать мышью.
 *  - FPS: камера внутри комнаты, WASD + мышь, управляет FpsCameraController.
 */
public class Room3DView extends Pane {

    private static final double SPACING_X = 280;
    private static final double SPACING_Z = 220;
    private static final int MAX_COLS = 3;

    // --- Orbit-режим ---
    private final Group orbitCameraGroup = new Group();
    private final Rotate orbitRotateX = new Rotate(-25, Rotate.X_AXIS);
    private final Rotate orbitRotateY = new Rotate(0, Rotate.Y_AXIS);

    // --- Общая камера ---
    private final PerspectiveCamera camera = new PerspectiveCamera(true);

    // --- Корень сцены ---
    private final Group root3D = new Group();
    private SubScene subScene;

    // --- FPS-контроллер ---
    private FpsCameraController fpsController;
    private boolean fpsMode = false;

    // --- Состояние orbit-мыши ---
    private double orbitLastX;
    private double orbitLastY;

    // roomId -> 3D модель комнаты (боксы)
    private final Map<String, Room3DModel> roomModels = new HashMap<>();
    private String highlightedRoomId;

    // roomId -> OBJ-модель, заменяющая геометрию комнаты
    private final Map<String, Group> roomObjOverrides = new HashMap<>();

    // Внешние модели, добавленные глобально (не привязаны к комнате)
    private final Group externalModels = new Group();

    public Room3DView() {
        buildScene();
        setupOrbitHandlers();
    }

    // =========================================================
    //  Построение сцены
    // =========================================================

    private void buildScene() {
        // Orbit: камера далеко, смотрит на центр
        camera.setTranslateZ(-800);
        camera.setFieldOfView(45);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);

        orbitCameraGroup.getTransforms().addAll(orbitRotateX, orbitRotateY);
        orbitCameraGroup.getChildren().add(camera);

        AmbientLight ambient = new AmbientLight(Color.rgb(80, 80, 110));
        PointLight mainLight = new PointLight(Color.WHITE);
        mainLight.setTranslateY(-500);
        mainLight.setTranslateZ(-300);

        root3D.getChildren().addAll(ambient, mainLight, orbitCameraGroup, externalModels);

        subScene = new SubScene(root3D, 700, 500, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#1a1a2e"));
        subScene.setCamera(camera);
        subScene.widthProperty().bind(widthProperty());
        subScene.heightProperty().bind(heightProperty());

        getChildren().add(subScene);
    }

    // =========================================================
    //  Orbit-управление (мышь + колёсико)
    // =========================================================

    private void setupOrbitHandlers() {
        subScene.setOnMousePressed(e -> {
            orbitLastX = e.getSceneX();
            orbitLastY = e.getSceneY();
        });

        subScene.setOnMouseDragged(e -> {
            if (fpsMode) return; // в FPS режиме orbit не работает
            double dx = e.getSceneX() - orbitLastX;
            double dy = e.getSceneY() - orbitLastY;

            if (e.isPrimaryButtonDown()) {
                // Вращение вокруг центра сцены
                orbitRotateY.setAngle(orbitRotateY.getAngle() + dx * 0.3);
                double newAngle = orbitRotateX.getAngle() - dy * 0.3;
                orbitRotateX.setAngle(Math.max(-89, Math.min(0, newAngle)));
            } else if (e.isSecondaryButtonDown()) {
                // Панорама
                orbitCameraGroup.setTranslateX(orbitCameraGroup.getTranslateX() - dx * 0.5);
                orbitCameraGroup.setTranslateY(orbitCameraGroup.getTranslateY() - dy * 0.5);
            }

            orbitLastX = e.getSceneX();
            orbitLastY = e.getSceneY();
        });

        subScene.setOnScroll(e -> {
            if (fpsMode) return;
            double newZ = camera.getTranslateZ() + e.getDeltaY() * 2;
            camera.setTranslateZ(Math.max(-2000, Math.min(-100, newZ)));
        });
    }

    // =========================================================
    //  Отрисовка дома
    // =========================================================

    /** Перерисовывает весь дом */
    public void drawHouse(House house) {
        root3D.getChildren().removeAll(roomModels.values());
        // Убираем OBJ-overrides, они будут добавлены заново ниже
        root3D.getChildren().removeAll(roomObjOverrides.values());
        roomModels.clear();

        if (house == null) return;

        int count = house.getRooms().size();
        double offsetX = -(Math.min(count, MAX_COLS) - 1) * SPACING_X / 2.0;

        int col = 0;
        for (Room room : house.getRooms()) {
            double posX = offsetX + (col % MAX_COLS) * SPACING_X;
            double posZ = (col / MAX_COLS) * SPACING_Z;

            Room3DModel model = new Room3DModel(room);
            model.setTranslateX(posX);
            model.setTranslateZ(posZ);

            if (room.getId().equals(highlightedRoomId)) {
                model.setHighlighted(true);
            }

            // Если комнате назначена OBJ-модель — прячем стены/пол, ставим OBJ на ту же позицию
            Group objOverride = roomObjOverrides.get(room.getId());
            if (objOverride != null) {
                model.setStructureVisible(false);
                objOverride.setTranslateX(posX);
                objOverride.setTranslateZ(posZ);
                root3D.getChildren().add(objOverride);
            }

            // Перетаскивание устройств мышью (только в orbit-режиме)
            setupDeviceDrag(model);

            // Hover-подсветка стен — временная, не перебивает selected
            final Room3DModel capturedModel = model;
            model.setOnMouseEntered(e -> {
                if (!fpsMode) capturedModel.setHovered(true);
            });
            model.setOnMouseExited(e -> capturedModel.setHovered(false));

            // Double-click на комнату → плавный фокус камеры
            final double focusX = posX;
            final double focusZ = posZ;
            model.setOnMouseClicked(e -> {
                if (fpsMode) return;
                if (e.getClickCount() == 2) {
                    focusCameraOn(focusX, focusZ);
                    e.consume();
                }
            });

            roomModels.put(room.getId(), model);
            root3D.getChildren().add(model);
            col++;
        }
    }

    /**
     * Вешает drag-обработчики на каждую 3D-модель устройства в комнате.
     * Перетаскивание двигает устройство по полу (X и Z, Y не меняется)
     * И сохраняет новую позицию в Device.x/y, чтобы она пережила refreshAll().
     */
    private void setupDeviceDrag(Room3DModel roomModel) {
        List<Group> deviceModels = roomModel.getDeviceModels();
        for (Group deviceModel : deviceModels) {
            final double[] dragStart = new double[2]; // [sceneX, sceneY]

            deviceModel.setOnMousePressed(e -> {
                if (fpsMode) return;
                dragStart[0] = e.getSceneX();
                dragStart[1] = e.getSceneY();
                e.consume(); // не передаём orbit-обработчику
            });

            deviceModel.setOnMouseDragged(e -> {
                if (fpsMode) return;
                // Грубое преобразование экранных координат в смещение в мире
                double scale = Math.abs(camera.getTranslateZ()) / 800.0;
                double dx = (e.getSceneX() - dragStart[0]) * scale * 0.4;
                double dz = (e.getSceneY() - dragStart[1]) * scale * 0.4;

                double newX = deviceModel.getTranslateX() + dx;
                double newZ = deviceModel.getTranslateZ() + dz;

                deviceModel.setTranslateX(newX);
                deviceModel.setTranslateZ(newZ);

                // Сохраняем позицию в модель, чтобы она пережила перерисовку.
                com.smarthome.model.device.Device device = roomModel.findDeviceByModel(deviceModel);
                if (device != null) {
                    device.setX(newX);
                    device.setY(newZ);
                }

                dragStart[0] = e.getSceneX();
                dragStart[1] = e.getSceneY();
                e.consume();
            });
        }
    }

    /**
     * Плавно перемещает orbit-камеру к заданной точке пола сцены.
     * Вызывается при double-click на комнату (требование TASK_3D_UI.md).
     * Анимирует одновременно translateX/Y группы и zoom камеры.
     */
    private void focusCameraOn(double worldX, double worldZ) {
        if (fpsMode) return;
        double targetZoom = -450; // чуть ближе чем дефолтные -800

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(450),
                        new KeyValue(orbitCameraGroup.translateXProperty(), worldX),
                        new KeyValue(orbitCameraGroup.translateYProperty(), 0),
                        new KeyValue(camera.translateZProperty(), targetZoom)
                )
        );
        timeline.play();
    }

    // =========================================================
    //  FPS-режим
    // =========================================================

    /**
     * Входит в FPS-режим внутри выбранной комнаты.
     * Камера перемещается в центр комнаты на уровень глаз.
     */
    public void enterFpsMode(Room room) {
        if (fpsMode) exitFpsMode();

        // Находим модель комнаты и берём её позицию в мире
        Room3DModel model = roomModels.get(room.getId());
        double spawnX = model != null ? model.getTranslateX() : 0;
        double spawnZ = model != null ? model.getTranslateZ() : 0;

        // Убираем камеру из orbit-группы
        orbitCameraGroup.getChildren().remove(camera);

        // Создаём FPS-контроллер, onExit = выход из FPS
        fpsController = new FpsCameraController(camera, this::exitFpsMode);
        fpsController.spawnAt(spawnX, 0, spawnZ);
        fpsController.attach(subScene);

        // Добавляем playerGroup в сцену вместо orbit-группы
        root3D.getChildren().add(fpsController.getPlayerGroup());

        fpsMode = true;
    }

    /**
     * Выходит из FPS-режима, возвращает orbit-камеру.
     */
    public void exitFpsMode() {
        if (!fpsMode || fpsController == null) return;

        // Отключаем FPS-контроллер
        fpsController.detach(subScene);
        root3D.getChildren().remove(fpsController.getPlayerGroup());
        fpsController = null;

        // Возвращаем камеру в orbit-группу
        orbitCameraGroup.getChildren().add(camera);
        camera.setTranslateZ(-800);

        fpsMode = false;
    }

    // =========================================================
    //  Подсветка комнаты
    // =========================================================

    /** Подсвечивает выбранную комнату, снимает подсветку с остальных */
    public void highlightRoom(Room room) {
        roomModels.values().forEach(m -> m.setHighlighted(false));

        if (room != null) {
            highlightedRoomId = room.getId();
            Room3DModel m = roomModels.get(room.getId());
            if (m != null) m.setHighlighted(true);
        } else {
            highlightedRoomId = null;
        }
    }

    public boolean isFpsMode() {
        return fpsMode;
    }

    // =========================================================
    //  Внешние 3D модели (загруженные через OBJ)
    // =========================================================

    /** Добавить загруженную OBJ-модель в сцену (глобально, без привязки к комнате). */
    public void addExternalModel(javafx.scene.Group model) {
        externalModels.getChildren().add(model);
    }

    /** Удалить все внешние (глобальные) модели из сцены. */
    public void clearExternalModels() {
        externalModels.getChildren().clear();
    }

    /**
     * Назначить OBJ-модель конкретной комнате.
     * Заменяет геометрию комнаты (стены/пол), устройства и интерактивность сохраняются.
     * Вызывает перерисовку дома.
     */
    public void assignObjToRoom(String roomId, Group model, House house) {
        roomObjOverrides.put(roomId, model);
        drawHouse(house);
    }

    /**
     * Снять OBJ-модель с комнаты, вернуть стандартные стены.
     */
    public void clearRoomObj(String roomId, House house) {
        roomObjOverrides.remove(roomId);
        drawHouse(house);
    }

    /** Снять OBJ-модели со всех комнат и убрать глобальные. */
    public void clearAllModels(House house) {
        roomObjOverrides.clear();
        externalModels.getChildren().clear();
        drawHouse(house);
    }
}
