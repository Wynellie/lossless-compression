package org.example.testingdata;

import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.Stream;

/**
 * Общие наборы данных для JUnit 5 и TestNG.
 */
public final class CompressionDatasets {

    private CompressionDatasets() {
    }

    public static List<TextCase> textCases() {
        return List.of(
                fromResource("empty-input", "datasets/text/empty.txt"),
                fromResource("single-symbol", "datasets/text/single_char.txt"),
                fromResource("repeated-symbols", "datasets/text/repeated_chars.txt"),
                fromResource("ascii-text", "datasets/text/ascii_text.txt"),
                fromResource("unicode-utf8", "datasets/text/unicode_utf8.txt"),
                fromResource("csv-data", "datasets/text/sample.csv"),
                fromResource("json-data", "datasets/text/sample.json"),
                fromResource("large-file", "datasets/text/large_text.txt")
        );
    }

    public static Stream<Arguments> textCasesForJUnit() {
        return textCases().stream().map(c -> Arguments.of(c.name(), c.content()));
    }

    public static Object[][] textCasesForTestNg() {
        return textCases()
                .stream()
                .map(c -> new Object[]{c.name(), c.content()})
                .toArray(Object[][]::new);
    }

    public static Stream<Arguments> malformedHuffmanPayloads() {
        return Stream.of(
                Arguments.of("broken-huffman-magic", TestResourceLoader.loadHex("datasets/binary/corrupted_huffman_magic.hex")),
                Arguments.of("truncated-huffman-payload", TestResourceLoader.loadHex("datasets/binary/truncated_payload.hex"))
        );
    }

    public static Stream<Arguments> malformedLzwPayloads() {
        return Stream.of(
                Arguments.of("broken-lzw-magic", TestResourceLoader.loadHex("datasets/binary/corrupted_lzw_magic.hex")),
                Arguments.of("truncated-lzw-payload", TestResourceLoader.loadHex("datasets/binary/truncated_payload.hex"))
        );
    }

    public static Object[][] malformedHuffmanPayloadsForTestNg() {
        return malformedHuffmanPayloads()
                .map(a -> new Object[]{a.get()[0], a.get()[1]})
                .toArray(Object[][]::new);
    }

    public static Object[][] malformedLzwPayloadsForTestNg() {
        return malformedLzwPayloads()
                .map(a -> new Object[]{a.get()[0], a.get()[1]})
                .toArray(Object[][]::new);
    }

    private static TextCase fromResource(String name, String resourcePath) {
        return new TextCase(name, TestResourceLoader.loadText(resourcePath));
    }

    public record TextCase(String name, String content) {
    }
}
