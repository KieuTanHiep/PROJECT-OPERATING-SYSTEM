
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class fcfs {

    static class Process {
        String id;
        int arrivalTime;
        int burstTime;
        int startTime;
        int completionTime;
        int waitingTime;
        int turnaroundTime;
        int responseTime;

        Process(String id, int arrivalTime, int burstTime) {
            this.id = id;
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
        }
    }

    public static void main(String[] args) {
        List<Process> processes = new ArrayList<>();
        try (Scanner scanner = new Scanner(System.in)) {
            int processCount = readInt(scanner, "Nhập số tiến trình: ", 1);

            for (int i = 1; i <= processCount; i++) {
                System.out.printf("%nTiến trình P%d:%n", i);
                int arrivalTime = readInt(scanner, "  Thời điểm đến (AT): ", 0);
                int burstTime = readInt(scanner, "  Thời gian chạy (BT): ", 1);
                processes.add(new Process("P" + i, arrivalTime, burstTime));
            }
        }

        // Sắp xếp theo Arrival Time, sau đó theo ID
        processes.sort(
            Comparator.comparingInt((Process p) -> p.arrivalTime)
                      .thenComparing(p -> p.id)
        );

        int currentTime = 0;
        List<String> gantt = new ArrayList<>();

        for (Process p : processes) {

            // CPU rỗi nếu tiến trình chưa đến
            if (currentTime < p.arrivalTime) {
                gantt.add("IDLE [" + currentTime + "-" + p.arrivalTime + "]");
                currentTime = p.arrivalTime;
            }

            // Tiến trình bắt đầu chạy
            p.startTime = currentTime;

            // FCFS chạy đến khi hoàn tất
            currentTime += p.burstTime;

            // Tính các chỉ số
            p.completionTime = currentTime;
            p.turnaroundTime = p.completionTime - p.arrivalTime;
            p.waitingTime = p.turnaroundTime - p.burstTime;
            p.responseTime = p.startTime - p.arrivalTime;

            // Ghi nhận Gantt Chart
            gantt.add(p.id + " [" + p.startTime + "-" + p.completionTime + "]");
        }

        // Hiển thị kết quả
        System.out.println("=== FCFS SCHEDULING ===");

        System.out.printf("%-8s %-8s %-8s %-8s %-8s %-8s %-8s%n",
                "Process", "AT", "BT", "ST", "CT", "TAT", "WT");

        double totalWT = 0;
        double totalTAT = 0;

        for (Process p : processes) {
            System.out.printf("%-8s %-8d %-8d %-8d %-8d %-8d %-8d%n",
                    p.id, p.arrivalTime, p.burstTime,
                    p.startTime, p.completionTime,
                    p.turnaroundTime, p.waitingTime);

            totalWT += p.waitingTime;
            totalTAT += p.turnaroundTime;
        }

        System.out.println("\nGantt Chart:");
        System.out.println(String.join(" | ", gantt));

        System.out.printf("%nAverage Waiting Time: %.2f%n",
                totalWT / processes.size());

        System.out.printf("Average Turnaround Time: %.2f%n",
                totalTAT / processes.size());
    }

    private static int readInt(Scanner scanner, String prompt, int minimum) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNext()) {
                throw new IllegalStateException("Đã hết dữ liệu đầu vào.");
            }
            if (!scanner.hasNextInt()) {
                scanner.next();
                System.out.println("Vui lòng nhập một số nguyên.");
                continue;
            }

            int value = scanner.nextInt();
            if (value < minimum) {
                System.out.printf("Giá trị phải lớn hơn hoặc bằng %d.%n", minimum);
                continue;
            }
            return value;
        }
    }
}
