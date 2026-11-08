package tools;

import gui.CpuScheduler;
import gui.CpuScheduler.Algorithm;
import gui.CpuScheduler.ProcessInput;
import gui.CpuScheduler.ScheduleResult;
import gui.CsvScheduleFiles;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Optional test-only correctness and benchmark runner. */
public final class SchedulerVerification {
    private static final long DEFAULT_SEED = 20261108L;
    private static volatile long measurementSink;

    private SchedulerVerification() {}

    private static long mixMeasurement(long previous, long contribution) {
        long mixed = previous ^ contribution;
        return mixed == 0L ? 1L : mixed;
    }

    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        Files.createDirectories(options.output());
        switch (options.command()) {
            case "check" -> check(options);
            case "benchmark" -> benchmark(options);
            case "all" -> {
                check(options);
                benchmark(options);
            }
            default -> throw new IllegalArgumentException("Use check, benchmark or all.");
        }
    }

    private static void check(Options options) throws IOException {
        List<Workload> workloads = new ArrayList<>();
        Path sampleInput = projectDirectory().resolve("input/data.csv");
        workloads.add(new Workload("data", CsvScheduleFiles.readProcesses(sampleInput), 2));
        Random random = new Random(options.seed());
        for (int caseNumber = 0; caseNumber < options.randomCases(); caseNumber++) {
            List<ProcessInput> inputs = new ArrayList<>();
            int count = 1 + random.nextInt(8);
            for (int i = 0; i < count; i++) {
                inputs.add(new ProcessInput("P" + (i + 1), random.nextInt(16),
                        1 + random.nextInt(10), random.nextInt(5) - 2));
            }
            Collections.shuffle(inputs, random);
            workloads.add(new Workload("random-" + caseNumber, List.copyOf(inputs), 1 + random.nextInt(6)));
        }

        int failed = 0;
        int checked = 0;
        Path resultsPath = options.output().resolve("correctness-results.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(resultsPath, StandardCharsets.UTF_8)) {
            csv(writer, "case", "seed", "algorithm", "quantum", "processCount", "status", "details",
                    "expectedTrace", "actualTrace", "expectedProcesses_ST_CT_TAT_WT_RT", "actualProcesses_ST_CT_TAT_WT_RT",
                    "expectedAverageWT", "actualAverageWT", "expectedAverageTAT", "actualAverageTAT",
                    "expectedAverageRT", "actualAverageRT", "expectedUtilization", "actualUtilization",
                    "expectedThroughput", "actualThroughput");
            for (Workload workload : workloads) {
                for (Algorithm algorithm : Algorithm.values()) {
                    boolean passed = checkOne(writer, options, workload, algorithm);
                    checked++;
                    if (!passed) {
                        failed++;
                    }
                }
            }
            // Include short/large quantum behavior on the same named workload.
            Workload sample = workloads.getFirst();
            for (int quantum : new int[] {1, 5, 64}) {
                if (!checkOne(writer, options,
                        new Workload("sample-quantum-" + quantum, sample.inputs(), quantum), Algorithm.ROUND_ROBIN)) {
                    failed++;
                }
                checked++;
            }
        }
        writeEnvironment(options.output().resolve("correctness-environment.txt"), options,
                "reference=tick-by-tick; per-process timing + complete timeline + 5 summary metrics\n"
                        + "smallWorkloads=" + workloads.size() + "\ncheckedSchedules=" + checked
                        + "\nfailedSchedules=" + failed + "\n");
        System.out.println("Correctness: " + (checked - failed) + "/" + checked + " PASS -> " + resultsPath);
        if (failed != 0) {
            throw new IllegalStateException(failed + " oracle checks failed; see " + resultsPath);
        }
        for (Workload workload : workloads) {
            if (!workload.name().startsWith("random-")) {
                var comparison = CpuScheduler.compare(workload.inputs(), workload.quantum());
                Path directory = options.output().resolve("comparisons");
                CsvScheduleFiles.writeComparison(directory.resolve(workload.name() + "-details.csv"),
                        comparison, workload.quantum());
                CsvScheduleFiles.writeComparisonSummary(directory.resolve(workload.name() + "-summary.csv"),
                        comparison, workload.quantum());
            }
        }
    }

    private static boolean checkOne(BufferedWriter writer, Options options, Workload workload, Algorithm algorithm)
            throws IOException {
        ScheduleResult expected = SchedulerOracle.expected(workload.inputs(), algorithm, workload.quantum());
        ScheduleResult actual;
        try {
            actual = CpuScheduler.schedule(workload.inputs(), algorithm, workload.quantum());
        } catch (RuntimeException exception) {
            List<String> row = new ArrayList<>(List.of(workload.name(), Long.toString(options.seed()),
                    algorithm.toString(), Integer.toString(workload.quantum()), Integer.toString(workload.inputs().size()),
                    "FAIL", exception.toString(), SchedulerOracle.describeTrace(expected), "",
                    SchedulerOracle.describeProcesses(expected), ""));
            row.addAll(List.of(Double.toString(expected.averageWaitingTime()), "",
                    Double.toString(expected.averageTurnaroundTime()), "", Double.toString(expected.averageResponseTime()), "",
                    Double.toString(expected.cpuUtilization()), "", Double.toString(expected.throughput()), ""));
            csv(writer, row.toArray(String[]::new));
            return false;
        }
        List<String> errors = new ArrayList<>(SchedulerOracle.differences(expected, actual));
        errors.addAll(SchedulerOracle.invariantErrors(workload.inputs(), actual));
        csv(writer, workload.name(), Long.toString(options.seed()), algorithm.toString(), Integer.toString(workload.quantum()),
                Integer.toString(workload.inputs().size()), errors.isEmpty() ? "PASS" : "FAIL", String.join("; ", errors),
                SchedulerOracle.describeTrace(expected), SchedulerOracle.describeTrace(actual),
                SchedulerOracle.describeProcesses(expected), SchedulerOracle.describeProcesses(actual),
                Double.toString(expected.averageWaitingTime()), Double.toString(actual.averageWaitingTime()),
                Double.toString(expected.averageTurnaroundTime()), Double.toString(actual.averageTurnaroundTime()),
                Double.toString(expected.averageResponseTime()), Double.toString(actual.averageResponseTime()),
                Double.toString(expected.cpuUtilization()), Double.toString(actual.cpuUtilization()),
                Double.toString(expected.throughput()), Double.toString(actual.throughput()));
        if (!workload.name().startsWith("random-")) {
            CsvScheduleFiles.writeResult(options.output().resolve("sample-results")
                            .resolve(workload.name() + "-" + algorithm.name().toLowerCase(Locale.ROOT) + ".csv"),
                    algorithm, workload.quantum(), actual);
        }
        return errors.isEmpty();
    }

    private static void benchmark(Options options) throws IOException {
        Path rawPath = options.output().resolve("benchmark-raw.csv");
        Path summaryPath = options.output().resolve("benchmark-summary.csv");
        int verifiedRuns = 0;
        try (BufferedWriter raw = Files.newBufferedWriter(rawPath, StandardCharsets.UTF_8);
                BufferedWriter summary = Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8)) {
            csv(raw, "algorithm", "processCount", "maxBurst", "seed", "quantum", "iteration", "elapsedNanos", "status");
            csv(summary, "algorithm", "processCount", "maxBurst", "seed", "quantum", "warmupRuns", "measuredRuns",
                    "medianNanos", "minNanos", "maxNanos", "medianMillis", "totalBurst", "completionTime", "timelineSlices");
            for (int count : options.sizes()) {
                for (int maxBurst : new int[] {10, 100, 1000}) {
                    long workloadSeed = options.seed() ^ (count * 1009L) ^ (maxBurst * 9176L);
                    List<ProcessInput> inputs = benchmarkWorkload(count, maxBurst, workloadSeed);
                    Path inputPath = options.output().resolve("benchmark-inputs")
                            .resolve("n" + count + "-burst" + maxBurst + ".csv");
                    writeWorkload(inputPath, inputs);
                    int quantum = 5;
                    for (Algorithm algorithm : Algorithm.values()) {
                        for (int i = 0; i < options.warmups(); i++) {
                            consume(CpuScheduler.schedule(inputs, algorithm, quantum));
                        }
                    }
                    long[][] durations = new long[Algorithm.values().length][options.repeats()];
                    ScheduleResult[] lastResults = new ScheduleResult[Algorithm.values().length];
                    Random ordering = new Random(workloadSeed);
                    for (int iteration = 0; iteration < options.repeats(); iteration++) {
                        List<Algorithm> algorithms = new ArrayList<>(List.of(Algorithm.values()));
                        Collections.shuffle(algorithms, ordering);
                        for (Algorithm algorithm : algorithms) {
                            long start = System.nanoTime();
                            ScheduleResult result = CpuScheduler.schedule(inputs, algorithm, quantum);
                            long elapsed = System.nanoTime() - start;
                            // Only oracle/invariant checks, I/O and data generation are excluded.
                            // Input validation, conversion and result allocation inside schedule() are measured.
                            consume(result);
                            List<String> errors = SchedulerOracle.invariantErrors(inputs, result);
                            csv(raw, algorithm.toString(), Integer.toString(count), Integer.toString(maxBurst),
                                    Long.toString(workloadSeed), Integer.toString(quantum), Integer.toString(iteration + 1),
                                    Long.toString(elapsed), errors.isEmpty() ? "PASS" : "FAIL");
                            if (!errors.isEmpty()) {
                                throw new IllegalStateException("Stress invariants failed for " + algorithm + ": " + errors);
                            }
                            durations[algorithm.ordinal()][iteration] = elapsed;
                            lastResults[algorithm.ordinal()] = result;
                            verifiedRuns++;
                        }
                    }
                    for (Algorithm algorithm : Algorithm.values()) {
                        long[] sorted = durations[algorithm.ordinal()].clone();
                        Arrays.sort(sorted);
                        double median = sorted.length % 2 == 0
                                ? sorted[sorted.length / 2 - 1] / 2.0 + sorted[sorted.length / 2] / 2.0
                                : sorted[sorted.length / 2];
                        ScheduleResult result = lastResults[algorithm.ordinal()];
                        csv(summary, algorithm.toString(), Integer.toString(count), Integer.toString(maxBurst),
                                Long.toString(workloadSeed), Integer.toString(quantum), Integer.toString(options.warmups()),
                                Integer.toString(options.repeats()), Double.toString(median), Long.toString(sorted[0]),
                                Long.toString(sorted[sorted.length - 1]), Double.toString(median / 1_000_000),
                                Long.toString(inputs.stream().mapToLong(ProcessInput::burstTime).sum()),
                                Integer.toString(result.timeline().getLast().endTime()), Integer.toString(result.timeline().size()));
                    }
                    System.out.println("Benchmark: n=" + count + ", maxBurst=" + maxBurst + " complete.");
                }
            }
        }
        writeEnvironment(options.output().resolve("benchmark-environment.txt"), options,
                "clock=System.nanoTime\nmeasurement=CpuScheduler.schedule; includes input validation,conversion,metrics,result/timeline allocation\n"
                        + "excluded=workload generation,CSV I/O,validation oracle/invariants,Swing rendering\n"
                        + "summary=median,min,max; no performance pass/fail threshold\n"
                        + "algorithmOrder=seeded shuffle per measured round\nmaxBursts=10,100,1000\nquantum=5\n"
                        + "verifiedMeasuredRuns=" + verifiedRuns + "\n");
        System.out.println("Stress: " + verifiedRuns + " measured schedules PASS. Summary -> " + summaryPath);
    }

    private static List<ProcessInput> benchmarkWorkload(int count, int maxBurst, long seed) {
        Random random = new Random(seed);
        List<ProcessInput> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new ProcessInput(String.format(Locale.ROOT, "P%06d", i + 1),
                    random.nextInt(Math.max(1, count / 4)), 1 + random.nextInt(maxBurst), 1 + random.nextInt(5)));
        }
        Collections.shuffle(result, random);
        return List.copyOf(result);
    }

    private static void writeWorkload(Path path, List<ProcessInput> inputs) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            csv(writer, "processId", "arrivalTime", "burstTime", "priority");
            for (ProcessInput input : inputs) {
                csv(writer, input.id(), Integer.toString(input.arrivalTime()), Integer.toString(input.burstTime()),
                        Integer.toString(input.priority()));
            }
        }
    }

    private static void consume(ScheduleResult result) {
        long contribution = result.timeline().getLast().endTime() + result.processes().size()
                + Double.doubleToLongBits(result.averageWaitingTime());
        measurementSink = mixMeasurement(measurementSink, contribution);
    }

    private static void writeEnvironment(Path path, Options options, String details) throws IOException {
        String physicalMemory = "unknown";
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            physicalMemory = Long.toString(os.getTotalMemorySize());
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, "generatedAtUtc=" + Instant.now() + "\n"
                + "javaVersion=" + System.getProperty("java.version") + "\n"
                + "javaVendor=" + System.getProperty("java.vendor") + "\n"
                + "javaVm=" + System.getProperty("java.vm.name") + "\n"
                + "osName=" + System.getProperty("os.name") + "\n"
                + "osVersion=" + System.getProperty("os.version") + "\n"
                + "osArch=" + System.getProperty("os.arch") + "\n"
                + "cpu=" + System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown") + "\n"
                + "logicalProcessors=" + Runtime.getRuntime().availableProcessors() + "\n"
                + "physicalMemoryBytes=" + physicalMemory + "\n"
                + "maxHeapBytes=" + Runtime.getRuntime().maxMemory() + "\n"
                + "seed=" + options.seed() + "\nrandomCases=" + options.randomCases()
                + "\nwarmupRuns=" + options.warmups() + "\nmeasuredRuns=" + options.repeats()
                + "\nprocessCounts=" + options.sizes() + "\n" + details, StandardCharsets.UTF_8);
    }

    private static void csv(BufferedWriter writer, String... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            String field = fields[i];
            if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
                writer.write('"');
                writer.write(field.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(field);
            }
        }
        writer.newLine();
    }

    private record Workload(String name, List<ProcessInput> inputs, int quantum) {}

    private static Path projectDirectory() {
        return Files.isRegularFile(Path.of("Code", "pom.xml")) ? Path.of("Code") : Path.of("");
    }

    private record Options(
            String command,
            Path output,
            long seed,
            int randomCases,
            int warmups,
            int repeats,
            List<Integer> sizes) {
        static Options parse(String[] args) {
            String[] arguments = args == null ? new String[0] : args;
            String command = arguments.length == 0 ? "all" : arguments[0];
            if (!List.of("check", "benchmark", "all").contains(command)) {
                throw new IllegalArgumentException("Usage: tools.SchedulerVerification check|benchmark|all [output-directory]"
                        + " [--seed=N] [--cases=N] [--warmups=N] [--repeats=N] [--sizes=100,500,1000]");
            }
            Path output = projectDirectory().resolve("target/verification");
            long seed = DEFAULT_SEED;
            int cases = 300;
            int warmups = 3;
            int repeats = 7;
            List<Integer> sizes = List.of(100, 500, 1000);
            for (int i = 1; i < arguments.length; i++) {
                String argument = arguments[i];
                if (!argument.startsWith("--")) {
                    output = Path.of(argument);
                    continue;
                }
                String option = argument;
                String value = "";
                int equalsIndex = argument.indexOf('=');
                if (equalsIndex >= 0) {
                    option = argument.substring(2, equalsIndex);
                    value = argument.substring(equalsIndex + 1);
                } else {
                    option = argument.substring(2);
                    if (i + 1 >= arguments.length || arguments[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for option: " + argument);
                    }
                    value = arguments[++i];
                }
                switch (option) {
                    case "seed" -> seed = parseLong(value, "seed");
                    case "cases" -> cases = nonnegative(value, "cases");
                    case "warmups" -> warmups = positive(value, "warmups");
                    case "repeats" -> repeats = positive(value, "repeats");
                    case "sizes" -> sizes = parseSizes(value);
                    default -> throw new IllegalArgumentException("Unknown option: --" + option);
                }
            }
            return new Options(command, output, seed, cases, warmups, repeats, sizes);
        }

        private static long parseLong(String value, String option) {
            String candidate = value == null ? "" : value.trim();
            try {
                return Long.parseLong(candidate);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("--" + option + " expects an integer value.", exception);
            }
        }

        private static List<Integer> parseSizes(String value) {
            String candidate = value == null ? "" : value.trim();
            List<Integer> parsed = Arrays.stream(candidate.split(",", -1))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .map(item -> positive(item, "sizes"))
                    .distinct()
                    .toList();
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("sizes must contain at least one positive integer.");
            }
            return parsed;
        }

        private static int positive(String value, String option) {
            int parsed = nonnegative(value, option);
            if (parsed == 0) {
                throw new IllegalArgumentException(option + " must be positive.");
            }
            return parsed;
        }

        private static int nonnegative(String value, String option) {
            String candidate = value == null ? "" : value.trim();
            if (candidate.isEmpty()) {
                throw new IllegalArgumentException(option + " must be an integer value.");
            }
            try {
                int parsed = Integer.parseInt(candidate);
                if (parsed < 0) {
                    throw new IllegalArgumentException(option + " must not be negative.");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(option + " must be an integer value.", exception);
            }
        }
    }
}
