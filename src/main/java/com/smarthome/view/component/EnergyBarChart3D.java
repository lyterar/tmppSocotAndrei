package com.smarthome.view.component;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D столбчатый график потребления по комнатам.
 * Каждый столбик — это Box, высота которого соответствует мощности в ваттах.
 * Вращается мышью, масштабируется колёсиком.
 */
public class EnergyBarChart3D extends Pane {

    private static final double BAR_WIDTH  = 40;
    private static final double BAR_DEPTH  = 40;
    private static final double SPACING    = 70;
    private static final double MAX_HEIGHT = 200; // высота столбика при 3000 Вт
    private static final double MAX_WATTS  = 3000;
    private static final double FLOOR_Y    = 110;
    private static final double ANIM_MS    = 600;

    private static final Color COLOR_BAR_LOW  = Color.web("#0044ff", 0.85);
    private static final Color COLOR_BAR_MID  = Color.web("#00aaff", 0.85);
    private static final Color COLOR_BAR_HIGH = Color.web("#00ffcc", 0.90);
    private static final Color COLOR_SPEC     = Color.web("#ffffff", 0.3);
    private static final Color COLOR_FLOOR    = Color.web("#0a0a1a");
    private static final Color COLOR_LABEL    = Color.web("#88ccff");
    private static final Color COLOR_BG       = Color.web("#05050f");

    private final Group    root3D  = new Group();
    private final Group    bars    = new Group();
    private final Group    labels  = new Group();
    private SubScene       subScene;
    private PerspectiveCamera camera;

    private final Map<String, Box>      barMap  = new HashMap<>();
    private final Map<String, Timeline> animMap = new HashMap<>();

    private double orbitLastX, orbitLastY;
    private final Rotate rotateX = new Rotate(-25, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(20,  Rotate.Y_AXIS);
    private final Group  cameraGroup = new Group();

    public EnergyBarChart3D() {
        buildScene();
        setupMouseHandlers();
    }

    private void buildScene() {
        camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-520);
        camera.setFieldOfView(45);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);

        cameraGroup.getTransforms().addAll(rotateX, rotateY);
        cameraGroup.getChildren().add(camera);

        AmbientLight ambient = new AmbientLight(Color.rgb(30, 40, 80));
        PointLight neonLight = new PointLight(Color.web("#0088ff"));
        neonLight.setTranslateY(-200);
        neonLight.setTranslateZ(-100);
        neonLight.setLightOn(true);

        PointLight topLight = new PointLight(Color.WHITE);
        topLight.setTranslateY(-300);

        Box floor = new Box(600, 2, 300);
        PhongMaterial floorMat = new PhongMaterial();
        floorMat.setDiffuseColor(COLOR_FLOOR);
        floorMat.setSpecularColor(Color.web("#1a3a6a"));
        floor.setMaterial(floorMat);
        floor.setTranslateY(FLOOR_Y);

        root3D.getChildren().addAll(ambient, neonLight, topLight, floor, bars, labels, cameraGroup);

        subScene = new SubScene(root3D, 500, 300, true, SceneAntialiasing.BALANCED);
        subScene.setFill(COLOR_BG);
        subScene.setCamera(camera);
        subScene.widthProperty().bind(widthProperty());
        subScene.heightProperty().bind(heightProperty());

