package org.openstreetmap.josm.plugins.quickaddressfill;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

final class BuildingResolver {

    static final String PREF_RELATION_SCAN_LIMIT = "quickaddressfill.streetmode.relationScanLimit";
    static final String PREF_WAY_SCAN_LIMIT = "quickaddressfill.streetmode.wayScanLimit";
    static final int DEFAULT_RELATION_SCAN_CANDIDATES = 3000;
    static final int DEFAULT_WAY_SCAN_CANDIDATES = 5000;

    private static final int MIN_SCAN_CANDIDATES = 100;
    private static final int MAX_SCAN_CANDIDATES = 100_000;
    private static final Set<String> WARNED_INVALID_LIMIT_KEYS = new HashSet<>();

    static final class BuildingResolutionResult {
        private final OsmPrimitive building;
        private final String source;
        private final int nearestCandidates;
        private final int relationCandidatesChecked;
        private final int wayCandidatesChecked;
        private final int relationScanLimit;
        private final int wayScanLimit;
        private final boolean relationLimitReached;
        private final boolean wayLimitReached;

        BuildingResolutionResult(OsmPrimitive building, String source, int nearestCandidates,
                int relationCandidatesChecked, int wayCandidatesChecked, int relationScanLimit,
                int wayScanLimit, boolean relationLimitReached, boolean wayLimitReached) {
            this.building = building;
            this.source = source;
            this.nearestCandidates = nearestCandidates;
            this.relationCandidatesChecked = relationCandidatesChecked;
            this.wayCandidatesChecked = wayCandidatesChecked;
            this.relationScanLimit = relationScanLimit;
            this.wayScanLimit = wayScanLimit;
            this.relationLimitReached = relationLimitReached;
            this.wayLimitReached = wayLimitReached;
        }

        OsmPrimitive getBuilding() {
            return building;
        }

        String getSource() {
            return source;
        }

        int getNearestCandidates() {
            return nearestCandidates;
        }

        int getRelationCandidatesChecked() {
            return relationCandidatesChecked;
        }

        int getWayCandidatesChecked() {
            return wayCandidatesChecked;
        }

        int getRelationScanLimit() {
            return relationScanLimit;
        }

        int getWayScanLimit() {
            return wayScanLimit;
        }

        boolean isRelationLimitReached() {
            return relationLimitReached;
        }

        boolean isWayLimitReached() {
            return wayLimitReached;
        }

        static BuildingResolutionResult notEvaluated() {
            return new BuildingResolutionResult(null, "not-evaluated", 0, 0, 0,
                    getConfiguredRelationScanLimit(), getConfiguredWayScanLimit(), false, false);
        }
    }

    BuildingResolutionResult resolveAtClick(MapFrame map, MouseEvent e) {
        if (map == null || map.mapView == null) {
            return new BuildingResolutionResult(null, "map-unavailable", 0, 0, 0,
                    getConfiguredRelationScanLimit(), getConfiguredWayScanLimit(), false, false);
        }

        List<OsmPrimitive> nearby = map.mapView.getAllNearest(e.getPoint(), this::isBuildingOrBuildingOutlineCandidate);
        int nearestCandidates = nearby == null ? 0 : nearby.size();
        OsmPrimitive building = chooseBuilding(nearby);
        if (building != null) {
            return new BuildingResolutionResult(building, "nearest-hit", nearestCandidates, 0, 0,
                    getConfiguredRelationScanLimit(), getConfiguredWayScanLimit(), false, false);
        }

        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            return new BuildingResolutionResult(null, "no-dataset", nearestCandidates, 0, 0,
                    getConfiguredRelationScanLimit(), getConfiguredWayScanLimit(), false, false);
        }

        RelationScanResult relationResult = findRelationContainingClick(dataSet, map, e);
        if (relationResult.relation != null) {
            return new BuildingResolutionResult(relationResult.relation, "relation-fallback", nearestCandidates,
                    relationResult.checked, 0, relationResult.scanLimit, getConfiguredWayScanLimit(),
                    relationResult.limitReached, false);
        }

