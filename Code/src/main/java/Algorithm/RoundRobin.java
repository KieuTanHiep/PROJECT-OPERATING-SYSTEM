package Algorithm;

import Model.Process;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class RoundRobin extends Scheduler {
    private int quantum;

    public RoundRobin(List<Process> processes, int quantum) {
        super(processes);
        this.quantum = quantum;
    }

    @Override
    public void calculate() {
        int n = processes.size();
        if (n == 0) return;

        List<Process> sortedList = new ArrayList<>();
        for (Process p : processes) {
            sortedList.add(new Process(p));
        }
        sortedList.sort((p1, p2) -> Integer.compare(p1.getArrivalTime(), p2.getArrivalTime()));

        int[] remainingBurstTime = new int[n];
        for (int i = 0; i < n; i++) {
            remainingBurstTime[i] = sortedList.get(i).getBurstTime();
        }

        Queue<Integer> queue = new LinkedList<>();
        boolean[] inQueue = new boolean[n];

        int currentTime = 0;
        int completed = 0;

        queue.add(0);
        inQueue[0] = true;
        currentTime = sortedList.get(0).getArrivalTime();

        while (completed < n) {
            if (queue.isEmpty()) {
                for (int i = 0; i < n; i++) {
                    if (remainingBurstTime[i] > 0) {
                        queue.add(i);
                        inQueue[i] = true;
                        currentTime = Math.max(currentTime, sortedList.get(i).getArrivalTime());
                        break;
                    }
                }
            }

            int index = queue.poll();
            Process p = sortedList.get(index);

            int executeTime = Math.min(remainingBurstTime[index], quantum);
            
            ganttChart.add(p.getName() + " (" + currentTime + " - " + (currentTime + executeTime) + ")");
            
            remainingBurstTime[index] -= executeTime;
            currentTime += executeTime;

            for (int i = 0; i < n; i++) {
                if (i != index && remainingBurstTime[i] > 0 && !inQueue[i] 
                        && sortedList.get(i).getArrivalTime() <= currentTime) {
                    queue.add(i);
                    inQueue[i] = true;
                }
            }

            if (remainingBurstTime[index] > 0) {
                queue.add(index);
            } else {
                completed++;
                int completionTime = currentTime;
                int turnaroundTime = completionTime - p.getArrivalTime();
                int waitingTime = turnaroundTime - p.getBurstTime();

                p.setCompletionTime(completionTime);
                p.setTurnaroundTime(turnaroundTime);
                p.setWaitingTime(waitingTime);

                for (Process original : processes) {
                    if (original.getName().equals(p.getName())) {
                        original.setCompletionTime(completionTime);
                        original.setTurnaroundTime(turnaroundTime);
                        original.setWaitingTime(waitingTime);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void printProcesses() {
        System.out.println("--- Round Robin Scheduling ---");
        System.out.println("Process\tArrival\tBurst\tCompletion\tTurnaround\tWaiting");
        for (Process p : processes) {
            System.out.println(p.getName() + "\t" +
                    p.getArrivalTime() + "\t" +
                    p.getBurstTime() + "\t" +
                    p.getCompletionTime() + "\t\t" +
                    p.getTurnaroundTime() + "\t\t" +
                    p.getWaitingTime());
        }
        System.out.printf("Average Waiting Time: %.2f\n", getAverageWaitingTime());
        System.out.printf("Average Turnaround Time: %.2f\n", getAverageTurnaroundTime());
    }

    @Override
    public void printGanttChart() {
        System.out.println("\n--- Gantt Chart ---");
        for (String slot : ganttChart) {
            System.out.print("[" + slot + "] ");
        }
        System.out.println();
    }
}
