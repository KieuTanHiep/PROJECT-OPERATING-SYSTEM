package gui;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Scripted desktop integration smoke; opens no visible window. Requires a desktop session. */
public final class GuiSmokeProbe {
    private static final Path CODE = Files.isRegularFile(Path.of("Code/pom.xml")) ? Path.of("Code") : Path.of("");
    private static final Path ARTIFACTS = CODE.resolve("target/gui-smoke");
    private static SchedulingGUI frame;

    private GuiSmokeProbe() {}

    private static <T> T edt(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static Object field(String name) throws Exception {
        Field field = SchedulingGUI.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(frame);
    }
    private static Object invoke(String name, Class<?>[] parameters, Object... values) throws Exception {
        Method method = SchedulingGUI.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(frame, values);
    }
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
    private static void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (edt(() -> (Boolean) field("busy"))) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("GUI worker did not finish");
            }
            Thread.sleep(10);
        }
    }
    private static void screenshot(String name) throws Exception {
        edt(() -> {
            frame.validate();
            JRootPane root = frame.getRootPane();
            BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                root.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            boolean painted = false;
            for (int y = 0; y < image.getHeight(); y += 50) {
                for (int x = 0; x < image.getWidth(); x += 50) {
                    painted |= (image.getRGB(x, y) & 0xFFFFFF) != 0;
                }
            }
            require(painted, "Hidden GUI rendering must contain painted content");
            ImageIO.write(image, "png", ARTIFACTS.resolve(name).toFile());
            return null;
        });
    }
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            failure.printStackTrace();
            System.exit(2);
        });
        try {
            Files.createDirectories(ARTIFACTS);
            var inputs = CsvScheduleFiles.readProcesses(CODE.resolve("input/data.csv"));
            edt(() -> {
                frame = new SchedulingGUI();
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                var model = (DefaultTableModel) field("inputModel");
                for (var input : inputs) {
                    model.addRow(new Object[] {input.id(), input.arrivalTime(), input.burstTime(), input.priority()});
                }
                JSpinner spinner = (JSpinner) field("quantumSpinner");
                ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("3");
                invoke("runSimulation", new Class<?>[] {boolean.class}, true);
                require((Boolean) field("busy"), "Comparison must run in background");
                require(!((JButton) field("compareButton")).isEnabled(), "Must block duplicate task");
                return null;
            });
            awaitIdle();
            edt(() -> {
                require(((List<?>) field("lastComparisons")).size() == 5, "Expected all five algorithms");
                require((Integer) field("lastQuantum") == 3, "Typed quantum must be committed");
                JTable table = (JTable) field("comparisonTable");
                require(table.getRowCount() == 5, "Comparison table must show five rows");
                table.getRowSorter().toggleSortOrder(1);
                table.setRowSelectionInterval(table.convertRowIndexToView(4), table.convertRowIndexToView(4));
                require(field("lastAlgorithm") == CpuScheduler.Algorithm.ROUND_ROBIN,
                        "Sorted-row selection must show matching algorithm");
                require(((JButton) field("exportComparisonButton")).isEnabled(), "Comparison export should be enabled");
                ((JTabbedPane) field("resultTabs")).setSelectedIndex(0);
                return null;
            });
            screenshot("gui-detail.png");
            edt(() -> {
                ((JTabbedPane) field("resultTabs")).setSelectedIndex(1);
                return null;
            });
            screenshot("gui-comparison.png");
            edt(() -> {
                ((JSpinner) field("quantumSpinner")).setValue(4);
                require(field("lastResult") == null, "Quantum edit must invalidate previous result");
                require(((List<?>) field("lastComparisons")).isEmpty(), "Quantum edit must invalidate comparison");
                require(!((JButton) field("exportButton")).isEnabled(), "Cannot export stale result");
                return null;
            });
            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean deliveredCancelledResult = new AtomicBoolean();
            edt(() -> {
                Callable<String> slowOperation = () -> {
                    started.countDown();
                    Thread.sleep(10000);
                    return "unexpected";
                };
                Consumer<String> success = value -> deliveredCancelledResult.set(true);
                invoke("startTask", new Class<?>[] {String.class, Callable.class, Consumer.class},
                        "Smoke cancellation", slowOperation, success);
                return null;
            });
            require(started.await(3, TimeUnit.SECONDS), "Background task must start");
            edt(() -> {
                require((Boolean) field("busy"), "EDT must stay responsive while task runs");
                invoke("cancelTask", new Class<?>[0]);
                require((Boolean) field("busy"), "Cancellation must wait for worker completion");
                return null;
            });
            awaitIdle();
            require(!deliveredCancelledResult.get(), "Cancelled task must not deliver success");
            edt(() -> {
                ((JComboBox<?>) field("algorithmBox")).setSelectedItem(CpuScheduler.Algorithm.SRTF);
                invoke("runSimulation", new Class<?>[] {boolean.class}, false);
                return null;
            });
            awaitIdle();
            edt(() -> {
                require(field("lastResult") != null, "Must be able to run again after cancellation");
                require(field("lastAlgorithm") == CpuScheduler.Algorithm.SRTF, "Expected selected algorithm");
                ((DefaultTableModel) field("inputModel")).setValueAt(8, 0, 2);
                require(field("lastResult") == null, "Input edit must invalidate previous result");
                require(!((JButton) field("exportButton")).isEnabled(), "Cannot export after input edit");
                frame.dispose();
                return null;
            });
            String result = "PASS: hidden GUI construction and SwingWorker comparison; all five rows; typed quantum commit; "
                    + "sorted-row detail selection; Gantt/detail and comparison rendering; config/input invalidation; "
                    + "EDT responsiveness; cooperative cancellation; restart after cancellation.\n"
                    + "Scripted smoke using hidden JFrame; file-chooser/overwrite/error dialogs not manually exercised.\n";
            Files.writeString(ARTIFACTS.resolve("gui-smoke.txt"), result);
            System.out.print(result);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
