package org.example.compressor;

import org.example.testingdata.TestResourceLoader;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * TestNG используется для расширенной матрицы сценариев:
 * алгоритм x набор данных x ожидаемое поведение.
 */
public class CompressionDataDrivenTestNGIT {

    private enum ExpectedBehavior {
        ROUND_TRIP,
        EXCEPTION
    }

    @DataProvider(name = "validCompressionMatrix")
    public Object[][] validCompressionMatrix() {
        Object[][] datasets = new Object[][]{
                {"empty", ""},
                {"one-char", "Q"},
                {"repeated", "AAAAABBBBBCCCCCDDDDDEEEEE"},
                {"unicode", "Привет, мир! 你好世界! مرحبا 🌍"},
                {"json", "{\"event\":\"save\",\"ok\":true,\"count\":3}"},
                {"csv", "id,name,score\n1,Alice,90\n2,Bob,85"},
                {"random", "a9#B2@x!P0$lmN*7QwE%rT"},
                {"multiline", "first line\nsecond line\nthird line"}
        };
        return expandForAlgorithms(datasets, ExpectedBehavior.ROUND_TRIP);
    }

    @DataProvider(name = "invalidPayloadMatrix")
    public Object[][] invalidPayloadMatrix() {
        int[] zeroFreq = new int[256];
        int[] negativeFreq = new int[256];
        negativeFreq[0] = -1;
        int[] twoSymbols = new int[256];
        twoSymbols[0] = 1;
        twoSymbols[1] = 1;

        return new Object[][]{
                {"Huffman", "corrupted-invalid-magic", invalidMagicPayload("Huffman"), ExpectedBehavior.EXCEPTION},
                {"LZW", "corrupted-invalid-magic", invalidMagicPayload("LZW"), ExpectedBehavior.EXCEPTION},
                {"Huffman", "corrupted-truncated-payload", truncatedPayload(), ExpectedBehavior.EXCEPTION},
                {"LZW", "corrupted-truncated-payload", truncatedPayload(), ExpectedBehavior.EXCEPTION},
                {"Huffman", "validation-negative-original-length", huffmanPayload(-1, zeroFreq, 0, new byte[0]), ExpectedBehavior.EXCEPTION},
                {"Huffman", "validation-negative-frequency", huffmanPayload(1, negativeFreq, 0, new byte[0]), ExpectedBehavior.EXCEPTION},
                {"Huffman", "validation-negative-bit-count", huffmanPayload(0, zeroFreq, -1, new byte[0]), ExpectedBehavior.EXCEPTION},
                {"Huffman", "validation-non-empty-with-zero-freq", huffmanPayload(1, zeroFreq, 0, new byte[0]), ExpectedBehavior.EXCEPTION},
                {"Huffman", "validation-payload-shorter-than-bitcount", huffmanPayload(2, twoSymbols, 9, new byte[]{0}), ExpectedBehavior.EXCEPTION},
                {"Huffman", "validation-not-enough-bits-to-decode", huffmanPayload(1, twoSymbols, 0, new byte[0]), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-negative-original-length", lzwPayload(-1, 1, new int[]{65}), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-negative-code-count", lzwPayload(1, -1, new int[]{}), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-zero-codes-non-empty", lzwPayload(1, 0, new int[]{}), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-invalid-first-code", lzwPayload(1, 1, new int[]{5000}), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-corrupted-code-stream", lzwPayload(2, 2, new int[]{65, 9999}), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-decoded-length-mismatch", lzwPayload(3, 1, new int[]{65}), ExpectedBehavior.EXCEPTION},
                {"LZW", "validation-truncated-code-stream-io", lzwTruncatedPayload(5, 2, new int[]{65}), ExpectedBehavior.EXCEPTION}
        };
    }

    @DataProvider(name = "edgeCompressionMatrix")
    public Object[][] edgeCompressionMatrix() {
        return new Object[][]{
                {"Huffman", "edge-empty-bytes", new byte[0], ExpectedBehavior.ROUND_TRIP},
                {"LZW", "edge-empty-bytes", new byte[0], ExpectedBehavior.ROUND_TRIP}
        };
    }

    @DataProvider(name = "lzwSpecialMatrix")
    public Object[][] lzwSpecialMatrix() {
        return new Object[][]{
                {"LZW", "lzw-code-equals-nextCode", lzwPayload(3, 2, new int[]{65, 256}), "AAA", ExpectedBehavior.ROUND_TRIP},
                {"LZW", "lzw-zero-codes-zero-length", lzwPayload(0, 0, new int[]{}), "", ExpectedBehavior.ROUND_TRIP},
                {"LZW", "lzw-dictionary-capacity-boundary", lzwRepeatedCodePayload(3900, 65), "A".repeat(3900), ExpectedBehavior.ROUND_TRIP}
        };
    }

