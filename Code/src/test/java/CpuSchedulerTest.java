import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gui.CpuScheduler;
import gui.CsvScheduleFiles;
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
    void roundRobinMapsProcessResultsAndEnqueuesBoundaryArrivalsBeforePreemptedProcess() {
        var result = CpuScheduler.schedule(
                List.of(
                        new CpuScheduler.ProcessInput("P2", 0, 5, 1),
                        new CpuScheduler.ProcessInput("P1", 0, 1, 1),
                        new CpuScheduler.ProcessInput("P3", 3, 1, 1),
                        new CpuScheduler.ProcessInput("P4", 10, 1, 1)),
                CpuScheduler.Algorithm.ROUND_ROBIN,
                2);

        assertEquals(
                List.of("P1 [0-1]", "P2 [1-3]", "P3 [3-4]", "P2 [4-7]",
                        "IDLE [7-10]", "P4 [10-11]"),
                describe(result.timeline()));

        var processesById = result.processes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        process -> process.process().id(), process -> process));
        assertEquals(0, processesById.get("P1").startTime());
        assertEquals(1, processesById.get("P1").completionTime());
        assertEquals(1, processesById.get("P1").turnaroundTime());
        assertEquals(0, processesById.get("P1").waitingTime());
        assertEquals(0, processesById.get("P1").responseTime());
        assertEquals(1, processesById.get("P2").startTime());
        assertEquals(7, processesById.get("P2").completionTime());
        assertEquals(7, processesById.get("P2").turnaroundTime());
        assertEquals(2, processesById.get("P2").waitingTime());
        assertEquals(1, processesById.get("P2").responseTime());
        assertEquals(3, processesById.get("P3").startTime());
        assertEquals(4, processesById.get("P3").completionTime());
        assertEquals(1, processesById.get("P3").turnaroundTime());
        assertEquals(0, processesById.get("P3").waitingTime());
        assertEquals(0, processesById.get("P3").responseTime());
        assertEquals(10, processesById.get("P4").startTime());
        assertEquals(11, processesById.get("P4").completionTime());
        assertEquals(1, processesById.get("P4").turnaroundTime());
        assertEquals(0, processesById.get("P4").waitingTime());
        assertEquals(0, processesById.get("P4").responseTime());
        assertEquals(0.5, result.averageWaitingTime(), 0.01);
        assertEquals(2.5, result.averageTurnaroundTime(), 0.01);
        assertEquals(0.25, result.averageResponseTime(), 0.01);
        assertEquals(8.0 / 11.0, result.cpuUtilization(), 0.01);
        assertEquals(4.0 / 11.0, result.throughput(), 0.01);
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
