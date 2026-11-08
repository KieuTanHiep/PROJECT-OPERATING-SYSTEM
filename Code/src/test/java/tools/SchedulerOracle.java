package tools;

import gui.CpuScheduler.Algorithm;
import gui.CpuScheduler.ExecutionSlice;
import gui.CpuScheduler.ProcessInput;
import gui.CpuScheduler.ProcessResult;
import gui.CpuScheduler.ScheduleResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Test-only reference simulator. Deliberately runs one time unit at a time. */
public final class SchedulerOracle {
    private static final double EPSILON = 1e-9;
    private static final int MAX_REFERENCE_TIME = 100_000;

    private SchedulerOracle() {}

    public static ScheduleResult expected(List<ProcessInput> inputs, Algorithm algorithm, int quantum) {
        validateReferenceInput(inputs, algorithm, quantum);
        long upperBound = inputs.stream().mapToLong(ProcessInput::burstTime).sum()
                + inputs.stream().mapToInt(ProcessInput::arrivalTime).max().orElse(0);
        if (upperBound > MAX_REFERENCE_TIME) {
            throw new IllegalArgumentException("Tick oracle is restricted to small workloads; use invariants for stress tests.");
        }
        List<State> states = inputs.stream().map(State::new).toList();
        List<State> arrivals = states.stream().sorted(Comparator
                .comparingInt((State state) -> state.input.arrivalTime())
                .thenComparing(state -> state.input.id())).toList();
        ArrayDeque<State> ready = new ArrayDeque<>();
        List<ExecutionSlice> timeline = new ArrayList<>();
        int nextArrival = 0;
        int time = 0;
        int completed = 0;
        int quantumLeft = 0;
        State running = null;

        while (completed < states.size()) {
            if (algorithm == Algorithm.ROUND_ROBIN) {
                while (nextArrival < arrivals.size()
                        && arrivals.get(nextArrival).input.arrivalTime() <= time) {
                    ready.addLast(arrivals.get(nextArrival++));
                }
                if (running == null && !ready.isEmpty()) {
                    running = ready.removeFirst();
                    quantumLeft = quantum;
                }
            } else if (running == null || algorithm == Algorithm.SRTF) {
                State best = null;
                for (State candidate : states) {
                    if (candidate.remaining > 0 && candidate.input.arrivalTime() <= time
                            && (best == null || order(candidate, best, algorithm) < 0)) {
                        best = candidate;
                    }
                }
                // SRTF does not interrupt the running process on an equal remaining time.
                if (algorithm != Algorithm.SRTF || running == null || best == null
                        || running.remaining != best.remaining) {
                    running = best;
                }
            }

            if (running == null) {
                appendTick(timeline, "IDLE", time++);
                continue;
            }
            if (running.start < 0) {
                running.start = time;
            }
            appendTick(timeline, running.input.id(), time++);
            running.remaining--;
            if (algorithm == Algorithm.ROUND_ROBIN) {
                quantumLeft--;
                // Arrivals at the quantum boundary go before the preempted process.
                while (nextArrival < arrivals.size()
                        && arrivals.get(nextArrival).input.arrivalTime() <= time) {
                    ready.addLast(arrivals.get(nextArrival++));
                }
            }
            if (running.remaining == 0) {
                running.completion = time;
                completed++;
                running = null;
            } else if (algorithm == Algorithm.ROUND_ROBIN && quantumLeft == 0) {
                ready.addLast(running);
                running = null;
            }
        }

        List<ProcessResult> results = new ArrayList<>();
        long burstSum = 0;
        double waitingSum = 0;
        double turnaroundSum = 0;
        double responseSum = 0;
        for (State state : states) {
            int turnaround = state.completion - state.input.arrivalTime();
            int waiting = turnaround - state.input.burstTime();
            int response = state.start - state.input.arrivalTime();
            results.add(new ProcessResult(state.input, state.start, state.completion,
                    turnaround, waiting, response));
            burstSum += state.input.burstTime();
            waitingSum += waiting;
            turnaroundSum += turnaround;
            responseSum += response;
        }
        results.sort(Comparator.comparing(result -> result.process().id()));
        return new ScheduleResult(List.copyOf(timeline), List.copyOf(results),
                waitingSum / states.size(), turnaroundSum / states.size(), responseSum / states.size(),
                (double) burstSum / time, (double) states.size() / time);
    }

