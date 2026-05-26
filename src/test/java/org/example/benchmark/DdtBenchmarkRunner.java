package org.example.benchmark;

import org.example.compressor.Compressor;
import org.example.compressor.HuffmanCompressor;
import org.example.compressor.LZWCompressor;
import org.example.testingdata.TestResourceLoader;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class DdtBenchmarkRunner {

    private static final int MEASURED_RUNS = 25;
    private static final int WARMUP_RUNS = 1;
    private static final double T_95_N25 = 2.064d;
    private static final DecimalFormat DECIMAL = new DecimalFormat("0.000000", DecimalFormatSymbols.getInstance(Locale.US));

    private DdtBenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("target", "ddt-benchmark");
        Files.createDirectories(outputDir);
        Files.createDirectories(Path.of("target", "failsafe-reports"));

        List<Scenario> scenarios = buildScenarios();
        List<RunRecord> runs = new ArrayList<>();
        List<SummaryRecord> summary = new ArrayList<>();

        boolean hasFailures = false;
        for (Scenario scenario : scenarios) {
            executeWarmup(scenario);
            List<Double> times = new ArrayList<>(MEASURED_RUNS);
            for (int run = 1; run <= MEASURED_RUNS; run++) {
                long start = System.nanoTime();
                String result = "PASS";
                try {
                    executeScenario(scenario);
                } catch (RuntimeException ex) {
                    result = "FAIL";
                    hasFailures = true;
                }
                long end = System.nanoTime();
                double timeMs = (end - start) / 1_000_000.0d;
                runs.add(new RunRecord(scenario.algorithm, scenario.dataset, scenario.expectedBehavior, run, timeMs, result));
                times.add(timeMs);
            }
            summary.add(SummaryRecord.of(scenario, times));
        }

        Path rawCsv = outputDir.resolve("benchmark_raw_runs.csv");
        Path summaryCsv = outputDir.resolve("benchmark_results.csv");
        writeRawCsv(rawCsv, runs);
        writeSummaryCsv(summaryCsv, summary);
        writeLatexContext(outputDir.resolve("latex_context.md"), summary, rawCsv, summaryCsv);

        if (hasFailures) {
            throw new IllegalStateException("DDT benchmark contains FAIL scenarios. See target/ddt-benchmark/benchmark_raw_runs.csv");
        }
    }

    private static void executeWarmup(Scenario scenario) {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            executeScenario(scenario);
        }
    }

    private static void executeScenario(Scenario scenario) {
        Compressor compressor = "Huffman".equals(scenario.algorithm) ? new HuffmanCompressor() : new LZWCompressor();
        if ("ROUND_TRIP".equals(scenario.expectedBehavior)) {
            byte[] compressed = compressor.compress(scenario.validInput);
            String restored = compressor.decompress(compressed);
            if (!scenario.validInput.equals(restored)) {
                throw new IllegalStateException("ROUND_TRIP mismatch");
            }
            return;
        }
        try {
            compressor.decompress(scenario.corruptedPayload);
            throw new IllegalStateException("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static List<Scenario> buildScenarios() {
        Map<String, String> validDatasets = new LinkedHashMap<>();
        validDatasets.put("empty", TestResourceLoader.loadText("datasets/text/empty.txt"));
        validDatasets.put("repeated", TestResourceLoader.loadText("datasets/text/repeated_chars.txt"));
        validDatasets.put("random", deterministicRandomText());
        validDatasets.put("json", TestResourceLoader.loadText("datasets/text/sample.json"));
        validDatasets.put("csv", TestResourceLoader.loadText("datasets/text/sample.csv"));
        validDatasets.put("unicode", TestResourceLoader.loadText("datasets/text/unicode_utf8.txt"));
        validDatasets.put("large", TestResourceLoader.loadText("datasets/text/large_text.txt"));

        List<Scenario> scenarios = new ArrayList<>();
        for (String algorithm : List.of("Huffman", "LZW")) {
            for (Map.Entry<String, String> entry : validDatasets.entrySet()) {
                scenarios.add(Scenario.roundTrip(algorithm, entry.getKey(), entry.getValue()));
            }
            byte[] corrupted = "Huffman".equals(algorithm)
                    ? TestResourceLoader.loadHex("datasets/binary/corrupted_huffman_magic.hex")
                    : TestResourceLoader.loadHex("datasets/binary/corrupted_lzw_magic.hex");
            scenarios.add(Scenario.exception(algorithm, "corrupted", corrupted));
        }

        scenarios.sort(Comparator.comparing((Scenario s) -> s.algorithm).thenComparing(s -> s.dataset));
        return scenarios;
    }

    private static String deterministicRandomText() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{};':\",./<>?|`~";
        Random random = new Random(20260525L);
        StringBuilder sb = new StringBuilder(4096);
        for (int i = 0; i < 4096; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static void writeRawCsv(Path path, List<RunRecord> runs) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.println("algorithm,dataset,expected_behavior,run,time_ms,result");
            for (RunRecord run : runs) {
                writer.printf(Locale.US, "%s,%s,%s,%d,%s,%s%n",
                        run.algorithm, run.dataset, run.expectedBehavior, run.run, DECIMAL.format(run.timeMs), run.result);
            }
        }
    }

    private static void writeSummaryCsv(Path path, List<SummaryRecord> summary) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.println("algorithm,dataset,expected_behavior,n,mean_ms,variance,stddev_ms,ci95_low_ms,ci95_high_ms");
            for (SummaryRecord s : summary) {
                writer.printf(Locale.US, "%s,%s,%s,%d,%s,%s,%s,%s,%s%n",
                        s.algorithm, s.dataset, s.expectedBehavior, s.n,
                        DECIMAL.format(s.meanMs), DECIMAL.format(s.variance), DECIMAL.format(s.stddevMs),
                        DECIMAL.format(s.ci95LowMs), DECIMAL.format(s.ci95HighMs));
            }
        }
    }

    private static void writeLatexContext(Path path, List<SummaryRecord> summary, Path rawCsv, Path summaryCsv) throws Exception {
        TestReportSummary junit = parseJUnitSummary(Path.of("target", "surefire-reports"));
        TestNgReportSummary testng = parseTestNgSummary(Path.of("test-output", "testng-results.xml"));
        CoverageSummary coverage = parseJaCoCoSummary(Path.of("target", "site", "jacoco", "jacoco.xml"));

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.println("# DDT Benchmark Context");
            writer.println();
            writer.println("Scenarios executed as matrix: `algorithm × dataset × expected behavior`.");
            writer.println("Algorithms: Huffman, LZW.");
            writer.println("Datasets: empty, repeated, random, json, csv, unicode, large, corrupted.");
            writer.println("`ROUND_TRIP` means `decompress(compress(x)) = x`.");
            writer.println("`EXCEPTION` means expected `IllegalArgumentException` on corrupted payload.");
            writer.println("Each scenario was executed with 1 warm-up run (excluded) and 25 measured runs.");
            writer.println();
            writer.println("CSV outputs:");
            writer.println("- `target/ddt-benchmark/benchmark_raw_runs.csv`");
            writer.println("- `target/ddt-benchmark/benchmark_results.csv`");
            writer.println();
            writer.println("## Aggregated results (25 runs)");
            writer.println();
            writer.println("| algorithm | dataset | expected_behavior | n | mean_ms | variance | stddev_ms | ci95_low_ms | ci95_high_ms |");
            writer.println("|---|---|---:|---:|---:|---:|---:|---:|---:|");
            for (SummaryRecord s : summary) {
                writer.printf(Locale.US, "| %s | %s | %s | %d | %s | %s | %s | %s | %s |%n",
                        s.algorithm, s.dataset, s.expectedBehavior, s.n,
                        DECIMAL.format(s.meanMs), DECIMAL.format(s.variance), DECIMAL.format(s.stddevMs),
                        DECIMAL.format(s.ci95LowMs), DECIMAL.format(s.ci95HighMs));
            }
            writer.println();
            writer.println("## Run commands");
            writer.println();
            writer.println("- Windows: `./mvnw.cmd clean verify`");
            writer.println("- Benchmark profile: `./mvnw.cmd clean verify -Pbenchmark`");
            writer.println("- Unix: `./mvnw clean verify`");
            writer.println("- Unix benchmark profile: `./mvnw clean verify -Pbenchmark`");
            writer.println();
            writer.println("Benchmark is enabled only with Maven profile `benchmark`.");
            writer.println();
            writer.println("## Actual JUnit / TestNG / JaCoCo results");
            writer.println();
            writer.printf(Locale.US, "- JUnit (Surefire): tests=%d, failures=%d, errors=%d, skipped=%d%n",
                    junit.tests, junit.failures, junit.errors, junit.skipped);
            writer.printf(Locale.US, "- TestNG: total=%d, passed=%d, failed=%d, skipped=%d%n",
                    testng.total, testng.passed, testng.failed, testng.skipped);
            writer.printf(Locale.US, "- JaCoCo line coverage: %s%%%n", DECIMAL.format(coverage.lineCoveragePct));
            writer.printf(Locale.US, "- JaCoCo branch coverage: %s%%%n", DECIMAL.format(coverage.branchCoveragePct));
            writer.println();
            writer.println("## Files for LaTeX editor");
            writer.println();
            writer.println("Use these files:");
            writer.println("- target/ddt-benchmark/benchmark_results.csv");
            writer.println("- target/ddt-benchmark/benchmark_raw_runs.csv");
            writer.println("- target/ddt-benchmark/latex_context.md");
            writer.println("- target/site/jacoco/jacoco.xml");
            writer.println("- target/surefire-reports/");
            writer.println("- target/failsafe-reports/");
            writer.println();
            writer.printf("Generated files:%n- `%s`%n- `%s`%n",
                    rawCsv.toString().replace('\\', '/'),
                    summaryCsv.toString().replace('\\', '/'));
        }
    }

    private static TestReportSummary parseJUnitSummary(Path reportsDir) throws Exception {
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        if (!Files.isDirectory(reportsDir)) {
            return new TestReportSummary(0, 0, 0, 0);
        }
        var factory = secureDocumentBuilderFactory();
        for (Path file : Files.list(reportsDir).filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml")).toList()) {
            var doc = factory.newDocumentBuilder().parse(file.toFile());
            var root = doc.getDocumentElement();
            tests += Integer.parseInt(root.getAttribute("tests"));
            failures += Integer.parseInt(root.getAttribute("failures"));
            errors += Integer.parseInt(root.getAttribute("errors"));
            skipped += Integer.parseInt(root.getAttribute("skipped"));
        }
        return new TestReportSummary(tests, failures, errors, skipped);
    }

    private static TestNgReportSummary parseTestNgSummary(Path testngXml) throws Exception {
        if (!Files.exists(testngXml)) {
            return new TestNgReportSummary(0, 0, 0, 0);
        }
        var factory = secureDocumentBuilderFactory();
        var doc = factory.newDocumentBuilder().parse(testngXml.toFile());
        var root = doc.getDocumentElement();
        int total = Integer.parseInt(root.getAttribute("total"));
        int passed = Integer.parseInt(root.getAttribute("passed"));
        int failed = Integer.parseInt(root.getAttribute("failed"));
        int skipped = Integer.parseInt(root.getAttribute("skipped"));
        return new TestNgReportSummary(total, passed, failed, skipped);
    }

    private static CoverageSummary parseJaCoCoSummary(Path jacocoXml) throws Exception {
        if (!Files.exists(jacocoXml)) {
            return new CoverageSummary(0, 0);
        }
        var factory = secureDocumentBuilderFactory();
        var doc = factory.newDocumentBuilder().parse(jacocoXml.toFile());
        double lineCoverage = 0;
        double branchCoverage = 0;
        var reportChildren = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < reportChildren.getLength(); i++) {
            var node = reportChildren.item(i);
            if (!"counter".equals(node.getNodeName())) {
                continue;
            }
            var attrs = node.getAttributes();
            String type = attrs.getNamedItem("type").getNodeValue();
            long missed = Long.parseLong(attrs.getNamedItem("missed").getNodeValue());
            long covered = Long.parseLong(attrs.getNamedItem("covered").getNodeValue());
            double ratio = (missed + covered) == 0 ? 0 : (covered * 100.0d / (missed + covered));
            if ("LINE".equals(type) && lineCoverage == 0) {
                lineCoverage = ratio;
            }
            if ("BRANCH".equals(type) && branchCoverage == 0) {
                branchCoverage = ratio;
            }
        }
        return new CoverageSummary(lineCoverage, branchCoverage);
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private record Scenario(String algorithm, String dataset, String expectedBehavior, String validInput, byte[] corruptedPayload) {
        private static Scenario roundTrip(String algorithm, String dataset, String validInput) {
            return new Scenario(algorithm, dataset, "ROUND_TRIP", validInput, null);
        }

        private static Scenario exception(String algorithm, String dataset, byte[] corruptedPayload) {
            return new Scenario(algorithm, dataset, "EXCEPTION", null, corruptedPayload);
        }
    }

    private record RunRecord(String algorithm, String dataset, String expectedBehavior, int run, double timeMs, String result) {
    }

    private record SummaryRecord(String algorithm, String dataset, String expectedBehavior, int n, double meanMs, double variance,
                                 double stddevMs, double ci95LowMs, double ci95HighMs) {
        private static SummaryRecord of(Scenario scenario, List<Double> samples) {
            int n = samples.size();
            double sum = samples.stream().mapToDouble(Double::doubleValue).sum();
            double mean = sum / n;
            double variance = 0;
            if (n > 1) {
                for (double sample : samples) {
                    double d = sample - mean;
                    variance += d * d;
                }
                variance /= (n - 1);
            }
            double stddev = Math.sqrt(variance);
            double margin = T_95_N25 * stddev / Math.sqrt(n);
            return new SummaryRecord(scenario.algorithm, scenario.dataset, scenario.expectedBehavior, n, mean, variance, stddev, mean - margin, mean + margin);
        }
    }

    private record TestReportSummary(int tests, int failures, int errors, int skipped) {
    }

    private record TestNgReportSummary(int total, int passed, int failed, int skipped) {
    }

    private record CoverageSummary(double lineCoveragePct, double branchCoveragePct) {
    }
}
