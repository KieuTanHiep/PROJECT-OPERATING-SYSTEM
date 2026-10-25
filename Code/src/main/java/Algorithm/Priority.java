import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class priority {

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
        String csvFile = "data.csv";

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (isFirstLine) {
                    isFirstLine = false;
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("process") || lowerLine.contains("id") || lowerLine.contains("at")) {
                        continue;
                    }
                }

                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String id = parts[0].trim();
                    int arrivalTime = Integer.parseInt(parts[1].trim());
                    int burstTime = Integer.parseInt(parts[2].trim());
                    int priority = Integer.parseInt(parts[3].trim());
                    processes.add(new Process(id, arrivalTime, burstTime, priority));
                }
            }
        } catch (IOException e) {
            System.out.println("Loi khi doc file : " + e.getMessage());
            return;
        } catch (NumberFormatException e) {
            System.out.println("Loi dinh dang so trong file: " + e.getMessage());
            return;
        }

        if (processes.isEmpty()) {
            System.out.println("Khong co tien trinh nao duoc tai tu file.");
            return;
        }

        int n = processes.size();
        boolean[] isCompleted = new boolean[n];
        int completedCount = 0;
        int currentTime = 0;
        List<String> gantt = new ArrayList<>();

        // Vòng lặp mô phỏng lập
        while (completedCount < n) {
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

            // Sắp xếp readyQueue
            readyQueue.sort(
                Comparator.comparingInt((Process p) -> p.priority)
                          .thenComparingInt(p -> p.arrivalTime)
                          .thenComparing(p -> p.id)
            );

            Process currentProcess = readyQueue.get(0);
            int originalIndex = processes.indexOf(currentProcess);

            // Thực thi tiến trình
            currentProcess.startTime = currentTime;
            // Chạy đến khi hoàn tất
            currentTime += currentProcess.burstTime;
            
            // Tính toán các thông số
            currentProcess.completionTime = currentTime;
            currentProcess.turnaroundTime = currentProcess.completionTime - currentProcess.arrivalTime;
            currentProcess.waitingTime = currentProcess.turnaroundTime - currentProcess.burstTime;
            currentProcess.responseTime = currentProcess.startTime - currentProcess.arrivalTime;

            isCompleted[originalIndex] = true;
            completedCount++;

            // Ghi nhận biểu đồ Gantt Chart
            gantt.add(currentProcess.id + " [" + currentProcess.startTime + "-" + currentProcess.completionTime + "]");
        }

        // Hiển thị kết quả thống kê
        System.out.println("PRIORITY SCHEDULING");
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

        System.out.printf("%nAverage Waiting Time: %.2f%n", totalWT / processes.size());
        System.out.printf("Average Turnaround Time: %.2f%n", totalTAT / processes.size());
    }
}
