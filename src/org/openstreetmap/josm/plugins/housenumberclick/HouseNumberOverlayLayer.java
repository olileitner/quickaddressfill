package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;

final class HouseNumberOverlayLayer extends Layer {

    private static final Font TEXT_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final Color BUBBLE_FILL_COLOR = new Color(255, 255, 220, 225);
    private static final Color BUBBLE_BORDER_COLOR = new Color(45, 45, 45, 210);
    private static final Color ODD_BUBBLE_FILL_COLOR = new Color(255, 236, 208, 230);
    private static final Color ODD_BUBBLE_BORDER_COLOR = new Color(182, 132, 74, 220);
    private static final Color EVEN_BUBBLE_FILL_COLOR = new Color(220, 234, 255, 230);
    private static final Color EVEN_BUBBLE_BORDER_COLOR = new Color(92, 126, 170, 220);
    private static final Color ODD_LINE_COLOR = new Color(193, 146, 88, 190);
    private static final Color EVEN_LINE_COLOR = new Color(98, 134, 179, 190);
    private static final Color DUPLICATE_BUBBLE_FILL_COLOR = new Color(255, 175, 175, 235);
    private static final Color DUPLICATE_BUBBLE_BORDER_COLOR = new Color(195, 20, 20, 235);
    private static final Color TEXT_COLOR = new Color(10, 10, 10, 230);

    private final HouseNumberOverlayCollector collector;
    private String selectedStreet = "";
    private boolean connectionLinesEnabled;
    private boolean separateEvenOddConnectionLinesEnabled;

    HouseNumberOverlayLayer() {
        super(I18n.tr("House number overlay"));
        this.collector = new HouseNumberOverlayCollector();
    }

    void updateSettings(String selectedStreet, boolean connectionLinesEnabled, boolean separateEvenOddConnectionLinesEnabled) {
        this.selectedStreet = normalize(selectedStreet);
        this.connectionLinesEnabled = connectionLinesEnabled;
        this.separateEvenOddConnectionLinesEnabled = connectionLinesEnabled && separateEvenOddConnectionLinesEnabled;
        invalidate();
    }

    @Override
    public void paint(Graphics2D graphics, MapView mapView, Bounds bounds) {
        if (mapView == null || selectedStreet.isEmpty()) {
            return;
        }

        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            return;
        }

        List<HouseNumberOverlayEntry> entries = collector.collect(dataSet, selectedStreet);
        if (entries.isEmpty()) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(TEXT_FONT);

