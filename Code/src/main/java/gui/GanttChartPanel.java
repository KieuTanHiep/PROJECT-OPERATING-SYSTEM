package gui;

import algorithm.SchedulingSupport;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/** Proportional timeline with bounded zoom and painting limited to the visible pixels. */
public final class GanttChartPanel extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;
    private static final int MARGIN = 28;
    private static final int BAR_TOP = 32;
    private static final int BAR_HEIGHT = 46;
    private static final int PREFERRED_WIDTH = 1000;
    private static final int MINIMUM_WIDTH = 800;
    private static final int CHART_HEIGHT = 130;
    private static final int MAX_WIDTH = 200_000;
    private static final double MAX_ZOOM = 128;
    private static final Color IDLE_COLOR = new Color(225, 228, 232);
    private static final Color BORDER_COLOR = new Color(70, 78, 88);
    private static final Color TEXT_COLOR = new Color(25, 35, 45);
    private transient List<CpuScheduler.ExecutionSlice> timeline = List.of();
    private double zoom = 1;

    public GanttChartPanel() {
        setBackground(Color.WHITE);
        setToolTipText("");
    }

    public void setTimeline(List<CpuScheduler.ExecutionSlice> slices) {
        timeline = List.copyOf(slices);
        revalidate();
        repaint();
    }

    public void setZoom(double zoom) {
        if (!Double.isFinite(zoom) || zoom < 1) {
            throw new IllegalArgumentException("Zoom phải là số hữu hạn >= 1.");
        }
        this.zoom = Math.min(zoom, MAX_ZOOM);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int viewportWidth = getParent() instanceof JViewport viewport ? viewport.getWidth() : PREFERRED_WIDTH;
        int baseWidth = Math.max(MINIMUM_WIDTH, viewportWidth);
        return new Dimension((int) Math.min(MAX_WIDTH, Math.round(baseWidth * zoom)), CHART_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (timeline.isEmpty()) {
                g.setColor(Color.GRAY);
                g.drawString("Chạy mô phỏng để xem Gantt chart.", MARGIN, 54);
                return;
            }
            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            int endTime = timeline.get(timeline.size() - 1).endTime();
            int plotWidth = Math.max(1, getWidth() - 2 * MARGIN);
            double timePerPixel = (double) endTime / plotWidth;
            int firstPixel = Math.max(MARGIN, clip.x);
            int lastPixel = Math.min(getWidth() - MARGIN, clip.x + clip.width);
            int index = sliceIndexAt(Math.max(0, (firstPixel - MARGIN) * timePerPixel));
            FontMetrics metrics = g.getFontMetrics();
            while (index >= 0 && index < timeline.size()) {
                CpuScheduler.ExecutionSlice slice = timeline.get(index);
                int left = MARGIN + (int) Math.floor((double) slice.startTime() / endTime * plotWidth);
                if (left > lastPixel) {
                    break;
                }
                int right = MARGIN + (int) Math.floor((double) slice.endTime() / endTime * plotWidth);
                int width = Math.max(1, right - left);
                g.setColor(SchedulingSupport.IDLE_LABEL.equals(slice.processId())
                        ? IDLE_COLOR : processColor(slice.processId()));
                g.fillRect(left, BAR_TOP, width, BAR_HEIGHT);
                if (width >= 3) {
                    g.setColor(BORDER_COLOR);
                    g.drawRect(left, BAR_TOP, width, BAR_HEIGHT);
                }
                if (width >= metrics.stringWidth(slice.processId()) + 10) {
                    g.setColor(TEXT_COLOR);
                    g.drawString(slice.processId(), left + (width - metrics.stringWidth(slice.processId())) / 2,
                            BAR_TOP + BAR_HEIGHT / 2 + metrics.getAscent() / 2 - 2);
                }
                if (right <= left) {
                    // Skip subpixel slices rather than traversing a huge trace on the event thread.
                    int next = sliceIndexAt((left + 1.0 - MARGIN) * timePerPixel);
                    index = Math.max(index + 1, next);
                } else {
                    index++;
                }
            }
            g.setColor(BORDER_COLOR);
            g.drawLine(MARGIN, BAR_TOP + BAR_HEIGHT + 5, getWidth() - MARGIN, BAR_TOP + BAR_HEIGHT + 5);
            int viewportWidth = getParent() instanceof JViewport viewport ? viewport.getWidth() : getWidth();
            int tickSpacing = Math.max(70, viewportWidth / 8);
            long timeStep = readableStep(Math.max(1, tickSpacing * timePerPixel));
            long firstTime = (long) Math.ceil(Math.max(0, firstPixel - MARGIN) * timePerPixel / timeStep) * timeStep;
            int finalX = getWidth() - MARGIN;
            for (long time = firstTime; time <= endTime; time += timeStep) {
                int pixel = MARGIN + (int) Math.round((double) time / endTime * plotWidth);
                if (pixel > lastPixel) {
                    break;
                }
                String label = Long.toString(time);
                g.drawLine(pixel, BAR_TOP + BAR_HEIGHT + 3, pixel, BAR_TOP + BAR_HEIGHT + 10);
                if (time != endTime && finalX - pixel > metrics.stringWidth(label) + 12) {
                    g.drawString(label, pixel - metrics.stringWidth(label) / 2, BAR_TOP + BAR_HEIGHT + 27);
                }
            }
            if (finalX >= firstPixel && finalX <= lastPixel) {
                String label = Integer.toString(endTime);
                g.drawString(label, finalX - metrics.stringWidth(label) / 2, BAR_TOP + BAR_HEIGHT + 27);
            }
        } finally {
            g.dispose();
        }
    }

    private static Color processColor(String id) {
        // Spread adjacent IDs (P1, P2, ...) across the hue wheel.
        float hue = Math.floorMod((long) id.hashCode() * 137, 360) / 360f;
        return Color.getHSBColor(hue, 0.32f, 0.94f);
    }

    private static long readableStep(double minimum) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(minimum)));
        double fraction = minimum / magnitude;
        double multiple = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
        return Math.max(1, (long) Math.ceil(multiple * magnitude));
    }

    private int sliceIndexAt(double time) {
        int low = 0;
        int high = timeline.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            CpuScheduler.ExecutionSlice slice = timeline.get(middle);
            if (time < slice.startTime()) {
                high = middle - 1;
            } else if (time >= slice.endTime()) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return low;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (timeline.isEmpty() || event.getY() < BAR_TOP || event.getY() > BAR_TOP + BAR_HEIGHT
                || event.getX() < MARGIN || event.getX() >= getWidth() - MARGIN) {
            return null;
        }
        int endTime = timeline.get(timeline.size() - 1).endTime();
        double time = (double) (event.getX() - MARGIN) / Math.max(1, getWidth() - 2 * MARGIN) * endTime;
        int index = sliceIndexAt(time);
        if (index < 0 || index >= timeline.size()) {
            return null;
        }
        CpuScheduler.ExecutionSlice slice = timeline.get(index);
        return slice.processId() + " [" + slice.startTime() + ", " + slice.endTime()
                + ") — " + (slice.endTime() - slice.startTime()) + " đơn vị thời gian";
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(PREFERRED_WIDTH, CHART_HEIGHT);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 32;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(32, visibleRect.width - 64);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return zoom == 1;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }
}
