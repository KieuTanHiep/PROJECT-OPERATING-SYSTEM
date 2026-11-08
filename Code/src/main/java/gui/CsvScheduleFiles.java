package gui;

import algorithm.SchedulingSupport;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CsvScheduleFiles {

    private static final List<String> INPUT_HEADER =
            List.of("processId", "arrivalTime", "burstTime", "priority");
    private static final String OUTPUT_HEADER = Arrays.stream(ResultColumn.values())
            .map(column -> column.header)
            .collect(Collectors.joining(","));
    private static final int OUTPUT_COLUMN_COUNT = ResultColumn.values().length;
    private static final String COMPARISON_HEADER =
            "algorithm,quantum,processCount,makespan,averageWaitingTime,"
                    + "averageTurnaroundTime,averageResponseTime,cpuUtilization,throughput,"
                    + "maximumWaitingTime,waitingTimeStdDev,jainFairnessIndex";

    private CsvScheduleFiles() {}

    public static List<CpuScheduler.ProcessInput> readProcesses(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Tệp CSV đang trống.");
            }
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }
            if (!parseRow(headerLine, 1).equals(INPUT_HEADER)) {
                throw new IOException("Tiêu đề CSV phải là: " + String.join(",", INPUT_HEADER));
            }

            List<CpuScheduler.ProcessInput> processes = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                SchedulingSupport.checkCancelled();
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                CpuScheduler.ProcessInput process = parseProcess(line, lineNumber);
                if (!ids.add(process.id())) {
                    throw new IOException("Dòng " + lineNumber + ": processId bị trùng: " + process.id());
                }
                processes.add(process);
            }
            if (processes.isEmpty()) {
                throw new IOException("CSV phải có ít nhất một tiến trình.");
            }
            try {
                SchedulingSupport.validateWorkload(processes, CpuScheduler.ProcessInput::id,
                        CpuScheduler.ProcessInput::arrivalTime, CpuScheduler.ProcessInput::burstTime, "CSV");
            } catch (IllegalArgumentException exception) {
                throw new IOException(exception.getMessage(), exception);
            }
            return List.copyOf(processes);
        }
    }

    public static void writeResult(
            Path path,
            CpuScheduler.Algorithm algorithm,
            int quantum,
            CpuScheduler.ScheduleResult result) throws IOException {
        writeDetailed(path, writer -> writeResultRows(writer, algorithm, quantum, result));
    }

    /** Exports complete traces and per-process results for every compared algorithm. */
    public static void writeComparison(
            Path path, List<CpuScheduler.ComparisonResult> comparisons, int quantum) throws IOException {
        requireComparisons(comparisons);
        writeDetailed(path, writer -> {
            for (CpuScheduler.ComparisonResult comparison : comparisons) {
                writeResultRows(writer, comparison.algorithm(), quantum, comparison.result());
            }
        });
    }

    /** Compact metrics table, including the explicitly defined fairness measures. */
    public static void writeComparisonSummary(
            Path path, List<CpuScheduler.ComparisonResult> comparisons, int quantum) throws IOException {
        requireComparisons(comparisons);
        writeAtomically(path, writer -> {
            writer.write(COMPARISON_HEADER);
            writer.newLine();
            for (CpuScheduler.ComparisonResult comparison : comparisons) {
                CpuScheduler.ScheduleResult result = comparison.result();
                writeRow(writer, comparison.algorithm().toString(),
                        comparison.algorithm() == CpuScheduler.Algorithm.ROUND_ROBIN
                                ? Integer.toString(quantum) : "",
                        Integer.toString(result.processes().size()),
                        Integer.toString(result.timeline().getLast().endTime()),
                        Double.toString(result.averageWaitingTime()),
                        Double.toString(result.averageTurnaroundTime()),
                        Double.toString(result.averageResponseTime()),
                        Double.toString(result.cpuUtilization()), Double.toString(result.throughput()),
                        Double.toString(comparison.maximumWaitingTime()),
                        Double.toString(comparison.waitingTimeStdDev()),
                        Double.toString(comparison.jainFairnessIndex()));
            }
        });
    }

    private static void requireComparisons(List<CpuScheduler.ComparisonResult> comparisons) {
        if (comparisons == null || comparisons.isEmpty() || comparisons.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Cần kết quả so sánh trước khi xuất CSV.");
        }
    }

    private static void writeResultRows(
            BufferedWriter writer, CpuScheduler.Algorithm algorithm, int quantum,
            CpuScheduler.ScheduleResult result) throws IOException {
        Map<String, CpuScheduler.ProcessInput> processesById = new HashMap<>();
        for (CpuScheduler.ProcessResult process : result.processes()) {
            processesById.put(process.process().id(), process.process());
        }

        for (CpuScheduler.ExecutionSlice slice : result.timeline()) {
            CpuScheduler.ProcessInput process = SchedulingSupport.IDLE_LABEL.equals(slice.processId())
                    ? null : processesById.get(slice.processId());
            ResultRow row = new ResultRow("TRACE", algorithm, quantum)
                    .with(ResultColumn.PROCESS_ID, slice.processId())
                    .with(ResultColumn.START_TIME, slice.startTime())
                    .with(ResultColumn.END_TIME, slice.endTime());
            if (process != null) {
                row.withProcess(process);
            }
            row.write(writer);
        }

        for (CpuScheduler.ProcessResult process : result.processes()) {
            new ResultRow("PROCESS", algorithm, quantum)
                    .withProcess(process.process())
                    .with(ResultColumn.START_TIME, process.startTime())
                    .with(ResultColumn.COMPLETION_TIME, process.completionTime())
                    .with(ResultColumn.TURNAROUND_TIME, process.turnaroundTime())
                    .with(ResultColumn.WAITING_TIME, process.waitingTime())
                    .with(ResultColumn.RESPONSE_TIME, process.responseTime())
                    .write(writer);
        }

        new ResultRow("SUMMARY", algorithm, quantum)
                .with(ResultColumn.AVERAGE_WAITING_TIME, result.averageWaitingTime())
                .with(ResultColumn.AVERAGE_TURNAROUND_TIME, result.averageTurnaroundTime())
                .with(ResultColumn.AVERAGE_RESPONSE_TIME, result.averageResponseTime())
                .with(ResultColumn.CPU_UTILIZATION, result.cpuUtilization())
                .with(ResultColumn.THROUGHPUT, result.throughput())
                .write(writer);
    }

    /** A single schema controls both the header and the positions of result fields. */
    private enum ResultColumn {
        RECORD_TYPE("recordType"),
        ALGORITHM("algorithm"),
        QUANTUM("quantum"),
        PROCESS_ID("processId"),
        ARRIVAL_TIME("arrivalTime"),
        BURST_TIME("burstTime"),
        PRIORITY("priority"),
        START_TIME("startTime"),
        END_TIME("endTime"),
        COMPLETION_TIME("completionTime"),
        TURNAROUND_TIME("turnaroundTime"),
        WAITING_TIME("waitingTime"),
        RESPONSE_TIME("responseTime"),
        AVERAGE_WAITING_TIME("averageWaitingTime"),
        AVERAGE_TURNAROUND_TIME("averageTurnaroundTime"),
        AVERAGE_RESPONSE_TIME("averageResponseTime"),
        CPU_UTILIZATION("cpuUtilization"),
        THROUGHPUT("throughput");

        private final String header;

        ResultColumn(String header) {
            this.header = header;
        }
    }

    private static final class ResultRow {
        private final String[] fields = new String[OUTPUT_COLUMN_COUNT];

        ResultRow(String type, CpuScheduler.Algorithm algorithm, int quantum) {
            Arrays.fill(fields, "");
            with(ResultColumn.RECORD_TYPE, type);
            with(ResultColumn.ALGORITHM, algorithm);
            with(ResultColumn.QUANTUM, quantum);
        }

        ResultRow with(ResultColumn column, Object value) {
            fields[column.ordinal()] = Objects.toString(value, "");
            return this;
        }

        ResultRow withProcess(CpuScheduler.ProcessInput process) {
            return with(ResultColumn.PROCESS_ID, process.id())
                    .with(ResultColumn.ARRIVAL_TIME, process.arrivalTime())
                    .with(ResultColumn.BURST_TIME, process.burstTime())
                    .with(ResultColumn.PRIORITY, process.priority());
        }

        void write(BufferedWriter writer) throws IOException {
            writeRow(writer, fields);
        }
    }

    @FunctionalInterface
    private interface CsvWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    private static void writeDetailed(Path path, CsvWriter content) throws IOException {
        writeAtomically(path, writer -> {
            writer.write(OUTPUT_HEADER);
            writer.newLine();
            content.write(writer);
        });
    }

    private static void writeAtomically(Path path, CsvWriter content) throws IOException {
        SchedulingSupport.checkCancelled();
        Path destination = path.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".schedule-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                content.write(writer);
            }
            SchedulingSupport.checkCancelled();
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static CpuScheduler.ProcessInput parseProcess(String line, int lineNumber) throws IOException {
        List<String> fields = parseRow(line, lineNumber);
        if (fields.size() != INPUT_HEADER.size()) {
            throw new IOException("Dòng " + lineNumber + ": cần đúng 4 cột.");
        }
        String id;
        try {
            id = SchedulingSupport.normalizeProcessId(fields.get(0));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Dòng " + lineNumber + ": " + exception.getMessage(), exception);
        }
        int arrivalTime = parseInteger(fields.get(1), lineNumber, "arrivalTime");
        int burstTime = parseInteger(fields.get(2), lineNumber, "burstTime");
        int priority = parseInteger(fields.get(3), lineNumber, "priority");
        if (arrivalTime < 0) {
            throw new IOException("Dòng " + lineNumber + ": arrivalTime phải >= 0.");
        }
        if (burstTime <= 0) {
            throw new IOException("Dòng " + lineNumber + ": burstTime phải > 0.");
        }
        return new CpuScheduler.ProcessInput(id, arrivalTime, burstTime, priority);
    }

    private static int parseInteger(String value, int lineNumber, String column) throws IOException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IOException("Dòng " + lineNumber + ": " + column + " phải là số nguyên.", exception);
        }
    }

    private static List<String> parseRow(String line, int lineNumber) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quoted) {
                if (character == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (closedQuote) {
                if (!Character.isWhitespace(character)) {
                    throw new IOException("Dòng " + lineNumber + ": ký tự thừa sau dấu nháy kép.");
                }
            } else if (character == '"') {
                if (field.length() != 0) {
                    throw new IOException("Dòng " + lineNumber + ": dấu nháy kép phải bao toàn bộ trường.");
                }
                quoted = true;
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IOException("Dòng " + lineNumber + ": thiếu dấu nháy kép đóng.");
        }
        fields.add(field.toString());
        return fields;
    }

    private static void writeRow(BufferedWriter writer, String... fields) throws IOException {
        SchedulingSupport.checkCancelled();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            String field = fields[i];
            if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0
                    || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
                writer.write('"');
                writer.write(field.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(field);
            }
        }
        writer.newLine();
    }
}
