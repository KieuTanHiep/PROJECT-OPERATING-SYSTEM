package gui;

import algorithm.Fcfs;
import algorithm.Priority;
import algorithm.RoundRobin;
import algorithm.Sjf;
import algorithm.Srtf;
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
            case SJF -> {
                Sjf.Result result = Sjf.schedule(
                        processes.stream()
                                .map(process -> new Sjf.Process(
                                        process.input.id(),
                                        process.input.arrivalTime(),
                                        process.input.burstTime()))
                                .toList());
                Map<String, SimProcess> processesById = processes.stream()
                        .collect(Collectors.toMap(
                                process -> process.input.id(), Function.identity()));
                for (Sjf.ExecutionSlice slice : result.timeline()) {
                    timeline.add(new ExecutionSlice(
                            slice.processId(), slice.startTime(), slice.endTime()));
                }
                for (Sjf.ProcessTiming timing : result.processes()) {
                    SimProcess process = processesById.get(timing.processId());
                    process.startTime = timing.startTime();
                    process.completionTime = timing.completionTime();
                    process.remainingTime = 0;
                }
            }
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
            case SRTF -> {
                Srtf.Result result = Srtf.schedule(
                        processes.stream()
                                .map(process -> new Srtf.Process(
                                        process.input.id(),
                                        process.input.arrivalTime(),
                                        process.input.burstTime()))
                                .toList());
                Map<String, SimProcess> processesById = processes.stream()
                        .collect(Collectors.toMap(
                                process -> process.input.id(), Function.identity()));
                for (Srtf.ExecutionSlice slice : result.timeline()) {
                    timeline.add(new ExecutionSlice(
                            slice.processId(), slice.startTime(), slice.endTime()));
                }
                for (Srtf.ProcessTiming timing : result.processes()) {
                    SimProcess process = processesById.get(timing.processId());
                    process.startTime = timing.startTime();
                    process.completionTime = timing.completionTime();
                    process.remainingTime = 0;
                }
            }
            case ROUND_ROBIN -> {
                RoundRobin.Result result = RoundRobin.schedule(
                        processes.stream()
                                .map(process -> new RoundRobin.Process(
                                        process.input.id(),
                                        process.input.arrivalTime(),
                                        process.input.burstTime()))
                                .toList(),
                        quantum);
                Map<String, SimProcess> processesById = processes.stream()
                        .collect(Collectors.toMap(
                                process -> process.input.id(), Function.identity()));
                for (RoundRobin.ExecutionSlice slice : result.timeline()) {
                    timeline.add(new ExecutionSlice(
                            slice.processId(), slice.startTime(), slice.endTime()));
                }
                for (RoundRobin.ProcessTiming timing : result.processes()) {
                    SimProcess process = processesById.get(timing.processId());
                    process.startTime = timing.startTime();
                    process.completionTime = timing.completionTime();
                    process.remainingTime = 0;
                }
            }
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
