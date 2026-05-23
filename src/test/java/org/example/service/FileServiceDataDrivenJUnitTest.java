package org.example.service;

import org.example.testingdata.CompressionDatasets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Data-driven FileService tests (JUnit 5)")
class FileServiceDataDrivenJUnitTest {

    private final FileService fileService = new FileService();

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "[valid json] {0}")
    @MethodSource("validJsonCases")
    void loadJson_validContent_shouldReturnSameText(String caseName, String content) throws IOException {
        Path file = tempDir.resolve(caseName + ".json");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        String loaded = fileService.loadJson(file);

        assertEquals(content, loaded);
    }

    @ParameterizedTest(name = "[invalid json] {0}")
    @MethodSource("invalidJsonCases")
    void loadJson_invalidContent_shouldThrow(String caseName, String content) throws IOException {
        Path file = tempDir.resolve(caseName + ".json");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> fileService.loadJson(file), caseName);
    }

    @ParameterizedTest(name = "[binary round-trip] {0}")
    @MethodSource("binaryPayloadCases")
    void compressedData_fileRoundTrip_shouldKeepBytes(String caseName, byte[] payload) throws IOException {
        Path file = tempDir.resolve(caseName + ".bin");
        fileService.saveCompressedData(file.toString(), payload);
        byte[] restored = fileService.loadCompressedData(file.toString());

        assertArrayEquals(payload, restored, caseName);
    }

    @ParameterizedTest(name = "[save results] {0}")
    @MethodSource("resultsCases")
    void saveResults_shouldCreateValidJson(String caseName, List<CompressionResult> results) throws IOException {
        Path report = tempDir.resolve(caseName + "-report.json");
        fileService.saveResults(report, results);

        String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"results\""), caseName);
        assertTrue(text.contains("\"generatedAt\""), caseName);
        assertDoesNotThrow(() -> com.google.gson.JsonParser.parseString(text), caseName);
    }

    @Test
    void loadJson_directoryPath_shouldThrowIOException() {
        assertThrows(IOException.class, () -> fileService.loadJson(tempDir));
    }

    @Test
    void saveCompressedData_directoryPath_shouldThrowIOException() {
        assertThrows(IOException.class, () -> fileService.saveCompressedData(tempDir.toString(), new byte[]{1, 2, 3}));
    }

    @Test
    void loadCompressedData_directoryPath_shouldThrowIOException() {
        assertThrows(IOException.class, () -> fileService.loadCompressedData(tempDir.toString()));
    }

    private static Stream<Arguments> validJsonCases() {
        return Stream.of(
                Arguments.of("json-object", "{\"k\":\"v\"}"),
                Arguments.of("json-array", "[1,2,3]"),
                Arguments.of("json-unicode", "{\"msg\":\"Привет мир\"}")
        );
    }

    private static Stream<Arguments> invalidJsonCases() {
        return Stream.of(
                Arguments.of("blank-content", "  \n\t"),
                Arguments.of("truncated", "{\"k\":"),
                Arguments.of("plain-text", "not-json")
        );
    }

    private static Stream<Arguments> binaryPayloadCases() {
        return CompressionDatasets.textCases()
                .stream()
                .map(c -> Arguments.of(c.name(), c.content().getBytes(StandardCharsets.UTF_8)));
    }

    private static Stream<Arguments> resultsCases() {
        return Stream.of(
                Arguments.of("empty-results", List.of()),
                Arguments.of("single-result",
                        List.of(new CompressionResult("Huffman", "sample.json", 100, 60, 2))),
                Arguments.of("two-results",
                        List.of(
                                new CompressionResult("Huffman", "sample.json", 100, 60, 2),
                                new CompressionResult("LZW", "sample.json", 100, 70, 1)
                        ))
        );
    }
}
