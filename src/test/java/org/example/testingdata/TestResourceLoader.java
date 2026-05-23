package org.example.testingdata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Небольшой помощник для чтения тестовых ресурсов как UTF-8 текста или байтов.
 */
public final class TestResourceLoader {

    private TestResourceLoader() {
    }

    public static String loadText(String resourcePath) {
        try (InputStream input = open(resourcePath)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read resource: " + resourcePath, e);
        }
    }

    public static byte[] loadBytes(String resourcePath) {
        try (InputStream input = open(resourcePath)) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read resource: " + resourcePath, e);
        }
    }

    public static byte[] loadHex(String resourcePath) {
        String hex = loadText(resourcePath).replaceAll("\\s+", "");
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex resource must contain an even number of digits: " + resourcePath);
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int start = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(start, start + 2), 16);
        }
        return bytes;
    }

    private static InputStream open(String resourcePath) {
        InputStream stream = TestResourceLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Resource not found: " + resourcePath);
        }
        return stream;
    }
}
