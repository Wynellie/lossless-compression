package org.example.compressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Реализация сжатия по алгоритму Хаффмана.
 */
public class HuffmanCompressor implements Compressor {

    private static final int MAGIC = 0x48464D31; // HFM1

    @Override
    public byte[] compress(String data) {
        Objects.requireNonNull(data, "data");
        byte[] input = data.getBytes(StandardCharsets.UTF_8);
        if (input.length == 0) {
            return new byte[0];
        }

        int[] frequencies = new int[256];
        for (byte value : input) {
            frequencies[value & 0xFF]++;
        }

        Node root = buildTree(frequencies);
        Map<Integer, String> codeTable = new HashMap<>();
        buildCodeTable(root, "", codeTable);

        BitWriter writer = new BitWriter();
        for (byte value : input) {
            String code = codeTable.get(value & 0xFF);
            writer.writeCode(code);
        }

        byte[] payload = writer.toByteArray();
        int bitCount = writer.getBitCount();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(MAGIC);
            dos.writeInt(input.length);
            for (int frequency : frequencies) {
                dos.writeInt(frequency);
            }
            dos.writeInt(bitCount);
            dos.write(payload);
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
                throw new IllegalArgumentException("Invalid Huffman payload");
            }

            int originalLength = dis.readInt();
            if (originalLength < 0) {
                throw new IllegalArgumentException("Invalid original length");
            }

            int[] frequencies = new int[256];
            int uniqueSymbols = 0;
            Integer singleSymbol = null;
            for (int i = 0; i < frequencies.length; i++) {
                int freq = dis.readInt();
                if (freq < 0) {
                    throw new IllegalArgumentException("Invalid symbol frequency");
                }
                frequencies[i] = freq;
                if (freq > 0) {
                    uniqueSymbols++;
                    singleSymbol = i;
                }
            }

            int bitCount = dis.readInt();
            if (bitCount < 0) {
                throw new IllegalArgumentException("Invalid bit count");
            }

            byte[] payload = dis.readAllBytes();
            if (originalLength == 0) {
                return "";
            }
            if (uniqueSymbols == 0) {
                throw new IllegalArgumentException("No symbols in non-empty payload");
            }

            if (uniqueSymbols == 1) {
                byte[] output = new byte[originalLength];
                for (int i = 0; i < output.length; i++) {
                    output[i] = (byte) (singleSymbol & 0xFF);
                }
                return new String(output, StandardCharsets.UTF_8);
            }

            if (payload.length * 8L < bitCount) {
                throw new IllegalArgumentException("Payload shorter than bit count");
            }

            Node root = buildTree(frequencies);
            BitReader reader = new BitReader(payload, bitCount);
            ByteArrayOutputStream decoded = new ByteArrayOutputStream(originalLength);

            while (decoded.size() < originalLength) {
                Node current = root;
                while (!current.isLeaf()) {
                    int bit = reader.readBit();
                    if (bit == -1) {
                        throw new IllegalArgumentException("Not enough bits to decode stream");
                    }
                    current = bit == 0 ? current.left : current.right;
                    if (current == null) {
                        throw new IllegalArgumentException("Corrupted Huffman tree traversal");
                    }
                }
                decoded.write(current.symbol);
            }

            return new String(decoded.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed Huffman payload", e);
        }
    }

    private static Node buildTree(int[] frequencies) {
        PriorityQueue<Node> queue = new PriorityQueue<>(
                Comparator.comparingInt(Node::getFrequency).thenComparingInt(Node::getSymbol));
        for (int i = 0; i < frequencies.length; i++) {
            int freq = frequencies[i];
            if (freq > 0) {
                queue.add(new Node(i, freq, null, null));
            }
        }

        if (queue.isEmpty()) {
            return null;
        }

        while (queue.size() > 1) {
            Node left = queue.poll();
            Node right = queue.poll();
            queue.add(new Node(-1, left.frequency + right.frequency, left, right));
        }
        return queue.poll();
    }

    private static void buildCodeTable(Node node, String prefix, Map<Integer, String> codeTable) {
        if (node.isLeaf()) {
            codeTable.put(node.symbol, prefix.isEmpty() ? "0" : prefix);
            return;
        }
        buildCodeTable(node.left, prefix + "0", codeTable);
        buildCodeTable(node.right, prefix + "1", codeTable);
    }

    private static final class Node {
        private final int symbol;
        private final int frequency;
        private final Node left;
        private final Node right;

        private Node(int symbol, int frequency, Node left, Node right) {
            this.symbol = symbol;
            this.frequency = frequency;
            this.left = left;
            this.right = right;
        }

        private int getSymbol() {
            return symbol;
        }

        private int getFrequency() {
            return frequency;
        }

        private boolean isLeaf() {
            return left == null && right == null;
        }
    }

    private static final class BitWriter {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int currentByte = 0;
        private int bitPosition = 0;
        private int bitCount = 0;

        private void writeCode(String code) {
            for (int i = 0; i < code.length(); i++) {
                writeBit(code.charAt(i) == '1' ? 1 : 0);
            }
        }

        private void writeBit(int bit) {
            currentByte = (currentByte << 1) | (bit & 1);
            bitPosition++;
            bitCount++;
            if (bitPosition == 8) {
                output.write(currentByte);
                currentByte = 0;
                bitPosition = 0;
            }
        }

        private byte[] toByteArray() {
            if (bitPosition > 0) {
                currentByte <<= (8 - bitPosition);
                output.write(currentByte);
            }
            return output.toByteArray();
        }

        private int getBitCount() {
            return bitCount;
        }
    }

    private static final class BitReader {
        private final byte[] data;
        private final int totalBits;
        private int bitIndex = 0;

        private BitReader(byte[] data, int totalBits) {
            this.data = data;
            this.totalBits = totalBits;
        }

        private int readBit() {
            if (bitIndex >= totalBits) {
                return -1;
            }
            int byteIndex = bitIndex / 8;
            int bitOffset = 7 - (bitIndex % 8);
            int bit = (data[byteIndex] >>> bitOffset) & 1;
            bitIndex++;
            return bit;
        }
    }
}
