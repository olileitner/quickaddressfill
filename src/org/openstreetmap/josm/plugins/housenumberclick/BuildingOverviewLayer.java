package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;

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

/**
 * Map layer that visualizes building-level address status to support completeness inspection.
 */
final class BuildingOverviewLayer extends Layer {

    /**
     * Selected required address field used to focus completeness-missing highlighting,
     * including an all-required-fields mode (street, postcode, house number, city).
     */
    enum MissingField {
        ALL,
        STREET,
        POSTCODE,
        HOUSE_NUMBER,
        CITY
    }

    private static final Color ADDRESSED_FILL_COLOR = new Color(86, 180, 233, 190);
    private static final Color UNADDRESSED_FILL_COLOR = new Color(230, 159, 0, 190);
    static final Color NO_ADDRESS_DATA_COLOR = new Color(135, 135, 135, 130);
    private static final Color LEGEND_BACKGROUND_COLOR = new Color(248, 248, 248, 215);
    private static final int LEGEND_PADDING = 8;
    private static final int LEGEND_ROW_HEIGHT = 16;
    private static final int LEGEND_SWATCH_SIZE = 11;

    private final DataSet dataSet;
    private final BuildingOverviewCollector collector;
    private final MissingField missingField;

    BuildingOverviewLayer(DataSet dataSet, MissingField missingField) {
        super(I18n.tr("Completeness overview"));
        this.dataSet = dataSet;
        this.collector = new BuildingOverviewCollector();
        this.missingField = missingField != null ? missingField : MissingField.POSTCODE;
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

        for (BuildingOverviewCollector.BuildingOverviewEntry entry : entries) {
            drawPrimitive(g, mapView, entry);
        }
        drawLegend(g, mapView);
        g.dispose();
    }

    private void drawLegend(Graphics2D g, MapView mapView) {
        if (mapView.getWidth() < 220 || mapView.getHeight() < 120) {
            return;
        }

        String title = I18n.tr("Completeness");
        String complete = completeLegendLabel();
        String incomplete = missingLegendLabel();
        String noAddressData = I18n.tr("No Address Data");

        int contentRows = 3;
        int legendHeight = LEGEND_PADDING * 2 + LEGEND_ROW_HEIGHT + (contentRows * LEGEND_ROW_HEIGHT);
        int legendWidth = Math.max(
                250,
                Math.max(
                        g.getFontMetrics().stringWidth(incomplete),
                        g.getFontMetrics().stringWidth(title)
                ) + 60
        );
        int legendX = Math.max(8, mapView.getWidth() - legendWidth - 10);
        int legendY = 10;

        g.setColor(LEGEND_BACKGROUND_COLOR);
        g.fillRoundRect(legendX, legendY, legendWidth, legendHeight, 8, 8);

        int textBaseX = legendX + LEGEND_PADDING;
        int rowY = legendY + LEGEND_PADDING + 12;
        g.setColor(Color.BLACK);
        g.drawString(title, textBaseX, rowY);

        rowY += LEGEND_ROW_HEIGHT;
        drawLegendRow(g, textBaseX, rowY, ADDRESSED_FILL_COLOR, complete);

        rowY += LEGEND_ROW_HEIGHT;
        drawLegendRow(g, textBaseX, rowY, UNADDRESSED_FILL_COLOR, incomplete);

        rowY += LEGEND_ROW_HEIGHT;
        drawLegendRow(g, textBaseX, rowY, NO_ADDRESS_DATA_COLOR, noAddressData);
    }

    private void drawLegendRow(Graphics2D g, int textBaseX, int rowY, Color swatchColor, String label) {
        int swatchY = rowY - LEGEND_SWATCH_SIZE + 3;
        g.setColor(swatchColor);
        g.fillRect(textBaseX, swatchY, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);
        g.setColor(Color.BLACK);
        g.drawString(label, textBaseX + LEGEND_SWATCH_SIZE + 6, rowY);
    }

