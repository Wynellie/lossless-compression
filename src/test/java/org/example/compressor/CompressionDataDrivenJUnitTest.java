package org.example.compressor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Data-driven compression tests (JUnit 5)")
class CompressionDataDrivenJUnitTest {

    private final HuffmanCompressor huffman = new HuffmanCompressor();
    private final LZWCompressor lzw = new LZWCompressor();

    // JUnit 5 используется для компактной проверки базового контракта.
    @ParameterizedTest(name = "[Huffman contract] {0}")
    @MethodSource("contractInputsForRoundTrip")
    void huffmanRoundTrip_contractShouldHold(String caseName, String input) {
        byte[] compressed = huffman.compress(input);
        String restored = huffman.decompress(compressed);
        assertEquals(input, restored, caseName);
    }

    // JUnit 5 используется для компактной проверки базового контракта.
    @ParameterizedTest(name = "[LZW contract] {0}")
    @MethodSource("contractInputsForRoundTrip")
    void lzwRoundTrip_contractShouldHold(String caseName, String input) {
        byte[] compressed = lzw.compress(input);
        String restored = lzw.decompress(compressed);
        assertEquals(input, restored, caseName);
    }

    @ParameterizedTest(name = "[edge: null input] {0}")
    @MethodSource("algorithmNames")
    void compress_nullInput_shouldThrow(String algorithm) {
        Compressor compressor = "Huffman".equals(algorithm) ? huffman : lzw;
        assertThrows(NullPointerException.class, () -> compressor.compress(null), algorithm);
    }

    @ParameterizedTest(name = "[edge: empty bytes] {0}")
    @MethodSource("algorithmNames")
    void decompress_emptyBytes_shouldReturnEmptyString(String algorithm) {
        Compressor compressor = "Huffman".equals(algorithm) ? huffman : lzw;
        assertEquals("", compressor.decompress(new byte[0]), algorithm);
    }

    private static String[] algorithmNames() {
        return new String[]{"Huffman", "LZW"};
    }

    private static Stream<Arguments> contractInputsForRoundTrip() {
        return Stream.of(
                Arguments.of("empty", ""),
                Arguments.of("one character", "Z"),
                Arguments.of("repeated text", "aaaaabbbbbccccccdddddd"),
                Arguments.of("unicode", "Привет, мир! こんにちは世界 🌍"),
                Arguments.of("json", "{\"id\":1,\"name\":\"alpha\",\"flags\":[true,false]}"),
                Arguments.of("csv-like text", "id,name,score\n1,Alice,91\n2,Bob,88"),
                Arguments.of("random-looking text", "xQ9#pL2!vN8@kR4%tY1^mD7&zW0*"),
                Arguments.of("multiline text", "line-1\nline-2\nline-3\nline-4")
        );
    }
}