    @Test(dataProvider = "validCompressionMatrix", groups = {"positive", "matrix"})
    public void roundTrip_matrixShouldRestoreSource(
            String algorithm,
            String datasetName,
            String input,
            ExpectedBehavior expectedBehavior
    ) {
        assertEquals(expectedBehavior, ExpectedBehavior.ROUND_TRIP, algorithm + ":" + datasetName);
        Compressor compressor = createCompressor(algorithm);
        String restored = compressor.decompress(compressor.compress(input));
        assertEquals(restored, input, algorithm + ":" + datasetName);
    }

    @Test(dataProvider = "invalidPayloadMatrix", groups = {"negative", "matrix", "validation"})
    public void invalidPayload_matrixShouldThrow(
            String algorithm,
            String datasetName,
            byte[] payload,
            ExpectedBehavior expectedBehavior
    ) {
        assertEquals(expectedBehavior, ExpectedBehavior.EXCEPTION, algorithm + ":" + datasetName);
        Compressor compressor = createCompressor(algorithm);
        expectThrows(algorithm + ":" + datasetName, IllegalArgumentException.class, () -> compressor.decompress(payload));
    }

    @Test(dataProvider = "edgeCompressionMatrix", groups = {"edge", "matrix"})
    public void edge_matrixShouldMatchExpectedBehavior(
            String algorithm,
            String datasetName,
            byte[] payload,
            ExpectedBehavior expectedBehavior
    ) {
        assertEquals(expectedBehavior, ExpectedBehavior.ROUND_TRIP, algorithm + ":" + datasetName);
        Compressor compressor = createCompressor(algorithm);
        assertEquals(compressor.decompress(payload), "", algorithm + ":" + datasetName);
    }

    @Test(dataProvider = "lzwSpecialMatrix", groups = {"edge", "matrix", "validation"})
    public void lzwSpecial_matrixShouldDecodeExpected(
            String algorithm,
            String datasetName,
            byte[] payload,
            String expected,
            ExpectedBehavior expectedBehavior
    ) {
        assertEquals(expectedBehavior, ExpectedBehavior.ROUND_TRIP, algorithm + ":" + datasetName);
        Compressor compressor = createCompressor(algorithm);
        assertEquals(compressor.decompress(payload), expected, algorithm + ":" + datasetName);
    }

    private static Compressor createCompressor(String algorithm) {
        return "Huffman".equals(algorithm) ? new HuffmanCompressor() : new LZWCompressor();
    }

    private static Object[][] expandForAlgorithms(Object[][] datasets, ExpectedBehavior expectedBehavior) {
        Object[][] result = new Object[datasets.length * 2][4];
        int index = 0;
        for (Object[] dataset : datasets) {
            result[index++] = new Object[]{"Huffman", dataset[0], dataset[1], expectedBehavior};
            result[index++] = new Object[]{"LZW", dataset[0], dataset[1], expectedBehavior};
        }
        return result;
    }

    private static byte[] invalidMagicPayload(String algorithm) {
        if ("Huffman".equals(algorithm)) {
            return TestResourceLoader.loadHex("datasets/binary/corrupted_huffman_magic.hex");
        }
        return TestResourceLoader.loadHex("datasets/binary/corrupted_lzw_magic.hex");
    }

    private static byte[] truncatedPayload() {
        return TestResourceLoader.loadHex("datasets/binary/truncated_payload.hex");
    }

    private static byte[] huffmanPayload(int originalLength, int[] frequencies, int bitCount, byte[] payload) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeInt(0x48464D31); // HFM1
            data.writeInt(originalLength);
            for (int i = 0; i < 256; i++) {
                data.writeInt(frequencies[i]);
            }
            data.writeInt(bitCount);
            data.write(payload);
            data.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build Huffman payload", e);
        }
    }

    private static byte[] lzwPayload(int originalLength, int codeCount, int[] codes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeInt(0x4C5A5731); // LZW1
            data.writeInt(originalLength);
            data.writeInt(codeCount);
            for (int code : codes) {
                data.writeInt(code);
            }
            data.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build LZW payload", e);
        }
    }

    private static byte[] lzwTruncatedPayload(int originalLength, int declaredCodeCount, int[] writtenCodes) {
        return lzwPayload(originalLength, declaredCodeCount, writtenCodes);
    }

    private static byte[] lzwRepeatedCodePayload(int codeCount, int code) {
        int[] codes = new int[codeCount];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = code;
        }
        return lzwPayload(codeCount, codeCount, codes);
    }
}
