package algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RoundRobin {

    public record Process(String id, int arrivalTime, int burstTime) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessTiming(String processId, int startTime, int completionTime) {}

    public record Result(List<ExecutionSlice> timeline, List<ProcessTiming> processes) {}

    private static final Comparator<Process> ARRIVAL_ORDER =
            Comparator.comparingInt(Process::arrivalTime).thenComparing(Process::id);

    private RoundRobin() {}

    public static Result schedule(List<Process> inputs, int quantum) {
        validate(inputs, quantum);

        List<SimProcess> processes = inputs.stream()
                .map(process -> new Process(SchedulingSupport.normalizeProcessId(process.id()),
                        process.arrivalTime(), process.burstTime()))
                .sorted(ARRIVAL_ORDER)
                .map(SimProcess::new)
                .toList();
        List<ExecutionSlice> timeline = new ArrayList<>();
        ArrayDeque<SimProcess> readyQueue = new ArrayDeque<>();
        int nextProcess = 0;
        int currentTime = 0;
        int completed = 0;

        while (completed < processes.size()) {
            SchedulingSupport.checkCancelled();
            while (nextProcess < processes.size()
                    && processes.get(nextProcess).input.arrivalTime() <= currentTime) {
                readyQueue.addLast(processes.get(nextProcess++));
            }

            if (readyQueue.isEmpty()) {
                int nextArrival = processes.get(nextProcess).input.arrivalTime();
                addSlice(timeline, SchedulingSupport.IDLE_LABEL, currentTime, nextArrival);
                currentTime = nextArrival;
                continue;
            }

            SimProcess running = readyQueue.removeFirst();
            if (running.startTime < 0) {
                running.startTime = currentTime;
            }

            int duration = Math.min(quantum, running.remainingTime);
            if (readyQueue.isEmpty()) {
                if (nextProcess == processes.size()) {
                    duration = running.remainingTime;
                } else {
                    // Skip self-requeues while no other process is ready. An
                    // arrival still waits until the end of the current quantum.
                    long untilArrival = (long) processes.get(nextProcess).input.arrivalTime()
                            - currentTime;
                    long wholeQuanta = (untilArrival + quantum - 1L) / quantum;
                    duration = (int) Math.min(running.remainingTime, wholeQuanta * quantum);
                }
            }
            int endTime = Math.addExact(currentTime, duration);
            addSlice(timeline, running.input.id(), currentTime, endTime);
            running.remainingTime -= duration;
            currentTime = endTime;

            while (nextProcess < processes.size()
                    && processes.get(nextProcess).input.arrivalTime() <= currentTime) {
                readyQueue.addLast(processes.get(nextProcess++));
            }

            if (running.remainingTime == 0) {
                running.completionTime = currentTime;
                completed++;
            } else {
                readyQueue.addLast(running);
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

    private static void validate(List<Process> processes, int quantum) {
        SchedulingSupport.validateWorkload(
                processes, Process::id, Process::arrivalTime, Process::burstTime, "RoundRobin");
        if (quantum <= 0) {
            throw new IllegalArgumentException("Quantum phải lớn hơn 0.");
        }
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