    public static List<String> differences(ScheduleResult expected, ScheduleResult actual) {
        List<String> errors = new ArrayList<>();
        if (!expected.timeline().equals(actual.timeline())) {
            errors.add("timeline differs");
        }
        Map<String, ProcessResult> expectedProcesses = byId(expected.processes());
        Map<String, ProcessResult> actualProcesses = byId(actual.processes());
        if (!expectedProcesses.equals(actualProcesses)) {
            errors.add("per-process ST/CT/TAT/WT/RT differs");
        }
        equalNumber(errors, "averageWaitingTime", expected.averageWaitingTime(), actual.averageWaitingTime());
        equalNumber(errors, "averageTurnaroundTime", expected.averageTurnaroundTime(), actual.averageTurnaroundTime());
        equalNumber(errors, "averageResponseTime", expected.averageResponseTime(), actual.averageResponseTime());
        equalNumber(errors, "cpuUtilization", expected.cpuUtilization(), actual.cpuUtilization());
        equalNumber(errors, "throughput", expected.throughput(), actual.throughput());
        return errors;
    }

    /** Checks accounting and work conservation without expanding a potentially huge timeline. */
    public static List<String> invariantErrors(List<ProcessInput> inputs, ScheduleResult result) {
        List<String> errors = new ArrayList<>();
        Map<String, ProcessInput> inputById = new LinkedHashMap<>();
        Map<String, Long> executed = new HashMap<>();
        Map<String, Integer> firstStart = new HashMap<>();
        Map<String, Integer> lastEnd = new HashMap<>();
        for (ProcessInput input : inputs) {
            inputById.put(input.id(), input);
        }
        int cursor = 0;
        for (ExecutionSlice slice : result.timeline()) {
            if (slice.startTime() != cursor || slice.endTime() <= slice.startTime()) {
                errors.add("timeline is not positive and contiguous at " + slice);
            }
            cursor = slice.endTime();
            if ("IDLE".equals(slice.processId())) {
                for (ProcessInput input : inputs) {
                    if (input.arrivalTime() < slice.endTime()
                            && executed.getOrDefault(input.id(), 0L) < input.burstTime()) {
                        errors.add("CPU idle while process is ready: " + input.id());
                        break;
                    }
                }
                continue;
            }
            ProcessInput input = inputById.get(slice.processId());
            if (input == null) {
                errors.add("unknown process in trace: " + slice.processId());
                continue;
            }
            if (slice.startTime() < input.arrivalTime()) {
                errors.add("process runs before arrival: " + input.id());
            }
            executed.merge(input.id(), (long) slice.endTime() - slice.startTime(), Long::sum);
            firstStart.putIfAbsent(input.id(), slice.startTime());
            lastEnd.put(input.id(), slice.endTime());
        }
        Map<String, ProcessResult> actualById = byId(result.processes());
        if (result.processes().size() != inputs.size() || actualById.size() != inputs.size()) {
            errors.add("process count differs");
        }
        double waitingSum = 0;
        double turnaroundSum = 0;
        double responseSum = 0;
        long burstSum = 0;
        for (ProcessInput input : inputs) {
            burstSum += input.burstTime();
            if (executed.getOrDefault(input.id(), 0L) != input.burstTime()) {
                errors.add("executed duration differs from burst: " + input.id());
            }
            ProcessResult actual = actualById.get(input.id());
            if (actual == null || !input.equals(actual.process())) {
                errors.add("missing or changed process: " + input.id());
                continue;
            }
            if (actual.startTime() != firstStart.getOrDefault(input.id(), -1)
                    || actual.completionTime() != lastEnd.getOrDefault(input.id(), -1)) {
                errors.add("ST/CT differs from trace: " + input.id());
            }
            int turnaround = actual.completionTime() - input.arrivalTime();
            int waiting = turnaround - input.burstTime();
            int response = actual.startTime() - input.arrivalTime();
            if (actual.turnaroundTime() != turnaround || actual.waitingTime() != waiting
                    || actual.responseTime() != response || waiting < 0 || response < 0 || response > waiting) {
                errors.add("invalid timing formulas: " + input.id());
            }
            waitingSum += waiting;
            turnaroundSum += turnaround;
            responseSum += response;
        }
        if (cursor <= 0 || inputs.isEmpty()) {
            errors.add("empty or nonpositive simulated duration");
            return errors;
        }
        equalNumber(errors, "averageWaitingTime", waitingSum / inputs.size(), result.averageWaitingTime());
        equalNumber(errors, "averageTurnaroundTime", turnaroundSum / inputs.size(), result.averageTurnaroundTime());
        equalNumber(errors, "averageResponseTime", responseSum / inputs.size(), result.averageResponseTime());
        equalNumber(errors, "cpuUtilization", (double) burstSum / cursor, result.cpuUtilization());
        equalNumber(errors, "throughput", (double) inputs.size() / cursor, result.throughput());
        return errors;
    }

