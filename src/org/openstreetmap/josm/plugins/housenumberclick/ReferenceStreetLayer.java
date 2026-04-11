package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;

final class ReferenceStreetLayer extends Layer {

    private static final Color REFERENCE_COLOR = new Color(220, 35, 35);
    private static final float STROKE_WIDTH = 14.0f;
    private static final float[] DASH_PATTERN = new float[] {18.0f, 12.0f};

    private String referenceStreetName = "";
    private final List<List<LatLon>> referenceStreetPolylines = new ArrayList<>();

    ReferenceStreetLayer() {
        super(I18n.tr("Street reference"));
    }

    void updateReferenceStreet(String streetName, DataSet referenceDataSet) {
        referenceStreetName = normalize(streetName);
        referenceStreetPolylines.clear();
        if (referenceDataSet == null || referenceStreetName.isEmpty()) {
            invalidate();
            return;
        }

        for (Way way : referenceDataSet.getWays()) {
            if (!isReferenceStreetWay(way)) {
                continue;
            }
            List<LatLon> polyline = new ArrayList<>();
            for (Node node : way.getNodes()) {
                if (node == null || !node.isUsable() || node.getCoor() == null) {
                    continue;
                }
                polyline.add(node.getCoor());
            }
            if (polyline.size() >= 2) {
                referenceStreetPolylines.add(polyline);
            }
        }
        invalidate();
    }

    boolean hasStreet(String streetName) {
        return !referenceStreetPolylines.isEmpty() && referenceStreetName.equalsIgnoreCase(normalize(streetName));
    }

    @Override
    public void paint(Graphics2D graphics, MapView mapView, Bounds bounds) {
        if (mapView == null || referenceStreetPolylines.isEmpty()) {
            return;
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        Collection<Bounds> loadedBounds = editDataSet != null ? editDataSet.getDataSourceBounds() : List.of();

        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(REFERENCE_COLOR);
        g.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, DASH_PATTERN, 0f));

        for (List<LatLon> polyline : referenceStreetPolylines) {
            for (int i = 1; i < polyline.size(); i++) {
                LatLon start = polyline.get(i - 1);
                LatLon end = polyline.get(i);
                if (isSegmentInsideAnyLoadedBounds(start, end, loadedBounds)) {
                    continue;
                }
                Point startPoint = mapView.getPoint(start);
                Point endPoint = mapView.getPoint(end);
                if (startPoint == null || endPoint == null) {
                    continue;
                }
                g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
            }
        }
        g.dispose();
    }

    private boolean isSegmentInsideAnyLoadedBounds(LatLon start, LatLon end, Collection<Bounds> loadedBounds) {
        if (start == null || end == null || loadedBounds == null || loadedBounds.isEmpty()) {
            return false;
        }

        LatLon mid = new LatLon((start.lat() + end.lat()) / 2.0, (start.lon() + end.lon()) / 2.0);
        for (Bounds bounds : loadedBounds) {
            if (bounds == null) {
                continue;
            }
            if (contains(bounds, start) && contains(bounds, end)) {
                return true;
            }
            if (contains(bounds, mid)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(Bounds bounds, LatLon point) {
        return point != null
                && point.lat() >= bounds.getMinLat() && point.lat() <= bounds.getMaxLat()
                && point.lon() >= bounds.getMinLon() && point.lon() <= bounds.getMaxLon();
    }

    private boolean isReferenceStreetWay(Way way) {
        return way != null
                && way.isUsable()
                && way.hasKey("highway")
                && normalize(way.get("name")).equalsIgnoreCase(referenceStreetName);
    }

    @Override
    public Icon getIcon() {
        Icon icon = ImageProvider.get("dialogs", "search");
        return icon != null ? icon : ImageProvider.get("housenumberclick");
    }

    @Override
    public String getToolTipText() {
        return I18n.tr("Reference geometry for selected street");
    }

    @Override
    public void mergeFrom(Layer from) {
        // Reference layer is transient and not mergeable.
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor visitor) {
        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (visitor != null && editDataSet != null) {
            visitor.visit(editDataSet.getDataSourceBoundingBox());
        }
    }

    @Override
    public Object getInfoComponent() {
        return I18n.tr("Street: {0}", referenceStreetName.isEmpty() ? I18n.tr("(none)") : referenceStreetName);
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

