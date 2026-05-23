package org.example.service;

import java.time.Instant;

/**
 * Иммутабельный DTO со статистикой одного запуска алгоритма сжатия.
 */
public final class CompressionResult {

    private final String algorithm;
    private final String sourceFile;
    private final long originalBytes;
    private final long compressedBytes;
    private final double compressionPercent;   // compressedBytes / originalBytes * 100
    private final long elapsedMillis;
    private final String timestamp;

    public CompressionResult(String algorithm,
                             String sourceFile,
                             long originalBytes,
                             long compressedBytes,
                             long elapsedMillis) {
        this.algorithm        = algorithm;
        this.sourceFile       = sourceFile;
        this.originalBytes    = originalBytes;
        this.compressedBytes  = compressedBytes;
        this.compressionPercent = originalBytes > 0
                ? (double) compressedBytes / originalBytes * 100.0
                : 0.0;
        this.elapsedMillis = elapsedMillis;
        this.timestamp     = Instant.now().toString();
    }

    public String getAlgorithm()         { return algorithm; }
    public String getSourceFile()        { return sourceFile; }
    public long   getOriginalBytes()     { return originalBytes; }
    public long   getCompressedBytes()   { return compressedBytes; }
    public double getCompressionPercent(){ return compressionPercent; }
    public long   getElapsedMillis()     { return elapsedMillis; }
    public String getTimestamp()         { return timestamp; }
}
