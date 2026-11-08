import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import algorithm.Fcfs;
import algorithm.Priority;
import algorithm.RoundRobin;
import algorithm.SchedulingSupport;
import algorithm.Sjf;
import algorithm.Srtf;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class AlgorithmEdgeCasesTest {

    @Test
    void everyDirectEngineRejectsReservedIdleIds() {
        for (String id : List.of("IDLE", "idle", "  IdLe  ")) {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> Fcfs.schedule(List.of(new Fcfs.Process(id, 2, 3)))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> Sjf.schedule(List.of(new Sjf.Process(id, 2, 3)))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> Srtf.schedule(List.of(new Srtf.Process(id, 2, 3)))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> Priority.schedule(List.of(new Priority.Process(id, 2, 3, 1)))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> RoundRobin.schedule(List.of(new RoundRobin.Process(id, 2, 3)), 1)));
        }
    }

    @Test
    void fcfsUsesNormalizedIdsForArrivalTiesAndRejectsNormalizedDuplicates() {
        assertEquals(
                List.of(new Fcfs.ExecutionSlice("A", 0, 1), new Fcfs.ExecutionSlice("B", 1, 2)),
                Fcfs.schedule(List.of(new Fcfs.Process(" B ", 0, 1),
                        new Fcfs.Process("\u2003A\u2003", 0, 1))).timeline());
        assertThrows(IllegalArgumentException.class,
                () -> Fcfs.schedule(List.of(new Fcfs.Process("P", 0, 1),
                        new Fcfs.Process(" P ", 0, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> Fcfs.schedule(List.of(new Fcfs.Process("\u2003", 0, 1))));
    }

    @Test
    void shortestAndPriorityJobsDoNotPreemptAndBreakReadyTiesByArrivalThenId() {
        assertEquals(List.of(
                        new Sjf.ExecutionSlice("Z", 0, 5),
                        new Sjf.ExecutionSlice("A", 5, 6),
                        new Sjf.ExecutionSlice("B", 6, 7)),
                Sjf.schedule(List.of(new Sjf.Process("Z", 0, 5),
                        new Sjf.Process("B", 1, 1), new Sjf.Process("A", 1, 1))).timeline());
        assertEquals(List.of(
                        new Priority.ExecutionSlice("Z", 0, 5),
                        new Priority.ExecutionSlice("B", 5, 6),
                        new Priority.ExecutionSlice("A", 6, 7),
                        new Priority.ExecutionSlice("C", 7, 8)),
                Priority.schedule(List.of(new Priority.Process("Z", 0, 5, 9),
                        new Priority.Process("A", 2, 1, 0),
                        new Priority.Process("B", 1, 1, 0),
                        new Priority.Process("C", 1, 1, 1))).timeline());
    }

    @Test
    void srtfKeepsRunningJobOnEqualRemainingTimeAndPreemptsForStrictlyShorterJob() {
        assertEquals(List.of(
                        new Srtf.ExecutionSlice("Z", 0, 3),
                        new Srtf.ExecutionSlice("B", 3, 4),
                        new Srtf.ExecutionSlice("Z", 4, 7),
                        new Srtf.ExecutionSlice("A", 7, 11)),
                Srtf.schedule(List.of(new Srtf.Process("Z", 0, 6),
                        new Srtf.Process("A", 2, 4),
                        new Srtf.Process("B", 3, 1))).timeline());
    }

    @Test
    void srtfHandlesSimultaneousCompletionAndArrivals() {
        assertEquals(List.of(
                        new Srtf.ExecutionSlice("P", 0, 2),
                        new Srtf.ExecutionSlice("A", 2, 3),
                        new Srtf.ExecutionSlice("B", 3, 4)),
                Srtf.schedule(List.of(new Srtf.Process("P", 0, 2),
                        new Srtf.Process("B", 2, 1),
                        new Srtf.Process("A", 2, 1))).timeline());
    }

    @Test
    void roundRobinSoloJumpPreservesQuantumBoundaryForFutureArrivals() {
        assertEquals(List.of(
                        new RoundRobin.ExecutionSlice("P1", 0, 6),
                        new RoundRobin.ExecutionSlice("P2", 6, 7),
                        new RoundRobin.ExecutionSlice("P1", 7, 11)),
                RoundRobin.schedule(List.of(new RoundRobin.Process("P1", 0, 10),
                        new RoundRobin.Process("P2", 4, 1)), 3).timeline());
        assertEquals(List.of(
                        new RoundRobin.ExecutionSlice("P1", 0, 3),
                        new RoundRobin.ExecutionSlice("P2", 3, 4),
                        new RoundRobin.ExecutionSlice("P1", 4, 11)),
                RoundRobin.schedule(List.of(new RoundRobin.Process("P1", 0, 10),
                        new RoundRobin.Process("P2", 3, 1)), 3).timeline());
    }

    @Test
    void hugeBurstsAndLongIdleGapsDoNotRequirePerTimeUnitIteration() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            assertEquals(List.of(new Srtf.ExecutionSlice("P", 0, Integer.MAX_VALUE)),
                    Srtf.schedule(List.of(new Srtf.Process("P", 0, Integer.MAX_VALUE))).timeline());
            assertEquals(List.of(new RoundRobin.ExecutionSlice("P", 0, Integer.MAX_VALUE)),
                    RoundRobin.schedule(List.of(new RoundRobin.Process("P", 0, Integer.MAX_VALUE)), 1)
                            .timeline());
            assertEquals(List.of(
                            new Srtf.ExecutionSlice("A", 0, 1_000_000),
                            new Srtf.ExecutionSlice("IDLE", 1_000_000, Integer.MAX_VALUE - 7),
                            new Srtf.ExecutionSlice("B", Integer.MAX_VALUE - 7, Integer.MAX_VALUE)),
                    Srtf.schedule(List.of(new Srtf.Process("A", 0, 1_000_000),
                            new Srtf.Process("B", Integer.MAX_VALUE - 7, 7))).timeline());
        });
    }

    @Test
    void everyEngineRejectsOverflowBeforeSimulation() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Fcfs.schedule(List.of(new Fcfs.Process("P", 1, Integer.MAX_VALUE)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Sjf.schedule(List.of(new Sjf.Process("P", 1, Integer.MAX_VALUE)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Srtf.schedule(List.of(new Srtf.Process("P", 1, Integer.MAX_VALUE)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Priority.schedule(List.of(new Priority.Process("P", 1, Integer.MAX_VALUE, 0)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RoundRobin.schedule(List.of(new RoundRobin.Process("P", 1, Integer.MAX_VALUE)), 1))));
    }

    @Test
    void tinyQuantumWithCompetingHugeJobsFailsClearlyBeforeExhaustingMemory() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> RoundRobin.schedule(List.of(
                            new RoundRobin.Process("A", 0, 1_000_000),
                            new RoundRobin.Process("B", 0, 1_000_000)), 1));
            assertTrue(error.getMessage().contains(Integer.toString(SchedulingSupport.MAX_TRACE_SLICES)));
            assertTrue(error.getMessage().contains("quantum"));
        });
    }

    @Test
    void everyEngineHonorsThreadInterruptionWithoutClearingIt() {
        try {
            Thread.currentThread().interrupt();
            assertAll(
                    () -> assertThrows(CancellationException.class,
                            () -> Fcfs.schedule(List.of(new Fcfs.Process("P", 0, 1)))),
                    () -> assertThrows(CancellationException.class,
                            () -> Sjf.schedule(List.of(new Sjf.Process("P", 0, 1)))),
                    () -> assertThrows(CancellationException.class,
                            () -> Srtf.schedule(List.of(new Srtf.Process("P", 0, 1)))),
                    () -> assertThrows(CancellationException.class,
                            () -> Priority.schedule(List.of(new Priority.Process("P", 0, 1, 0)))),
                    () -> assertThrows(CancellationException.class,
                            () -> RoundRobin.schedule(List.of(new RoundRobin.Process("P", 0, 1)), 1)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

}
