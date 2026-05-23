package org.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Сервис файлового ввода-вывода:
 * загрузка исходного JSON-файла и сохранение отчёта со статистикой сжатия.
 */
public class FileService {

    private final Gson gson;

    public FileService() {
        this.gson = new GsonBuilder()
                .setStrictness(Strictness.STRICT)
                .setPrettyPrinting()
                .create();
    }

    /**
     * Читает файл по указанному пути, проверяет, что содержимое является
     * корректным JSON, и возвращает содержимое в виде строки.
     *
     * @param path путь к JSON-файлу
     * @return содержимое файла
     * @throws IOException              если файл не найден, не доступен для чтения
     *                                  или является директорией
     * @throws IllegalArgumentException если файл пустой или содержит невалидный JSON
     * @throws NullPointerException     если path == null
     */
    public String loadJson(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        if (Files.isDirectory(path)) {
            throw new IOException("Указанный путь является директорией: " + path);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);

        if (content.isBlank()) {
            throw new IllegalArgumentException("Файл не содержит данных: " + path);
        }

        try {
            gson.fromJson(content, JsonElement.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException(
                    "Файл содержит некорректный JSON: " + path, e);
        }

        return content;
    }

    /**
     * Сохраняет сжатые данные (массив байт) в файл по указанному пути.
     *
     * @param path путь для записи файла
     * @param data сжатые данные (может быть пустым массивом)
     * @throws IOException          если запись не удалась
     * @throws NullPointerException если path или data == null
     */
    public void saveCompressedData(String path, byte[] data) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(data, "data");

        Path filePath = Path.of(path);
        if (Files.isDirectory(filePath)) {
            throw new IOException("Указанный путь является директорией: " + path);
        }

        Files.write(filePath, data);
    }

    /**
     * Загружает сжатые данные из файла по указанному пути.
     *
     * @param path путь к файлу
     * @return содержимое файла в виде массива байт
     * @throws IOException          если файл не найден или недоступен для чтения
     * @throws NullPointerException если path == null
     */
    public byte[] loadCompressedData(String path) throws IOException {
        Objects.requireNonNull(path, "path");

        Path filePath = Path.of(path);
        if (Files.isDirectory(filePath)) {
            throw new IOException("Указанный путь является директорией: " + path);
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Сериализует список результатов сжатия в JSON-файл.
     * Файл включает метку времени генерации и массив результатов.
     *
     * @param path    путь для записи отчёта
     * @param results список результатов (может быть пустым)
     * @throws IOException          если запись не удалась
     * @throws NullPointerException если path или results == null
     */
    public void saveResults(Path path, List<CompressionResult> results) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(results, "results");

        Report report = new Report(Instant.now().toString(), results);
        Files.writeString(path, gson.toJson(report), StandardCharsets.UTF_8);
    }

    // ── внутренняя структура для сериализации ──────────────────────────────

    private static final class Report {
        @SuppressWarnings("unused")
        private final String generatedAt;
        @SuppressWarnings("unused")
        private final List<CompressionResult> results;

        private Report(String generatedAt, List<CompressionResult> results) {
            this.generatedAt = generatedAt;
            this.results     = results;
        }
    }
}