    private void drawPrimitive(Graphics2D g, MapView mapView, BuildingOverviewCollector.BuildingOverviewEntry entry) {
        OsmPrimitive primitive = entry.getPrimitive();
        if (primitive instanceof Way) {
            drawWay(
                    g,
                    mapView,
                    (Way) primitive,
                    entry.hasNoAddressData(),
                    entry.hasMissingStreet(),
                    entry.hasMissingPostcode(),
                    entry.hasMissingHouseNumber(),
                    entry.hasMissingCity(),
                    entry.hasMissingRequiredAddressFields()
            );
            return;
        }
        if (primitive instanceof Relation) {
            drawRelation(
                    g,
                    mapView,
                    (Relation) primitive,
                    entry.hasNoAddressData(),
                    entry.hasMissingStreet(),
                    entry.hasMissingPostcode(),
                    entry.hasMissingHouseNumber(),
                    entry.hasMissingCity(),
                    entry.hasMissingRequiredAddressFields()
            );
        }
    }

    private void drawRelation(Graphics2D g, MapView mapView, Relation relation,
            boolean hasNoAddressData,
            boolean hasMissingStreet,
            boolean hasMissingPostcode,
            boolean hasMissingHouseNumber,
            boolean hasMissingCity,
            boolean hasMissingRequiredAddressFields) {
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
            drawWay(
                    g,
                    mapView,
                    member.getWay(),
                    hasNoAddressData,
                    hasMissingStreet,
                    hasMissingPostcode,
                    hasMissingHouseNumber,
                    hasMissingCity,
                    hasMissingRequiredAddressFields
            );
        }
    }

    private void drawWay(Graphics2D g, MapView mapView, Way way,
            boolean hasNoAddressData,
            boolean hasMissingStreet,
            boolean hasMissingPostcode,
            boolean hasMissingHouseNumber,
            boolean hasMissingCity,
            boolean hasMissingRequiredAddressFields) {
        Path2D polygon = buildScreenPolygon(mapView, way);
        if (polygon == null) {
            return;
        }

        Color fillColor = resolveFillColor(
                hasNoAddressData,
                hasMissingStreet,
                hasMissingPostcode,
                hasMissingHouseNumber,
                hasMissingCity,
                hasMissingRequiredAddressFields
        );
        if (fillColor == null) {
            return;
        }
        g.setColor(fillColor);
        g.fill(polygon);
    }

    private Color resolveFillColor(
            boolean hasNoAddressData,
            boolean hasMissingStreet,
            boolean hasMissingPostcode,
            boolean hasMissingHouseNumber,
            boolean hasMissingCity,
            boolean hasMissingRequiredAddressFields) {
        if (hasNoAddressData) {
            return NO_ADDRESS_DATA_COLOR;
        }
        if (hasSelectedMissingField(
                hasMissingStreet,
                hasMissingPostcode,
                hasMissingHouseNumber,
                hasMissingCity
        )) {
            return UNADDRESSED_FILL_COLOR;
        }
        if (!hasMissingRequiredAddressFields) {
            return ADDRESSED_FILL_COLOR;
        }
        return null;
    }

    private boolean hasSelectedMissingField(boolean hasMissingStreet, boolean hasMissingPostcode,
            boolean hasMissingHouseNumber, boolean hasMissingCity) {
        switch (missingField) {
            case ALL:
                return hasMissingStreet || hasMissingPostcode || hasMissingHouseNumber || hasMissingCity;
            case STREET:
                return hasMissingStreet;
            case HOUSE_NUMBER:
                return hasMissingHouseNumber;
            case CITY:
                return hasMissingCity;
            case POSTCODE:
            default:
                return hasMissingPostcode;
        }
    }

    private String completeLegendLabel() {
        switch (missingField) {
            case ALL:
                return I18n.tr("All address keys present");
            case STREET:
                return I18n.tr("Street present");
            case HOUSE_NUMBER:
                return I18n.tr("House number present");
            case CITY:
                return I18n.tr("City present");
            case POSTCODE:
            default:
                return I18n.tr("Postcode present");
        }
    }

    private String missingLegendLabel() {
        switch (missingField) {
            case ALL:
                return I18n.tr("Address incomplete");
            case STREET:
                return I18n.tr("Street missing");
            case HOUSE_NUMBER:
                return I18n.tr("House number missing");
            case CITY:
                return I18n.tr("City missing");
            case POSTCODE:
            default:
                return I18n.tr("Postcode missing");
        }
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
        if (missingField == MissingField.ALL) {
            return I18n.tr("Completeness overview (complete / incomplete / no address data)");
        }
        return I18n.tr("Completeness overview (complete / selected missing field / no address data)");
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
        return I18n.tr("Minimum building area: {0}", BuildingOverviewCollector.MIN_BUILDING_AREA);
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
