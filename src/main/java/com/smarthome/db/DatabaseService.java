package com.smarthome.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.smarthome.model.device.Device;
import com.smarthome.model.device.DeviceType;
import com.smarthome.model.house.House;
import com.smarthome.model.room.Room;
import com.smarthome.model.room.RoomType;
import com.smarthome.pattern.creational.DeviceFactory;

import java.lang.reflect.Type;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Сервис работы с PostgreSQL.
 * Отвечает за: подключение, авто-создание БД, инициализацию схемы,
 * CRUD для комнат, устройств и реестра 3D-моделей.
 *
 * Запускается через AppContext. При недоступности БД приложение
 * продолжает работу без персистентности (graceful degradation).
 */
public class DatabaseService {

    private final String URL;
    private final String BASE_URL;
    private final String USER;
    private final String PASS;
    private final String DB_NAME;

    private Connection conn;
    private final Gson gson = new Gson();

    public DatabaseService() {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                System.err.println("[DB] config.properties не найден, используются значения по умолчанию");
            }
        } catch (IOException e) {
            System.err.println("[DB] Ошибка чтения config.properties: " + e.getMessage());
        }
        String host   = props.getProperty("db.host", "localhost");
        String port   = props.getProperty("db.port", "5432");
        String db     = props.getProperty("db.name", "smarthome");
        this.USER     = props.getProperty("db.user", "postgres");
        this.PASS     = props.getProperty("db.password", "");
        this.DB_NAME  = db;
        this.URL      = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        this.BASE_URL = "jdbc:postgresql://" + host + ":" + port + "/postgres";
    }

    // =========================================================
    //  Подключение и инициализация
    // =========================================================

    /**
     * Подключается к БД. Если БД "smarthome" не существует — создаёт её.
     * Затем создаёт таблицы если их нет.
     */
    public void connect() throws SQLException {
        ensureDatabaseExists();
        conn = DriverManager.getConnection(URL, USER, PASS);
        initSchema();
        System.out.println("[DB] Подключено к " + URL);
    }

    private void ensureDatabaseExists() {
        try (Connection c = DriverManager.getConnection(BASE_URL, USER, PASS);
             Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname='" + DB_NAME + "'");
            if (!rs.next()) {
                st.execute("CREATE DATABASE " + DB_NAME);
                System.out.println("[DB] База данных '" + DB_NAME + "' создана");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Не удалось создать БД: " + e.getMessage());
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    id      VARCHAR(50)  PRIMARY KEY,
                    name    VARCHAR(200) NOT NULL,
                    type    VARCHAR(50)  NOT NULL,
                    width   DOUBLE PRECISION DEFAULT 200,
                    height  DOUBLE PRECISION DEFAULT 150,
                    x       DOUBLE PRECISION DEFAULT 0,
                    y       DOUBLE PRECISION DEFAULT 0
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS devices (
                    id         VARCHAR(50)  PRIMARY KEY,
                    room_id    VARCHAR(50)  REFERENCES rooms(id) ON DELETE CASCADE,
                    name       VARCHAR(200) NOT NULL,
                    type       VARCHAR(50)  NOT NULL,
                    is_on      BOOLEAN      DEFAULT FALSE,
                    pos_x      DOUBLE PRECISION DEFAULT 0,
                    pos_y      DOUBLE PRECISION DEFAULT 0,
                    parameters TEXT         DEFAULT '{}'
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS model_registry (
                    category  VARCHAR(20) NOT NULL,
                    type_name VARCHAR(50) NOT NULL,
                    file_url  TEXT        NOT NULL,
                    PRIMARY KEY (category, type_name)
                )
                """);
        }
        System.out.println("[DB] Схема готова");
    }

    // =========================================================
    //  Загрузка всего дома из БД при старте
    // =========================================================

    public void loadInto(House house, DeviceFactory factory) throws SQLException {
        Map<String, Room> roomById = new HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, type, width, height, x, y FROM rooms ORDER BY y, x")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Room room = new Room(rs.getString("name"),
                        RoomType.valueOf(rs.getString("type")));
                room.setId(rs.getString("id"));
                room.setWidth(rs.getDouble("width"));
                room.setHeight(rs.getDouble("height"));
                room.setX(rs.getDouble("x"));
                room.setY(rs.getDouble("y"));
                house.addRoom(room);
                roomById.put(room.getId(), room);
            }
        }

        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, room_id, name, type, is_on, pos_x, pos_y, parameters FROM devices")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Room room = roomById.get(rs.getString("room_id"));
                if (room == null) continue;

                Device device = factory.createDevice(
                        rs.getString("name"),
                        DeviceType.valueOf(rs.getString("type")));
                device.setId(rs.getString("id"));
                device.setX(rs.getDouble("pos_x"));
                device.setY(rs.getDouble("pos_y"));
                if (rs.getBoolean("is_on")) device.turnOn();

                String paramJson = rs.getString("parameters");
                if (paramJson != null && !paramJson.isBlank() && !paramJson.equals("{}")) {
                    Map<String, Object> params = gson.fromJson(paramJson, mapType);
                    if (params != null) params.forEach(device::setParameter);
                }
                room.addDevice(device);
            }
        }

        System.out.println("[DB] Загружено " + house.getRooms().size() + " комнат, "
                + house.getAllDevices().size() + " устройств");
    }

    // =========================================================
    //  Комнаты
    // =========================================================

    public void saveRoom(Room room) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO rooms (id, name, type, width, height, x, y)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name=EXCLUDED.name, type=EXCLUDED.type,
                    width=EXCLUDED.width, height=EXCLUDED.height,
                    x=EXCLUDED.x, y=EXCLUDED.y
                """)) {
            ps.setString(1, room.getId());
            ps.setString(2, room.getName());
            ps.setString(3, room.getType().name());
            ps.setDouble(4, room.getWidth());
            ps.setDouble(5, room.getHeight());
            ps.setDouble(6, room.getX());
            ps.setDouble(7, room.getY());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка сохранения комнаты: " + e.getMessage());
        }
    }

    public void deleteRoom(String roomId) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM rooms WHERE id=?")) {
            ps.setString(1, roomId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка удаления комнаты: " + e.getMessage());
        }
    }

    // =========================================================
    //  Устройства
    // =========================================================

    public void saveDevice(Device device, String roomId) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO devices (id, room_id, name, type, is_on, pos_x, pos_y, parameters)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    room_id=EXCLUDED.room_id, name=EXCLUDED.name,
                    is_on=EXCLUDED.is_on, pos_x=EXCLUDED.pos_x,
                    pos_y=EXCLUDED.pos_y, parameters=EXCLUDED.parameters
                """)) {
            ps.setString(1, device.getId());
            ps.setString(2, roomId);
            ps.setString(3, device.getName());
            ps.setString(4, device.getType().name());
            ps.setBoolean(5, device.isOn());
            ps.setDouble(6, device.getX());
            ps.setDouble(7, device.getY());
            ps.setString(8, gson.toJson(device.getParameters()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка сохранения устройства: " + e.getMessage());
        }
    }

    /** Обновляет состояние устройства (on/off, параметры, позицию) без изменения room_id */
    public void updateDeviceState(Device device) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE devices SET is_on=?, pos_x=?, pos_y=?, parameters=? WHERE id=?
                """)) {
            ps.setBoolean(1, device.isOn());
            ps.setDouble(2, device.getX());
            ps.setDouble(3, device.getY());
            ps.setString(4, gson.toJson(device.getParameters()));
            ps.setString(5, device.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка обновления состояния: " + e.getMessage());
        }
    }

    public void deleteDevice(String deviceId) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM devices WHERE id=?")) {
            ps.setString(1, deviceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка удаления устройства: " + e.getMessage());
        }
    }

    // =========================================================
    //  Реестр 3D-моделей
    // =========================================================

    /** Загружает все записи реестра. Ключ: "DEVICE:LIGHT", "ROOM:BEDROOM" и т.д. */
    public Map<String, String> loadModelRegistry() throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category, type_name, file_url FROM model_registry")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("category") + ":" + rs.getString("type_name"),
                        rs.getString("file_url"));
            }
        }
        return result;
    }

    public void saveModelEntry(String category, String typeName, String fileUrl) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO model_registry (category, type_name, file_url)
                VALUES (?, ?, ?)
                ON CONFLICT (category, type_name) DO UPDATE SET file_url=EXCLUDED.file_url
                """)) {
            ps.setString(1, category);
            ps.setString(2, typeName);
            ps.setString(3, fileUrl);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка сохранения модели: " + e.getMessage());
        }
    }

    public void deleteModelEntry(String category, String typeName) {
        if (!isConnected()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM model_registry WHERE category=? AND type_name=?")) {
            ps.setString(1, category);
            ps.setString(2, typeName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка удаления модели: " + e.getMessage());
        }
    }

    public void clearModelRegistry() {
        if (!isConnected()) return;
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM model_registry");
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка очистки реестра: " + e.getMessage());
        }
    }

    // =========================================================
    //  Утилиты
    // =========================================================

    public boolean isConnected() {
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        try {
            if (isConnected()) {
                conn.close();
                System.out.println("[DB] Соединение закрыто");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Ошибка закрытия: " + e.getMessage());
        }
    }
}