        getChildren().add(subScene);
    }

    private void setupMouseHandlers() {
        subScene.setOnMousePressed(e -> {
            orbitLastX = e.getSceneX();
            orbitLastY = e.getSceneY();
        });
        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - orbitLastX;
            double dy = e.getSceneY() - orbitLastY;
            if (e.isPrimaryButtonDown()) {
                rotateY.setAngle(rotateY.getAngle() + dx * 0.4);
                double newX = rotateX.getAngle() - dy * 0.3;
                rotateX.setAngle(Math.max(-80, Math.min(0, newX)));
            }
            orbitLastX = e.getSceneX();
            orbitLastY = e.getSceneY();
        });
        subScene.setOnScroll(e -> {
            double newZ = camera.getTranslateZ() + e.getDeltaY() * 1.5;
            camera.setTranslateZ(Math.max(-1200, Math.min(-150, newZ)));
        });
    }

    // Вызывается каждые 2 сек из DashboardController при получении нового снимка
    public void update(Map<String, Double> wattsByRoom) {
        barMap.keySet().removeIf(name -> {
            if (!wattsByRoom.containsKey(name)) {
                removeBar(name);
                return true;
            }
            return false;
        });

        int index = 0;
        int count = wattsByRoom.size();
        double startX = -(count - 1) * SPACING / 2.0;

        for (Map.Entry<String, Double> entry : wattsByRoom.entrySet()) {
            String roomName = entry.getKey();
            double watts    = entry.getValue();
            double posX     = startX + index * SPACING;

            if (!barMap.containsKey(roomName)) {
                addBar(roomName, posX);
                repositionLabels(wattsByRoom, startX);
            }

            animateBarHeight(roomName, watts, posX);
            index++;
        }
    }

    private void addBar(String roomName, double posX) {
        Box bar = new Box(BAR_WIDTH, 1, BAR_DEPTH);
        bar.setTranslateX(posX);
        bar.setTranslateY(FLOOR_Y - 0.5);
        bar.setMaterial(makeMaterial(0));
        bars.getChildren().add(bar);
        barMap.put(roomName, bar);

        // Подпись под столбиком
        Text label = new Text(shortenName(roomName));
        label.setFont(Font.font("Monospace", 9));
        label.setFill(COLOR_LABEL);
        label.setTranslateX(posX - 18);
        label.setTranslateY(FLOOR_Y + 14);
        label.setRotationAxis(new Point3D(0, 1, 0));
        label.setRotate(0);
        label.setId("lbl_" + roomName);
        labels.getChildren().add(label);
    }

    private void removeBar(String roomName) {
        Box bar = barMap.remove(roomName);
        if (bar != null) bars.getChildren().remove(bar);
        labels.getChildren().removeIf(n -> ("lbl_" + roomName).equals(n.getId()));
        Timeline anim = animMap.remove(roomName);
        if (anim != null) anim.stop();
    }

    private void animateBarHeight(String roomName, double watts, double posX) {
        Box bar = barMap.get(roomName);
        if (bar == null) return;

        double targetH = Math.max(2, (watts / MAX_WATTS) * MAX_HEIGHT);
        double targetY = FLOOR_Y - targetH / 2.0;

        Timeline prev = animMap.get(roomName);
        if (prev != null) prev.stop();

        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(ANIM_MS),
                        new KeyValue(bar.heightProperty(), targetH),
                        new KeyValue(bar.translateYProperty(), targetY)
                )
        );
        // Цвет обновляем после завершения анимации чтобы не мерцал
        tl.setOnFinished(e -> bar.setMaterial(makeMaterial(watts)));
        tl.play();
        bar.setTranslateX(posX);
        animMap.put(roomName, tl);
    }

    private void repositionLabels(Map<String, Double> wattsByRoom, double startX) {
        int i = 0;
        for (String name : wattsByRoom.keySet()) {
            double posX = startX + i * SPACING;
            labels.getChildren().stream()
                    .filter(n -> ("lbl_" + name).equals(n.getId()))
                    .forEach(n -> {
                        n.setTranslateX(posX - 18);
                    });
            i++;
        }
    }

    // Чем выше мощность — тем теплее цвет: синий → голубой → зелёный
    private PhongMaterial makeMaterial(double watts) {
        double ratio = Math.min(1.0, watts / MAX_WATTS);
        Color diffuse;
        if (ratio < 0.4) {
            diffuse = COLOR_BAR_LOW.interpolate(COLOR_BAR_MID, ratio / 0.4);
        } else {
            diffuse = COLOR_BAR_MID.interpolate(COLOR_BAR_HIGH, (ratio - 0.4) / 0.6);
        }
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(diffuse);
        mat.setSpecularColor(COLOR_SPEC);
        mat.setSpecularPower(30);
        return mat;
    }

    private String shortenName(String name) {
        return name.length() > 8 ? name.substring(0, 7) + "." : name;
    }
}