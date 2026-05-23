package org.example.profiler;

import org.example.compressor.HuffmanCompressor;
import org.example.compressor.LZWCompressor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Профилировщик алгоритмов сжатия Хаффмана и LZW.
 *
 * <p>Алгоритм работы:
 * <ol>
 *   <li>Для каждого типа энтропии и каждого размера генерирует датасет.</li>
 *   <li>Выполняет {@value #WARMUP_RUNS} прогревочных прогонов (JIT-компиляция).</li>
 *   <li>Замеряет {@value #MEASURE_RUNS} прогонов компрессии и декомпрессии.</li>
 *   <li>Автоматически останавливается при OutOfMemoryError или превышении таймаута.</li>
 *   <li>Сохраняет все замеры в CSV для дальнейшего статистического анализа.</li>
 * </ol>
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=org.example.profiler.CompressionProfiler}
 */
public final class CompressionProfiler {

    // ── Константы ─────────────────────────────────────────────────────────────

    /** Прогревочные прогоны (не попадают в CSV). */
    private static final int WARMUP_RUNS   = 5;

    /** Измерительных прогонов на датасет × алгоритм. */
    private static final int MEASURE_RUNS  = 30;

    /**
     * Таймаут одного прогона: 10 секунд в наносекундах.
     * При превышении данный датасет × алгоритм помечается тайм-аутом и пропускается.
     */
    private static final long TIMEOUT_NS   = 10_000_000_000L;

    /**
     * Размеры датасетов в КБ.
     * Профилировщик идёт по ним в порядке возрастания и останавливается
     * при первом OOM или тайм-ауте для данного типа энтропии.
     */
    private static final int[] SIZES_KB = {1, 2, 5, 10, 50, 100, 500, 1024, 5120};

    private static final String[] ENTROPY_TYPES = {"HIGH_ENTROPY", "LOW_ENTROPY", "MEDIUM_ENTROPY"};

    private static final String CSV_HEADER =
            "dataset,entropy_type,size_kb,algorithm,run_id," +
            "compress_ms,decompress_ms,input_bytes,compressed_bytes,compression_ratio";

    // ── Точка входа ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        Path outDir     = Path.of("profiler_results");
        Path datasetDir = outDir.resolve("datasets");
        Files.createDirectories(datasetDir);

        HuffmanCompressor huffman = new HuffmanCompressor();
        LZWCompressor     lzw    = new LZWCompressor();

        List<String> csvRows = new ArrayList<>();
        csvRows.add(CSV_HEADER);

        System.out.println("=== CompressionProfiler ===");
        System.out.printf("Warmup: %d runs | Measure: %d runs | Timeout: %ds%n%n",
                WARMUP_RUNS, MEASURE_RUNS, TIMEOUT_NS / 1_000_000_000);

        for (String entropyType : ENTROPY_TYPES) {
            System.out.printf("── %s ──────────────────────────────────%n", entropyType);

            for (int sizeKb : SIZES_KB) {
                int targetBytes = sizeKb * 1024;
                String datasetName = buildDatasetName(entropyType, sizeKb);

                // ── Генерация датасета ────────────────────────────────────────
                String data;
                try {
                    data = DatasetGenerator.generate(entropyType, targetBytes);
                } catch (OutOfMemoryError e) {
                    System.out.printf("  [OOM] Генерация %s — останов%n", datasetName);
                    break;
                }

                int inputBytes = data.getBytes(StandardCharsets.UTF_8).length;

                // Сохраняем датасет на диск (удобно для воспроизводимости)
                Files.writeString(datasetDir.resolve(datasetName), data, StandardCharsets.UTF_8);

                System.out.printf("  %s (%d КБ, %d байт)%n", datasetName, sizeKb, inputBytes);

                // ── Профилирование каждого алгоритма ─────────────────────────
                boolean stopHuffman = false;
                boolean stopLZW     = false;

                for (int algoIdx = 0; algoIdx < 2; algoIdx++) {
                    boolean isHuffman   = (algoIdx == 0);
                    String  algoName    = isHuffman ? "Huffman" : "LZW";
                    var     compressor  = isHuffman ? huffman : lzw;
                    boolean shouldStop  = isHuffman ? stopHuffman : stopLZW;

                    if (shouldStop) continue;

                    // ── Прогрев (JIT + caches) ────────────────────────────────
                    boolean warmupFailed = false;
                    try {
                        for (int w = 0; w < WARMUP_RUNS; w++) {
                            byte[] compressed = compressor.compress(data);
                            compressor.decompress(compressed);
                        }
                    } catch (OutOfMemoryError e) {
                        System.out.printf("    [OOM] Прогрев %s/%s — пропуск%n", datasetName, algoName);
                        warmupFailed = true;
                    }
                    if (warmupFailed) continue;

                    // ── Замерные прогоны ──────────────────────────────────────
                    boolean timedOut = false;
                    for (int run = 1; run <= MEASURE_RUNS; run++) {
                        try {
                            // Минимизируем GC-помехи
                            System.gc();

                            long t0 = System.nanoTime();
                            byte[] compressed = compressor.compress(data);
                            long t1 = System.nanoTime();
                            compressor.decompress(compressed);
                            long t2 = System.nanoTime();

                            double compressMs   = (t1 - t0) / 1e6;
                            double decompressMs = (t2 - t1) / 1e6;

                            // Проверка таймаута
                            if ((t1 - t0) > TIMEOUT_NS || (t2 - t1) > TIMEOUT_NS) {
                                System.out.printf("    [TIMEOUT] %s/%s прогон %d%n",
                                        datasetName, algoName, run);
                                timedOut = true;
                                break;
                            }

                            double ratio = (double) inputBytes / Math.max(compressed.length, 1);

                            csvRows.add(String.format(Locale.US,
                                    "%s,%s,%d,%s,%d,%.6f,%.6f,%d,%d,%.6f",
                                    datasetName, entropyType, sizeKb, algoName, run,
                                    compressMs, decompressMs,
                                    inputBytes, compressed.length, ratio));

                        } catch (OutOfMemoryError e) {
                            System.out.printf("    [OOM] %s/%s прогон %d%n",
                                    datasetName, algoName, run);
                            timedOut = true;
                            break;
                        }
                    }

                    if (!timedOut) {
                        System.out.printf("    %-7s — %d замеров ОК%n", algoName, MEASURE_RUNS);
                    } else {
                        // При тайм-ауте на данном размере нет смысла пробовать большие
                        if (isHuffman) stopHuffman = true;
                        else           stopLZW     = true;
                    }
                }

                // Если оба алгоритма упёрлись в лимит — прекращаем этот тип энтропии
                if (stopHuffman && stopLZW) {
                    System.out.printf("  Оба алгоритма достигли предела на %d КБ — останов%n", sizeKb);
                    break;
                }
            }
            System.out.println();
        }

        // ── Запись CSV ────────────────────────────────────────────────────────
        Path csvPath = outDir.resolve("results.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            csvRows.forEach(pw::println);
        }

        int dataRows = csvRows.size() - 1; // минус заголовок
        System.out.printf("Готово. Записано %d строк → %s%n", dataRows, csvPath.toAbsolutePath());
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    /**
     * Формирует имя файла датасета.
     * Примеры: DS_HIGH_ENTROPY_1KB.json, DS_LOW_ENTROPY_1MB.json
     */
    private static String buildDatasetName(String entropyType, int sizeKb) {
        String sizeLabel;
        if (sizeKb < 1024) {
            sizeLabel = sizeKb + "KB";
        } else {
            sizeLabel = (sizeKb / 1024) + "MB";
        }
        return String.format("DS_%s_%s.json", entropyType, sizeLabel);
    }
}
