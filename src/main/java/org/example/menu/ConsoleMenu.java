package org.example.menu;

import org.example.compressor.Compressor;
import org.example.compressor.HuffmanCompressor;
import org.example.compressor.LZWCompressor;
import org.example.service.CompressionResult;
import org.example.service.FileService;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Консольное меню приложения.
 * Зависимости инжектируются через конструктор — класс полностью тестируем.
 */
public class ConsoleMenu {

    private static final String LINE  = "─".repeat(70);
    private static final String DLINE = "═".repeat(70);

    // Magic-числа для определения алгоритма по заголовку .bin файла
    private static final int MAGIC_HUFFMAN = 0x48464D31; // HFM1
    private static final int MAGIC_LZW     = 0x4C5A5731; // LZW1

    private final Scanner     scanner;
    private final PrintStream out;
    private final FileService fileService;
    private final Compressor  huffman;
    private final Compressor  lzw;

    /** Текущий загруженный JSON (из файла или после декомпрессии). */
    private String loadedContent;
    private String loadedSourcePath;

    /** Последние сжатые байты и соответствующий алгоритм. */
    private byte[] lastCompressedBytes;
    private String lastCompressedAlgorithmName;

    private final List<CompressionResult> sessionResults = new ArrayList<>();

    /** Создаёт меню со стандартными потоками ввода-вывода. */
    public ConsoleMenu(InputStream in, PrintStream out) {
        this(new Scanner(in, StandardCharsets.UTF_8),
             out,
             new FileService(),
             new HuffmanCompressor(),
             new LZWCompressor());
    }

    /** Конструктор для инжекции зависимостей (тесты, кастомные реализации). */
    public ConsoleMenu(Scanner scanner,
                       PrintStream out,
                       FileService fileService,
                       Compressor huffman,
                       Compressor lzw) {
        this.scanner     = scanner;
        this.out         = out;
        this.fileService = fileService;
        this.huffman     = huffman;
        this.lzw         = lzw;
    }

    // главный цикл

    public void run() {
        out.println(DLINE);
        out.println("   Утилита сжатия данных: Huffman & LZW");
        out.println(DLINE);

        boolean running = true;
        while (running) {
            printMenu();
            String choice;
            try {
                choice = scanner.nextLine().trim();
            } catch (NoSuchElementException e) {
                break;
            }

            switch (choice) {
                case "1" -> showHelp();
                case "2" -> loadJsonFile();
                case "3" -> compressData("Huffman", huffman);
                case "4" -> compressData("LZW", lzw);
                case "5" -> saveCompressedFile();
                case "6" -> loadCompressedFile();
                case "7" -> decompressData();
                case "8" -> showAndSaveComparison();
                case "9" -> {
                    out.println("Выход из программы. До свидания!");
                    running = false;
                }
                default -> out.println("Неверный выбор. Введите число от 1 до 9.");
            }
        }
    }

    // отображение меню

    private void printMenu() {
        out.println();
        if (loadedContent != null) {
            out.printf("  [ JSON: %s ]%n", loadedSourcePath);
        }
        if (lastCompressedBytes != null) {
            out.printf("  [ .bin: %s, %,d байт ]%n",
                    lastCompressedAlgorithmName, lastCompressedBytes.length);
        }
        out.println(LINE);
        out.println("  1. Справка");
        out.println("  2. Загрузить JSON-файл");
        out.println("  3. Сжать алгоритмом Хаффмана");
        out.println("  4. Сжать алгоритмом LZW");
        out.println("  5. Сохранить сжатый файл (.bin)");
        out.println("  6. Загрузить сжатый файл (.bin)");
        out.println("  7. Декомпрессировать данные");
        out.println("  8. Вывести сравнение эффективности");
        out.println("  9. Выход");
        out.println(LINE);
        out.print("  Выбор: ");
    }

    // пункт 1: справка

    void showHelp() {
        out.println();
        out.println(DLINE);
        out.println("  СПРАВКА");
        out.println(DLINE);
        out.println("  Программа позволяет сжимать JSON-данные двумя алгоритмами без потерь.");
        out.println();
        out.println("  Huffman (алгоритм Хаффмана)");
        out.println("    Строит оптимальное дерево кодирования на основе частот символов.");
        out.println("    Лучший эффект — на текстах с неравномерным распределением символов.");
        out.println();
        out.println("  LZW (Lempel-Ziv-Welch)");
        out.println("    Строит динамический словарь повторяющихся подстрок.");
        out.println("    Лучший эффект — на данных с длинными повторяющимися паттернами.");
        out.println();
        out.println("  Полный сценарий работы:");
        out.println("    1. Загрузите JSON-файл (пункт 2).");
        out.println("    2. Сожмите выбранным алгоритмом (пункты 3 или 4).");
        out.println("    3. Сохраните сжатые данные в .bin файл (пункт 5).");
        out.println("    4. При следующем запуске загрузите .bin файл (пункт 6).");
        out.println("    5. Декомпрессируйте данные (пункт 7) — JSON будет восстановлен.");
        out.println("    6. Выведите сравнение алгоритмов и сохраните отчёт (пункт 8).");
        out.println(DLINE);
    }

