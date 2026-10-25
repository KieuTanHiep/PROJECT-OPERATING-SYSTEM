package Algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class Priority {

    static class Process {
        String id;
        int arrivalTime;
        int burstTime;
        int priority;
        int startTime;
        int completionTime;
        int waitingTime;
        int turnaroundTime;
        int responseTime;

        Process(String id, int arrivalTime, int burstTime, int priority) {
            this.id = id;
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
            this.priority = priority;
        }
    }

    public static void main(String[] args) {
        List<Process> processes = new ArrayList<>();
        try (Scanner scanner = new Scanner(System.in)) {
            int processCount = readInt(scanner, "Nhap so tien trinh: ", 1);

            for (int i = 1; i <= processCount; i++) {
                System.out.printf("%nTiến trình P%d:%n", i);
                int arrivalTime = readInt(scanner, "  Thoi diem den (AT): ", 0);
                int burstTime = readInt(scanner, "  Thoi gian chay (BT): ", 1);
                int priority = readInt(scanner, "  Do uu tien: ", 0);
                processes.add(new Process("P" + i, arrivalTime, burstTime, priority));
            }
        }

        int n = processes.size();
        boolean[] isCompleted = new boolean[n];
        int completedCount = 0;
        int currentTime = 0;
        List<String> gantt = new ArrayList<>();

        // Vòng lặp mô phỏng
        while (completedCount < n) {
            // Tải danh sách công việc
            List<Process> readyQueue = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!isCompleted[i] && processes.get(i).arrivalTime <= currentTime) {
                    readyQueue.add(processes.get(i));
                }
            }

            // Kiểm tra hàng chờ
            if (readyQueue.isEmpty()) {
                int nextArrival = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!isCompleted[i] && processes.get(i).arrivalTime > currentTime) {
                        if (processes.get(i).arrivalTime < nextArrival) {
                            nextArrival = processes.get(i).arrivalTime;
                        }
                    }
                }
                if (nextArrival != Integer.MAX_VALUE) {
                    gantt.add("IDLE [" + currentTime + "-" + nextArrival + "]");
                    currentTime = nextArrival;
                } else {
                    currentTime++;
                }
                continue;
            }

            //Chọn tiến trình đang ở trạng thái sẵn sàng có độ ưu tiên cao nhất
            readyQueue.sort(
                Comparator.comparingInt((Process p) -> p.priority)
                          .thenComparingInt(p -> p.arrivalTime)
                          .thenComparing(p -> p.id)
            );

            Process currentProcess = readyQueue.get(0);
            int originalIndex = processes.indexOf(currentProcess);
            ///Thời điểm bắt đầu chạy
            currentProcess.startTime = currentTime;
            ///Tiến trình chạy đến khi hoàn tất
            currentTime += currentProcess.burstTime;
            /// Tính toán các chỉ số
            currentProcess.completionTime = currentTime;
            currentProcess.turnaroundTime = currentProcess.completionTime - currentProcess.arrivalTime;
            currentProcess.waitingTime = currentProcess.turnaroundTime - currentProcess.burstTime;
            currentProcess.responseTime = currentProcess.startTime - currentProcess.arrivalTime;
            /// Đánh dấu tiến trình đã hoàn tất
            isCompleted[originalIndex] = true;
            completedCount++;

            // Ghi nhận vào Gantt Chart
            gantt.add(currentProcess.id + " [" + currentProcess.startTime + "-" + currentProcess.completionTime + "]");
        }

        // Hiển thị kết quả bảng thống kê
        System.out.println("\nPRIORITY SCHEDULING (NON-PREEMPTIVE)");

        System.out.printf("%-8s %-8s %-8s %-10s %-8s %-8s %-8s %-8s%n",
                "Process", "AT", "BT", "Priority", "ST", "CT", "TAT", "WT");

        double totalWT = 0;
        double totalTAT = 0;

        for (Process p : processes) {
            System.out.printf("%-8s %-8d %-8d %-10d %-8d %-8d %-8d %-8d%n",
                    p.id, p.arrivalTime, p.burstTime, p.priority,
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
                throw new IllegalStateException("Da het du lieu dau vao.");
            }
            if (!scanner.hasNextInt()) {
                scanner.next();
                System.out.println("Vui long nhap mot so nguyen.");
                continue;
            }

            int value = scanner.nextInt();
            if (value < minimum) {
                System.out.printf("Gia tri phai lon hon hoac bang %d.%n", minimum);
                continue;
            }
            return value;
        }
    }
}