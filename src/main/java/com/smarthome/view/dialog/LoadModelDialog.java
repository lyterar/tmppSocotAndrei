package com.smarthome.view.dialog;

import com.smarthome.model.device.DeviceType;
import com.smarthome.model.room.RoomType;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * Диалог загрузки OBJ-модели с выбором типа назначения.
 * Возвращает Result: URL + DeviceType (или RoomType) для реестра,
 * либо url без привязки — чтобы добавить модель в сцену глобально.
 */
public class LoadModelDialog extends Dialog<LoadModelDialog.Result> {

    public record Result(String url, DeviceType deviceType, RoomType roomType) {
        public boolean isGlobal() { return deviceType == null && roomType == null; }
    }

    private static final String GLOBAL_LABEL = "— Добавить в сцену (без привязки) —";

    private final TextField urlField = new TextField();

    public LoadModelDialog(Window owner) {
        setTitle("Загрузить 3D модель (.obj)");
        initOwner(owner);

        // --- Строка URL ---
        urlField.setPromptText("https://... или file:///path/to/model.obj");
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Button browseBtn = new Button("Выбрать файл...");
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выбрать OBJ файл");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Wavefront OBJ", "*.obj"),
                    new FileChooser.ExtensionFilter("Все файлы", "*.*")
            );
            File file = chooser.showOpenDialog(owner);
            if (file != null) urlField.setText(file.toURI().toString());
        });

        HBox urlRow = new HBox(8, urlField, browseBtn);

        // --- Выбор типа ---
        Label typeLabel = new Label("Назначить как модель для:");

        ComboBox<Object> typeCombo = new ComboBox<>();
        typeCombo.getItems().add(GLOBAL_LABEL);
        typeCombo.getItems().addAll((Object[]) DeviceType.values());
        typeCombo.getItems().addAll((Object[]) RoomType.values());
        typeCombo.getSelectionModel().selectFirst();
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        typeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item));
            }
        });
        // Кнопка комбо тоже должна показывать метку
        ListCell<Object> btnCell = new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item));
            }
        };
        typeCombo.setButtonCell(btnCell);

        Label hint = new Label("Совет: скачайте .obj модели с free3d.com, cgtrader.com, sketchfab.com");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #7777aa;");
        hint.setWrapText(true);

        VBox content = new VBox(10, urlRow, typeLabel, typeCombo, hint);
        content.setPadding(new Insets(16));
        content.setPrefWidth(500);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Загрузить");
        okBtn.setDisable(true);
        urlField.textProperty().addListener((obs, o, n) ->
                okBtn.setDisable(n == null || n.isBlank()));

        setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String url = toFileUrl(urlField.getText().trim());
            Object selected = typeCombo.getSelectionModel().getSelectedItem();
            DeviceType dt = selected instanceof DeviceType d ? d : null;
            RoomType rt   = selected instanceof RoomType r   ? r : null;
            return new Result(url, dt, rt);
        });
    }

    /**
     * Приводит любой ввод пользователя к корректному file:/// URL.
     * Обрабатывает: Windows-путь (E:\...), file:\ с обратными слэшами,
     * file:/ с одним слэшем, уже корректный file:/// и http/https.
     */
    private static String toFileUrl(String input) {
        // http/https/ftp оставляем как есть
        if (input.matches("(?i)^https?://.*") || input.matches("(?i)^ftp://.*")) {
            return input;
        }
        // Заменяем все обратные слэши на прямые
        String s = input.replace('\\', '/');
        // Уже нормальный file:// URL
        if (s.startsWith("file://")) {
            return s;
        }
        // file: с одним слэшем или без слэшей — убираем схему и нормализуем
        if (s.startsWith("file:")) {
            s = s.substring("file:".length()).replaceFirst("^/+", "");
        }
        // Теперь s — это путь вида "E:/room/OBJ/Room.obj" или "/home/user/model.obj"
        return "file:///" + s;
    }

    private static String labelFor(Object item) {
        if (item instanceof String s)      return s;
        if (item instanceof DeviceType dt) return "Устройство: " + dt.getDisplayName();
        if (item instanceof RoomType rt)   return "Комната: " + rt.getDisplayName();
        return item.toString();
    }
}
