package org.example.compressor;

/**
 * Интерфейс для алгоритмов сжатия данных.
 */
public interface Compressor {

    /**
     * Сжимает строку в массив байт.
     *
     * @param data исходная строка
     * @return сжатые данные
     */
    byte[] compress(String data);

    /**
     * Распаковывает массив байт в строку.
     *
     * @param data сжатые данные
     * @return восстановленная строка
     */
    String decompress(byte[] data);
}
