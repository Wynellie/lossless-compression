package org.example.profiler;

import java.util.Random;

/**
 * Генератор датасетов для профилировщика сжатия.
 *
 * Типы датасетов:
 *  HIGH_ENTROPY   — случайные печатаемые ASCII-символы (плохо сжимаются).
 *  LOW_ENTROPY    — цикличный паттерн из 4 символов (очень хорошо сжимается).
 *  MEDIUM_ENTROPY — структурированные JSON-записи с умеренным разнообразием.
 *
 * Все результаты — корректный JSON (не нужно экранировать кавычки внутри данных,
 * т.к. используются только «безопасные» символы).
 */
public final class DatasetGenerator {

    // Воспроизводимый генератор: одинаковые датасеты при каждом запуске
    private static final Random RNG = new Random(42L);

    /**
     * Символы без спецзначений в JSON — не требуют экранирования.
     * Высокая энтропия: ~86 уникальных символов.
     */
    private static final String HIGH_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" +
            "0123456789!@#$%^&*()_-+=[]{}|;:,.?~";

    /** Низкая энтропия: только 4 уникальных символа. */
    private static final String LOW_PATTERN = "aaaabbbccd";

    private static final String[] NAMES  = {"Alice","Bob","Charlie","Diana","Eve","Frank","Grace","Henry"};
    private static final String[] CITIES = {"Moscow","London","Paris","Berlin","Tokyo","Rome","Madrid","Seoul"};

    private DatasetGenerator() {}

    // ── Генераторы ─────────────────────────────────────────────────────────────

    /**
     * Высокая энтропия: случайные символы внутри JSON-строки.
     * Итоговый JSON ≈ targetBytes байт (все символы ASCII → 1 байт/символ).
     */
    public static String generateHighEntropy(int targetBytes) {
        // Обёртка: {"t":"high","d":"<data>"}
        String prefix = "{\"t\":\"high\",\"d\":\"";
        String suffix = "\"}";
        int dataLen = Math.max(1, targetBytes - prefix.length() - suffix.length());

        StringBuilder sb = new StringBuilder(prefix.length() + dataLen + suffix.length());
        sb.append(prefix);
        for (int i = 0; i < dataLen; i++) {
            sb.append(HIGH_CHARS.charAt(RNG.nextInt(HIGH_CHARS.length())));
        }
        sb.append(suffix);
        return sb.toString();
    }

    /**
     * Низкая энтропия: циклический 10-символьный паттерн.
     */
    public static String generateLowEntropy(int targetBytes) {
        String prefix = "{\"t\":\"low\",\"d\":\"";
        String suffix = "\"}";
        int dataLen = Math.max(1, targetBytes - prefix.length() - suffix.length());

        StringBuilder sb = new StringBuilder(prefix.length() + dataLen + suffix.length());
        sb.append(prefix);
        for (int i = 0; i < dataLen; i++) {
            sb.append(LOW_PATTERN.charAt(i % LOW_PATTERN.length()));
        }
        sb.append(suffix);
        return sb.toString();
    }

    /**
     * Средняя энтропия: JSON-массив структурированных записей.
     * Строим записи до тех пор, пока не достигнем targetBytes.
     */
    public static String generateMediumEntropy(int targetBytes) {
        String header = "{\"t\":\"medium\",\"records\":[";
        String footer = "]}";
        // Резерв под footer + запятую
        int budget = targetBytes - header.length() - footer.length() - 2;

        StringBuilder sb = new StringBuilder(targetBytes + 64);
        sb.append(header);

        int id = 1;
        boolean first = true;
        while (sb.length() < header.length() + budget) {
            String name  = NAMES [(id - 1) % NAMES.length];
            String city  = CITIES[(id - 1) % CITIES.length];
            int    score = 50 + (id % 50);
            String record = String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"city\":\"%s\",\"score\":%d}",
                    id, name, city, score);
            if (!first) sb.append(',');
            sb.append(record);
            first = false;
            id++;
        }

        sb.append(footer);
        return sb.toString();
    }

    // ── Вспомогательный метод ─────────────────────────────────────────────────

    /**
     * Делегирует генерацию нужному методу по имени типа.
     */
    public static String generate(String entropyType, int targetBytes) {
        return switch (entropyType) {
            case "HIGH_ENTROPY"   -> generateHighEntropy(targetBytes);
            case "LOW_ENTROPY"    -> generateLowEntropy(targetBytes);
            case "MEDIUM_ENTROPY" -> generateMediumEntropy(targetBytes);
            default -> throw new IllegalArgumentException("Unknown entropy type: " + entropyType);
        };
    }
}
