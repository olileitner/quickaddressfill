package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;

final class PostcodeOverviewLayer extends Layer {

    private static final Color MISSING_POSTCODE_COLOR = new Color(110, 110, 110, 165);
    private static final Color OUTLINE_COLOR = new Color(20, 20, 20, 170);
    private static final Color LEGEND_BACKGROUND_COLOR = new Color(248, 248, 248, 215);
    private static final Color LEGEND_BORDER_COLOR = new Color(35, 35, 35, 180);
    private static final int LEGEND_PADDING = 8;
    private static final int LEGEND_ROW_HEIGHT = 16;
    private static final int LEGEND_SWATCH_SIZE = 11;
    static final int TOP_POSTCODE_LEGEND_LIMIT = 5;
    private static final int POSTCODE_FILL_ALPHA = 165;
    private static final Color[] POSTCODE_PALETTE = new Color[] {
            new Color(214, 39, 40),
            new Color(31, 119, 180),
            new Color(44, 160, 44),
            new Color(255, 127, 14),
            new Color(148, 103, 189),
            new Color(140, 86, 75),
            new Color(227, 119, 194),
            new Color(23, 190, 207),
            new Color(188, 189, 34),
            new Color(57, 59, 121),
            new Color(140, 162, 82),
            new Color(132, 60, 57),
            new Color(123, 65, 115),
            new Color(99, 121, 57),
            new Color(82, 84, 163),
            new Color(165, 81, 148)
    };

    private final DataSet dataSet;
    private final BuildingOverviewCollector collector;

    PostcodeOverviewLayer(DataSet dataSet) {
        super(I18n.tr("Postcode overview"));
        this.dataSet = dataSet;
        this.collector = new BuildingOverviewCollector();
    }