        WayScanResult wayResult = findWayContainingClick(dataSet, map, e);
        if (wayResult.way != null) {
            return new BuildingResolutionResult(wayResult.way, "way-fallback", nearestCandidates,
                    relationResult.checked, wayResult.checked, relationResult.scanLimit, wayResult.scanLimit,
                    relationResult.limitReached, wayResult.limitReached);
        }

        return new BuildingResolutionResult(null, "no-hit", nearestCandidates,
                relationResult.checked, wayResult.checked, relationResult.scanLimit, wayResult.scanLimit,
                relationResult.limitReached, wayResult.limitReached);
    }

    static int getConfiguredRelationScanLimit() {
        return readConfiguredScanLimit(PREF_RELATION_SCAN_LIMIT, DEFAULT_RELATION_SCAN_CANDIDATES);
    }

    static int getConfiguredWayScanLimit() {
        return readConfiguredScanLimit(PREF_WAY_SCAN_LIMIT, DEFAULT_WAY_SCAN_CANDIDATES);
    }

    private static int readConfiguredScanLimit(String key, int defaultValue) {
        if (Config.getPref() == null) {
            return defaultValue;
        }

        String rawValue = Config.getPref().get(key, "");
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isEmpty()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(normalized);
            if (value < MIN_SCAN_CANDIDATES || value > MAX_SCAN_CANDIDATES) {
                warnInvalidScanLimitOnce(key, normalized, defaultValue);
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException ex) {
            warnInvalidScanLimitOnce(key, normalized, defaultValue);
            Logging.debug(ex);
            return defaultValue;
        }
    }

    private static void warnInvalidScanLimitOnce(String key, String value, int defaultValue) {
        if (!WARNED_INVALID_LIMIT_KEYS.add(key)) {
            return;
        }
        Logging.warn(
                "QuickAddressFill BuildingResolver: invalid scan limit preference {0}={1}, falling back to default {2} (allowed range {3}-{4}).",
                key,
                value,
                defaultValue,
                MIN_SCAN_CANDIDATES,
                MAX_SCAN_CANDIDATES
        );
    }

    private RelationScanResult findRelationContainingClick(DataSet dataSet, MapFrame map, MouseEvent e) {
        if (dataSet == null || map == null || map.mapView == null || map.mapView.getRealBounds() == null) {
            return new RelationScanResult(null, 0, false, getConfiguredRelationScanLimit());
        }
        int relationScanLimit = getConfiguredRelationScanLimit();

        LatLon clickLatLon = map.mapView.getLatLon(e.getX(), e.getY());
        if (clickLatLon == null) {
            return new RelationScanResult(null, 0, false, relationScanLimit);
        }

        int scanned = 0;
        for (Relation relation : dataSet.searchRelations(map.mapView.getRealBounds().toBBox())) {
            scanned++;
            if (scanned > relationScanLimit) {
                return new RelationScanResult(null, relationScanLimit, true, relationScanLimit);
            }
            if (!isBuildingCandidate(relation)) {
                continue;
            }
            for (RelationMember member : relation.getMembers()) {
                String role = member.getRole();
                if (member.isWay() && (role == null || role.isEmpty() || "outer".equals(role))
                        && containsPoint(member.getWay(), map, e.getPoint(), clickLatLon)) {
                    return new RelationScanResult(relation, scanned, false, relationScanLimit);
                }
            }
        }
        return new RelationScanResult(null, scanned, false, relationScanLimit);
    }

    private WayScanResult findWayContainingClick(DataSet dataSet, MapFrame map, MouseEvent e) {
        if (dataSet == null || map == null || map.mapView == null || map.mapView.getRealBounds() == null) {
            return new WayScanResult(null, 0, false, getConfiguredWayScanLimit());
        }
        int wayScanLimit = getConfiguredWayScanLimit();

        LatLon clickLatLon = map.mapView.getLatLon(e.getX(), e.getY());
        if (clickLatLon == null) {
            return new WayScanResult(null, 0, false, wayScanLimit);
        }

        Way best = null;
        double bestArea = Double.MAX_VALUE;
        int scanned = 0;
        for (Way way : dataSet.searchWays(map.mapView.getRealBounds().toBBox())) {
            scanned++;
            if (scanned > wayScanLimit) {
                return new WayScanResult(best, wayScanLimit, true, wayScanLimit);
            }
            if (!isBuildingOrBuildingOutlineCandidate(way)) {
                continue;
            }
            if (!containsPoint(way, map, e.getPoint(), clickLatLon)) {
                continue;
            }

            double area = way.getBBox().area();
            if (area < bestArea) {
                best = way;
                bestArea = area;
            }
        }
        return new WayScanResult(best, scanned, false, wayScanLimit);
    }

    private boolean containsPoint(Way way, MapFrame map, Point clickPoint, LatLon clickLatLon) {
        if (way == null || map == null || map.mapView == null || !way.isClosed() || way.getNodesCount() < 4) {
            return false;
        }

        if (clickLatLon == null || !way.getBBox().bounds(clickLatLon)) {
            return false;
        }

        Polygon polygon = new Polygon();
        for (Node node : way.getNodes()) {
            if (node == null || node.getCoor() == null) {
                return false;
            }
            Point point = map.mapView.getPoint(node);
            if (point == null) {
                return false;
            }
            polygon.addPoint(point.x, point.y);
        }
        return polygon.contains(clickPoint);
    }

    private boolean isBuildingCandidate(OsmPrimitive primitive) {
        return primitive != null
                && primitive.isUsable()
                && primitive.hasTag("building")
                && (primitive instanceof Way || primitive instanceof Relation);
    }

    private boolean isBuildingOrBuildingOutlineCandidate(OsmPrimitive primitive) {
        if (isBuildingCandidate(primitive)) {
            return true;
        }
        return primitive instanceof Way && isWayInBuildingRelation((Way) primitive);
    }

    private boolean isWayInBuildingRelation(Way way) {
        if (way == null || !way.isUsable()) {
            return false;
        }
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (referrer instanceof Relation && referrer.isUsable() && referrer.hasTag("building")) {
                return true;
            }
        }
        return false;
    }

    private Relation getBuildingRelationForWay(Way way) {
        if (way == null || !way.isUsable()) {
            return null;
        }
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (referrer instanceof Relation && referrer.isUsable() && referrer.hasTag("building")) {
                return (Relation) referrer;
            }
        }
        return null;
    }

    private OsmPrimitive chooseBuilding(List<OsmPrimitive> nearby) {
        if (nearby == null || nearby.isEmpty()) {
            return null;
        }

        // Prefer way buildings so selection feedback is immediately visible on map.
        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Way && primitive.hasTag("building")) {
                return primitive;
            }
        }

        // If only an untagged outer way is hit, resolve to its building relation.
        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Way) {
                Relation relation = getBuildingRelationForWay((Way) primitive);
                if (relation != null) {
                    return relation;
                }
            }
        }

        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Relation && primitive.hasTag("building")) {
                return primitive;
            }
        }
        return null;
    }

    private static final class RelationScanResult {
        private final Relation relation;
        private final int checked;
        private final boolean limitReached;
        private final int scanLimit;

        private RelationScanResult(Relation relation, int checked, boolean limitReached, int scanLimit) {
            this.relation = relation;
            this.checked = checked;
            this.limitReached = limitReached;
            this.scanLimit = scanLimit;
        }
    }

    private static final class WayScanResult {
        private final Way way;
        private final int checked;
        private final boolean limitReached;
        private final int scanLimit;

        private WayScanResult(Way way, int checked, boolean limitReached, int scanLimit) {
            this.way = way;
            this.checked = checked;
            this.limitReached = limitReached;
            this.scanLimit = scanLimit;
        }
    }
}

