import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import GUI.CpuScheduler;
import org.junit.jupiter.api.Test;

class CpuSchedulerTest {

    @Test
    void fcfsRunsProcessesInArrivalOrder() {
        var result = CpuScheduler.schedule(
                List.of(
                        new CpuScheduler.ProcessInput("P1", 0, 5, 2),
                        new CpuScheduler.ProcessInput("P2", 1, 3, 1),
                        new CpuScheduler.ProcessInput("P3", 2, 2, 3)),
                CpuScheduler.Algorithm.FCFS,
                2);

        assertEquals(
                List.of("P1 [0-5]", "P2 [5-8]", "P3 [8-10]"),
                describe(result.timeline()));
        assertEquals(3.33, result.averageWaitingTime(), 0.01);
        assertEquals(6.67, result.averageTurnaroundTime(), 0.01);
    }

    @Test
    void fcfsRecordsIdleTimeBeforeFirstArrival() {
        var result = CpuScheduler.schedule(
                List.of(new CpuScheduler.ProcessInput("P1", 3, 2, 1)),
                CpuScheduler.Algorithm.FCFS,
                2);

        assertEquals(List.of("IDLE [0-3]", "P1 [3-5]"), describe(result.timeline()));
    }

    @Test
    void sjfChoosesShortestReadyProcess() {
        var result = CpuScheduler.schedule(
                workload(),
                CpuScheduler.Algorithm.SJF,
                2);

        assertEquals(
                List.of("P1 [0-7]", "P3 [7-8]", "P2 [8-12]"),
                describe(result.timeline()));
    }

    @Test
    void srtfPreemptsForShorterProcesses() {
        var result = CpuScheduler.schedule(
                workload(),
                CpuScheduler.Algorithm.SRTF,
                2);

        assertEquals(
                List.of("P1 [0-2]", "P2 [2-4]", "P3 [4-5]", "P2 [5-7]", "P1 [7-12]"),
                describe(result.timeline()));
    }

    @Test
    void priorityChoosesLowestPriorityNumber() {
        var result = CpuScheduler.schedule(
                workload(),
                CpuScheduler.Algorithm.PRIORITY,
                2);

        assertEquals(
                List.of("P1 [0-7]", "P2 [7-11]", "P3 [11-12]"),
                describe(result.timeline()));
    }

    @Test
    void roundRobinUsesConfiguredQuantum() {
        var result = CpuScheduler.schedule(
                workload(),
                CpuScheduler.Algorithm.ROUND_ROBIN,
                2);

        assertEquals(
                List.of("P1 [0-2]", "P2 [2-4]", "P1 [4-6]", "P3 [6-7]", "P2 [7-9]", "P1 [9-12]"),
                describe(result.timeline()));
    }

    @Test
    void rejectsNonPositiveRoundRobinQuantum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CpuScheduler.schedule(
                        workload(),
                        CpuScheduler.Algorithm.ROUND_ROBIN,
                        0));
    }

    private List<CpuScheduler.ProcessInput> workload() {
        return List.of(
                new CpuScheduler.ProcessInput("P1", 0, 7, 2),
                new CpuScheduler.ProcessInput("P2", 2, 4, 1),
                new CpuScheduler.ProcessInput("P3", 4, 1, 3));
    }

    private List<String> describe(List<CpuScheduler.ExecutionSlice> timeline) {
        return timeline.stream()
                .map(slice -> slice.processId() + " [" + slice.startTime() + "-" + slice.endTime() + "]")
                .toList();
    }
}
