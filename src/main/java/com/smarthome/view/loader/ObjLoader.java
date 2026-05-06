package com.smarthome.view.loader;

import org.fxyz3d.importers.Model3D;
import org.fxyz3d.importers.obj.ObjImporter;

import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Загрузчик 3D моделей в формате Wavefront OBJ.
 * Использует библиотеку FXyz3D (org.fxyz3d:fxyz3d-importers).
 *
 * Возможности FXyz3D поверх кастомной реализации:
 *  - текстуры (map_Kd из MTL-файла)
 *  - сглаженные нормали (vn)
 *  - несколько объектов в одном файле (o/g)
 *  - корректная триангуляция вогнутых полигонов
 */
public class ObjLoader {

    private static final double TARGET_SIZE = 60.0;

    /** Загрузить OBJ по URL (http/https) или file-ссылке */
    public Group loadUrl(String urlString) throws IOException {
        URL url = parseUrl(urlString);
        return load(url);
    }

    /**
     * URI.create() падает на не-ASCII символах (кириллица в пути к файлу).
     * Для file:-ссылок декодируем %XX, берём путь через Path и перестраиваем URL.
     */
    private static URL parseUrl(String urlString) throws IOException {
        try {
            return URI.create(urlString).toURL();
        } catch (IllegalArgumentException e) {
            if (urlString.startsWith("file:")) {
                // Убираем схему и лишние слэши: file:/ file:// file:///
                String pathStr = urlString.replaceFirst("^file:/{1,3}", "");
                // На Windows путь начинается с буквы диска (C:/...), на Unix — с /
                if (!pathStr.startsWith("/") && !pathStr.contains(":")) {
                    pathStr = "/" + pathStr;
                }
                // Декодируем %20 и прочие %XX обратно в символы
                pathStr = URLDecoder.decode(pathStr, StandardCharsets.UTF_8);
                return Path.of(pathStr).toUri().toURL();
            }
            throw new IOException("Некорректный URL: " + urlString, e);
        }
    }

    /** Загрузить OBJ из локального файла */
    public Group loadFile(Path path) throws IOException {
        return load(path.toUri().toURL());
    }

    private Group load(URL url) throws IOException {
        ObjImporter.setScale(1.0f);   // авто-масштаб делаем сами
        ObjImporter.setFlatXZ(false); // не плющить Y
        ObjImporter importer = new ObjImporter();
        Model3D model = importer.load(url);
        Group root = model.getRoot();
        autoScale(root);
        return root;
    }

    /**
     * Вписывает модель в куб TARGET_SIZE единиц и центрирует в начале координат.
     * Это нужно, потому что OBJ-модели из интернета приходят в произвольных масштабах.
     */
    private void autoScale(Group group) {
        Bounds b = group.getBoundsInLocal();
        if (b.isEmpty() || b.getWidth() == 0) return;

        double maxDim = Math.max(b.getWidth(), Math.max(b.getHeight(), b.getDepth()));
        double scale  = TARGET_SIZE / maxDim;
        double cx     = (b.getMinX() + b.getMaxX()) / 2.0;
        double cy     = (b.getMinY() + b.getMaxY()) / 2.0;
        double cz     = (b.getMinZ() + b.getMaxZ()) / 2.0;

        // Сначала сдвигаем центр в 0, потом масштабируем
        group.getTransforms().addAll(
                new Scale(scale, scale, scale),
                new Translate(-cx, -cy, -cz)
        );
    }
}
