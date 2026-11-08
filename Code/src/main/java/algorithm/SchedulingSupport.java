package algorithm;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Shared input and cancellation rules for the scheduling engines. */
public final class SchedulingSupport {

    public static final String IDLE_LABEL = "IDLE";

    /** Bound the materialized trace so tiny RR quanta cannot exhaust desktop memory. */
    public static final int MAX_TRACE_SLICES = 250_000;

    private SchedulingSupport() {}

    public static String normalizeProcessId(String id) {
        String normalized = id == null ? "" : id.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Mã tiến trình không được để trống.");
        }
        if (IDLE_LABEL.equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Mã IDLE được dành riêng cho khoảng CPU rỗi.");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Mã tiến trình không được chứa ký tự điều khiển.");
        }
        return normalized;
    }

    public static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Đã hủy mô phỏng.");
        }
    }

    public static void checkTraceCapacity(int currentSize) {
        if (currentSize >= MAX_TRACE_SLICES) {
            throw new IllegalArgumentException(
                    "Trace vượt giới hạn " + MAX_TRACE_SLICES
                            + " đoạn. Hãy tăng quantum hoặc giảm workload.");
        }
    }

    public static <T> void validateWorkload(
            List<T> processes,
            Function<T, String> id,
            ToIntFunction<T> arrivalTime,
            ToIntFunction<T> burstTime,
            String algorithmName) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException(algorithmName + " cần ít nhất một tiến trình.");
        }
        Set<String> ids = new HashSet<>();
        for (T process : processes) {
            checkCancelled();
            if (process == null) {
                throw new IllegalArgumentException("Tiến trình không được để trống.");
            }
            String normalizedId = normalizeProcessId(id.apply(process));
            if (!ids.add(normalizedId)) {
                throw new IllegalArgumentException("Mã tiến trình bị trùng: " + normalizedId);
            }
            if (arrivalTime.applyAsInt(process) < 0 || burstTime.applyAsInt(process) <= 0) {
                throw new IllegalArgumentException("arrivalTime phải >= 0 và burstTime phải > 0.");
            }
        }

        // All these algorithms are work-conserving and have no I/O/context-switch
        // cost. Their makespan depends on releases and total work, not job order.
        long endTime = 0;
        for (T process : processes.stream()
                .sorted(Comparator.comparingInt(arrivalTime))
                .toList()) {
            checkCancelled();
            endTime = Math.max(endTime, arrivalTime.applyAsInt(process))
                    + burstTime.applyAsInt(process);
            if (endTime > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Tổng thời gian mô phỏng vượt giới hạn số nguyên 32-bit.");
            }
        }
    }
}
