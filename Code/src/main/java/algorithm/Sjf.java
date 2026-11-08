package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Sjf {

    public record Process(String id, int arrivalTime, int burstTime) {}

    public record ExecutionSlice(String processId, int startTime, int endTime) {}

    public record ProcessTiming(String processId, int startTime, int completionTime) {}

    public record Result(List<ExecutionSlice> timeline, List<ProcessTiming> processes) {}

    private static final Comparator<Process> BURST_ORDER =
            Comparator.comparingInt(Process::burstTime)
                    .thenComparingInt(Process::arrivalTime)
                    .thenComparing(Process::id);

    private Sjf() {}

    public static Result schedule(List<Process> inputs) {
        validate(inputs);
        List<Process> unfinished = new ArrayList<>(inputs);
        List<ExecutionSlice> timeline = new ArrayList<>();
        List<ProcessTiming> timings = new ArrayList<>();
        int currentTime = 0;

        while (!unfinished.isEmpty()) {
            int schedulingTime = currentTime;
            Process next = unfinished.stream()
                    .filter(process -> process.arrivalTime() <= schedulingTime)
                    .min(BURST_ORDER)
                    .orElse(null);

            if (next == null) {
                int nextArrival = unfinished.stream()
                        .mapToInt(Process::arrivalTime)
                        .min()
                        .orElseThrow();
                timeline.add(new ExecutionSlice("IDLE", currentTime, nextArrival));
                currentTime = nextArrival;
                continue;
            }

            int startTime = currentTime;
            currentTime = Math.addExact(currentTime, next.burstTime());
            timeline.add(new ExecutionSlice(next.id(), startTime, currentTime));
            timings.add(new ProcessTiming(next.id(), startTime, currentTime));
            unfinished.remove(next);
        }

        return new Result(List.copyOf(timeline), List.copyOf(timings));
    }

    private static void validate(List<Process> processes) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("SJF cần ít nhất một tiến trình.");
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
}
