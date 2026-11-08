import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gui.CpuScheduler;
import gui.CpuScheduler.Algorithm;
import gui.CpuScheduler.ProcessInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tools.SchedulerOracle;

class SchedulerOracleTest {
    @Test
    void referenceRejectsInvalidInputsBeforeStartingItsTickLoop() {
        assertThrows(IllegalArgumentException.class, () -> SchedulerOracle.expected(null, Algorithm.FCFS, 2));
        assertThrows(IllegalArgumentException.class, () -> SchedulerOracle.expected(
                Arrays.asList((ProcessInput) null), Algorithm.FCFS, 2));
        for (ProcessInput input : List.of(new ProcessInput("P1", 0, 0, 1),
                new ProcessInput("P1", 0, -1, 1), new ProcessInput("P1", -1, 2, 1),
                new ProcessInput("IDLE", 0, 1, 1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> SchedulerOracle.expected(List.of(input), Algorithm.FCFS, 2));
        }
    }

    @Test
    void seededWorkloadsMatchIndependentTickOracleForAllAlgorithmsAndMetrics() {
        long seed = 20261108L;
        Random random = new Random(seed);
        for (int caseNumber = 0; caseNumber < 300; caseNumber++) {
            List<ProcessInput> inputs = new ArrayList<>();
            int count = 1 + random.nextInt(8);
            for (int i = 0; i < count; i++) {
                inputs.add(new ProcessInput("P" + (i + 1), random.nextInt(16),
                        1 + random.nextInt(10), random.nextInt(5) - 2));
            }
            Collections.shuffle(inputs, random);
            int quantum = 1 + random.nextInt(6);
            for (Algorithm algorithm : Algorithm.values()) {
                var expected = SchedulerOracle.expected(inputs, algorithm, quantum);
                var actual = CpuScheduler.schedule(inputs, algorithm, quantum);
                List<String> errors = new ArrayList<>(SchedulerOracle.differences(expected, actual));
                errors.addAll(SchedulerOracle.invariantErrors(inputs, actual));
                assertTrue(errors.isEmpty(), "seed=" + seed + ", case=" + caseNumber + ", algorithm=" + algorithm
                        + ", quantum=" + quantum + ", inputs=" + inputs + ", errors=" + errors
                        + "\nexpected=" + expected + "\nactual=" + actual);
            }
        }
    }

    @Test
    void referenceAndSchedulerHonorHandCalculatedInitialIdleAndTieBreaks() {
        List<ProcessInput> inputs = List.of(
                new ProcessInput("Z", 5, 2, 2), new ProcessInput("A", 5, 1, 1),
                new ProcessInput("M", 12, 1, 1));
        for (Algorithm algorithm : Algorithm.values()) {
            var expected = SchedulerOracle.expected(inputs, algorithm, 2);
            assertEquals("IDLE[0:5]|A[5:6]|Z[6:8]|IDLE[8:12]|M[12:13]", SchedulerOracle.describeTrace(expected));
            assertEquals(1.0 / 3.0, expected.averageWaitingTime(), 1e-9);
            assertEquals(5.0 / 3.0, expected.averageTurnaroundTime(), 1e-9);
            assertEquals(4.0 / 13.0, expected.cpuUtilization(), 1e-9);
            assertEquals(3.0 / 13.0, expected.throughput(), 1e-9);
            assertTrue(SchedulerOracle.differences(expected, CpuScheduler.schedule(inputs, algorithm, 2)).isEmpty());
        }
    }

    @Test
    void referenceAndSchedulerKeepRunningSrtfProcessOnEqualRemainingTime() {
        List<ProcessInput> inputs = List.of(new ProcessInput("B", 0, 4, 1), new ProcessInput("A", 2, 2, 1));
        var expected = SchedulerOracle.expected(inputs, Algorithm.SRTF, 1);
        assertEquals("B[0:4]|A[4:6]", SchedulerOracle.describeTrace(expected));
        assertEquals(1, expected.averageWaitingTime(), 1e-9);
        assertEquals(1, expected.averageResponseTime(), 1e-9);
        assertTrue(SchedulerOracle.differences(expected, CpuScheduler.schedule(inputs, Algorithm.SRTF, 1)).isEmpty());
    }

    @Test
    void referenceAndSchedulerQueueBoundaryArrivalBeforePreemptedRoundRobinProcess() {
        List<ProcessInput> inputs = List.of(new ProcessInput("P1", 0, 5, 1), new ProcessInput("P2", 2, 1, 1));
        var expected = SchedulerOracle.expected(inputs, Algorithm.ROUND_ROBIN, 2);
        assertEquals("P1[0:2]|P2[2:3]|P1[3:6]", SchedulerOracle.describeTrace(expected));
        assertEquals("P1:0:6:6:1:0|P2:2:3:1:0:0", SchedulerOracle.describeProcesses(expected));
        assertTrue(SchedulerOracle.differences(expected, CpuScheduler.schedule(inputs, Algorithm.ROUND_ROBIN, 2)).isEmpty());
    }
}