        if (connectionLinesEnabled && entries.size() > 1) {
            drawConnectionLines(g, mapView, entries);
        }
        Set<String> duplicateNumbers = collectDuplicateHouseNumbers(entries);
        drawBubblesAndLabels(g, mapView, entries, duplicateNumbers);
        g.dispose();
    }

    private void drawConnectionLines(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries) {
        g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (!separateEvenOddConnectionLinesEnabled) {
            g.setColor(BUBBLE_FILL_COLOR);
            drawConnectionLinePath(g, mapView, entries, null);
            return;
        }

        g.setColor(EVEN_LINE_COLOR);
        drawConnectionLinePath(g, mapView, entries, 0);
        g.setColor(ODD_LINE_COLOR);
        drawConnectionLinePath(g, mapView, entries, 1);
    }

    private void drawConnectionLinePath(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries, Integer parityFilter) {
        Point previous = null;
        for (HouseNumberOverlayEntry entry : entries) {
            if (parityFilter != null) {
                int numberPart = entry.getNumberPart();
                if (numberPart == Integer.MAX_VALUE || Math.abs(numberPart % 2) != parityFilter) {
                    continue;
                }
            }

            Point current = mapView.getPoint(entry.getLabelPoint());
            if (!isOnScreen(current, mapView)) {
                previous = null;
                continue;
            }
            if (previous != null) {
                g.drawLine(previous.x, previous.y, current.x, current.y);
            }
            previous = current;
        }
    }

    private void drawBubblesAndLabels(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries,
            Set<String> duplicateNumbers) {
        FontMetrics metrics = g.getFontMetrics();

        for (HouseNumberOverlayEntry entry : entries) {
            Point point = mapView.getPoint(entry.getLabelPoint());
            if (!isOnScreen(point, mapView)) {
                continue;
            }

            String label = entry.getHouseNumber();
            int textWidth = metrics.stringWidth(label);
            int textHeight = metrics.getAscent();

            int bubbleWidth = Math.max(26, textWidth + 14);
            int bubbleHeight = Math.max(24, textHeight + 10);
            int x = point.x - bubbleWidth / 2;
            int y = point.y - bubbleHeight / 2;
            int numberPart = entry.getNumberPart();
            boolean duplicateHouseNumber = duplicateNumbers.contains(normalizeHouseNumberKey(label));

            if (duplicateHouseNumber) {
                int ringPadding = 5;
                int outerX = x - ringPadding;
                int outerY = y - ringPadding;
                int outerWidth = bubbleWidth + (ringPadding * 2);
                int outerHeight = bubbleHeight + (ringPadding * 2);
                g.setColor(new Color(
                        DUPLICATE_BUBBLE_BORDER_COLOR.getRed(),
                        DUPLICATE_BUBBLE_BORDER_COLOR.getGreen(),
                        DUPLICATE_BUBBLE_BORDER_COLOR.getBlue(),
                        175
                ));
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(outerX, outerY, outerWidth, outerHeight);
            }

            g.setColor(resolveBubbleFillColor(duplicateHouseNumber, numberPart));
            g.fillOval(x, y, bubbleWidth, bubbleHeight);
            g.setColor(resolveBubbleBorderColor(duplicateHouseNumber, numberPart));
            g.setStroke(new BasicStroke(duplicateHouseNumber ? 3.2f : 1.6f));
            g.drawOval(x, y, bubbleWidth, bubbleHeight);

            int textX = point.x - textWidth / 2;
            int textY = point.y + (metrics.getAscent() - metrics.getDescent()) / 2;
            g.setColor(TEXT_COLOR);
            g.drawString(label, textX, textY);
        }
    }

    private Set<String> collectDuplicateHouseNumbers(List<HouseNumberOverlayEntry> entries) {
        Map<String, Integer> houseNumberCounts = new HashMap<>();
        for (HouseNumberOverlayEntry entry : entries) {
            String key = normalize(entry.getHouseNumber());
            key = normalizeHouseNumberKey(key);
            if (key.isEmpty()) {
                continue;
            }
            houseNumberCounts.put(key, houseNumberCounts.getOrDefault(key, 0) + 1);
        }

        Set<String> duplicateNumbers = new HashSet<>();
        for (Map.Entry<String, Integer> countEntry : houseNumberCounts.entrySet()) {
            if (countEntry.getValue() > 1) {
                duplicateNumbers.add(countEntry.getKey());
            }
        }
        return duplicateNumbers;
    }

    private Color resolveBubbleFillColor(boolean duplicateHouseNumber, int numberPart) {
        if (duplicateHouseNumber) {
            return DUPLICATE_BUBBLE_FILL_COLOR;
        }
        if (numberPart == Integer.MAX_VALUE) {
            return BUBBLE_FILL_COLOR;
        }
        return Math.abs(numberPart % 2) == 0 ? EVEN_BUBBLE_FILL_COLOR : ODD_BUBBLE_FILL_COLOR;
    }

    private Color resolveBubbleBorderColor(boolean duplicateHouseNumber, int numberPart) {
        if (duplicateHouseNumber) {
            return DUPLICATE_BUBBLE_BORDER_COLOR;
        }
        if (numberPart == Integer.MAX_VALUE) {
            return BUBBLE_BORDER_COLOR;
        }
        return Math.abs(numberPart % 2) == 0 ? EVEN_BUBBLE_BORDER_COLOR : ODD_BUBBLE_BORDER_COLOR;
    }

    private boolean isOnScreen(Point point, MapView mapView) {
        return point != null
                && point.x >= -40
                && point.y >= -40
                && point.x <= mapView.getWidth() + 40
                && point.y <= mapView.getHeight() + 40;
    }

    @Override
    public Icon getIcon() {
        Icon icon = ImageProvider.get("dialogs", "search");
        return icon != null ? icon : ImageProvider.get("housenumberclick");
    }

    @Override
    public String getToolTipText() {
        return I18n.tr("House number overlay for selected street");
    }

    @Override
    public void mergeFrom(Layer from) {
        // Overlay layer is ephemeral and intentionally not mergeable.
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor visitor) {
        if (visitor == null) {
            return;
        }
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet != null) {
            visitor.visit(dataSet.getDataSourceBoundingBox());
        }
    }

    @Override
    public Object getInfoComponent() {
        return I18n.tr("Street: {0}", selectedStreet.isEmpty() ? I18n.tr("(none)") : selectedStreet);
    }

    @Override
    public Action[] getMenuEntries() {
        LayerListDialog layerListDialog = LayerListDialog.getInstance();
        if (layerListDialog == null) {
            return new Action[0];
        }
        return new Action[] {
                layerListDialog.createShowHideLayerAction(),
                layerListDialog.createDeleteLayerAction()
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeHouseNumberKey(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }
}






