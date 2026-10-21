package GUI;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class SchedulingGUI extends JFrame {

    private final DefaultTableModel inputModel = new DefaultTableModel(
            new Object[] {"Process", "Arrival time", "Burst time", "Priority"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JTable inputTable = new JTable(inputModel);
    private final JComboBox<CpuScheduler.Algorithm> algorithmBox =
            new JComboBox<>(CpuScheduler.Algorithm.values());
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(2, 1, Integer.MAX_VALUE, 1));
    private final JTextArea ganttOutput = new JTextArea(5, 50);
    private final DefaultTableModel resultModel = new DefaultTableModel(
            new Object[] {"Process", "AT", "BT", "Priority", "ST", "CT", "TAT", "WT", "RT"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel summaryLabel = new JLabel("Nhập tiến trình rồi chọn thuật toán để bắt đầu.");

    public SchedulingGUI() {
        super("CPU Scheduling Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(850, 620));
        setLayout(new BorderLayout(8, 8));

        add(createControls(), BorderLayout.NORTH);
        add(createInputPanel(), BorderLayout.CENTER);
        add(createResultsPanel(), BorderLayout.SOUTH);
        addSampleProcesses();

        algorithmBox.addActionListener(event -> updateQuantumState());
        updateQuantumState();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        controls.add(new JLabel("Thuật toán:"));
        controls.add(algorithmBox);
        controls.add(new JLabel("Quantum:"));
        controls.add(quantumSpinner);

        JButton addButton = new JButton("Thêm tiến trình");
        addButton.addActionListener(event -> inputModel.addRow(
                new Object[] {"P" + (inputModel.getRowCount() + 1), 0, 1, 1}));
        controls.add(addButton);

        JButton removeButton = new JButton("Xóa dòng");
        removeButton.addActionListener(event -> removeSelectedProcesses());
        controls.add(removeButton);

        JButton runButton = new JButton("Chạy mô phỏng");
        runButton.addActionListener(event -> runSimulation());
        controls.add(runButton);
        return controls;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Danh sách tiến trình"));
        inputTable.setFillsViewportHeight(true);
        panel.add(new JScrollPane(inputTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Kết quả"));
        ganttOutput.setEditable(false);
        ganttOutput.setLineWrap(true);
        ganttOutput.setWrapStyleWord(true);
        panel.add(new JScrollPane(ganttOutput), BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(resultModel)), BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(0, 1));
        footer.add(summaryLabel);
        panel.add(footer, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(820, 270));
        return panel;
    }

    private void addSampleProcesses() {
        inputModel.addRow(new Object[] {"P1", 0, 5, 2});
        inputModel.addRow(new Object[] {"P2", 1, 3, 1});
        inputModel.addRow(new Object[] {"P3", 2, 2, 3});
    }

    private void updateQuantumState() {
        quantumSpinner.setEnabled(algorithmBox.getSelectedItem() == CpuScheduler.Algorithm.ROUND_ROBIN);
    }

    private void removeSelectedProcesses() {
        int[] selectedRows = inputTable.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            inputModel.removeRow(selectedRows[i]);
        }
    }

    private void runSimulation() {
        try {
            if (inputTable.isEditing() && !inputTable.getCellEditor().stopCellEditing()) {
                throw new IllegalArgumentException("Hãy hoàn tất chỉnh sửa ô đang nhập.");
            }

            List<CpuScheduler.ProcessInput> processes = readProcesses();
            CpuScheduler.Algorithm algorithm =
                    (CpuScheduler.Algorithm) algorithmBox.getSelectedItem();
            int quantum = (Integer) quantumSpinner.getValue();
            CpuScheduler.ScheduleResult result =
                    CpuScheduler.schedule(processes, algorithm, quantum);
            displayResult(result);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            JOptionPane.showMessageDialog(
                    this, exception.getMessage(), "Dữ liệu không hợp lệ", JOptionPane.ERROR_MESSAGE);
        } catch (UnsupportedOperationException exception) {
            JOptionPane.showMessageDialog(
                    this, exception.getMessage(), "Chưa triển khai", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private List<CpuScheduler.ProcessInput> readProcesses() {
        List<CpuScheduler.ProcessInput> processes = new ArrayList<>();
        for (int row = 0; row < inputModel.getRowCount(); row++) {
            String id = cellText(row, 0);
            int arrivalTime = parseInteger(row, 1, "thời điểm đến");
            int burstTime = parseInteger(row, 2, "thời gian chạy");
            int priority = parseInteger(row, 3, "priority");
            processes.add(new CpuScheduler.ProcessInput(id, arrivalTime, burstTime, priority));
        }
        return processes;
    }

    private String cellText(int row, int column) {
        Object value = inputModel.getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }

    private int parseInteger(int row, int column, String label) {
        String value = cellText(row, column);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Dòng " + (row + 1) + ": " + label + " phải là số nguyên.");
        }
    }

    private void displayResult(CpuScheduler.ScheduleResult result) {
        StringBuilder gantt = new StringBuilder("Gantt chart:\n");
        for (CpuScheduler.ExecutionSlice slice : result.timeline()) {
            if (gantt.charAt(gantt.length() - 1) != '\n') {
                gantt.append("  |  ");
            }
            gantt.append(slice.processId())
                    .append(" [")
                    .append(slice.startTime())
                    .append('-')
                    .append(slice.endTime())
                    .append(']');
        }
        ganttOutput.setText(gantt.toString());

        resultModel.setRowCount(0);
        for (CpuScheduler.ProcessResult process : result.processes()) {
            CpuScheduler.ProcessInput input = process.process();
            resultModel.addRow(new Object[] {
                input.id(),
                input.arrivalTime(),
                input.burstTime(),
                input.priority(),
                process.startTime(),
                process.completionTime(),
                process.turnaroundTime(),
                process.waitingTime(),
                process.responseTime()
            });
        }

        summaryLabel.setText(String.format(
                "WT trung bình: %.2f    TAT trung bình: %.2f    RT trung bình: %.2f"
                        + "    CPU utilization: %.2f%%    Throughput: %.3f",
                result.averageWaitingTime(),
                result.averageTurnaroundTime(),
                result.averageResponseTime(),
                result.cpuUtilization() * 100,
                result.throughput()));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SchedulingGUI().setVisible(true));
    }
}
