package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.tools.Geometry;

/**
 * Collects read-only address carriers across building and non-building objects and links address nodes to buildings.
 */
final class AddressEntryCollector {

    private static final double ASSOCIATION_OUTLINE_TOLERANCE_METERS = 1.5;
    private static final double SPATIAL_CELL_SIZE_METERS = 120.0;

    List<AddressEntry> collect(DataSet dataSet) {
        if (dataSet == null) {
            return List.of();
        }

        Map<Long, OsmPrimitive> canonicalBuildings = collectCanonicalBuildings(dataSet);
        Map<Long, BuildingGeometry> buildingGeometries = buildBuildingGeometries(canonicalBuildings.values());
        BuildingSpatialIndex spatialIndex = new BuildingSpatialIndex(buildingGeometries.values());

        List<AddressEntry> entries = new ArrayList<>();
        for (OsmPrimitive building : canonicalBuildings.values()) {
            AddressEntry buildingEntry = createBuildingEntry(building);
            if (buildingEntry != null) {
                entries.add(buildingEntry);
            }
        }

        for (Node node : dataSet.getNodes()) {
            AddressEntry nodeEntry = createNodeEntry(node, buildingGeometries, spatialIndex);
            if (nodeEntry != null) {
                entries.add(nodeEntry);
            }
        }

        for (Way way : dataSet.getWays()) {
            AddressEntry wayEntry = createOtherObjectEntry(way);
            if (wayEntry != null) {
                entries.add(wayEntry);
            }
        }
        for (Relation relation : dataSet.getRelations()) {
            AddressEntry relationEntry = createOtherObjectEntry(relation);
            if (relationEntry != null) {
                entries.add(relationEntry);
            }
        }

        return entries;
    }

    private Map<Long, OsmPrimitive> collectCanonicalBuildings(DataSet dataSet) {
        Map<Long, OsmPrimitive> canonicalBuildings = new LinkedHashMap<>();
        for (Way way : dataSet.getWays()) {
            collectCanonicalBuildingPrimitive(canonicalBuildings, way);
        }
        for (Relation relation : dataSet.getRelations()) {
            collectCanonicalBuildingPrimitive(canonicalBuildings, relation);
        }
        return canonicalBuildings;
    }

    private void collectCanonicalBuildingPrimitive(Map<Long, OsmPrimitive> target, OsmPrimitive primitive) {
        if (!AddressedBuildingMatcher.isBuildingGeometry(primitive)) {
            return;
        }
        OsmPrimitive canonical = resolveCanonicalBuilding(primitive);
        if (canonical == null || !AddressedBuildingMatcher.isBuildingGeometry(canonical)) {
            return;
        }
        target.putIfAbsent(canonical.getUniqueId(), canonical);
    }

    private OsmPrimitive resolveCanonicalBuilding(OsmPrimitive primitive) {
        if (!(primitive instanceof Way) || !primitive.isUsable()) {
            return primitive;
        }
        Way way = (Way) primitive;
        Relation canonicalRelation = findBuildingOuterMultipolygonRelation(way);
        return canonicalRelation != null ? canonicalRelation : primitive;
    }

