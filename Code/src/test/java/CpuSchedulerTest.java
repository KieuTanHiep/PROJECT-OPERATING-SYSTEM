import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import GUI.CpuScheduler;
import GUI.CsvScheduleFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CpuSchedulerTest {

    @Test
    void fcfsSchedulesTheSharedSampleWorkload() throws IOException {
        var result = CpuScheduler.schedule(
                sampleWorkload(), CpuScheduler.Algorithm.FCFS, 2);

        assertEquals(
                List.of("P1 [0-7]", "P2 [7-11]", "P3 [11-12]", "P4 [12-15]",
                        "IDLE [15-20]", "P5 [20-22]"),
                describe(result.timeline()));
        assertEquals(4.2, result.averageWaitingTime(), 0.01);
        assertEquals(7.6, result.averageTurnaroundTime(), 0.01);
    }

    @Test
    void sjfSchedulesTheSharedSampleWorkload() throws IOException {
        var result = CpuScheduler.schedule(
                sampleWorkload(), CpuScheduler.Algorithm.SJF, 2);

        assertEquals(
                List.of("P1 [0-7]", "P3 [7-8]", "P4 [8-11]", "P2 [11-15]",
                        "IDLE [15-20]", "P5 [20-22]"),
                describe(result.timeline()));
    }

    @Test
    void srtfSchedulesTheSharedSampleWorkload() throws IOException {
        var result = CpuScheduler.schedule(
                sampleWorkload(), CpuScheduler.Algorithm.SRTF, 2);

        assertEquals(
                List.of("P1 [0-2]", "P3 [2-3]", "P2 [3-7]", "P4 [7-10]",
                        "P1 [10-15]", "IDLE [15-20]", "P5 [20-22]"),
                describe(result.timeline()));
    }

    @Test
    void prioritySchedulesTheSharedSampleWorkload() throws IOException {
        var result = CpuScheduler.schedule(
                sampleWorkload(), CpuScheduler.Algorithm.PRIORITY, 2);

        assertEquals(
                List.of("P1 [0-7]", "P2 [7-11]", "P4 [11-14]", "P3 [14-15]",
                        "IDLE [15-20]", "P5 [20-22]"),
                describe(result.timeline()));
    }

    @Test
    void roundRobinSchedulesTheSharedSampleWorkload() throws IOException {
        var result = CpuScheduler.schedule(
                sampleWorkload(), CpuScheduler.Algorithm.ROUND_ROBIN, 2);

        assertEquals(
                List.of("P1 [0-2]", "P2 [2-4]", "P3 [4-5]", "P1 [5-7]",
                        "P2 [7-9]", "P4 [9-11]", "P1 [11-13]", "P4 [13-14]",
                        "P1 [14-15]", "IDLE [15-20]", "P5 [20-22]"),
                describe(result.timeline()));
    }

    @Test
    void rejectsNonPositiveRoundRobinQuantum() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () -> CpuScheduler.schedule(
                        sampleWorkload(), CpuScheduler.Algorithm.ROUND_ROBIN, 0));
    }

    private List<CpuScheduler.ProcessInput> sampleWorkload() throws IOException {
        return CsvScheduleFiles.readProcesses(Path.of("input", "data.csv"));
    }

    private List<String> describe(List<CpuScheduler.ExecutionSlice> timeline) {
        return timeline.stream()
                .map(slice -> slice.processId() + " [" + slice.startTime() + "-" + slice.endTime() + "]")
                .toList();
    }
}