    @Override
    public void paint(Graphics2D graphics, MapView mapView, Bounds bounds) {
        if (graphics == null || mapView == null || dataSet == null) {
            return;
        }

        List<BuildingOverviewCollector.BuildingOverviewEntry> entries = collector.collect(dataSet);
        if (entries.isEmpty()) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.0f));

        for (BuildingOverviewCollector.BuildingOverviewEntry entry : entries) {
            drawPrimitive(g, mapView, entry.getPrimitive());
        }
        drawLegend(g, mapView, entries);
        g.dispose();
    }

    private void drawLegend(Graphics2D g, MapView mapView, List<BuildingOverviewCollector.BuildingOverviewEntry> entries) {
        if (mapView.getWidth() < 180 || mapView.getHeight() < 120) {
            return;
        }

        Map<String, Integer> postcodeCounts = collectPostcodeCounts(entries);
        int missingCount = postcodeCounts.getOrDefault("", 0);
        List<String> topPostcodes = sortPostcodesForLegend(postcodeCounts, TOP_POSTCODE_LEGEND_LIMIT);

        String title = I18n.tr("Postcode");
        int contentRows = 1 + topPostcodes.size();
        int legendHeight = LEGEND_PADDING * 2 + LEGEND_ROW_HEIGHT + (contentRows * LEGEND_ROW_HEIGHT);
        int legendWidth = Math.max(210, g.getFontMetrics().stringWidth(title) + 80);
        int legendX = Math.max(8, mapView.getWidth() - legendWidth - 10);
        int legendY = 10;

        g.setColor(LEGEND_BACKGROUND_COLOR);
        g.fillRoundRect(legendX, legendY, legendWidth, legendHeight, 8, 8);
        g.setColor(LEGEND_BORDER_COLOR);
        g.drawRoundRect(legendX, legendY, legendWidth, legendHeight, 8, 8);

        int textBaseX = legendX + LEGEND_PADDING;
        int rowY = legendY + LEGEND_PADDING + 12;
        g.setColor(Color.BLACK);
        g.drawString(title, textBaseX, rowY);

        rowY += LEGEND_ROW_HEIGHT;
        drawLegendRow(g, textBaseX, rowY, resolveColorForPostcode(""),
                I18n.tr("No postcode ({0})", missingCount));

        for (String postcode : topPostcodes) {
            rowY += LEGEND_ROW_HEIGHT;
            int count = postcodeCounts.getOrDefault(postcode, 0);
            drawLegendRow(g, textBaseX, rowY, resolveColorForPostcode(postcode),
                    I18n.tr("{0} ({1})", postcode, count));
        }
    }

    private void drawLegendRow(Graphics2D g, int textBaseX, int rowY, Color swatchColor, String label) {
        int swatchY = rowY - LEGEND_SWATCH_SIZE + 3;
        g.setColor(swatchColor);
        g.fillRect(textBaseX, swatchY, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);
        g.setColor(OUTLINE_COLOR);
        g.drawRect(textBaseX, swatchY, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);
        g.setColor(Color.BLACK);
        g.drawString(label, textBaseX + LEGEND_SWATCH_SIZE + 6, rowY);
    }

    private Map<String, Integer> collectPostcodeCounts(List<BuildingOverviewCollector.BuildingOverviewEntry> entries) {
        Map<String, Integer> counts = new HashMap<>();
        if (entries == null) {
            return counts;
        }
        for (BuildingOverviewCollector.BuildingOverviewEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String postcode = normalize(entry.getPrimitive() == null ? null : entry.getPrimitive().get("addr:postcode"));
            counts.merge(postcode, 1, Integer::sum);
        }
        return counts;
    }

    static List<String> sortPostcodesForLegend(Map<String, Integer> postcodeCounts, int limit) {
        if (postcodeCounts == null || postcodeCounts.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : postcodeCounts.entrySet()) {
            if (entry == null) {
                continue;
            }
            String postcode = entry.getKey() == null ? "" : entry.getKey().trim();
            if (postcode.isEmpty()) {
                continue;
            }
            ranked.add(Map.entry(postcode, entry.getValue() == null ? 0 : entry.getValue()));
        }

        ranked.sort(Comparator
                .<Map.Entry<String, Integer>>comparingInt(e -> e.getValue() == null ? 0 : e.getValue())
                .reversed()
                .thenComparing(e -> e.getKey().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(Map.Entry::getKey));

        int resultSize = Math.min(limit, ranked.size());
        List<String> result = new ArrayList<>(resultSize);
        for (int i = 0; i < resultSize; i++) {
            result.add(ranked.get(i).getKey());
        }
        return result;
    }

    private void drawPrimitive(Graphics2D g, MapView mapView, OsmPrimitive primitive) {
        if (primitive instanceof Way) {
            drawWay(g, mapView, (Way) primitive, resolveFillColor(primitive));
            return;
        }
        if (primitive instanceof Relation) {
            drawRelation(g, mapView, (Relation) primitive, resolveFillColor(primitive));
        }
    }

    private Color resolveFillColor(OsmPrimitive primitive) {
        String postcode = normalize(primitive == null ? null : primitive.get("addr:postcode"));
        return resolveColorForPostcode(postcode);
    }

    static Color resolveColorForPostcode(String postcode) {
        String normalizedPostcode = postcode == null ? "" : postcode.trim();
        if (normalizedPostcode.isEmpty()) {
            return MISSING_POSTCODE_COLOR;
        }
        long seed = Integer.toUnsignedLong(normalizedPostcode.toLowerCase(java.util.Locale.ROOT).hashCode());
        int index = (int) (seed % POSTCODE_PALETTE.length);
        Color base = POSTCODE_PALETTE[index];
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), POSTCODE_FILL_ALPHA);
    }

    private void drawRelation(Graphics2D g, MapView mapView, Relation relation, Color fillColor) {
        if (relation == null || !relation.isUsable()) {
            return;
        }

        for (RelationMember member : relation.getMembers()) {
            if (member == null || !member.isWay()) {
                continue;
            }
            String role = normalize(member.getRole());
            if (!role.isEmpty() && !"outer".equals(role)) {
                continue;
            }
            drawWay(g, mapView, member.getWay(), fillColor);
        }
    }

    private void drawWay(Graphics2D g, MapView mapView, Way way, Color fillColor) {
        Path2D polygon = buildScreenPolygon(mapView, way);
        if (polygon == null) {
            return;
        }

        g.setColor(fillColor);
        g.fill(polygon);
        g.setColor(OUTLINE_COLOR);
        g.draw(polygon);
    }

    private Path2D buildScreenPolygon(MapView mapView, Way way) {
        if (way == null || !way.isUsable() || !way.isClosed() || way.getNodesCount() < 4) {
            return null;
        }

        Path2D path = new Path2D.Double();
        boolean hasStart = false;
        for (Node node : way.getNodes()) {
            if (node == null || !node.isUsable()) {
                return null;
            }

            Point point = mapView.getPoint(node);
            if (point == null) {
                return null;
            }

            if (!hasStart) {
                path.moveTo(point.x, point.y);
                hasStart = true;
            } else {
                path.lineTo(point.x, point.y);
            }
        }

        if (!hasStart) {
            return null;
        }
        path.closePath();
        return path;
    }

    @Override
    public Icon getIcon() {
        Icon icon = ImageProvider.get("dialogs", "search");
        return icon != null ? icon : ImageProvider.get("housenumberclick");
    }

    @Override
    public String getToolTipText() {
        return I18n.tr("Postcode overview (gray: missing postcode, same color: same postcode)");
    }

    @Override
    public void mergeFrom(Layer from) {
        // Display-only layer, no merge behavior.
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor visitor) {
        if (visitor == null || dataSet == null) {
            return;
        }
        visitor.visit(dataSet.getDataSourceBoundingBox());
    }

    @Override
    public Object getInfoComponent() {
        return I18n.tr("Postcode overview (gray: missing postcode)");
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



