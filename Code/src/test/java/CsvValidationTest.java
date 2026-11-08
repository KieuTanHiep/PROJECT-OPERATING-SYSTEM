import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gui.CpuScheduler;
import gui.CsvScheduleFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvValidationTest {
    private static final String HEADER = "processId,arrivalTime,burstTime,priority\n";

    @TempDir
    Path directory;

    @Test
    void rejectsMalformedEmptyAndInvalidCsvWithAnExplanation() throws IOException {
        var contents = List.of("", HEADER, "id,arrival,burst,priority\nP1,0,1,1\n",
                HEADER + "P1,0,1\n", HEADER + "P1,0,1,1,extra\n",
                HEADER + "P1,-1,1,1\n", HEADER + "P1,0,0,1\n",
                HEADER + "P1,abc,1,1\n", HEADER + "P1,0,1,2147483648\n",
                HEADER + "P1,0,1,1\n P1 ,1,2,2\n", HEADER + "IDLE,2,3,1\n",
                HEADER + " idle ,2,3,1\n", HEADER + "\"P1,0,1,1\n",
                HEADER + "P\"1,0,1,1\n", HEADER + "\"P1\"x,0,1,1\n",
                HEADER + "P1,2147483647,1,1\n");
        for (int i = 0; i < contents.size(); i++) {
            Path input = directory.resolve("invalid-" + i + ".csv");
            Files.writeString(input, contents.get(i));
            var error = assertThrows(IOException.class, () -> CsvScheduleFiles.readProcesses(input),
                    "Invalid CSV case " + i + " must be rejected");
            assertFalse(error.getMessage().isBlank());
        }
    }

    @Test
    void supportsBomEscapedQuotesBlankLinesAndTrimmedIds() throws IOException {
        Path input = directory.resolve("valid.csv");
        Files.writeString(input, "\uFEFF" + HEADER + "\n\" P,\"\"1 \"  ,0,2,-5\n");
        assertEquals(List.of(new CpuScheduler.ProcessInput("P,\"1", 0, 2, -5)),
                CsvScheduleFiles.readProcesses(input));
    }

    @Test
    void comparisonExportsHaveOneHeaderAndAllFiveAlgorithms() throws IOException {
        var comparisons = CpuScheduler.compare(List.of(
                new CpuScheduler.ProcessInput("P,\"1", 2, 2, 1),
                new CpuScheduler.ProcessInput("P2", 3, 1, 2)), 1);
        Path detail = directory.resolve("nested/comparison.csv");
        Path summary = directory.resolve("summary.csv");
        CsvScheduleFiles.writeComparison(detail, comparisons, 1);
        CsvScheduleFiles.writeComparisonSummary(summary, comparisons, 1);
        var rows = Files.readAllLines(detail);
        assertEquals(1, rows.stream().filter(row -> row.startsWith("recordType,")).count());
        assertEquals(5, rows.stream().filter(row -> row.startsWith("SUMMARY,")).count());
        assertEquals(10, rows.stream().filter(row -> row.startsWith("PROCESS,")).count());
        assertTrue(rows.stream().anyMatch(row -> row.contains("\"P,\"\"1\"")));
        assertEquals(5, rows.stream().filter(row -> row.contains(",IDLE,,,,0,2,")).count());
        var summaries = Files.readAllLines(summary);
        assertEquals(6, summaries.size());
        assertTrue(summaries.getFirst().endsWith("maximumWaitingTime,waitingTimeStdDev,jainFairnessIndex"));
        for (int i = 1; i < summaries.size(); i++) {
            assertEquals(12, summaries.get(i).split(",", -1).length);
        }
        assertTrue(summaries.getLast().startsWith("Round Robin,1,"));
    }

    @Test
    void successfulExportReplacesAnExistingFileWithoutTemporaryFiles() throws IOException {
        Path output = directory.resolve("result.csv");
        Files.writeString(output, "old content");
        var result = CpuScheduler.schedule(List.of(
                new CpuScheduler.ProcessInput("P1", 0, 1, 1)), CpuScheduler.Algorithm.FCFS, 2);
        CsvScheduleFiles.writeResult(output, CpuScheduler.Algorithm.FCFS, 2, result);
        String exported = Files.readString(output);
        assertTrue(exported.startsWith("recordType,algorithm,"));
        assertFalse(exported.contains("old content"));
        try (var paths = Files.list(directory)) {
            assertEquals(List.of(output), paths.toList());
        }
    }

    @Test
    void interruptedExportPreservesAnExistingFileAndLeavesNoTemporaryFiles() throws IOException {
        Path output = directory.resolve("result.csv");
        Files.writeString(output, "existing result");
        var result = CpuScheduler.schedule(List.of(
                new CpuScheduler.ProcessInput("P1", 0, 1, 1)), CpuScheduler.Algorithm.FCFS, 2);
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class,
                    () -> CsvScheduleFiles.writeResult(output, CpuScheduler.Algorithm.FCFS, 2, result));
        } finally {
            Thread.interrupted();
        }
        assertEquals("existing result", Files.readString(output));
        try (var paths = Files.list(directory)) {
            assertEquals(List.of(output), paths.toList());
        }
    }
}
