package org.example.compressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Реализация сжатия по алгоритму LZW.
 */
public class LZWCompressor implements Compressor {

    private static final int MAGIC = 0x4C5A5731; // LZW1
    private static final int MAX_DICTIONARY_SIZE = 4096;

    @Override
    public byte[] compress(String data) {
        Objects.requireNonNull(data, "data");
        byte[] input = data.getBytes(StandardCharsets.UTF_8);
        if (input.length == 0) {
            return new byte[0];
        }

        Map<ByteSequence, Integer> dictionary = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            dictionary.put(new ByteSequence(new byte[]{(byte) i}), i);
        }

        int nextCode = 256;
        List<Integer> codes = new ArrayList<>();

        ByteSequence current = new ByteSequence(new byte[]{input[0]});
        for (int i = 1; i < input.length; i++) {
            byte symbol = input[i];
            ByteSequence candidate = current.append(symbol);
            Integer candidateCode = dictionary.get(candidate);
            if (candidateCode != null) {
                current = candidate;
            } else {
                codes.add(dictionary.get(current));
                if (nextCode < MAX_DICTIONARY_SIZE) {
                    dictionary.put(candidate, nextCode++);
                }
                current = new ByteSequence(new byte[]{symbol});
            }
        }
        codes.add(dictionary.get(current));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(MAGIC);
            dos.writeInt(input.length);
            dos.writeInt(codes.size());
            for (int code : codes) {
                dos.writeInt(code);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected I/O during compression", e);
        }
    }

    @Override
    public String decompress(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            return "";
        }

        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("Invalid LZW payload");
            }

            int originalLength = dis.readInt();
            if (originalLength < 0) {
                throw new IllegalArgumentException("Invalid original length");
            }

            int codeCount = dis.readInt();
            if (codeCount < 0) {
                throw new IllegalArgumentException("Invalid code count");
            }
            if (codeCount == 0) {
                if (originalLength == 0) {
                    return "";
                }
                throw new IllegalArgumentException("No codes for non-empty payload");
            }

            List<Integer> codes = new ArrayList<>(codeCount);
            for (int i = 0; i < codeCount; i++) {
                codes.add(dis.readInt());
            }

            Map<Integer, byte[]> dictionary = new HashMap<>();
            for (int i = 0; i < 256; i++) {
                dictionary.put(i, new byte[]{(byte) i});
            }

            int nextCode = 256;
            int firstCode = codes.get(0);
            byte[] previous = dictionary.get(firstCode);
            if (previous == null) {
                throw new IllegalArgumentException("Invalid first LZW code");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(previous);

            for (int i = 1; i < codes.size(); i++) {
                int code = codes.get(i);
                byte[] entry = dictionary.get(code);

                if (entry == null) {
                    if (code == nextCode) {
                        entry = concat(previous, previous[0]);
                    } else {
                        throw new IllegalArgumentException("Corrupted LZW code stream");
                    }
                }

                output.write(entry);

                if (nextCode < MAX_DICTIONARY_SIZE) {
                    dictionary.put(nextCode++, concat(previous, entry[0]));
                }
                previous = entry;
            }

            byte[] decoded = output.toByteArray();
            if (decoded.length != originalLength) {
                throw new IllegalArgumentException("Decoded length mismatch");
            }
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed LZW payload", e);
        }
    }

    private static byte[] concat(byte[] base, byte suffix) {
        byte[] result = Arrays.copyOf(base, base.length + 1);
        result[base.length] = suffix;
        return result;
    }

    private static final class ByteSequence {
        private final byte[] value;
        private final int hash;

        private ByteSequence(byte[] value) {
            this.value = value;
            this.hash = Arrays.hashCode(value);
        }

        private ByteSequence append(byte symbol) {
            byte[] extended = Arrays.copyOf(value, value.length + 1);
            extended[value.length] = symbol;
            return new ByteSequence(extended);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ByteSequence other)) {
                return false;
            }
            return Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
