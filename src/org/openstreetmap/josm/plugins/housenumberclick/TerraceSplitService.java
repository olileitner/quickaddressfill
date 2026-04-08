package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;

final class TerraceSplitService {

    private static final double LINE_MARGIN_FALLBACK = 1e-5;
    private static final double EPSILON = 1e-9;

    private final SingleBuildingSplitService singleBuildingSplitService;
    private final CornerSnapService cornerSnapService;

    TerraceSplitService() {
        this(new SingleBuildingSplitService(), new CornerSnapService());
    }

    TerraceSplitService(SingleBuildingSplitService singleBuildingSplitService, CornerSnapService cornerSnapService) {
        this.singleBuildingSplitService = singleBuildingSplitService;
        this.cornerSnapService = cornerSnapService;
    }

    TerraceSplitResult splitBuildingIntoTerrace(
            DataSet dataSet,
            Way buildingWay,
            TerraceSplitRequest request,
            SplitContext context
    ) {
        if (dataSet == null) {
            return TerraceSplitResult.failure("No editable dataset is available.");
        }
        if (buildingWay == null) {
            return TerraceSplitResult.failure("No building way selected.");
        }
        if (buildingWay.getDataSet() != dataSet) {
            return TerraceSplitResult.failure("Selected building is not part of the current dataset.");
        }
        if (!buildingWay.isClosed()) {
            return TerraceSplitResult.failure("The selected way must be closed.");
        }
        if (!buildingWay.hasKey("building")) {
            return TerraceSplitResult.failure("The selected way must have a building=* tag.");
        }
        if (request == null || !request.hasValidParts()) {
            return TerraceSplitResult.failure("Terrace split requires parts >= 2.");
        }

        Bounds bounds = computeBounds(buildingWay);
        if (!bounds.isValid()) {
            return TerraceSplitResult.failure("Building geometry is not suitable for terrace split.");
        }

        SplitOrientation splitOrientation = buildSplitOrientation(buildingWay);
        boolean splitAlongLongitude = splitOrientation == null && bounds.width() >= bounds.height();
        double margin = Math.max(Math.max(bounds.width(), bounds.height()) * 0.25, LINE_MARGIN_FALLBACK);

        List<Way> pieces = new ArrayList<>();
        pieces.add(buildingWay);

        SplitContext splitContext = context == null ? SplitContext.empty() : context;

        for (int partIndex = 1; partIndex < request.getParts(); partIndex++) {
            double ratio = partIndex / (double) request.getParts();
            LatLon lineStart;
            LatLon lineEnd;
            if (splitOrientation != null) {
                SplitLine splitLine = splitOrientation.lineAtRatio(ratio);
                lineStart = toLatLon(splitLine.start);
                lineEnd = toLatLon(splitLine.end);
            } else if (splitAlongLongitude) {
                double lon = bounds.minLon + (bounds.width() * ratio);
                lineStart = new LatLon(bounds.minLat - margin, lon);
                lineEnd = new LatLon(bounds.maxLat + margin, lon);
            } else {
                double lat = bounds.minLat + (bounds.height() * ratio);
                lineStart = new LatLon(lat, bounds.minLon - margin);
                lineEnd = new LatLon(lat, bounds.maxLon + margin);
            }

            Way splitTarget = findSingleSplitTarget(pieces, lineStart, lineEnd);
            if (splitTarget == null) {
                return TerraceSplitResult.failure("Building geometry is not suitable for equal terrace splitting.");
            }

            SingleSplitResult splitResult = singleBuildingSplitService.splitBuilding(
                    dataSet,
                    splitTarget,
                    lineStart,
                    lineEnd,
                    splitContext
            );
            if (!splitResult.isSuccess() || splitResult.getResultWays().size() != 2) {
                return TerraceSplitResult.failure("Terrace split failed: " + splitResult.getMessage());
            }

            pieces.remove(splitTarget);
            pieces.addAll(splitResult.getResultWays());
        }

        if (pieces.size() != request.getParts()) {
            return TerraceSplitResult.failure("Terrace split produced an unexpected number of result ways.");
        }

        pieces.sort(buildDeterministicOrder(splitOrientation, splitAlongLongitude));
        return TerraceSplitResult.success("Terrace split completed.", pieces);
    }

    private Way findSingleSplitTarget(List<Way> pieces, LatLon lineStart, LatLon lineEnd) {
        Way candidate = null;
        for (Way way : pieces) {
            if (way == null || !way.isUsable() || !way.isClosed()) {
                continue;
            }
            IntersectionScanResult scan = cornerSnapService.findSplitIntersections(way, lineStart, lineEnd);
            if (!scan.isSuccess() || scan.getIntersections().size() != 2) {
                continue;
            }
            if (candidate != null) {
                return null;
            }
            candidate = way;
        }
        return candidate;
    }

