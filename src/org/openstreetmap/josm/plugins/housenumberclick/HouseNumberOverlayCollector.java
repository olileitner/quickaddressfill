package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.tools.Geometry;

final class HouseNumberOverlayCollector {

    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*([^\\d].*)?$");

    List<HouseNumberOverlayEntry> collect(DataSet dataSet, MapView mapView, String selectedStreet) {
        String normalizedStreet = normalize(selectedStreet);
        if (dataSet == null || mapView == null || normalizedStreet.isEmpty()) {
            return new ArrayList<>();
        }

        Bounds visibleBounds = mapView.getRealBounds();
        List<HouseNumberOverlayEntry> entries = new ArrayList<>();
        int stableIndex = 0;

        for (Way way : dataSet.getWays()) {
            HouseNumberOverlayEntry entry = buildEntry(way, normalizedStreet, visibleBounds, stableIndex);
            if (entry != null) {
                entries.add(entry);
                stableIndex++;
            }
        }

        for (Relation relation : dataSet.getRelations()) {
            HouseNumberOverlayEntry entry = buildEntry(relation, normalizedStreet, visibleBounds, stableIndex);
            if (entry != null) {
                entries.add(entry);
                stableIndex++;
            }
        }

        entries.sort(createComparator());
        return entries;
    }

