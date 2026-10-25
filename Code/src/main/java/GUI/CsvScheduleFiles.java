package gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsvScheduleFiles {

    private static final List<String> INPUT_HEADER =
            List.of("processId", "arrivalTime", "burstTime", "priority");
    private static final String OUTPUT_HEADER =
            "recordType,algorithm,quantum,processId,arrivalTime,burstTime,priority,"
                    + "startTime,endTime,completionTime,turnaroundTime,waitingTime,responseTime,"
                    + "averageWaitingTime,averageTurnaroundTime,averageResponseTime,cpuUtilization,throughput";

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
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseRow(line, lineNumber);
                if (fields.size() != INPUT_HEADER.size()) {
                    throw new IOException("Dòng " + lineNumber + ": cần đúng 4 cột.");
                }

                String id = fields.get(0).trim();
                if (id.isEmpty()) {
                    throw new IOException("Dòng " + lineNumber + ": processId không được để trống.");
                }
                if (!ids.add(id)) {
                    throw new IOException("Dòng " + lineNumber + ": processId bị trùng: " + id);
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
                processes.add(new CpuScheduler.ProcessInput(id, arrivalTime, burstTime, priority));
            }
            if (processes.isEmpty()) {
                throw new IOException("CSV phải có ít nhất một tiến trình.");
            }
            return List.copyOf(processes);
        }
    }

    public static void writeResult(
            Path path,
            CpuScheduler.Algorithm algorithm,
            int quantum,
            CpuScheduler.ScheduleResult result) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, CpuScheduler.ProcessInput> processesById = new HashMap<>();
        for (CpuScheduler.ProcessResult process : result.processes()) {
            processesById.put(process.process().id(), process.process());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(OUTPUT_HEADER);
            writer.newLine();

            for (CpuScheduler.ExecutionSlice slice : result.timeline()) {
                CpuScheduler.ProcessInput process = processesById.get(slice.processId());
                writeRow(writer, "TRACE", algorithm.toString(), Integer.toString(quantum),
                        slice.processId(),
                        process == null ? "" : Integer.toString(process.arrivalTime()),
                        process == null ? "" : Integer.toString(process.burstTime()),
                        process == null ? "" : Integer.toString(process.priority()),
                        Integer.toString(slice.startTime()), Integer.toString(slice.endTime()),
                        "", "", "", "", "", "", "", "", "");
            }

            for (CpuScheduler.ProcessResult process : result.processes()) {
                CpuScheduler.ProcessInput input = process.process();
                writeRow(writer, "PROCESS", algorithm.toString(), Integer.toString(quantum),
                        input.id(), Integer.toString(input.arrivalTime()),
                        Integer.toString(input.burstTime()), Integer.toString(input.priority()),
                        Integer.toString(process.startTime()), "", Integer.toString(process.completionTime()),
                        Integer.toString(process.turnaroundTime()), Integer.toString(process.waitingTime()),
                        Integer.toString(process.responseTime()), "", "", "", "", "");
            }

            writeRow(writer, "SUMMARY", algorithm.toString(), Integer.toString(quantum),
                    "", "", "", "", "", "", "", "", "", "",
                    Double.toString(result.averageWaitingTime()),
                    Double.toString(result.averageTurnaroundTime()),
                    Double.toString(result.averageResponseTime()),
                    Double.toString(result.cpuUtilization()),
                    Double.toString(result.throughput()));
        }
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
            } else if (character == '"' && field.length() == 0 && !closedQuote) {
                quoted = true;
            } else if (closedQuote && !Character.isWhitespace(character)) {
                throw new IOException("Dòng " + lineNumber + ": ký tự thừa sau dấu nháy kép.");
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
