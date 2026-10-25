
import java.util.Scanner;
import java.util.Arrays;
import java.util.Comparator;

public class SRTF {

    static class Process {
        int pid;
        int arrivalTime;
        int burstTime;
        int remainingTime;
        int completionTime;
        int turnaroundTime;
        int waitingTime;
        int responseTime;
        boolean started = false;

        Process(int pid, int arrivalTime, int burstTime) {
            this.pid = pid;
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
            this.remainingTime = burstTime;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Nhap so tien trinh: ");
        int n = sc.nextInt();

        if (n <= 0) {
            System.out.println("So tien trinh phai lon hon 0!");
            sc.close();
            return;
        }

        Process[] p = new Process[n];

        for (int i = 0; i < n; i++) {
            System.out.println("\nTien trinh P" + (i + 1));

            System.out.print("Thoi diem den (AT): ");
            int at = sc.nextInt();

            System.out.print("Thoi gian xu ly (BT): ");
            int bt = sc.nextInt();

            if (at < 0 || bt <= 0) {
                System.out.println("AT phai >= 0 va BT phai > 0!");
                sc.close();
                return;
            }

            p[i] = new Process(i + 1, at, bt);
        }

        int completed = 0;
        int time = 0;
        double totalWT = 0;
        double totalTAT = 0;

        System.out.println("\n===== THUAT TOAN SRTF =====");

        while (completed < n) {
            Process current = null;

            // Tim tien trinh da den co thoi gian con lai nho nhat
            for (Process process : p) {
                if (process.arrivalTime <= time
                        && process.remainingTime > 0) {

                    if (current == null
                            || process.remainingTime
                            < current.remainingTime
                            || (process.remainingTime
                            == current.remainingTime
                            && process.arrivalTime
                            < current.arrivalTime)) {
                        current = process;
                    }
                }
            }

            // Neu chua co tien trinh nao den
            if (current == null) {
                time++;
                continue;
            }

            // Ghi nhan thoi diem phan hoi lan dau
            if (!current.started) {
                current.responseTime =
                        time - current.arrivalTime;
                current.started = true;
            }

            // CPU xu ly 1 don vi thoi gian
            current.remainingTime--;
            time++;

            // Neu tien trinh hoan thanh
            if (current.remainingTime == 0) {
                current.completionTime = time;

                current.turnaroundTime =
                        current.completionTime
                        - current.arrivalTime;

                current.waitingTime =
                        current.turnaroundTime
                        - current.burstTime;

                totalWT += current.waitingTime;
                totalTAT += current.turnaroundTime;
                completed++;
            }
        }

        // In ket qua theo thu tu tien trinh
        System.out.println("\nPID\tAT\tBT\tCT\tTAT\tWT\tRT");

        for (Process process : p) {
            System.out.printf(
                "P%d\t%d\t%d\t%d\t%d\t%d\t%d%n",
                process.pid,
                process.arrivalTime,
                process.burstTime,
                process.completionTime,
                process.turnaroundTime,
                process.waitingTime,
                process.responseTime
            );
        }

        System.out.printf(
            "\nThoi gian cho trung binh (AWT): %.2f%n",
            totalWT / n
        );

        System.out.printf(
            "Thoi gian quay vong trung binh (ATAT): %.2f%n",
            totalTAT / n
        );

        sc.close();
    }
}
