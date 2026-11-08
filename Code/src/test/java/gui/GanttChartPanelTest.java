package gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GanttChartPanelTest {
    @Test
    void mapsProportionalPixelsToInitialIdleAndExactExecutionIntervals() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GanttChartPanel panel = new GanttChartPanel();
            panel.setSize(1000, 130);
            panel.setTimeline(List.of(
                    new CpuScheduler.ExecutionSlice("IDLE", 0, 2),
                    new CpuScheduler.ExecutionSlice("P1", 2, 3),
                    new CpuScheduler.ExecutionSlice("P2", 3, 5)));

            assertEquals("IDLE [0, 2) — 2 đơn vị thời gian", tooltip(panel, 100, 50));
            assertEquals("P1 [2, 3) — 1 đơn vị thời gian", tooltip(panel, 500, 50));
            assertEquals("P2 [3, 5) — 2 đơn vị thời gian", tooltip(panel, 800, 50));
            assertNull(tooltip(panel, 10, 50));
            assertNull(tooltip(panel, 100, 10));

            BufferedImage image = paint(panel);
            assertTrue(image.getRGB(100, 40) != image.getRGB(500, 40), "Idle and process use distinct colors");
            Color p1 = new Color(image.getRGB(500, 40));
            Color p2 = new Color(image.getRGB(800, 40));
            int colorDistance = Math.abs(p1.getRed() - p2.getRed())
                    + Math.abs(p1.getGreen() - p2.getGreen()) + Math.abs(p1.getBlue() - p2.getBlue());
            assertTrue(colorDistance >= 60, "Adjacent IDs must remain visually distinguishable");
            assertEquals(image.getRGB(610, 40), image.getRGB(900, 40), "P2 spans both pixels in proportion to time");
        });
    }

    @Test
    void scalesMaximumIntegerTimeWithoutAllocatingAWidthBasedOnDuration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GanttChartPanel panel = new GanttChartPanel();
            panel.setSize(1000, 130);
            panel.setTimeline(List.of(
                    new CpuScheduler.ExecutionSlice("IDLE", 0, 2_000_000_000),
                    new CpuScheduler.ExecutionSlice("P1", 2_000_000_000, Integer.MAX_VALUE)));
            assertEquals("P1 [2000000000, 2147483647) — 147483647 đơn vị thời gian", tooltip(panel, 970, 50));
            assertDoesNotThrow(() -> paint(panel));
            assertTrue(panel.getPreferredSize().width <= 200_000);
            panel.setZoom(1e12);
            assertTrue(panel.getPreferredSize().width <= 200_000);
            assertFalse(panel.getScrollableTracksViewportWidth());
            panel.setZoom(1);
            assertTrue(panel.getScrollableTracksViewportWidth());
        });
    }

    @Test
    @Timeout(10)
    void denseTraceIsRenderedAsAnOverviewAndRetainsExactTooltipLookup() throws Exception {
        List<CpuScheduler.ExecutionSlice> slices = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            slices.add(new CpuScheduler.ExecutionSlice(i % 2 == 0 ? "P1" : "P2", i, i + 1));
        }
        SwingUtilities.invokeAndWait(() -> {
            GanttChartPanel panel = new GanttChartPanel();
            panel.setSize(1000, 130);
            panel.setTimeline(slices);
            assertDoesNotThrow(() -> paint(panel));
            assertEquals("P1 [100000, 100001) — 1 đơn vị thời gian", tooltip(panel, 500, 50));
            assertEquals(1000, panel.getPreferredSize().width);
        });
    }

    @Test
    void emptyTimelineAndInvalidZoomAreHandled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GanttChartPanel panel = new GanttChartPanel();
            panel.setSize(1000, 130);
            assertNull(tooltip(panel, 100, 50));
            assertDoesNotThrow(() -> paint(panel));
            assertThrows(IllegalArgumentException.class, () -> panel.setZoom(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> panel.setZoom(0));
        });
    }

    private static String tooltip(GanttChartPanel panel, int x, int y) {
        return panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    private static BufferedImage paint(GanttChartPanel panel) {
        BufferedImage image = new BufferedImage(1000, 130, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