    // пункт 2: загрузка JSON

    void loadJsonFile() {
        out.print("  Путь к JSON-файлу: ");
        String pathStr = readLine();
        if (pathStr == null || pathStr.isBlank()) {
            out.println("  Путь не указан.");
            return;
        }

        try {
            loadedContent    = fileService.loadJson(Path.of(pathStr));
            loadedSourcePath = pathStr;
            sessionResults.clear();
            long sizeBytes = loadedContent.getBytes(StandardCharsets.UTF_8).length;
            out.printf("  Файл успешно загружен. Размер: %,d байт.%n", sizeBytes);
        } catch (IOException e) {
            out.println("  Ошибка чтения файла: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            out.println("  Ошибка данных: " + e.getMessage());
        }
    }

    // пункты 3/4: сжатие

    void compressData(String algorithmName, Compressor compressor) {
        if (loadedContent == null) {
            out.println("  Сначала загрузите JSON-файл (пункт 2).");
            return;
        }

        long originalBytes = loadedContent.getBytes(StandardCharsets.UTF_8).length;

        long   startNs    = System.nanoTime();
        byte[] compressed = compressor.compress(loadedContent);
        long   elapsedMs  = (System.nanoTime() - startNs) / 1_000_000L;

        lastCompressedBytes         = compressed;
        lastCompressedAlgorithmName = algorithmName;

        CompressionResult result = new CompressionResult(
                algorithmName, loadedSourcePath, originalBytes, compressed.length, elapsedMs);

        sessionResults.removeIf(r -> r.getAlgorithm().equals(algorithmName));
        sessionResults.add(result);

        out.printf("  %-8s | %,d → %,d байт | %.2f%% от исх. | %d мс%n",
                algorithmName,
                result.getOriginalBytes(),
                result.getCompressedBytes(),
                result.getCompressionPercent(),
                result.getElapsedMillis());
        out.println("  Сжатые данные готовы. Сохраните их в .bin файл (пункт 5).");
    }

    // пункт 5: сохранение .bin

    void saveCompressedFile() {
        if (lastCompressedBytes == null) {
            out.println("  Нет сжатых данных. Выполните сжатие (пункты 3 или 4).");
            return;
        }

        out.printf("  Алгоритм: %s | Размер: %,d байт%n",
                lastCompressedAlgorithmName, lastCompressedBytes.length);
        out.print("  Путь для сохранения .bin файла: ");
        String pathStr = readLine();
        if (pathStr == null || pathStr.isBlank()) {
            out.println("  Путь не указан.");
            return;
        }

        try {
            fileService.saveCompressedData(pathStr, lastCompressedBytes);
            out.printf("  Файл сохранён: %s%n", pathStr);
        } catch (IOException e) {
            out.println("  Ошибка сохранения: " + e.getMessage());
        }
    }

    // пункт 6: загрузка .bin

    void loadCompressedFile() {
        out.print("  Путь к .bin файлу: ");
        String pathStr = readLine();
        if (pathStr == null || pathStr.isBlank()) {
            out.println("  Путь не указан.");
            return;
        }

        try {
            byte[] data = fileService.loadCompressedData(pathStr);
            String detectedAlgorithm = detectAlgorithm(data);
            if (detectedAlgorithm == null) {
                out.println("  Ошибка: формат файла не распознан. Ожидался Huffman (.HFM1) или LZW (.LZW1).");
                return;
            }

            lastCompressedBytes         = data;
            lastCompressedAlgorithmName = detectedAlgorithm;

            out.printf("  Файл загружен: %s%n", pathStr);
            out.printf("  Определён алгоритм: %s | Размер: %,d байт%n", detectedAlgorithm, data.length);
            out.println("  Используйте пункт 7 для декомпрессии.");
        } catch (IOException e) {
            out.println("  Ошибка загрузки файла: " + e.getMessage());
        }
    }

    // пункт 7: декомпрессия

    void decompressData() {
        if (lastCompressedBytes == null) {
            out.println("  Нет сжатых данных. Загрузите .bin файл (пункт 6) или выполните сжатие (пункты 3, 4).");
            return;
        }

        Compressor compressor = resolveCompressor(lastCompressedAlgorithmName);
        if (compressor == null) {
            out.println("  Неизвестный алгоритм: " + lastCompressedAlgorithmName);
            return;
        }

        try {
            long   startNs      = System.nanoTime();
            String decompressed = compressor.decompress(lastCompressedBytes);
            long   elapsedMs    = (System.nanoTime() - startNs) / 1_000_000L;

            loadedContent    = decompressed;
            loadedSourcePath = "[декомпрессировано из " + lastCompressedAlgorithmName + "]";

            long restoredBytes = decompressed.getBytes(StandardCharsets.UTF_8).length;
            out.printf("  Декомпрессия завершена (%s, %d мс).%n", lastCompressedAlgorithmName, elapsedMs);
            out.printf("  Восстановлено: %,d байт.%n", restoredBytes);

            askSaveRestoredJson();
        } catch (IllegalArgumentException e) {
            out.println("  Ошибка декомпрессии: " + e.getMessage());
        }
    }

    private void askSaveRestoredJson() {
        out.print("  Сохранить восстановленный JSON в файл? Путь (Enter — пропустить): ");
        String pathStr = readLine();
        if (pathStr == null || pathStr.isBlank()) {
            return;
        }

        try {
            Files.writeString(Path.of(pathStr), loadedContent, StandardCharsets.UTF_8);
            out.printf("  Восстановленный JSON сохранён: %s%n", pathStr);
        } catch (IOException e) {
            out.println("  Ошибка сохранения: " + e.getMessage());
        }
    }

    // пункт 8: сравнение

    void showAndSaveComparison() {
        if (sessionResults.isEmpty()) {
            out.println("  Нет данных для сравнения. Выполните сжатие (пункты 3 или 4).");
            return;
        }

        printComparisonTable();

        out.print("  Путь для сохранения отчёта (Enter — пропустить): ");
        String outputPath = readLine();
        if (outputPath != null && !outputPath.isBlank()) {
            saveComparisonToFile(outputPath);
        }
    }

    private void printComparisonTable() {
        out.println();
        out.println(DLINE);
        out.println("  СРАВНЕНИЕ ЭФФЕКТИВНОСТИ АЛГОРИТМОВ");
        out.println(DLINE);
        out.printf("  %-10s | %15s | %14s | %10s | %10s%n",
                "Алгоритм", "Исх. (байт)", "Сжатое (байт)", "Степень %", "Время (мс)");
        out.println("  " + "─".repeat(68));
        for (CompressionResult r : sessionResults) {
            out.printf("  %-10s | %,15d | %,14d | %9.2f%% | %,9d%n",
                    r.getAlgorithm(),
                    r.getOriginalBytes(),
                    r.getCompressedBytes(),
                    r.getCompressionPercent(),
                    r.getElapsedMillis());
        }
        out.println(DLINE);

        if (sessionResults.size() > 1) {
            printRecommendations();
        }
    }

    private void printRecommendations() {
        CompressionResult best = sessionResults.stream()
                .min((a, b) -> Long.compare(a.getCompressedBytes(), b.getCompressedBytes()))
                .orElseThrow();
        out.printf("  Лучший результат по размеру: %s (%.2f%% от исходного)%n",
                best.getAlgorithm(), best.getCompressionPercent());

        CompressionResult fastest = sessionResults.stream()
                .min((a, b) -> Long.compare(a.getElapsedMillis(), b.getElapsedMillis()))
                .orElseThrow();
        out.printf("  Быстрейший алгоритм: %s (%d мс)%n",
                fastest.getAlgorithm(), fastest.getElapsedMillis());

        // Рекомендация
        CompressionResult smallerRatio = sessionResults.stream()
                .min((a, b) -> Double.compare(a.getCompressionPercent(), b.getCompressionPercent()))
                .orElseThrow();
        out.printf("  Рекомендация: для данного файла лучше подходит %s%n",
                smallerRatio.getAlgorithm());
    }

    private void saveComparisonToFile(String outputPath) {
        try {
            fileService.saveResults(Path.of(outputPath), sessionResults);
            out.println("  Отчёт сохранён: " + outputPath);
        } catch (IOException e) {
            out.println("  Ошибка сохранения: " + e.getMessage());
        }
    }

    // вспомогательные методы

    /**
     * Определяет алгоритм по первым 4 байтам (magic-заголовку) файла.
     * @return "Huffman", "LZW" или null, если формат не распознан
     */
    private static String detectAlgorithm(byte[] data) {
        if (data.length < 4) {
            return null;
        }
        int magic = ((data[0] & 0xFF) << 24)
                  | ((data[1] & 0xFF) << 16)
                  | ((data[2] & 0xFF) << 8)
                  |  (data[3] & 0xFF);
        if (magic == MAGIC_HUFFMAN) return "Huffman";
        if (magic == MAGIC_LZW)     return "LZW";
        return null;
    }

    /** Возвращает компрессор по имени алгоритма. */
    private Compressor resolveCompressor(String algorithmName) {
        if ("Huffman".equals(algorithmName)) return huffman;
        if ("LZW".equals(algorithmName))     return lzw;
        return null;
    }

    /** Читает строку с обработкой EOF. */
    private String readLine() {
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    // геттеры для тестов (package-private)

    String getLoadedContent()                    { return loadedContent; }
    String getLoadedSourcePath()                 { return loadedSourcePath; }
    byte[] getLastCompressedBytes()              { return lastCompressedBytes; }
    String getLastCompressedAlgorithmName()      { return lastCompressedAlgorithmName; }
    List<CompressionResult> getSessionResults()  { return sessionResults; }
}
