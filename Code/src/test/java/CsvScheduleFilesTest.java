import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import GUI.CpuScheduler;
import GUI.CsvScheduleFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvScheduleFilesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsValidCsvAndSupportsQuotedProcessIds() throws IOException {
        Path input = temporaryDirectory.resolve("processes.csv");
        java.nio.file.Files.writeString(input,
                "processId,arrivalTime,burstTime,priority\n\"P,1\",0,3,2\n");

        assertEquals(
                List.of(new CpuScheduler.ProcessInput("P,1", 0, 3, 2)),
                CsvScheduleFiles.readProcesses(input));
    }

    @Test
    void rejectsDuplicateIdsAndInvalidTimes() throws IOException {
        Path duplicateIds = temporaryDirectory.resolve("duplicate.csv");
        java.nio.file.Files.writeString(duplicateIds,
                "processId,arrivalTime,burstTime,priority\nP1,0,2,1\nP1,1,3,2\n");
        assertTrue(assertThrows(IOException.class,
                () -> CsvScheduleFiles.readProcesses(duplicateIds))
                .getMessage().contains("bị trùng"));

        Path invalidBurst = temporaryDirectory.resolve("invalid.csv");
        java.nio.file.Files.writeString(invalidBurst,
                "processId,arrivalTime,burstTime,priority\nP1,0,0,1\n");
        assertTrue(assertThrows(IOException.class,
                () -> CsvScheduleFiles.readProcesses(invalidBurst))
                .getMessage().contains("burstTime"));

        Path invalidHeader = temporaryDirectory.resolve("wrong-header.csv");
        java.nio.file.Files.writeString(invalidHeader,
                "id,arrival,burst,priority\nP1,0,2,1\n");
        assertTrue(assertThrows(IOException.class,
                () -> CsvScheduleFiles.readProcesses(invalidHeader))
                .getMessage().contains("Tiêu đề CSV"));
    }

    @Test
    void exportsTimelineProcessMetricsAndSummary() throws IOException {
        CpuScheduler.ScheduleResult result = CpuScheduler.schedule(
                List.of(
                        new CpuScheduler.ProcessInput("P1", 0, 2, 1),
                        new CpuScheduler.ProcessInput("P2", 4, 1, 2)),
                CpuScheduler.Algorithm.FCFS,
                2);
        Path output = temporaryDirectory.resolve("nested").resolve("result.csv");

        CsvScheduleFiles.writeResult(output, CpuScheduler.Algorithm.FCFS, 2, result);

        List<String> rows = java.nio.file.Files.readAllLines(output);
        assertEquals(7, rows.size());
        assertTrue(rows.get(1).startsWith("TRACE,FCFS,2,P1,0,2,1,0,2,"));
        assertTrue(rows.get(2).startsWith("TRACE,FCFS,2,IDLE,,,,2,4,"));
        assertTrue(rows.get(3).startsWith("TRACE,FCFS,2,P2,4,1,2,4,5,"));
        assertTrue(rows.get(6).startsWith("SUMMARY,FCFS,2,"));
    }

    @Test
    void sampleCsvMapsToExpectedTraceForEveryAlgorithm() throws IOException {
        List<CpuScheduler.ProcessInput> processes =
                CsvScheduleFiles.readProcesses(Path.of("input", "data.csv"));
        Map<CpuScheduler.Algorithm, List<String>> expectedTraces = Map.of(
                CpuScheduler.Algorithm.FCFS,
                List.of("P1 [0-7]", "P2 [7-11]", "P3 [11-12]", "P4 [12-15]",
                        "IDLE [15-20]", "P5 [20-22]"),
                CpuScheduler.Algorithm.SJF,
                List.of("P1 [0-7]", "P3 [7-8]", "P4 [8-11]", "P2 [11-15]",
                        "IDLE [15-20]", "P5 [20-22]"),
                CpuScheduler.Algorithm.PRIORITY,
                List.of("P1 [0-7]", "P2 [7-11]", "P4 [11-14]", "P3 [14-15]",
                        "IDLE [15-20]", "P5 [20-22]"),
                CpuScheduler.Algorithm.SRTF,
                List.of("P1 [0-2]", "P3 [2-3]", "P2 [3-7]", "P4 [7-10]",
                        "P1 [10-15]", "IDLE [15-20]", "P5 [20-22]"),
                CpuScheduler.Algorithm.ROUND_ROBIN,
                List.of("P1 [0-2]", "P2 [2-4]", "P3 [4-5]", "P1 [5-7]",
                        "P2 [7-9]", "P4 [9-11]", "P1 [11-13]", "P4 [13-14]",
                        "P1 [14-15]", "IDLE [15-20]", "P5 [20-22]"));

        for (Map.Entry<CpuScheduler.Algorithm, List<String>> entry : expectedTraces.entrySet()) {
            CpuScheduler.ScheduleResult result =
                    CpuScheduler.schedule(processes, entry.getKey(), 2);
            assertEquals(entry.getValue(), describe(result.timeline()), entry.getKey().toString());
        }
    }

    private List<String> describe(List<CpuScheduler.ExecutionSlice> timeline) {
        return timeline.stream()
                .map(slice -> slice.processId() + " [" + slice.startTime() + "-" + slice.endTime() + "]")
                .toList();
    }
}
