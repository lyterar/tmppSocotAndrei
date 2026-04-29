package com.smarthome.view.component;

import com.smarthome.model.device.Device;
import com.smarthome.model.room.Room;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3D модель одной комнаты: пол, 4 стены, устройства, название.
 * Стены полупрозрачные — видно содержимое комнаты.
 */
public class Room3DModel extends Group {

    private static final double WALL_H = 80;
    private static final double WALL_THICKNESS = 4;

    private final Room room;
    private final Box[] walls = new Box[4];
    private final PhongMaterial wallMaterial;
    private final PhongMaterial wallHighlightMaterial;
    private final PhongMaterial wallHoverMaterial;

    // Состояния подсветки: selected устойчивое, hovered временное
    private boolean selected = false;
    private boolean hovered = false;

    // Связка Device ↔ 3D-группа (для drag с сохранением позиции в модели)
    private final Map<Device, Group> deviceToModel = new LinkedHashMap<>();

    public Room3DModel(Room room) {
        this.room = room;

        Color roomColor = Color.web(room.getType().getColor());
        // Стены — светлее цвета комнаты
        Color wallColor = roomColor.deriveColor(0, 0.7, 1.4, 0.45);
        wallMaterial = new PhongMaterial(wallColor);
        wallMaterial.setSpecularColor(Color.WHITE);

        wallHighlightMaterial = new PhongMaterial(roomColor.brighter());
        wallHighlightMaterial.setSpecularColor(Color.WHITE);

        // Hover — промежуточная яркость между обычным и highlight
        Color hoverColor = roomColor.deriveColor(0, 0.85, 1.15, 0.65);
        wallHoverMaterial = new PhongMaterial(hoverColor);
        wallHoverMaterial.setSpecularColor(Color.WHITE);

        buildRoom();
        addDevices();
        addLabel();
    }

    private void buildRoom() {
        double floorW = room.getWidth();
        double floorD = room.getHeight();

        // Пол
        Box floor = new Box(floorW, 2, floorD);
        floor.setMaterial(new PhongMaterial(Color.web(room.getType().getColor())));

        // Стены: front (+Z), back (-Z), left (-X), right (+X)
        walls[0] = new Box(floorW, WALL_H, WALL_THICKNESS);
        walls[0].setTranslateZ(floorD / 2);
        walls[0].setTranslateY(-WALL_H / 2);

        walls[1] = new Box(floorW, WALL_H, WALL_THICKNESS);
        walls[1].setTranslateZ(-floorD / 2);
        walls[1].setTranslateY(-WALL_H / 2);

        walls[2] = new Box(WALL_THICKNESS, WALL_H, floorD);
        walls[2].setTranslateX(-floorW / 2);
        walls[2].setTranslateY(-WALL_H / 2);

        walls[3] = new Box(WALL_THICKNESS, WALL_H, floorD);
        walls[3].setTranslateX(floorW / 2);
        walls[3].setTranslateY(-WALL_H / 2);

        for (Box wall : walls) {
            wall.setMaterial(wallMaterial);
        }

        getChildren().add(floor);
        getChildren().addAll(walls);
    }

    private void addDevices() {
        double floorW = room.getWidth();
        double floorD = room.getHeight();
        double startX = -floorW / 2 + 20;
        double startZ = -floorD / 2 + 20;
        double spacing = 38;
        int maxCols = Math.max(1, (int) ((floorW - 30) / spacing));

        int i = 0;
        for (Device device : room.getDevices()) {
            Group deviceModel = Device3DModel.createModel(device.getType(), device.isOn());

            // Если у устройства есть сохранённая позиция — используем её,
            // иначе раскладываем по сетке. В 3D: Device.x → локальный X, Device.y → Z.
            boolean hasSavedPosition = device.getX() != 0 || device.getY() != 0;
            if (hasSavedPosition) {
                deviceModel.setTranslateX(device.getX());
                deviceModel.setTranslateZ(device.getY());
            } else {
                double dx = startX + (i % maxCols) * spacing;
                double dz = startZ + (i / maxCols) * spacing;
                deviceModel.setTranslateX(dx);
                deviceModel.setTranslateZ(dz);
                // Запоминаем стартовую позицию в модели, чтобы drag был от неё.
                device.setX(dx);
                device.setY(dz);
            }

            deviceToModel.put(device, deviceModel);
            getChildren().add(deviceModel);
            i++;
        }
    }

    /** Возвращает список 3D-групп устройств (для drag в orbit-режиме) */
    public List<Group> getDeviceModels() {
        return new ArrayList<>(deviceToModel.values());
    }

    /** Возвращает Device, которому соответствует данная 3D-группа (или null) */
    public Device findDeviceByModel(Group model) {
        for (Map.Entry<Device, Group> e : deviceToModel.entrySet()) {
            if (e.getValue() == model) return e.getKey();
        }
        return null;
    }

    private void addLabel() {
        Text label = new Text(room.getName());
        label.setFont(Font.font("Arial", 13));
        label.setFill(Color.WHITE);
        // Горизонтально лежащий текст над комнатой
        label.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        label.setTranslateX(-room.getWidth() / 2 + 4);
        label.setTranslateY(-WALL_H - 8);
        getChildren().add(label);
    }

    /** Переключает подсветку стен (при выборе комнаты — устойчивое выделение) */
    public void setHighlighted(boolean highlighted) {
        this.selected = highlighted;
        applyWallMaterial();
    }

    /** Временная подсветка при наведении мыши — не перебивает selected */
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
        applyWallMaterial();
    }

    /** Выбирает актуальный материал стен в зависимости от состояния */
    private void applyWallMaterial() {
        PhongMaterial mat;
        if (selected) {
            mat = wallHighlightMaterial;           // выбрана — ярко
        } else if (hovered) {
            mat = wallHoverMaterial;               // hover — промежуточный
        } else {
            mat = wallMaterial;                    // обычная
        }
        for (Box wall : walls) {
            wall.setMaterial(mat);
        }
    }
}
