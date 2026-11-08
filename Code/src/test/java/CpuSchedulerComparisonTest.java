import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gui.CpuScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class CpuSchedulerComparisonTest {
    @Test
    void comparesTheSameWorkloadAndCalculatesDefinedFairnessMeasures() {
        var inputs = new ArrayList<>(List.of(
                new CpuScheduler.ProcessInput("P2", 0, 2, 1),
                new CpuScheduler.ProcessInput("P1", 0, 2, 1)));
        var original = List.copyOf(inputs);
        var comparisons = CpuScheduler.compare(inputs, 1);
        assertEquals(original, inputs, "Comparison must not mutate the input order");
        assertEquals(List.of(CpuScheduler.Algorithm.values()),
                comparisons.stream().map(CpuScheduler.ComparisonResult::algorithm).toList());
        for (var comparison : comparisons) {
            boolean rr = comparison.algorithm() == CpuScheduler.Algorithm.ROUND_ROBIN;
            assertEquals(rr ? 1.5 : 1.0, comparison.result().averageWaitingTime(), 1e-12);
            assertEquals(rr ? 0.5 : 1.0, comparison.result().averageResponseTime(), 1e-12);
            assertEquals(2.0, comparison.maximumWaitingTime(), 1e-12);
            assertEquals(rr ? 0.5 : 1.0, comparison.waitingTimeStdDev(), 1e-12);
            assertEquals(rr ? 49.0 / 50 : 0.9, comparison.jainFairnessIndex(), 1e-12);
            assertEquals(0.5, comparison.result().throughput(), 1e-12);
            assertEquals(1.0, comparison.result().cpuUtilization(), 1e-12);
        }
        assertThrows(UnsupportedOperationException.class, () -> comparisons.clear());
    }

    @Test
    void aSingleDelayedProcessHasNoWaitingAndIncludesInitialIdleInMetrics() {
        for (var comparison : CpuScheduler.compare(
                List.of(new CpuScheduler.ProcessInput("P1", 5, 3, 1)), 2)) {
            assertEquals(0.0, comparison.maximumWaitingTime());
            assertEquals(0.0, comparison.waitingTimeStdDev());
            assertEquals(1.0, comparison.jainFairnessIndex());
            assertEquals(3.0 / 8, comparison.result().cpuUtilization(), 1e-12);
            assertEquals(1.0 / 8, comparison.result().throughput(), 1e-12);
            assertEquals(new CpuScheduler.ExecutionSlice("IDLE", 0, 5),
                    comparison.result().timeline().getFirst());
        }
    }

    @Test
    void rejectsReservedAndDuplicateNormalizedIdsForEveryAlgorithm() {
        for (var algorithm : CpuScheduler.Algorithm.values()) {
            for (String id : List.of("IDLE", " idle ", "IdLe", "P\n1", "P\t1")) {
                assertThrows(IllegalArgumentException.class, () -> CpuScheduler.schedule(
                        List.of(new CpuScheduler.ProcessInput(id, 2, 3, 1)), algorithm, 2));
            }
            assertThrows(IllegalArgumentException.class, () -> CpuScheduler.schedule(List.of(
                    new CpuScheduler.ProcessInput("P1", 0, 2, 1),
                    new CpuScheduler.ProcessInput(" P1 ", 1, 1, 1)), algorithm, 2));
        }
    }

    @Test
    void normalizesIdsWithoutModifyingCallerRecords() {
        var input = new CpuScheduler.ProcessInput(" P1 ", 0, 2, 1);
        for (var algorithm : CpuScheduler.Algorithm.values()) {
            var result = CpuScheduler.schedule(List.of(input), algorithm, 2);
            assertEquals("P1", result.processes().getFirst().process().id());
            assertEquals("P1", result.timeline().getFirst().processId());
        }
        assertEquals(" P1 ", input.id());
    }

    @Test
    void ignoresQuantumForNonRoundRobinAlgorithms() {
        var valid = List.of(new CpuScheduler.ProcessInput("P1", 0, 3, 1),
                new CpuScheduler.ProcessInput("P2", 1, 2, 1));
        var fcfs = assertDoesNotThrow(() -> CpuScheduler.schedule(valid, CpuScheduler.Algorithm.FCFS, 0));
        var sjf = assertDoesNotThrow(() -> CpuScheduler.schedule(valid, CpuScheduler.Algorithm.SJF, -1));
        assertEquals(fcfs, CpuScheduler.schedule(valid, CpuScheduler.Algorithm.FCFS, 2));
        assertEquals(sjf, CpuScheduler.schedule(valid, CpuScheduler.Algorithm.SJF, 2));
    }

    @Test
    void rejectsEmptyNullAndInvalidTimeInputsAndInvalidComparisonQuantum() {
        var valid = List.of(new CpuScheduler.ProcessInput("P1", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> CpuScheduler.compare(valid, 0));
        assertThrows(IllegalArgumentException.class, () -> CpuScheduler.schedule(valid, null, 2));
        for (var algorithm : CpuScheduler.Algorithm.values()) {
            assertThrows(IllegalArgumentException.class, () -> CpuScheduler.schedule(null, algorithm, 2));
            assertThrows(IllegalArgumentException.class, () -> CpuScheduler.schedule(List.of(), algorithm, 2));
            assertThrows(IllegalArgumentException.class, () -> CpuScheduler.schedule(
                    Arrays.asList((CpuScheduler.ProcessInput) null), algorithm, 2));
            for (var input : List.of(new CpuScheduler.ProcessInput("P1", -1, 1, 1),
                    new CpuScheduler.ProcessInput("P1", 0, 0, 1),
                    new CpuScheduler.ProcessInput("P1", Integer.MAX_VALUE, 1, 1))) {
                assertThrows(IllegalArgumentException.class,
                        () -> CpuScheduler.schedule(List.of(input), algorithm, 2));
            }
        }
    }

    @Test
    void cancellationStopsBeforeWorkStartsWithoutSwallowingInterrupt() {
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, () -> CpuScheduler.compare(
                    List.of(new CpuScheduler.ProcessInput("P1", 0, 10, 1)), 2));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
