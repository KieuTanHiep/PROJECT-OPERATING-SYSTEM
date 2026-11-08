package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class Srtf {

    public record Process(String id, int arrivalTime, int burstTime) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessTiming(String processId, int startTime, int completionTime) {}

    public record Result(List<ExecutionSlice> timeline, List<ProcessTiming> processes) {}

    private static final Comparator<SimProcess> REMAINING_TIME_ORDER =
            Comparator.comparingInt((SimProcess process) -> process.remainingTime)
                    .thenComparingInt(process -> process.input.arrivalTime())
                    .thenComparing(process -> process.input.id());

    private static final Comparator<SimProcess> ARRIVAL_ORDER =
            Comparator.comparingInt((SimProcess process) -> process.input.arrivalTime())
                    .thenComparing(process -> process.input.id());

    private Srtf() {}

    public static Result schedule(List<Process> inputs) {
        validate(inputs);
        List<SimProcess> processes = inputs.stream()
                .map(process -> new Process(SchedulingSupport.normalizeProcessId(process.id()),
                        process.arrivalTime(), process.burstTime()))
                .map(SimProcess::new).toList();
        List<SimProcess> arrivals = processes.stream().sorted(ARRIVAL_ORDER).toList();
        PriorityQueue<SimProcess> ready = new PriorityQueue<>(REMAINING_TIME_ORDER);
        List<ExecutionSlice> timeline = new ArrayList<>();
        int currentTime = 0;
        int nextArrival = 0;
        int completed = 0;
        SimProcess running = null;

        while (completed < processes.size()) {
            SchedulingSupport.checkCancelled();
            if (running == null && ready.isEmpty()
                    && currentTime < arrivals.get(nextArrival).input.arrivalTime()) {
                int arrivalTime = arrivals.get(nextArrival).input.arrivalTime();
                addSlice(timeline, SchedulingSupport.IDLE_LABEL, currentTime, arrivalTime);
                currentTime = arrivalTime;
            }
            while (nextArrival < arrivals.size()
                    && arrivals.get(nextArrival).input.arrivalTime() <= currentTime) {
                ready.add(arrivals.get(nextArrival++));
            }

            // Equal remaining time keeps the current process; a new job must
            // be strictly shorter to cause preemption.
            if (running != null && !ready.isEmpty()
                    && ready.peek().remainingTime < running.remainingTime) {
                ready.add(running);
                running = null;
            }
            if (running == null) {
                running = ready.remove();
            }
            if (running.startTime < 0) {
                running.startTime = currentTime;
            }
            int duration = running.remainingTime;
            if (nextArrival < arrivals.size()) {
                duration = Math.min(duration,
                        arrivals.get(nextArrival).input.arrivalTime() - currentTime);
            }
            int endTime = Math.addExact(currentTime, duration);
            addSlice(timeline, running.input.id(), currentTime, endTime);
            running.remainingTime -= duration;
            currentTime = endTime;
            if (running.remainingTime == 0) {
                running.completionTime = currentTime;
                completed++;
                running = null;
            }
        }

        List<ProcessTiming> timings = processes.stream()
                .map(process -> new ProcessTiming(
                        process.input.id(), process.startTime, process.completionTime))
                .toList();
        return new Result(List.copyOf(timeline), timings);
    }

    private static void addSlice(
            List<ExecutionSlice> timeline, String processId, int startTime, int endTime) {
        if (!timeline.isEmpty()) {
            ExecutionSlice previous = timeline.get(timeline.size() - 1);
            if (previous.processId().equals(processId) && previous.endTime() == startTime) {
                timeline.set(timeline.size() - 1,
                        new ExecutionSlice(processId, previous.startTime(), endTime));
                return;
            }
        }
        SchedulingSupport.checkTraceCapacity(timeline.size());
        timeline.add(new ExecutionSlice(processId, startTime, endTime));
    }

    private static void validate(List<Process> processes) {
        SchedulingSupport.validateWorkload(
                processes, Process::id, Process::arrivalTime, Process::burstTime, "SRTF");
    }

    private static final class SimProcess {
        private final Process input;
        private int remainingTime;
        private int startTime = -1;
        private int completionTime = -1;

        private SimProcess(Process input) {
            this.input = input;
            remainingTime = input.burstTime();
        }
    }
}
