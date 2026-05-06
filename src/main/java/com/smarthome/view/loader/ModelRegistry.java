package com.smarthome.view.loader;

import com.smarthome.model.device.DeviceType;
import com.smarthome.model.room.RoomType;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Material;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр пользовательских 3D моделей.
 * Хранит меш-данные (TriangleMesh + Material) по типу устройства или комнаты.
 * TriangleMesh и Material безопасно шарятся между несколькими MeshView —
 * поэтому можно создавать сколько угодно экземпляров одной модели без перезагрузки OBJ.
 *
 * Паттерн: Singleton + Prototype (новый Group создаётся каждый раз из сохранённых мешей)
 */
public class ModelRegistry {

    private static ModelRegistry instance;

    private final Map<DeviceType, List<MeshData>> deviceModels = new HashMap<>();
    private final Map<RoomType,  List<MeshData>> roomModels   = new HashMap<>();

    private ModelRegistry() {}

    public static ModelRegistry getInstance() {
        if (instance == null) instance = new ModelRegistry();
        return instance;
    }

    // === Регистрация ===

    public void registerForDeviceType(DeviceType type, Group objRoot) {
        deviceModels.put(type, extractMeshes(objRoot));
    }

    public void registerForRoomType(RoomType type, Group objRoot) {
        roomModels.put(type, extractMeshes(objRoot));
    }

    // === Создание экземпляров ===

    /**
     * Создаёт новый Group для устройства. Масштаб ~18 единиц (как у примитивов).
     * Возвращает null если модель не зарегистрирована — тогда рисуется примитив.
     */
    public Group createDeviceInstance(DeviceType type) {
        return createInstance(deviceModels.get(type));
    }

    /**
     * Создаёт новый Group для комнаты без масштабирования —
     * Room3DModel масштабирует сам под размер комнаты.
     * Возвращает null если модель не зарегистрирована.
     */
    public Group createRoomInstance(RoomType type) {
        return createInstance(roomModels.get(type));
    }

    public boolean hasDeviceModel(DeviceType type) {
        return deviceModels.containsKey(type);
    }

    public boolean hasRoomModel(RoomType type) {
        return roomModels.containsKey(type);
    }

    public void clearDeviceModel(DeviceType type) {
        deviceModels.remove(type);
    }

    public void clearRoomModel(RoomType type) {
        roomModels.remove(type);
    }

    public void clearAll() {
        deviceModels.clear();
        roomModels.clear();
    }

    // === Внутреннее ===

    private Group createInstance(List<MeshData> meshes) {
        if (meshes == null || meshes.isEmpty()) return null;
        Group g = new Group();
        for (MeshData data : meshes) {
            MeshView mv = new MeshView(data.mesh());
            mv.setMaterial(data.material());
            g.getChildren().add(mv);
        }
        return g;
    }

    private List<MeshData> extractMeshes(Group root) {
        List<MeshData> result = new ArrayList<>();
        extractRecursive(root, result);
        return result;
    }

    private void extractRecursive(Node node, List<MeshData> result) {
        if (node instanceof MeshView mv && mv.getMesh() instanceof TriangleMesh tm) {
            result.add(new MeshData(tm, mv.getMaterial()));
        } else if (node instanceof Group g) {
            for (Node child : g.getChildren()) {
                extractRecursive(child, result);
            }
        }
    }

    private record MeshData(TriangleMesh mesh, Material material) {}
}
