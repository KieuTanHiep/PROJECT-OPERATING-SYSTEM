package gui;

import algorithm.SchedulingSupport;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public final class SchedulingGUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int DETAILS_TAB = 0;
    private static final int COMPARISON_TAB = 1;
    private static final int DEFAULT_QUANTUM = 2;
    private static final Object[] INPUT_COLUMNS = {"Process", "Arrival time", "Burst time", "Priority"};
    private boolean busy;
    private long inputRevision;
    private final DefaultTableModel inputModel = new DefaultTableModel(INPUT_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return !busy;
        }
    };
    private final JTable inputTable = new JTable(inputModel);
    private final JComboBox<CpuScheduler.Algorithm> algorithmBox = new JComboBox<>(CpuScheduler.Algorithm.values());
    private final JSpinner quantumSpinner =
            new JSpinner(new SpinnerNumberModel(DEFAULT_QUANTUM, 1, Integer.MAX_VALUE, 1));
    private final JButton importButton = new JButton("Nạp CSV");
    private final JButton addButton = new JButton("Thêm tiến trình");
    private final JButton removeButton = new JButton("Xóa dòng");
    private final JButton runButton = new JButton("Chạy thuật toán");
    private final JButton compareButton = new JButton("So sánh cả 5");
    private final JButton exportButton = new JButton("Xuất kết quả đang xem");
    private final JButton exportComparisonButton = new JButton("Xuất so sánh CSV");
    private final JButton cancelButton = new JButton("Hủy tác vụ");
    private final GanttChartPanel ganttChart = new GanttChartPanel();
    private final ProcessResultsModel resultModel = new ProcessResultsModel();
    private final JLabel resultTitle = new JLabel("Chưa có kết quả.");
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("Thêm tiến trình hoặc nạp CSV để bắt đầu.");
    private final JProgressBar progressBar = new JProgressBar();
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final DefaultTableModel comparisonModel = new DefaultTableModel(
            new Object[] {"Thuật toán", "WT TB ↓", "TAT TB ↓", "RT TB ↓", "WT lớn nhất ↓",
                "Độ lệch WT ↓", "Jain ↑", "CPU (%)", "Throughput"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? String.class : Double.class;
        }
    };
    private final JTable comparisonTable = new JTable(comparisonModel);
    private final JTextArea comparisonNotes = new JTextArea(5, 60);
    private transient CpuScheduler.ScheduleResult lastResult;
    private CpuScheduler.Algorithm lastAlgorithm;
    private int lastQuantum;
    private transient List<CpuScheduler.ComparisonResult> lastComparisons = List.of();
    private transient SwingWorker<?, ?> currentWorker;
    private transient volatile Thread backgroundThread;
    private volatile boolean cancellationRequested;

    public SchedulingGUI() {
        super("So sánh thuật toán lập lịch CPU");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(940, 700));
        setLayout(new BorderLayout(8, 8));
        add(createControls(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createInputPanel(), createResultsPanel());
        split.setResizeWeight(0.3);
        split.setDividerLocation(200);
        split.setPreferredSize(new Dimension(1160, 650));
        add(split, BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);
        inputModel.addTableModelListener(event -> invalidateResult());
        algorithmBox.addActionListener(event -> {
            invalidateResult();
            updateControlState();
        });
        quantumSpinner.addChangeListener(event -> invalidateResult());
        quantumSpinner.setToolTipText("Quantum chỉ cần thiết cho Round Robin; các thuật toán khác dùng mặc định.");
        updateControlState();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 3));
        controls.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        JPanel runControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runControls.add(new JLabel("Thuật toán:"));
        runControls.add(algorithmBox);
        runControls.add(new JLabel("Quantum RR:"));
        runControls.add(quantumSpinner);
        runButton.addActionListener(event -> runSimulation(false));
        runControls.add(runButton);
        compareButton.addActionListener(event -> runSimulation(true));
        runControls.add(compareButton);
        cancelButton.addActionListener(event -> cancelTask());
        runControls.add(cancelButton);
        controls.add(runControls);
        JPanel dataControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        importButton.addActionListener(event -> importCsv());
        addButton.addActionListener(event -> addProcess());
        removeButton.addActionListener(event -> removeSelectedProcesses());
        exportButton.addActionListener(event -> exportCsv(false));
        exportComparisonButton.addActionListener(event -> exportCsv(true));
        dataControls.add(importButton);
        dataControls.add(addButton);
        dataControls.add(removeButton);
        dataControls.add(exportButton);
        dataControls.add(exportComparisonButton);
        controls.add(dataControls);
        return controls;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Danh sách tiến trình — nhấp đúp ô để sửa"));
        inputTable.setFillsViewportHeight(true);
        inputTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        panel.add(new JScrollPane(inputTable), BorderLayout.CENTER);
        return panel;
    }

    private JTabbedPane createResultsPanel() {
        resultTabs.addTab("Chi tiết và Gantt", createDetailsPanel());
        resultTabs.addTab("So sánh 5 thuật toán", createComparisonPanel());
        return resultTabs;
    }

    private JPanel createDetailsPanel() {
        JPanel details = new JPanel(new BorderLayout(4, 4));
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JPanel chartPanel = new JPanel(new BorderLayout());
        JPanel chartToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chartToolbar.add(resultTitle);
        chartToolbar.add(new JLabel("Phóng to Gantt:"));
        JComboBox<String> zoomBox = new JComboBox<>(new String[] {"Vừa khung", "2×", "4×", "8×", "16×", "32×"});
        zoomBox.addActionListener(event -> ganttChart.setZoom(1 << zoomBox.getSelectedIndex()));
        chartToolbar.add(zoomBox);
        chartPanel.add(chartToolbar, BorderLayout.NORTH);
        JScrollPane chartScroll = new JScrollPane(ganttChart,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        chartScroll.setPreferredSize(new Dimension(1000, 160));
        chartPanel.add(chartScroll, BorderLayout.CENTER);
        details.add(chartPanel, BorderLayout.NORTH);
        JTable processTable = new JTable(resultModel);
        processTable.setAutoCreateRowSorter(true);
        processTable.setFillsViewportHeight(true);
        details.add(new JScrollPane(processTable), BorderLayout.CENTER);
        details.add(summaryLabel, BorderLayout.SOUTH);
        return details;
    }

    private JPanel createComparisonPanel() {
        JPanel comparisonPanel = new JPanel(new BorderLayout(4, 6));
        comparisonPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        comparisonPanel.add(new JLabel("Chọn một dòng để xem Gantt và từng tiến trình ở tab Chi tiết."), BorderLayout.NORTH);
        comparisonTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        comparisonTable.setAutoCreateRowSorter(true);
        comparisonTable.setFillsViewportHeight(true);
        comparisonTable.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setHorizontalAlignment(RIGHT);
                setText(value instanceof Number number ? String.format(Locale.ROOT, "%.4f", number.doubleValue()) : "");
            }
        });
        comparisonTable.getSelectionModel().addListSelectionListener(event -> {
            int selectedRow = comparisonTable.getSelectedRow();
            if (!event.getValueIsAdjusting() && selectedRow >= 0) {
                int modelRow = comparisonTable.convertRowIndexToModel(selectedRow);
                if (modelRow < lastComparisons.size()) {
                    CpuScheduler.ComparisonResult comparison = lastComparisons.get(modelRow);
                    lastResult = comparison.result();
                    lastAlgorithm = comparison.algorithm();
                    displayResult(lastResult);
                }
            }
        });
        comparisonTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && comparisonTable.getSelectedRow() >= 0) {
                    resultTabs.setSelectedIndex(DETAILS_TAB);
                }
            }
        });
        comparisonTable.getTableHeader().setToolTipText(
                "Jain tính từ BT/TAT: gần 1 là mức phục vụ tương đối đồng đều; không bảo đảm không có starvation.");
        comparisonPanel.add(new JScrollPane(comparisonTable), BorderLayout.CENTER);
        comparisonNotes.setEditable(false);
        comparisonNotes.setLineWrap(true);
        comparisonNotes.setWrapStyleWord(true);
        comparisonNotes.setBackground(comparisonPanel.getBackground());
        comparisonPanel.add(new JScrollPane(comparisonNotes), BorderLayout.SOUTH);
        return comparisonPanel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        panel.add(statusLabel, BorderLayout.CENTER);
        progressBar.setPreferredSize(new Dimension(140, 16));
        panel.add(progressBar, BorderLayout.EAST);
        return panel;
    }

    private void updateControlState() {
        inputTable.setEnabled(!busy);
        algorithmBox.setEnabled(!busy);
        boolean requireQuantum = algorithmBox.getSelectedItem() == CpuScheduler.Algorithm.ROUND_ROBIN;
        quantumSpinner.setEnabled(!busy && requireQuantum);
        importButton.setEnabled(!busy);
        addButton.setEnabled(!busy);
        removeButton.setEnabled(!busy);
        runButton.setEnabled(!busy);
        compareButton.setEnabled(!busy);
        exportButton.setEnabled(!busy && lastResult != null);
        exportComparisonButton.setEnabled(!busy && !lastComparisons.isEmpty());
        cancelButton.setEnabled(busy && !cancellationRequested);
        progressBar.setIndeterminate(busy);
    }

    private void stopEditing() {
        if (inputTable.isEditing() && !inputTable.getCellEditor().stopCellEditing()) {
            throw new IllegalArgumentException("Hãy hoàn tất chỉnh sửa ô đang nhập.");
        }
    }

    private void removeSelectedProcesses() {
        stopEditing();
        int[] selectedRows = inputTable.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            inputModel.removeRow(selectedRows[i]);
        }
    }

    private void addProcess() {
        stopEditing();
        JTextField idField = new JTextField(12);
        JTextField arrivalField = new JTextField("0", 12);
        JTextField burstField = new JTextField(12);
        JTextField priorityField = new JTextField("1", 12);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 6));
        fields.add(new JLabel("Mã tiến trình:"));
        fields.add(idField);
        fields.add(new JLabel("Thời điểm đến:"));
        fields.add(arrivalField);
        fields.add(new JLabel("Thời gian chạy:"));
        fields.add(burstField);
        fields.add(new JLabel("Priority (số nhỏ ưu tiên cao):"));
        fields.add(priorityField);
        if (JOptionPane.showConfirmDialog(this, fields, "Thêm tiến trình", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            String id = SchedulingSupport.normalizeProcessId(idField.getText());
            for (int row = 0; row < inputModel.getRowCount(); row++) {
                if (cellText(row, 0).equals(id)) {
                    throw new IllegalArgumentException("Mã tiến trình bị trùng: " + id);
                }
            }
            int arrivalTime = parseInteger(arrivalField.getText(), "thời điểm đến");
            int burstTime = parseInteger(burstField.getText(), "thời gian chạy");
            int priority = parseInteger(priorityField.getText(), "priority");
            if (arrivalTime < 0 || burstTime <= 0) {
                throw new IllegalArgumentException("Thời điểm đến phải >= 0 và thời gian chạy phải > 0.");
            }
            inputModel.addRow(new Object[] {id, arrivalTime, burstTime, priority});
        } catch (IllegalArgumentException exception) {
            showError(exception);
        }
    }

    private void runSimulation(boolean compareAll) {
        try {
            stopEditing();
            List<CpuScheduler.ProcessInput> processes = List.copyOf(readProcesses());
            CpuScheduler.Algorithm algorithm = (CpuScheduler.Algorithm) algorithmBox.getSelectedItem();
            int quantum = algorithm == CpuScheduler.Algorithm.ROUND_ROBIN || compareAll
                    ? parseQuantum()
                    : DEFAULT_QUANTUM;
            long revision = inputRevision;
            clearResults();
            startTask(compareAll ? "Đang so sánh 5 thuật toán…" : "Đang mô phỏng " + algorithm + "…",
                    () -> computeSimulation(processes, algorithm, quantum, compareAll),
                    run -> applySimulation(run, quantum, revision));
        } catch (ParseException exception) {
            showError(new IllegalArgumentException("Quantum phải là số nguyên dương."));
        } catch (IllegalArgumentException exception) {
            showError(exception);
        }
    }

    private int parseQuantum() throws ParseException {
        quantumSpinner.commitEdit();
        int quantum = (Integer) quantumSpinner.getValue();
        if (quantum <= 0) {
            throw new ParseException("Quantum phải là số nguyên dương.", 0);
        }
        return quantum;
    }

    private static SimulationRun computeSimulation(
            List<CpuScheduler.ProcessInput> processes, CpuScheduler.Algorithm algorithm,
            int quantum, boolean compareAll) {
        if (!compareAll) {
            return new SimulationRun(algorithm, CpuScheduler.schedule(processes, algorithm, quantum), List.of());
        }
        List<CpuScheduler.ComparisonResult> comparisons = CpuScheduler.compare(processes, quantum);
        CpuScheduler.ComparisonResult first = comparisons.getFirst();
        return new SimulationRun(first.algorithm(), first.result(), comparisons);
    }

    private void applySimulation(SimulationRun run, int quantum, long revision) {
        if (revision != inputRevision) {
            statusLabel.setText("Dữ liệu đã đổi; hãy chạy lại mô phỏng.");
            return;
        }
        lastQuantum = quantum;
        lastResult = run.result();
        lastAlgorithm = run.algorithm();
        lastComparisons = run.comparisons();
        if (lastComparisons.isEmpty()) {
            displayResult(lastResult);
            resultTabs.setSelectedIndex(DETAILS_TAB);
        } else {
            displayComparisons();
            resultTabs.setSelectedIndex(COMPARISON_TAB);
        }
        statusLabel.setText("Hoàn tất " + lastResult.processes().size() + " tiến trình."
                + (lastComparisons.isEmpty() ? "" : " Chọn một thuật toán để xem chi tiết."));
    }

    private record SimulationRun(
            CpuScheduler.Algorithm algorithm,
            CpuScheduler.ScheduleResult result,
            List<CpuScheduler.ComparisonResult> comparisons) {}

    private void importCsv() {
        JFileChooser chooser = csvChooser("Chọn tệp CSV tiến trình", dataDirectory("input"), null);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path inputPath = chooser.getSelectedFile().toPath();
        startTask("Đang nạp " + inputPath.getFileName() + "…", () -> CsvScheduleFiles.readProcesses(inputPath), processes -> {
            Object[][] rows = new Object[processes.size()][4];
            for (int i = 0; i < processes.size(); i++) {
                CpuScheduler.ProcessInput process = processes.get(i);
                rows[i] = new Object[] {process.id(), process.arrivalTime(), process.burstTime(), process.priority()};
            }
            inputModel.setDataVector(rows, INPUT_COLUMNS);
            statusLabel.setText("Đã nạp " + processes.size() + " tiến trình từ " + inputPath.getFileName() + ".");
        });
    }

    private void exportCsv(boolean comparison) {
        try {
            stopEditing();
            quantumSpinner.commitEdit();
        } catch (IllegalArgumentException | ParseException exception) {
            showError(exception);
            return;
        }
        if (lastResult == null || (comparison && lastComparisons.isEmpty())) {
            statusLabel.setText("Dữ liệu hoặc cấu hình đã đổi; hãy chạy mô phỏng trước khi xuất.");
            return;
        }
        boolean summaryOnly = false;
        if (comparison) {
            int choice = JOptionPane.showOptionDialog(this, "Chọn nội dung cần xuất:", "Xuất so sánh CSV",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[] {"Bảng so sánh chỉ số", "Toàn bộ trace và kết quả", "Hủy"}, "Bảng so sánh chỉ số");
            if (choice < 0 || choice == 2) {
                return;
            }
            summaryOnly = choice == 0;
        }
        String defaultName = comparison ? (summaryOnly ? "comparison-summary.csv" : "comparison-details.csv")
                : "schedule-results.csv";
        JFileChooser chooser = csvChooser("Lưu kết quả CSV", dataDirectory("output"), defaultName);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path outputPath = chooser.getSelectedFile().toPath();
        if (!outputPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            outputPath = outputPath.resolveSibling(outputPath.getFileName() + ".csv");
        }
        if (Files.exists(outputPath) && JOptionPane.showConfirmDialog(this,
                "Tệp đã tồn tại. Ghi đè " + outputPath.getFileName() + "?", "Xác nhận ghi đè",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        Path chosenPath = outputPath;
        boolean writeSummary = summaryOnly;
        CpuScheduler.ScheduleResult result = lastResult;
        CpuScheduler.Algorithm algorithm = lastAlgorithm;
        int quantum = lastQuantum;
        List<CpuScheduler.ComparisonResult> comparisons = lastComparisons;
        startTask("Đang xuất CSV…", () -> {
            if (comparison && writeSummary) {
                CsvScheduleFiles.writeComparisonSummary(chosenPath, comparisons, quantum);
            } else if (comparison) {
                CsvScheduleFiles.writeComparison(chosenPath, comparisons, quantum);
            } else {
                CsvScheduleFiles.writeResult(chosenPath, algorithm, quantum, result);
            }
            return chosenPath;
        }, path -> statusLabel.setText("Đã lưu: " + path.toAbsolutePath()));
    }

    private static Path dataDirectory(String name) {
        return Files.exists(Path.of("pom.xml")) || !Files.exists(Path.of("Code", "pom.xml"))
                ? Path.of(name) : Path.of("Code", name);
    }

    private static JFileChooser csvChooser(String title, Path directory, String defaultName) {
        JFileChooser chooser = new JFileChooser(directory.toAbsolutePath().toFile());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
        if (defaultName != null) {
            chooser.setSelectedFile(directory.resolve(defaultName).toAbsolutePath().toFile());
        }
        return chooser;
    }

    private <T> void startTask(String message, Callable<T> operation, Consumer<T> success) {
        if (busy) {
            return;
        }
        busy = true;
        cancellationRequested = false;
        statusLabel.setText(message);
        updateControlState();
        currentWorker = new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                backgroundThread = Thread.currentThread();
                try {
                    if (cancellationRequested) {
                        throw new CancellationException();
                    }
                    T value = operation.call();
                    if (cancellationRequested || Thread.currentThread().isInterrupted()) {
                        throw new CancellationException();
                    }
                    return value;
                } finally {
                    backgroundThread = null;
                }
            }

            @Override
            protected void done() {
                try {
                    T value = get();
                    if (cancellationRequested) {
                        statusLabel.setText("Đã hủy tác vụ.");
                    } else {
                        success.accept(value);
                    }
                } catch (CancellationException exception) {
                    statusLabel.setText("Đã hủy tác vụ.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Tác vụ bị gián đoạn.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cancellationRequested || cause instanceof CancellationException || cause instanceof InterruptedException) {
                        statusLabel.setText("Đã hủy tác vụ.");
                    } else {
                        showError(cause);
                    }
                } finally {
                    currentWorker = null;
                    busy = false;
                    cancellationRequested = false;
                    updateControlState();
                }
            }
        };
        currentWorker.execute();
    }

    private void cancelTask() {
        if (currentWorker == null) {
            return;
        }
        cancellationRequested = true;
        Thread thread = backgroundThread;
        if (thread != null) {
            thread.interrupt();
        }
        statusLabel.setText("Đang hủy tác vụ…");
        updateControlState();
    }

    private void invalidateResult() {
        inputRevision++;
        clearResults();
        statusLabel.setText("Dữ liệu hoặc cấu hình đã thay đổi; hãy chạy mô phỏng lại.");
    }

    private void clearResults() {
        lastResult = null;
        lastAlgorithm = null;
        lastComparisons = List.of();
        comparisonModel.setRowCount(0);
        comparisonNotes.setText("");
        ganttChart.setTimeline(List.of());
        resultModel.setResult(null);
        resultTitle.setText("Chưa có kết quả.");
        summaryLabel.setText(" ");
        updateControlState();
    }

    private List<CpuScheduler.ProcessInput> readProcesses() {
        List<CpuScheduler.ProcessInput> processes = new ArrayList<>();
        for (int row = 0; row < inputModel.getRowCount(); row++) {
            processes.add(new CpuScheduler.ProcessInput(cellText(row, 0),
                    parseInteger(cellText(row, 1), "thời điểm đến ở dòng " + (row + 1)),
                    parseInteger(cellText(row, 2), "thời gian chạy ở dòng " + (row + 1)),
                    parseInteger(cellText(row, 3), "priority ở dòng " + (row + 1))));
        }
        return processes;
    }

    private String cellText(int row, int column) {
        Object value = inputModel.getValueAt(row, column);
        return value == null ? "" : value.toString().strip();
    }

    private static int parseInteger(String value, String label) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " phải là số nguyên.");
        }
    }

    private void displayResult(CpuScheduler.ScheduleResult result) {
        ganttChart.setTimeline(result.timeline());
        resultModel.setResult(result);
        resultTitle.setText(lastAlgorithm + (lastAlgorithm == CpuScheduler.Algorithm.ROUND_ROBIN
                ? " (quantum = " + lastQuantum + ")" : ""));
        summaryLabel.setText(String.format(Locale.ROOT,
                "<html>WT TB: %.2f &nbsp; TAT TB: %.2f &nbsp; RT TB: %.2f"
                        + "<br>CPU utilization: %.2f%% &nbsp; Throughput: %.4f tiến trình/đơn vị thời gian"
                        + " &nbsp; %d đoạn chạy.</html>",
                result.averageWaitingTime(), result.averageTurnaroundTime(), result.averageResponseTime(),
                result.cpuUtilization() * 100, result.throughput(), result.timeline().size()));
        updateControlState();
    }

    private void displayComparisons() {
        comparisonModel.setRowCount(0);
        for (CpuScheduler.ComparisonResult comparison : lastComparisons) {
            CpuScheduler.ScheduleResult result = comparison.result();
            comparisonModel.addRow(new Object[] {comparison.algorithm().toString(), result.averageWaitingTime(),
                result.averageTurnaroundTime(), result.averageResponseTime(), comparison.maximumWaitingTime(),
                comparison.waitingTimeStdDev(), comparison.jainFairnessIndex(), result.cpuUtilization() * 100, result.throughput()});
        }
        comparisonNotes.setText("WT trung bình thấp nhất: " + bestAlgorithms(row -> row.result().averageWaitingTime(), false)
                + ". RT trung bình thấp nhất: " + bestAlgorithms(row -> row.result().averageResponseTime(), false)
                + ".\nWT lớn nhất thấp nhất: " + bestAlgorithms(CpuScheduler.ComparisonResult::maximumWaitingTime, false)
                + ". Jain cao nhất: " + bestAlgorithms(CpuScheduler.ComparisonResult::jainFairnessIndex, true)
                + ".\nJain tính từ BT/TAT; gần 1 nghĩa là mức phục vụ tương đối đồng đều, không bảo đảm không có starvation."
                + "\nThroughput có thể bằng nhau giữa các thuật toán vì mô hình bỏ qua chi phí chuyển ngữ cảnh, I/O và aging."
                + " Quantum Round Robin = " + lastQuantum + ".");
        comparisonNotes.setCaretPosition(0);
        comparisonTable.setRowSelectionInterval(0, 0);
    }

    private String bestAlgorithms(ToDoubleFunction<CpuScheduler.ComparisonResult> metric, boolean maximum) {
        double best = lastComparisons.stream().mapToDouble(metric).reduce(maximum ? Math::max : Math::min).orElse(0);
        return String.join(", ", lastComparisons.stream().filter(row -> Math.abs(metric.applyAsDouble(row) - best) < 1e-9)
                .map(row -> row.algorithm().toString()).toList());
    }

    private void showError(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception instanceof OutOfMemoryError ? "Không đủ bộ nhớ để xử lý workload này."
                    : "Không thể hoàn tất tác vụ: " + exception.getClass().getSimpleName();
        }
        statusLabel.setText("Tác vụ chưa hoàn tất.");
        JOptionPane.showMessageDialog(this, message, "Không thể thực hiện", JOptionPane.ERROR_MESSAGE);
    }

    private static final class ProcessResultsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Process", "AT", "BT", "Priority", "ST", "CT", "TAT", "WT", "RT"};
        private transient List<CpuScheduler.ProcessResult> processes = List.of();

        void setResult(CpuScheduler.ScheduleResult result) {
            processes = result == null ? List.of() : result.processes();
            fireTableDataChanged();
        }
        @Override
        public int getRowCount() {
            return processes.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? String.class : Integer.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            CpuScheduler.ProcessResult process = processes.get(row);
            return switch (column) {
                case 0 -> process.process().id();
                case 1 -> process.process().arrivalTime();
                case 2 -> process.process().burstTime();
                case 3 -> process.process().priority();
                case 4 -> process.startTime();
                case 5 -> process.completionTime();
                case 6 -> process.turnaroundTime();
                case 7 -> process.waitingTime();
                case 8 -> process.responseTime();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SchedulingGUI().setVisible(true));
    }
}