    public static String describeTrace(ScheduleResult result) {
        return result.timeline().stream()
                .map(slice -> slice.processId() + "[" + slice.startTime() + ":" + slice.endTime() + "]")
                .collect(Collectors.joining("|"));
    }

    public static String describeProcesses(ScheduleResult result) {
        return result.processes().stream().sorted(Comparator.comparing(process -> process.process().id()))
                .map(process -> process.process().id() + ":" + process.startTime() + ":"
                        + process.completionTime() + ":" + process.turnaroundTime() + ":"
                        + process.waitingTime() + ":" + process.responseTime())
                .collect(Collectors.joining("|"));
    }

    private static void validateReferenceInput(List<ProcessInput> inputs, Algorithm algorithm, int quantum) {
        if (inputs == null || inputs.isEmpty() || algorithm == null || quantum <= 0) {
            throw new IllegalArgumentException("Reference input must be nonempty with a positive quantum.");
        }
        Set<String> ids = new HashSet<>();
        for (ProcessInput input : inputs) {
            if (input == null || input.id() == null || input.id().isBlank()
                    || "IDLE".equalsIgnoreCase(input.id().strip())) {
                throw new IllegalArgumentException("Reference processes need nonblank, non-IDLE identifiers.");
            }
            if (!ids.add(input.id()) || input.arrivalTime() < 0 || input.burstTime() <= 0) {
                throw new IllegalArgumentException("Reference inputs need unique IDs, arrival >= 0 and burst > 0.");
            }
        }
    }

    private static Map<String, ProcessResult> byId(List<ProcessResult> processes) {
        Map<String, ProcessResult> result = new HashMap<>();
        for (ProcessResult process : processes) {
            result.put(process.process().id(), process);
        }
        return result;
    }

    private static void equalNumber(List<String> errors, String name, double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > EPSILON * Math.max(1, Math.abs(expected))) {
            errors.add(name + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static int order(State left, State right, Algorithm algorithm) {
        int primary = switch (algorithm) {
            case FCFS -> 0;
            case SJF -> Integer.compare(left.input.burstTime(), right.input.burstTime());
            case SRTF -> Integer.compare(left.remaining, right.remaining);
            case PRIORITY -> Integer.compare(left.input.priority(), right.input.priority());
            case ROUND_ROBIN -> throw new IllegalArgumentException("RR uses a FIFO queue.");
        };
        if (primary != 0) {
            return primary;
        }
        int arrival = Integer.compare(left.input.arrivalTime(), right.input.arrivalTime());
        return arrival != 0 ? arrival : left.input.id().compareTo(right.input.id());
    }

    private static void appendTick(List<ExecutionSlice> timeline, String id, int start) {
        if (!timeline.isEmpty()) {
            ExecutionSlice last = timeline.get(timeline.size() - 1);
            if (last.processId().equals(id) && last.endTime() == start) {
                timeline.set(timeline.size() - 1, new ExecutionSlice(id, last.startTime(), start + 1));
                return;
            }
        }
        timeline.add(new ExecutionSlice(id, start, start + 1));
    }

    private static final class State {
        final ProcessInput input;
        int remaining;
        int start = -1;
        int completion = -1;

        State(ProcessInput input) {
            this.input = input;
            remaining = input.burstTime();
        }
    }
}
