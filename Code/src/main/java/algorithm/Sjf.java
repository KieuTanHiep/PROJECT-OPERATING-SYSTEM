package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class Sjf {

    public record Process(String id, int arrivalTime, int burstTime) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessTiming(String processId, int startTime, int completionTime) {}

    public record Result(List<ExecutionSlice> timeline, List<ProcessTiming> processes) {}

    private static final Comparator<Process> BURST_ORDER =
            Comparator.comparingInt(Process::burstTime)
                    .thenComparingInt(Process::arrivalTime)
                    .thenComparing(Process::id);

    private static final Comparator<Process> ARRIVAL_ORDER =
            Comparator.comparingInt(Process::arrivalTime).thenComparing(Process::id);

    private Sjf() {}

    public static Result schedule(List<Process> inputs) {
        validate(inputs);
        List<Process> arrivals = inputs.stream()
                .map(process -> new Process(SchedulingSupport.normalizeProcessId(process.id()),
                        process.arrivalTime(), process.burstTime()))
                .sorted(ARRIVAL_ORDER)
                .toList();
        PriorityQueue<Process> ready = new PriorityQueue<>(BURST_ORDER);
        List<ExecutionSlice> timeline = new ArrayList<>();
        List<ProcessTiming> timings = new ArrayList<>();
        int nextArrival = 0;
        int currentTime = 0;

        while (nextArrival < arrivals.size() || !ready.isEmpty()) {
            SchedulingSupport.checkCancelled();
            if (ready.isEmpty() && currentTime < arrivals.get(nextArrival).arrivalTime()) {
                int arrivalTime = arrivals.get(nextArrival).arrivalTime();
                SchedulingSupport.checkTraceCapacity(timeline.size());
                timeline.add(new ExecutionSlice(SchedulingSupport.IDLE_LABEL, currentTime, arrivalTime));
                currentTime = arrivalTime;
            }
            while (nextArrival < arrivals.size()
                    && arrivals.get(nextArrival).arrivalTime() <= currentTime) {
                ready.add(arrivals.get(nextArrival++));
            }
            Process next = ready.remove();
            int startTime = currentTime;
            currentTime = Math.addExact(currentTime, next.burstTime());
            SchedulingSupport.checkTraceCapacity(timeline.size());
            timeline.add(new ExecutionSlice(next.id(), startTime, currentTime));
            timings.add(new ProcessTiming(next.id(), startTime, currentTime));
        }
        return new Result(List.copyOf(timeline), List.copyOf(timings));
    }

    private static void validate(List<Process> processes) {
        SchedulingSupport.validateWorkload(
                processes, Process::id, Process::arrivalTime, Process::burstTime, "SJF");
    }
}