    private Comparator<Way> buildDeterministicOrder(SplitOrientation splitOrientation, boolean primaryLongitude) {
        if (splitOrientation != null) {
            return Comparator
                    .comparingDouble((Way way) -> projectedCentroid(way, splitOrientation.mainAxis))
                    .thenComparingInt(Way::getNodesCount)
                    .thenComparingLong(Way::getUniqueId);
        }
        return Comparator
                .comparingDouble((Way way) -> primaryLongitude ? centroidLon(way) : centroidLat(way))
                .thenComparingDouble(way -> primaryLongitude ? centroidLat(way) : centroidLon(way))
                .thenComparingInt(Way::getNodesCount);
    }

    private double projectedCentroid(Way way, Vector2D axis) {
        List<Node> ring = getOpenRingNodes(way);
        if (ring.isEmpty() || axis == null) {
            return Double.POSITIVE_INFINITY;
        }
        double eastSum = 0.0;
        double northSum = 0.0;
        int count = 0;
        for (Node node : ring) {
            EastNorth en = toEastNorth(node == null ? null : node.getCoor());
            if (en == null) {
                continue;
            }
            eastSum += en.east();
            northSum += en.north();
            count++;
        }
        if (count == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return ((eastSum / count) * axis.x) + ((northSum / count) * axis.y);
    }

    private double centroidLat(Way way) {
        List<Node> ring = getOpenRingNodes(way);
        if (ring.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double sum = 0.0;
        int count = 0;
        for (Node node : ring) {
            if (node == null || node.getCoor() == null) {
                continue;
            }
            sum += node.getCoor().lat();
            count++;
        }
        return count == 0 ? Double.POSITIVE_INFINITY : sum / count;
    }

    private double centroidLon(Way way) {
        List<Node> ring = getOpenRingNodes(way);
        if (ring.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double sum = 0.0;
        int count = 0;
        for (Node node : ring) {
            if (node == null || node.getCoor() == null) {
                continue;
            }
            sum += node.getCoor().lon();
            count++;
        }
        return count == 0 ? Double.POSITIVE_INFINITY : sum / count;
    }

    private List<Node> getOpenRingNodes(Way way) {
        List<Node> ring = new ArrayList<>(way.getNodes());
        if (ring.size() > 1 && ring.get(0).equals(ring.get(ring.size() - 1))) {
            ring.remove(ring.size() - 1);
        }
        return ring;
    }

    private SplitOrientation buildSplitOrientation(Way way) {
        List<Node> corners = getOpenRingNodes(way);
        if (corners.size() != 4 || corners.stream().distinct().count() != 4) {
            return null;
        }

        EastNorth a = toEastNorth(corners.get(0).getCoor());
        EastNorth b = toEastNorth(corners.get(1).getCoor());
        EastNorth c = toEastNorth(corners.get(2).getCoor());
        EastNorth d = toEastNorth(corners.get(3).getCoor());
        if (a == null || b == null || c == null || d == null) {
            return null;
        }

        Vector2D ab = Vector2D.from(a, b);
        Vector2D bc = Vector2D.from(b, c);
        Vector2D cd = Vector2D.from(c, d);
        Vector2D da = Vector2D.from(d, a);
        if (ab.length() <= EPSILON || bc.length() <= EPSILON || cd.length() <= EPSILON || da.length() <= EPSILON) {
            return null;
        }

        double pair1 = (ab.length() + cd.length()) / 2.0;
        double pair2 = (bc.length() + da.length()) / 2.0;
        Vector2D first = pair1 >= pair2 ? ab : bc;
        Vector2D second = pair1 >= pair2 ? cd : da;

        Vector2D mainAxis = alignAndAverage(first.normalize(), second.normalize()).normalize();
        if (mainAxis.length() <= EPSILON) {
            return null;
        }
        Vector2D perpendicular = mainAxis.perpendicular().normalize();
        if (perpendicular.length() <= EPSILON) {
            return null;
        }

        double axisMin = Double.POSITIVE_INFINITY;
        double axisMax = Double.NEGATIVE_INFINITY;
        double eastSum = 0.0;
        double northSum = 0.0;
        double maxEdge = Math.max(Math.max(ab.length(), bc.length()), Math.max(cd.length(), da.length()));
        for (Node corner : corners) {
            EastNorth en = toEastNorth(corner.getCoor());
            if (en == null) {
                return null;
            }
            double projection = (en.east() * mainAxis.x) + (en.north() * mainAxis.y);
            axisMin = Math.min(axisMin, projection);
            axisMax = Math.max(axisMax, projection);
            eastSum += en.east();
            northSum += en.north();
        }

        if (!Double.isFinite(axisMin) || !Double.isFinite(axisMax) || axisMax - axisMin <= EPSILON) {
            return null;
        }

        double centerEast = eastSum / corners.size();
        double centerNorth = northSum / corners.size();
        double halfLineLength = Math.max(maxEdge * 2.0, EPSILON);
        return new SplitOrientation(mainAxis, perpendicular, axisMin, axisMax, centerEast, centerNorth, halfLineLength);
    }

    private Vector2D alignAndAverage(Vector2D first, Vector2D second) {
        if (first.dot(second) < 0.0) {
            second = second.multiply(-1.0);
        }
        return first.add(second);
    }

    private EastNorth toEastNorth(LatLon coor) {
        if (coor == null) {
            return null;
        }
        if (ProjectionRegistry.getProjection() != null) {
            return ProjectionRegistry.getProjection().latlon2eastNorth(coor);
        }
        return new EastNorth(coor.lon(), coor.lat());
    }

    private LatLon toLatLon(EastNorth eastNorth) {
        if (eastNorth == null) {
            return null;
        }
        if (ProjectionRegistry.getProjection() != null) {
            return ProjectionRegistry.getProjection().eastNorth2latlon(eastNorth);
        }
        return new LatLon(eastNorth.north(), eastNorth.east());
    }

    private Bounds computeBounds(Way way) {
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (Node node : getOpenRingNodes(way)) {
            if (node == null || node.getCoor() == null) {
                continue;
            }
            LatLon coor = node.getCoor();
            minLat = Math.min(minLat, coor.lat());
            maxLat = Math.max(maxLat, coor.lat());
            minLon = Math.min(minLon, coor.lon());
            maxLon = Math.max(maxLon, coor.lon());
        }
        return new Bounds(minLat, maxLat, minLon, maxLon);
    }

    private static final class Bounds {
        private final double minLat;
        private final double maxLat;
        private final double minLon;
        private final double maxLon;

        private Bounds(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }

        private boolean isValid() {
            return Double.isFinite(minLat)
                    && Double.isFinite(maxLat)
                    && Double.isFinite(minLon)
                    && Double.isFinite(maxLon)
                    && maxLat > minLat
                    && maxLon > minLon;
        }

        private double width() {
            return maxLon - minLon;
        }

        private double height() {
            return maxLat - minLat;
        }
    }

    private static final class SplitLine {
        private final EastNorth start;
        private final EastNorth end;

        private SplitLine(EastNorth start, EastNorth end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class SplitOrientation {
        private final Vector2D mainAxis;
        private final Vector2D perpendicular;
        private final double axisMin;
        private final double axisMax;
        private final double centerEast;
        private final double centerNorth;
        private final double halfLineLength;

        private SplitOrientation(Vector2D mainAxis, Vector2D perpendicular, double axisMin, double axisMax,
                double centerEast, double centerNorth, double halfLineLength) {
            this.mainAxis = mainAxis;
            this.perpendicular = perpendicular;
            this.axisMin = axisMin;
            this.axisMax = axisMax;
            this.centerEast = centerEast;
            this.centerNorth = centerNorth;
            this.halfLineLength = halfLineLength;
        }

        private SplitLine lineAtRatio(double ratio) {
            double axisPosition = axisMin + ((axisMax - axisMin) * ratio);
            double centerProjection = (centerEast * mainAxis.x) + (centerNorth * mainAxis.y);
            double offset = axisPosition - centerProjection;

            double lineCenterEast = centerEast + (mainAxis.x * offset);
            double lineCenterNorth = centerNorth + (mainAxis.y * offset);

            double startEast = lineCenterEast - (perpendicular.x * halfLineLength);
            double startNorth = lineCenterNorth - (perpendicular.y * halfLineLength);
            double endEast = lineCenterEast + (perpendicular.x * halfLineLength);
            double endNorth = lineCenterNorth + (perpendicular.y * halfLineLength);

            return new SplitLine(
                    new EastNorth(startEast, startNorth),
                    new EastNorth(endEast, endNorth)
            );
        }
    }

    private static final class Vector2D {
        private final double x;
        private final double y;

        private Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        private static Vector2D from(EastNorth from, EastNorth to) {
            return new Vector2D(to.east() - from.east(), to.north() - from.north());
        }

        private double length() {
            return Math.hypot(x, y);
        }

        private Vector2D normalize() {
            double len = length();
            if (len <= EPSILON) {
                return new Vector2D(0.0, 0.0);
            }
            return new Vector2D(x / len, y / len);
        }

        private Vector2D add(Vector2D other) {
            return new Vector2D(x + other.x, y + other.y);
        }

        private Vector2D multiply(double scalar) {
            return new Vector2D(x * scalar, y * scalar);
        }

        private double dot(Vector2D other) {
            return (x * other.x) + (y * other.y);
        }

        private Vector2D perpendicular() {
            return new Vector2D(-y, x);
        }
    }
}

