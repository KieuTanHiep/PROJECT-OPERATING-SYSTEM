package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        List<Process> processes = inputs.stream().sorted(ARRIVAL_ORDER).toList();
        List<ExecutionSlice> timeline = new ArrayList<>();
        List<ProcessTiming> timings = new ArrayList<>();
        int currentTime = 0;

        for (Process process : processes) {
            if (currentTime < process.arrivalTime()) {
                timeline.add(new ExecutionSlice("IDLE", currentTime, process.arrivalTime()));
                currentTime = process.arrivalTime();
            }

            int startTime = currentTime;
            currentTime = Math.addExact(currentTime, process.burstTime());
            timeline.add(new ExecutionSlice(process.id(), startTime, currentTime));
            timings.add(new ProcessTiming(process.id(), startTime, currentTime));
        }
        return new Result(List.copyOf(timeline), List.copyOf(timings));
    }

    private static void validate(List<Process> processes) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("FCFS cần ít nhất một tiến trình.");
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
