package gui;

import algorithm.Fcfs;
import algorithm.Priority;
import algorithm.RoundRobin;
import algorithm.SchedulingSupport;
import algorithm.Sjf;
import algorithm.Srtf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CpuScheduler {

    public enum Algorithm {
        FCFS("FCFS"),
        SJF("SJF"),
        SRTF("SRTF"),
        PRIORITY("Priority"),
        ROUND_ROBIN("Round Robin");

        private final String displayName;

        Algorithm(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record ProcessInput(String id, int arrivalTime, int burstTime, int priority) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessResult(
            ProcessInput process,
            int startTime,
            int completionTime,
            int turnaroundTime,
            int waitingTime,
            int responseTime) {}

    public record ScheduleResult(
            List<ExecutionSlice> timeline,
            List<ProcessResult> processes,
            double averageWaitingTime,
            double averageTurnaroundTime,
            double averageResponseTime,
            double cpuUtilization,
            double throughput) {}

    /** Jain's index is calculated on burst / turnaround (inverse slowdown), not raw waiting. */
    public record ComparisonResult(
            Algorithm algorithm,
            ScheduleResult result,
            double maximumWaitingTime,
            double waitingTimeStdDev,
            double jainFairnessIndex) {}

    private CpuScheduler() {}

    public static ScheduleResult schedule(
            List<ProcessInput> inputs, Algorithm algorithm, int quantum) {
        List<ProcessInput> normalizedInputs = validate(inputs, algorithm, quantum);
        EngineResult execution = switch (algorithm) {
            case FCFS -> scheduleFcfs(normalizedInputs);
            case SJF -> scheduleSjf(normalizedInputs);
            case SRTF -> scheduleSrtf(normalizedInputs);
            case PRIORITY -> schedulePriority(normalizedInputs);
            case ROUND_ROBIN -> scheduleRoundRobin(normalizedInputs, quantum);
        };
        return createResult(normalizedInputs, execution);
    }

    /** Runs every algorithm against an immutable snapshot of the same workload. */
    public static List<ComparisonResult> compare(List<ProcessInput> inputs, int quantum) {
        List<ProcessInput> snapshot = validate(inputs, Algorithm.ROUND_ROBIN, quantum);
        List<ComparisonResult> comparisons = new ArrayList<>();
        for (Algorithm algorithm : Algorithm.values()) {
            SchedulingSupport.checkCancelled();
            ScheduleResult result = schedule(snapshot, algorithm, quantum);
            comparisons.add(summarizeComparison(algorithm, result));
        }
        return List.copyOf(comparisons);
    }

    private static List<ProcessInput> validate(
            List<ProcessInput> inputs, Algorithm algorithm, int quantum) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Hãy chọn thuật toán lập lịch.");
        }
        if (algorithm == Algorithm.ROUND_ROBIN && quantum <= 0) {
            throw new IllegalArgumentException("Quantum phải lớn hơn 0.");
        }

        SchedulingSupport.validateWorkload(inputs, ProcessInput::id,
                ProcessInput::arrivalTime, ProcessInput::burstTime, "Bộ lập lịch");
        return inputs.stream()
                .map(input -> new ProcessInput(SchedulingSupport.normalizeProcessId(input.id()),
                        input.arrivalTime(), input.burstTime(), input.priority()))
                .toList();
    }

    private static EngineResult scheduleFcfs(List<ProcessInput> inputs) {
        Fcfs.Result result = Fcfs.schedule(inputs.stream()
                .map(input -> new Fcfs.Process(input.id(), input.arrivalTime(), input.burstTime()))
                .toList());
        return new EngineResult(
                result.timeline().stream().map(slice -> executionSlice(
                        slice.processId(), slice.startTime(), slice.endTime())).toList(),
                result.processes().stream().map(timing -> processTiming(
                        timing.processId(), timing.startTime(), timing.completionTime())).toList());
    }

    private static EngineResult scheduleSjf(List<ProcessInput> inputs) {
        Sjf.Result result = Sjf.schedule(inputs.stream()
                .map(input -> new Sjf.Process(input.id(), input.arrivalTime(), input.burstTime()))
                .toList());
        return new EngineResult(
                result.timeline().stream().map(slice -> executionSlice(
                        slice.processId(), slice.startTime(), slice.endTime())).toList(),
                result.processes().stream().map(timing -> processTiming(
                        timing.processId(), timing.startTime(), timing.completionTime())).toList());
    }

    private static EngineResult scheduleSrtf(List<ProcessInput> inputs) {
        Srtf.Result result = Srtf.schedule(inputs.stream()
                .map(input -> new Srtf.Process(input.id(), input.arrivalTime(), input.burstTime()))
                .toList());
        return new EngineResult(
                result.timeline().stream().map(slice -> executionSlice(
                        slice.processId(), slice.startTime(), slice.endTime())).toList(),
                result.processes().stream().map(timing -> processTiming(
                        timing.processId(), timing.startTime(), timing.completionTime())).toList());
    }

    private static EngineResult schedulePriority(List<ProcessInput> inputs) {
        Priority.Result result = Priority.schedule(inputs.stream()
                .map(input -> new Priority.Process(
                        input.id(), input.arrivalTime(), input.burstTime(), input.priority()))
                .toList());
        return new EngineResult(
                result.timeline().stream().map(slice -> executionSlice(
                        slice.processId(), slice.startTime(), slice.endTime())).toList(),
                result.processes().stream().map(timing -> processTiming(
                        timing.processId(), timing.startTime(), timing.completionTime())).toList());
    }

    private static EngineResult scheduleRoundRobin(List<ProcessInput> inputs, int quantum) {
        RoundRobin.Result result = RoundRobin.schedule(inputs.stream()
                .map(input -> new RoundRobin.Process(input.id(), input.arrivalTime(), input.burstTime()))
                .toList(), quantum);
        return new EngineResult(
                result.timeline().stream().map(slice -> executionSlice(
                        slice.processId(), slice.startTime(), slice.endTime())).toList(),
                result.processes().stream().map(timing -> processTiming(
                        timing.processId(), timing.startTime(), timing.completionTime())).toList());
    }

    private static ExecutionSlice executionSlice(String processId, int startTime, int endTime) {
        SchedulingSupport.checkCancelled();
        return new ExecutionSlice(processId, startTime, endTime);
    }

    private static ProcessTiming processTiming(String processId, int startTime, int completionTime) {
        SchedulingSupport.checkCancelled();
        return new ProcessTiming(processId, startTime, completionTime);
    }

    private static ScheduleResult createResult(List<ProcessInput> inputs, EngineResult execution) {
        Map<String, ProcessTiming> timingsById = execution.timings().stream()
                .collect(Collectors.toMap(ProcessTiming::processId, Function.identity()));
        List<ProcessResult> results = new ArrayList<>();
        double totalWaitingTime = 0;
        double totalTurnaroundTime = 0;
        double totalResponseTime = 0;
        long totalBurstTime = 0;

        for (ProcessInput input : inputs) {
            SchedulingSupport.checkCancelled();
            ProcessTiming timing = timingsById.get(input.id());
            int turnaroundTime = timing.completionTime() - input.arrivalTime();
            int waitingTime = turnaroundTime - input.burstTime();
            int responseTime = timing.startTime() - input.arrivalTime();
            results.add(new ProcessResult(input, timing.startTime(), timing.completionTime(),
                    turnaroundTime, waitingTime, responseTime));
            totalWaitingTime += waitingTime;
            totalTurnaroundTime += turnaroundTime;
            totalResponseTime += responseTime;
            totalBurstTime += input.burstTime();
        }

        results.sort(Comparator.comparing(result -> result.process().id()));
        int endTime = execution.timeline().getLast().endTime();
        double processCount = inputs.size();
        return new ScheduleResult(
                List.copyOf(execution.timeline()),
                List.copyOf(results),
                totalWaitingTime / processCount,
                totalTurnaroundTime / processCount,
                totalResponseTime / processCount,
                (double) totalBurstTime / endTime,
                processCount / endTime);
    }

    private static ComparisonResult summarizeComparison(Algorithm algorithm, ScheduleResult result) {
        double maxWaiting = 0;
        double squaredWaitingDifferences = 0;
        double serviceScoreSum = 0;
        double squaredServiceScoreSum = 0;
        for (ProcessResult process : result.processes()) {
            SchedulingSupport.checkCancelled();
            maxWaiting = Math.max(maxWaiting, process.waitingTime());
            double difference = process.waitingTime() - result.averageWaitingTime();
            squaredWaitingDifferences += difference * difference;
            double serviceScore = (double) process.process().burstTime() / process.turnaroundTime();
            serviceScoreSum += serviceScore;
            squaredServiceScoreSum += serviceScore * serviceScore;
        }
        int count = result.processes().size();
        double fairness = serviceScoreSum * serviceScoreSum / (count * squaredServiceScoreSum);
        return new ComparisonResult(
                algorithm, result, maxWaiting,
                Math.sqrt(squaredWaitingDifferences / count), Math.min(1.0, fairness));
    }

    private record ProcessTiming(String processId, int startTime, int completionTime) {}

    private record EngineResult(List<ExecutionSlice> timeline, List<ProcessTiming> timings) {}
}