    private Comparator<HouseNumberOverlayEntry> createComparator() {
        return Comparator
                .comparingInt(HouseNumberOverlayEntry::getNumberPart)
                .thenComparing(HouseNumberOverlayEntry::getSuffixPart, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HouseNumberOverlayEntry::getHouseNumber, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(HouseNumberOverlayEntry::getStableIndex);
    }

    private HouseNumberOverlayEntry buildEntry(OsmPrimitive primitive, String selectedStreet, Bounds visibleBounds, int stableIndex) {
        if (!isMatchingAddressedBuilding(primitive, selectedStreet)) {
            return null;
        }

        if (!isVisibleInCurrentView(primitive, visibleBounds)) {
            return null;
        }

        EastNorth labelPoint = resolveLabelPoint(primitive);
        if (labelPoint == null) {
            return null;
        }

        String houseNumber = normalize(primitive.get("addr:housenumber"));
        ParsedHouseNumber parsedHouseNumber = parseHouseNumber(houseNumber);
        return new HouseNumberOverlayEntry(
                primitive,
                houseNumber,
                parsedHouseNumber.numberPart,
                parsedHouseNumber.suffixPart,
                labelPoint,
                stableIndex
        );
    }

    private boolean isMatchingAddressedBuilding(OsmPrimitive primitive, String selectedStreet) {
        if (primitive == null || !primitive.isUsable()) {
            return false;
        }

        boolean building = primitive.hasKey("building");
        if (!building) {
            return false;
        }

        if (primitive instanceof Way && !((Way) primitive).isClosed()) {
            return false;
        }

        if (primitive instanceof Relation) {
            String relationType = normalize(primitive.get("type"));
            if (!relationType.isEmpty() && !"multipolygon".equals(relationType)) {
                return false;
            }
        }

        String houseNumber = normalize(primitive.get("addr:housenumber"));
        String street = normalize(primitive.get("addr:street"));
        return !houseNumber.isEmpty() && selectedStreet.equals(street);
    }

    private boolean isVisibleInCurrentView(OsmPrimitive primitive, Bounds visibleBounds) {
        if (visibleBounds == null) {
            return true;
        }

        BBox bbox = primitive.getBBox();
        if (bbox == null || !bbox.isValid()) {
            return false;
        }
        BBox visibleBBox = visibleBounds.toBBox();
        return visibleBBox != null && bbox.intersects(visibleBBox);
    }

    private EastNorth resolveLabelPoint(OsmPrimitive primitive) {
        Area area = Geometry.getAreaEastNorth(primitive);
        if (area == null || area.isEmpty()) {
            return null;
        }

        Rectangle2D bounds = area.getBounds2D();
        if (bounds == null || bounds.isEmpty()) {
            return null;
        }

        List<EastNorth> candidates = new ArrayList<>();
        addBoundingBoxCandidates(candidates, primitive, bounds);
        addGeometryCandidates(candidates, primitive);

        EastNorth bestInside = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (EastNorth candidate : candidates) {
            if (candidate == null || !area.contains(candidate.east(), candidate.north())) {
                continue;
            }
            double score = interiorScore(candidate, bounds);
            if (score > bestScore) {
                bestScore = score;
                bestInside = candidate;
            }
        }

        return bestInside;
    }

    private void addBoundingBoxCandidates(List<EastNorth> candidates, OsmPrimitive primitive, Rectangle2D areaBounds) {
        BBox primitiveBox = primitive.getBBox();
        Projection projection = ProjectionRegistry.getProjection();
        if (primitiveBox != null && primitiveBox.isValid() && projection != null) {
            LatLon centerLatLon = primitiveBox.getCenter();
            candidates.add(projection.latlon2eastNorth(centerLatLon));
        }

        double cx = areaBounds.getCenterX();
        double cy = areaBounds.getCenterY();
        double dx = areaBounds.getWidth() * 0.25;
        double dy = areaBounds.getHeight() * 0.25;

        candidates.add(new EastNorth(cx, cy));
        candidates.add(new EastNorth(cx + dx, cy));
        candidates.add(new EastNorth(cx - dx, cy));
        candidates.add(new EastNorth(cx, cy + dy));
        candidates.add(new EastNorth(cx, cy - dy));
        candidates.add(new EastNorth(cx + dx, cy + dy));
        candidates.add(new EastNorth(cx + dx, cy - dy));
        candidates.add(new EastNorth(cx - dx, cy + dy));
        candidates.add(new EastNorth(cx - dx, cy - dy));
    }

    private void addGeometryCandidates(List<EastNorth> candidates, OsmPrimitive primitive) {
        if (primitive instanceof Way) {
            addWayGeometryCandidates(candidates, (Way) primitive);
            return;
        }

        if (!(primitive instanceof Relation)) {
            return;
        }

        Relation relation = (Relation) primitive;
        for (RelationMember member : relation.getMembers()) {
            String role = member.getRole();
            boolean isOuter = role == null || role.isEmpty() || "outer".equals(role);
            if (!isOuter || !member.isWay()) {
                continue;
            }
            Way way = member.getWay();
            if (way == null || !way.isUsable()) {
                continue;
            }
            addWayGeometryCandidates(candidates, way);
        }
    }

    private void addWayGeometryCandidates(List<EastNorth> candidates, Way way) {
        if (way == null || !way.isClosed()) {
            return;
        }

        List<Node> nodes = way.getNodes();
        if (nodes.size() < 3) {
            return;
        }

        EastNorth centroid = Geometry.getCentroid(nodes);
        if (centroid != null) {
            candidates.add(centroid);
        }

        EastNorth center = Geometry.getCenter(nodes);
        if (center != null) {
            candidates.add(center);
        }
    }

    private double interiorScore(EastNorth point, Rectangle2D bounds) {
        double left = point.east() - bounds.getMinX();
        double right = bounds.getMaxX() - point.east();
        double top = bounds.getMaxY() - point.north();
        double bottom = point.north() - bounds.getMinY();
        return Math.min(Math.min(left, right), Math.min(top, bottom));
    }

    private ParsedHouseNumber parseHouseNumber(String houseNumber) {
        String normalized = normalize(houseNumber);
        Matcher matcher = HOUSE_NUMBER_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return new ParsedHouseNumber(Integer.MAX_VALUE, normalized.toLowerCase(Locale.ROOT));
        }

        int numberPart;
        try {
            numberPart = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            numberPart = Integer.MAX_VALUE;
        }

        String suffix = normalize(matcher.group(2)).toLowerCase(Locale.ROOT);
        return new ParsedHouseNumber(numberPart, suffix);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ParsedHouseNumber {
        private final int numberPart;
        private final String suffixPart;

        ParsedHouseNumber(int numberPart, String suffixPart) {
            this.numberPart = numberPart;
            this.suffixPart = suffixPart;
        }
    }
}


