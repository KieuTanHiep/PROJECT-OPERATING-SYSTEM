package gui;

import algorithm.Fcfs;
import algorithm.Priority;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
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

    private static final Comparator<SimProcess> ARRIVAL_ORDER =
            Comparator.comparingInt((SimProcess process) -> process.input.arrivalTime())
                    .thenComparing(process -> process.input.id());

    private CpuScheduler() {}

    public static ScheduleResult schedule(
            List<ProcessInput> inputs, Algorithm algorithm, int quantum) {
        validate(inputs, algorithm, quantum);

        List<SimProcess> processes = new ArrayList<>();
        for (ProcessInput input : inputs) {
            processes.add(new SimProcess(input));
        }

        List<ExecutionSlice> timeline = new ArrayList<>();
        switch (algorithm) {
            case FCFS -> {
                Fcfs.Result result = Fcfs.schedule(
                        processes.stream()
                                .map(process -> new Fcfs.Process(
                                        process.input.id(),
                                        process.input.arrivalTime(),
                                        process.input.burstTime()))
                                .toList());
                Map<String, SimProcess> processesById = processes.stream()
                        .collect(Collectors.toMap(
                                process -> process.input.id(), Function.identity()));
                for (Fcfs.ExecutionSlice slice : result.timeline()) {
                    timeline.add(new ExecutionSlice(slice.processId(), slice.startTime(), slice.endTime()));
                }
                for (Fcfs.ProcessTiming timing : result.processes()) {
                    SimProcess process = processesById.get(timing.processId());
                    process.startTime = timing.startTime();
                    process.completionTime = timing.completionTime();
                    process.remainingTime = 0;
                }
            }
            case SJF -> runNonPreemptive(
                    processes,
                    timeline,
                    Comparator.comparingInt((SimProcess process) -> process.input.burstTime())
                            .thenComparingInt(process -> process.input.arrivalTime())
                            .thenComparing(process -> process.input.id()));
            case PRIORITY -> {
                Priority.Result result = Priority.schedule(
                        processes.stream()
                                .map(process -> new Priority.Process(
                                        process.input.id(),
                                        process.input.arrivalTime(),
                                        process.input.burstTime(),
                                        process.input.priority()))
                                .toList());
                Map<String, SimProcess> processesById = processes.stream()
                        .collect(Collectors.toMap(
                                process -> process.input.id(), Function.identity()));
                for (Priority.ExecutionSlice slice : result.timeline()) {
                    timeline.add(new ExecutionSlice(
                            slice.processId(), slice.startTime(), slice.endTime()));
                }
                for (Priority.ProcessTiming timing : result.processes()) {
                    SimProcess process = processesById.get(timing.processId());
                    process.startTime = timing.startTime();
                    process.completionTime = timing.completionTime();
                    process.remainingTime = 0;
                }
            }
            case SRTF -> runSrtf(processes, timeline);
            case ROUND_ROBIN -> runRoundRobin(processes, timeline, quantum);
        }

        return createResult(processes, timeline);
    }

    private static void validate(List<ProcessInput> inputs, Algorithm algorithm, int quantum) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Hãy nhập ít nhất một tiến trình.");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("Hãy chọn thuật toán lập lịch.");
        }
        if (algorithm == Algorithm.ROUND_ROBIN && quantum <= 0) {
            throw new IllegalArgumentException("Quantum phải lớn hơn 0.");
        }

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            ProcessInput input = inputs.get(i);
            if (input == null) {
                throw new IllegalArgumentException("Tiến trình ở dòng " + (i + 1) + " không hợp lệ.");
            }
            String id = input.id() == null ? "" : input.id().trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Mã tiến trình ở dòng " + (i + 1) + " không được để trống.");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Mã tiến trình bị trùng: " + id);
            }
            if (input.arrivalTime() < 0) {
                throw new IllegalArgumentException("Thời điểm đến phải lớn hơn hoặc bằng 0.");
            }
            if (input.burstTime() <= 0) {
                throw new IllegalArgumentException("Thời gian chạy phải lớn hơn 0.");
            }
        }
    }

    private static void runNonPreemptive(
            List<SimProcess> processes,
            List<ExecutionSlice> timeline,
            Comparator<SimProcess> selector) {
        int currentTime = 0;
        int completed = 0;

        while (completed < processes.size()) {
            int schedulingTime = currentTime;
            SimProcess next = processes.stream()
                    .filter(process -> process.completionTime < 0)
                    .filter(process -> process.input.arrivalTime() <= schedulingTime)
                    .min(selector)
                    .orElse(null);

            if (next == null) {
                int nextArrival = processes.stream()
                        .filter(process -> process.completionTime < 0)
                        .mapToInt(process -> process.input.arrivalTime())
                        .min()
                        .orElseThrow();
                addSlice(timeline, "IDLE", currentTime, nextArrival);
                currentTime = nextArrival;
                continue;
            }

            next.startTime = currentTime;
            currentTime = Math.addExact(currentTime, next.input.burstTime());
            next.remainingTime = 0;
            next.completionTime = currentTime;
            completed++;
            addSlice(timeline, next.input.id(), next.startTime, currentTime);
        }
    }

    private static void runSrtf(List<SimProcess> processes, List<ExecutionSlice> timeline) {
        int currentTime = 0;
        int completed = 0;
        SimProcess running = null;

        while (completed < processes.size()) {
            int schedulingTime = currentTime;
            SimProcess previous = running;
            SimProcess best = processes.stream()
                    .filter(process -> process.remainingTime > 0)
                    .filter(process -> process.input.arrivalTime() <= schedulingTime)
                    .min(Comparator.comparingInt((SimProcess process) -> process.remainingTime)
                            .thenComparingInt(process -> process.input.arrivalTime())
                            .thenComparing(process -> process.input.id()))
                    .orElse(null);

            if (best == null) {
                int nextArrival = processes.stream()
                        .filter(process -> process.remainingTime > 0)
                        .mapToInt(process -> process.input.arrivalTime())
                        .min()
                        .orElseThrow();
                addSlice(timeline, "IDLE", currentTime, nextArrival);
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
            addSlice(timeline, running.input.id(), currentTime, Math.addExact(currentTime, 1));
            running.remainingTime--;
            currentTime = Math.addExact(currentTime, 1);

            if (running.remainingTime == 0) {
                running.completionTime = currentTime;
                completed++;
                running = null;
            }
        }
    }

    private static void runRoundRobin(
            List<SimProcess> processes, List<ExecutionSlice> timeline, int quantum) {
        List<SimProcess> arrivalOrder = new ArrayList<>(processes);
        arrivalOrder.sort(ARRIVAL_ORDER);
        ArrayDeque<SimProcess> queue = new ArrayDeque<>();
        int nextProcess = 0;
        int currentTime = 0;
        int completed = 0;

        while (completed < processes.size()) {
            while (nextProcess < arrivalOrder.size()
                    && arrivalOrder.get(nextProcess).input.arrivalTime() <= currentTime) {
                queue.addLast(arrivalOrder.get(nextProcess++));
            }

            if (queue.isEmpty()) {
                int nextArrival = arrivalOrder.get(nextProcess).input.arrivalTime();
                addSlice(timeline, "IDLE", currentTime, nextArrival);
                currentTime = nextArrival;
                continue;
            }

            SimProcess running = queue.removeFirst();
            if (running.startTime < 0) {
                running.startTime = currentTime;
            }
            int duration = Math.min(quantum, running.remainingTime);
            int endTime = Math.addExact(currentTime, duration);
            addSlice(timeline, running.input.id(), currentTime, endTime);
            running.remainingTime -= duration;
            currentTime = endTime;

            while (nextProcess < arrivalOrder.size()
                    && arrivalOrder.get(nextProcess).input.arrivalTime() <= currentTime) {
                queue.addLast(arrivalOrder.get(nextProcess++));
            }

            if (running.remainingTime == 0) {
                running.completionTime = currentTime;
                completed++;
            } else {
                queue.addLast(running);
            }
        }
    }

    static void addSlice(
            List<ExecutionSlice> timeline, String processId, int startTime, int endTime) {
        if (startTime == endTime) {
            return;
        }
        if (!timeline.isEmpty()) {
            ExecutionSlice previous = timeline.get(timeline.size() - 1);
            if (previous.processId().equals(processId) && previous.endTime() == startTime) {
                timeline.set(
                        timeline.size() - 1,
                        new ExecutionSlice(processId, previous.startTime(), endTime));
                return;
            }
        }
        timeline.add(new ExecutionSlice(processId, startTime, endTime));
    }

    private static ScheduleResult createResult(
            List<SimProcess> processes, List<ExecutionSlice> timeline) {
        List<ProcessResult> results = new ArrayList<>();
        double totalWaitingTime = 0;
        double totalTurnaroundTime = 0;
        double totalResponseTime = 0;
        int totalBurstTime = 0;

        for (SimProcess process : processes) {
            int turnaroundTime = process.completionTime - process.input.arrivalTime();
            int waitingTime = turnaroundTime - process.input.burstTime();
            int responseTime = process.startTime - process.input.arrivalTime();
            results.add(new ProcessResult(
                    process.input,
                    process.startTime,
                    process.completionTime,
                    turnaroundTime,
                    waitingTime,
                    responseTime));
            totalWaitingTime += waitingTime;
            totalTurnaroundTime += turnaroundTime;
            totalResponseTime += responseTime;
            totalBurstTime = Math.addExact(totalBurstTime, process.input.burstTime());
        }

        results.sort(Comparator.comparing(ProcessResult::process, Comparator.comparing(ProcessInput::id)));
        int endTime = timeline.get(timeline.size() - 1).endTime();
        double processCount = processes.size();
        return new ScheduleResult(
                List.copyOf(timeline),
                List.copyOf(results),
                totalWaitingTime / processCount,
                totalTurnaroundTime / processCount,
                totalResponseTime / processCount,
                (double) totalBurstTime / endTime,
                processCount / endTime);
    }

    static final class SimProcess {
        final ProcessInput input;
        int remainingTime;
        int startTime = -1;
        int completionTime = -1;

        SimProcess(ProcessInput input) {
            this.input = input;
            remainingTime = input.burstTime();
        }
    }
}
