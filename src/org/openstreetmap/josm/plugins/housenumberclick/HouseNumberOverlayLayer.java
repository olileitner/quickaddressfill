package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.List;

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
    private static final Color LINE_COLOR = new Color(35, 95, 165, 140);
    private static final Color BUBBLE_FILL_COLOR = new Color(255, 255, 220, 225);
    private static final Color BUBBLE_BORDER_COLOR = new Color(45, 45, 45, 210);
    private static final Color TEXT_COLOR = new Color(10, 10, 10, 230);

    private final HouseNumberOverlayCollector collector;
    private String selectedStreet = "";
    private boolean connectionLinesEnabled;

    HouseNumberOverlayLayer() {
        super(I18n.tr("House number overlay"));
        this.collector = new HouseNumberOverlayCollector();
    }

    void updateSettings(String selectedStreet, boolean connectionLinesEnabled) {
        this.selectedStreet = normalize(selectedStreet);
        this.connectionLinesEnabled = connectionLinesEnabled;
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

        List<HouseNumberOverlayEntry> entries = collector.collect(dataSet, mapView, selectedStreet);
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
        drawBubblesAndLabels(g, mapView, entries);
        g.dispose();
    }

    private void drawConnectionLines(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries) {
        g.setColor(LINE_COLOR);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Point previous = null;
        for (HouseNumberOverlayEntry entry : entries) {
            Point current = mapView.getPoint(entry.getLabelPoint());
            if (!isOnScreen(current, mapView)) {
                continue;
            }
            if (previous != null) {
                g.drawLine(previous.x, previous.y, current.x, current.y);
            }
            previous = current;
        }
    }

    private void drawBubblesAndLabels(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries) {
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

            g.setColor(BUBBLE_FILL_COLOR);
            g.fillOval(x, y, bubbleWidth, bubbleHeight);
            g.setColor(BUBBLE_BORDER_COLOR);
            g.setStroke(new BasicStroke(1.6f));
            g.drawOval(x, y, bubbleWidth, bubbleHeight);

            int textX = point.x - textWidth / 2;
            int textY = point.y + (metrics.getAscent() - metrics.getDescent()) / 2;
            g.setColor(TEXT_COLOR);
            g.drawString(label, textX, textY);
        }
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
}

