package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Fcfs {

    public record Process(String id, int arrivalTime, int burstTime) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessTiming(String processId, int startTime, int completionTime) {}

    public record Result(List<ExecutionSlice> timeline, List<ProcessTiming> processes) {}

    private static final Comparator<Process> ARRIVAL_ORDER =
            Comparator.comparingInt(Process::arrivalTime).thenComparing(Process::id);

    private Fcfs() {}

    public static Result schedule(List<Process> inputs) {
        validate(inputs);
        List<Process> processes = inputs.stream()
                .map(process -> new Process(SchedulingSupport.normalizeProcessId(process.id()),
                        process.arrivalTime(), process.burstTime()))
                .sorted(ARRIVAL_ORDER).toList();
        List<ExecutionSlice> timeline = new ArrayList<>();
        List<ProcessTiming> timings = new ArrayList<>();
        int currentTime = 0;

        for (Process process : processes) {
            SchedulingSupport.checkCancelled();
            if (currentTime < process.arrivalTime()) {
                SchedulingSupport.checkTraceCapacity(timeline.size());
                timeline.add(new ExecutionSlice(SchedulingSupport.IDLE_LABEL, currentTime, process.arrivalTime()));
                currentTime = process.arrivalTime();
            }

            int startTime = currentTime;
            currentTime = Math.addExact(currentTime, process.burstTime());
            SchedulingSupport.checkTraceCapacity(timeline.size());
            timeline.add(new ExecutionSlice(process.id(), startTime, currentTime));
            timings.add(new ProcessTiming(process.id(), startTime, currentTime));
        }
        return new Result(List.copyOf(timeline), List.copyOf(timings));
    }

    private static void validate(List<Process> processes) {
        SchedulingSupport.validateWorkload(
                processes, Process::id, Process::arrivalTime, Process::burstTime, "FCFS");
    }
}