    private Relation findBuildingOuterMultipolygonRelation(Way way) {
        if (way == null || !way.isUsable()) {
            return null;
        }
        Relation best = null;
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (!(referrer instanceof Relation relation) || !relation.isUsable()) {
                continue;
            }
            if (!relation.hasTag("type", "multipolygon") || !relation.hasTag("building")) {
                continue;
            }
            for (RelationMember member : relation.getMembers()) {
                if (member == null || !member.isWay() || member.getWay() != way) {
                    continue;
                }
                String role = normalize(member.getRole());
                if (!role.isEmpty() && !"outer".equals(role)) {
                    continue;
                }
                if (best == null || relation.getUniqueId() < best.getUniqueId()) {
                    best = relation;
                }
            }
        }
        return best;
    }

    private Map<Long, BuildingGeometry> buildBuildingGeometries(Iterable<OsmPrimitive> buildings) {
        Map<Long, BuildingGeometry> geometries = new HashMap<>();
        if (ProjectionRegistry.getProjection() == null) {
            return geometries;
        }
        if (buildings == null) {
            return geometries;
        }
        for (OsmPrimitive building : buildings) {
            if (building == null || !building.isUsable()) {
                continue;
            }
            Area area = Geometry.getAreaEastNorth(building);
            if (area == null || area.isEmpty()) {
                continue;
            }
            Rectangle2D bounds = area.getBounds2D();
            if (bounds == null || bounds.isEmpty()) {
                continue;
            }
            List<Way> outerWays = resolveOuterWays(building);
            geometries.put(building.getUniqueId(), new BuildingGeometry(building, area, bounds, outerWays));
        }
        return geometries;
    }

    private List<Way> resolveOuterWays(OsmPrimitive building) {
        List<Way> outers = new ArrayList<>();
        if (building instanceof Way way) {
            if (way.isUsable() && way.isClosed()) {
                outers.add(way);
            }
            return outers;
        }
        if (!(building instanceof Relation relation) || !relation.isUsable()) {
            return outers;
        }
        for (RelationMember member : relation.getMembers()) {
            if (member == null || !member.isWay()) {
                continue;
            }
            String role = normalize(member.getRole());
            if (!role.isEmpty() && !"outer".equals(role)) {
                continue;
            }
            Way outerWay = member.getWay();
            if (outerWay != null && outerWay.isUsable() && outerWay.isClosed()) {
                outers.add(outerWay);
            }
        }
        return outers;
    }

    private AddressEntry createBuildingEntry(OsmPrimitive building) {
        if (building == null || !building.isUsable()) {
            return null;
        }
        String houseNumber = normalize(building.get("addr:housenumber"));
        if (houseNumber.isEmpty()) {
            return null;
        }
        return new AddressEntry(
                building,
                building,
                AddressEntry.CarrierType.BUILDING,
                building,
                houseNumber,
                resolveStreetWithFallback(building, building),
                firstNonEmpty(building.get("addr:postcode"), building.get("addr:postcode")),
                firstNonEmpty(building.get("addr:city"), building.get("addr:city")),
                firstNonEmpty(building.get("addr:country"), building.get("addr:country")),
                resolveLabelPoint(building)
        );
    }

    private AddressEntry createNodeEntry(Node node, Map<Long, BuildingGeometry> buildingGeometries, BuildingSpatialIndex spatialIndex) {
        if (node == null || !node.isUsable()) {
            return null;
        }
        String houseNumber = normalize(node.get("addr:housenumber"));
        if (houseNumber.isEmpty()) {
            return null;
        }
        OsmPrimitive associatedBuilding = resolveAssociatedBuilding(node, buildingGeometries, spatialIndex);
        boolean entrance = !normalize(node.get("entrance")).isEmpty();
        return new AddressEntry(
                node,
                node,
                entrance ? AddressEntry.CarrierType.ENTRANCE_NODE : AddressEntry.CarrierType.ADDRESS_NODE,
                associatedBuilding,
                houseNumber,
                resolveStreetWithFallback(node, associatedBuilding),
                resolveFieldWithBuildingFallback(node, associatedBuilding, "addr:postcode"),
                resolveFieldWithBuildingFallback(node, associatedBuilding, "addr:city"),
                resolveFieldWithBuildingFallback(node, associatedBuilding, "addr:country"),
                resolveNodeEastNorth(node)
        );
    }

    private AddressEntry createOtherObjectEntry(OsmPrimitive primitive) {
        if (primitive == null || !primitive.isUsable()) {
            return null;
        }
        if (primitive instanceof Node) {
            return null;
        }
        if (AddressedBuildingMatcher.isBuildingGeometry(primitive)) {
            return null;
        }
        String houseNumber = normalize(primitive.get("addr:housenumber"));
        if (houseNumber.isEmpty()) {
            return null;
        }
        return new AddressEntry(
                primitive,
                primitive,
                AddressEntry.CarrierType.OTHER_OBJECT,
                null,
                houseNumber,
                resolveStreetWithFallback(primitive, null),
                normalize(primitive.get("addr:postcode")),
                normalize(primitive.get("addr:city")),
                normalize(primitive.get("addr:country")),
                resolveLabelPoint(primitive)
        );
    }

    private OsmPrimitive resolveAssociatedBuilding(Node node, Map<Long, BuildingGeometry> geometries,
            BuildingSpatialIndex spatialIndex) {
        OsmPrimitive byReferrer = resolveAssociatedBuildingFromReferrers(node);
        if (byReferrer != null) {
            return byReferrer;
        }
        EastNorth nodePoint = resolveNodeEastNorth(node);
        if (node == null || !node.isUsable() || nodePoint == null || spatialIndex == null) {
            return null;
        }

        BuildingGeometry best = null;
        double bestArea = Double.POSITIVE_INFINITY;
        for (BuildingGeometry candidate : spatialIndex.query(nodePoint)) {
            if (candidate == null) {
                continue;
            }
            if (!containsNode(candidate, node) && !isNearBuildingOutline(candidate, node)) {
                continue;
            }
            double area = candidate.bounds.getWidth() * candidate.bounds.getHeight();
            if (area < bestArea) {
                bestArea = area;
                best = candidate;
            }
        }
        return best == null ? null : best.primitive;
    }

    private OsmPrimitive resolveAssociatedBuildingFromReferrers(Node node) {
        if (node == null || !node.isUsable()) {
            return null;
        }
        Relation bestRelation = null;
        Way bestWay = null;
        for (OsmPrimitive referrer : node.getReferrers()) {
            if (referrer instanceof Way way && AddressedBuildingMatcher.isBuildingGeometry(way)) {
                if (bestWay == null || way.getUniqueId() < bestWay.getUniqueId()) {
                    bestWay = way;
                }
            }
            if (!(referrer instanceof Relation relation) || !relation.isUsable()) {
                continue;
            }
            if (!AddressedBuildingMatcher.isBuildingGeometry(relation)) {
                continue;
            }
            if (bestRelation == null || relation.getUniqueId() < bestRelation.getUniqueId()) {
                bestRelation = relation;
            }
        }
        return bestRelation != null ? bestRelation : bestWay;
    }

    private boolean containsNode(BuildingGeometry geometry, Node node) {
        EastNorth point = resolveNodeEastNorth(node);
        if (geometry == null || node == null || point == null) {
            return false;
        }
        if (!geometry.bounds.contains(point.east(), point.north())) {
            return false;
        }
        return geometry.area.contains(point.east(), point.north());
    }

    private boolean isNearBuildingOutline(BuildingGeometry geometry, Node node) {
        EastNorth point = resolveNodeEastNorth(node);
        if (geometry == null || node == null || point == null) {
            return false;
        }
        double toleranceSquared = ASSOCIATION_OUTLINE_TOLERANCE_METERS * ASSOCIATION_OUTLINE_TOLERANCE_METERS;
        for (Way outerWay : geometry.outerWays) {
            if (outerWay == null || !outerWay.isUsable()) {
                continue;
            }
            List<Node> nodes = outerWay.getNodes();
            for (int i = 1; i < nodes.size(); i++) {
                Node first = nodes.get(i - 1);
                Node second = nodes.get(i);
                if (first == null || second == null || first.getEastNorth() == null || second.getEastNorth() == null) {
                    continue;
                }
                double distanceSquared = distanceSquaredToSegment(point, first.getEastNorth(), second.getEastNorth());
                if (distanceSquared <= toleranceSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceSquaredToSegment(EastNorth point, EastNorth segmentStart, EastNorth segmentEnd) {
        double px = point.east();
        double py = point.north();
        double ax = segmentStart.east();
        double ay = segmentStart.north();
        double bx = segmentEnd.east();
        double by = segmentEnd.north();
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = (dx * dx) + (dy * dy);
        if (lengthSquared <= 0.0) {
            double ex = px - ax;
            double ey = py - ay;
            return (ex * ex) + (ey * ey);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double projectionX = ax + (t * dx);
        double projectionY = ay + (t * dy);
        double ex = px - projectionX;
        double ey = py - projectionY;
        return (ex * ex) + (ey * ey);
    }

    private EastNorth resolveLabelPoint(OsmPrimitive primitive) {
        if (primitive == null || !primitive.isUsable()) {
            return null;
        }
        if (primitive instanceof Node node) {
            return resolveNodeEastNorth(node);
        }

        if (ProjectionRegistry.getProjection() == null) {
            if (primitive.getBBox() == null || !primitive.getBBox().isValid()) {
                return null;
            }
            LatLon center = primitive.getBBox().getCenter();
            return center == null ? null : new EastNorth(center.lon(), center.lat());
        }

        Area area = Geometry.getAreaEastNorth(primitive);
        if (area != null && !area.isEmpty()) {
            Rectangle2D bounds = area.getBounds2D();
            if (bounds != null && !bounds.isEmpty()) {
                return new EastNorth(bounds.getCenterX(), bounds.getCenterY());
            }
        }

        if (primitive.getBBox() != null && primitive.getBBox().isValid() && ProjectionRegistry.getProjection() != null) {
            LatLon center = primitive.getBBox().getCenter();
            if (center != null) {
                return ProjectionRegistry.getProjection().latlon2eastNorth(center);
            }
        }
        return null;
    }

    private String resolveStreetWithFallback(OsmPrimitive primitive, OsmPrimitive associatedBuilding) {
        String street = normalize(primitive == null ? null : primitive.get("addr:street"));
        if (!street.isEmpty()) {
            return street;
        }
        String associatedStreet = resolveAssociatedStreetName(primitive);
        if (!associatedStreet.isEmpty()) {
            return associatedStreet;
        }
        if (associatedBuilding != null) {
            return normalize(associatedBuilding.get("addr:street"));
        }
        return "";
    }

    private EastNorth resolveNodeEastNorth(Node node) {
        if (node == null || !node.isUsable()) {
            return null;
        }
        if (ProjectionRegistry.getProjection() != null) {
            return node.getEastNorth();
        }
        LatLon coor = node.getCoor();
        if (coor == null) {
            return null;
        }
        // Fallback for projection-less test contexts; exact meter-space is not required there.
        return new EastNorth(coor.lon(), coor.lat());
    }

    private String resolveFieldWithBuildingFallback(OsmPrimitive primitive, OsmPrimitive associatedBuilding, String key) {
        String value = normalize(primitive == null ? null : primitive.get(key));
        if (!value.isEmpty()) {
            return value;
        }
        if (associatedBuilding != null) {
            return normalize(associatedBuilding.get(key));
        }
        return "";
    }

    private String resolveAssociatedStreetName(OsmPrimitive primitive) {
        if (primitive == null || !primitive.isUsable()) {
            return "";
        }
        for (OsmPrimitive referrer : primitive.getReferrers()) {
            if (!(referrer instanceof Relation relation) || !relation.isUsable()) {
                continue;
            }
            if (!relation.hasTag("type", "associatedStreet")) {
                continue;
            }
            if (!isAssociatedStreetMember(relation, primitive)) {
                continue;
            }
            String street = normalize(relation.get("name"));
            if (street.isEmpty()) {
                street = normalize(relation.get("addr:street"));
            }
            if (!street.isEmpty()) {
                return street;
            }
        }
        return "";
    }

    private boolean isAssociatedStreetMember(Relation relation, OsmPrimitive primitive) {
        if (relation == null || primitive == null) {
            return false;
        }
        for (RelationMember member : relation.getMembers()) {
            if (member == null || member.getMember() != primitive) {
                continue;
            }
            String role = normalize(member.getRole()).toLowerCase(Locale.ROOT);
            if (role.isEmpty() || "house".equals(role) || "address".equals(role) || "addr".equals(role)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonEmpty(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        if (!normalizedPrimary.isEmpty()) {
            return normalizedPrimary;
        }
        return normalize(fallback);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class BuildingGeometry {
        private final OsmPrimitive primitive;
        private final Area area;
        private final Rectangle2D bounds;
        private final List<Way> outerWays;

        private BuildingGeometry(OsmPrimitive primitive, Area area, Rectangle2D bounds, List<Way> outerWays) {
            this.primitive = primitive;
            this.area = area;
            this.bounds = bounds;
            this.outerWays = outerWays != null ? outerWays : List.of();
        }
    }

    /**
     * Lightweight EastNorth grid index to avoid full node x building scans for building association.
     */
    private static final class BuildingSpatialIndex {
        private final Map<String, Set<BuildingGeometry>> geometriesByCell = new HashMap<>();

        private BuildingSpatialIndex(Iterable<BuildingGeometry> geometries) {
            if (geometries == null) {
                return;
            }
            for (BuildingGeometry geometry : geometries) {
                if (geometry == null || geometry.bounds == null) {
                    continue;
                }
                int minX = toCell(geometry.bounds.getMinX());
                int maxX = toCell(geometry.bounds.getMaxX());
                int minY = toCell(geometry.bounds.getMinY());
                int maxY = toCell(geometry.bounds.getMaxY());
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        geometriesByCell.computeIfAbsent(cellKey(x, y), ignored -> new LinkedHashSet<>()).add(geometry);
                    }
                }
            }
        }

        private Set<BuildingGeometry> query(EastNorth point) {
            if (point == null) {
                return Set.of();
            }
            int cx = toCell(point.east());
            int cy = toCell(point.north());
            Set<BuildingGeometry> results = new LinkedHashSet<>();
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int y = cy - 1; y <= cy + 1; y++) {
                    Set<BuildingGeometry> cell = geometriesByCell.get(cellKey(x, y));
                    if (cell != null) {
                        results.addAll(cell);
                    }
                }
            }
            return results;
        }

        private int toCell(double value) {
            return (int) Math.floor(value / SPATIAL_CELL_SIZE_METERS);
        }

        private String cellKey(int x, int y) {
            return x + ":" + y;
        }
    }
}




