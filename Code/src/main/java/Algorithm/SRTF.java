package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Srtf {

    public record Process(String id, int arrivalTime, int burstTime) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessTiming(String processId, int startTime, int completionTime) {}

    public record Result(List<ExecutionSlice> timeline, List<ProcessTiming> processes) {}

    private static final Comparator<SimProcess> REMAINING_TIME_ORDER =
            Comparator.comparingInt((SimProcess process) -> process.remainingTime)
                    .thenComparingInt(process -> process.input.arrivalTime())
                    .thenComparing(process -> process.input.id());

    private Srtf() {}

    public static Result schedule(List<Process> inputs) {
        validate(inputs);
        List<SimProcess> processes = inputs.stream().map(SimProcess::new).toList();
        List<ExecutionSlice> timeline = new ArrayList<>();
        int currentTime = 0;
        int completed = 0;
        SimProcess running = null;

        while (completed < processes.size()) {
            int schedulingTime = currentTime;
            SimProcess previous = running;
            SimProcess best = processes.stream()
                    .filter(process -> process.remainingTime > 0)
                    .filter(process -> process.input.arrivalTime() <= schedulingTime)
                    .min(REMAINING_TIME_ORDER)
                    .orElse(null);

            if (best == null) {
                int nextArrival = processes.stream()
                        .filter(process -> process.remainingTime > 0)
                        .mapToInt(process -> process.input.arrivalTime())
                        .min()
                        .orElseThrow();
                timeline.add(new ExecutionSlice("IDLE", currentTime, nextArrival));
                currentTime = nextArrival;
                running = null;
                continue;
            }

            if (previous != null
                    && previous.remainingTime > 0
                    && previous.input.arrivalTime() <= currentTime
                    && previous.remainingTime == best.remainingTime) {
                running = previous;
            } else {
                running = best;
            }

            if (running.startTime < 0) {
                running.startTime = currentTime;
            }
            int endTime = Math.addExact(currentTime, 1);
            addSlice(timeline, running.input.id(), currentTime, endTime);
            running.remainingTime--;
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
        timeline.add(new ExecutionSlice(processId, startTime, endTime));
    }

    private static void validate(List<Process> processes) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("SRTF cần ít nhất một tiến trình.");
        }
        Set<String> ids = new HashSet<>();
        for (Process process : processes) {
            if (process == null || process.id() == null || process.id().isBlank()) {
                throw new IllegalArgumentException("Mã tiến trình không được để trống.");
            }
            if (!ids.add(process.id().trim())) {
                throw new IllegalArgumentException("Mã tiến trình bị trùng: " + process.id());
            }
            if (process.arrivalTime() < 0 || process.burstTime() <= 0) {
                throw new IllegalArgumentException("arrivalTime phải >= 0 và burstTime phải > 0.");
            }
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
