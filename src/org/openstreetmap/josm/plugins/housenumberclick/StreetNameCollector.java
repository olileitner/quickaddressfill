package org.openstreetmap.josm.plugins.housenumberclick;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.TreeSet;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.Logging;

/**
 * Utility for collecting and spatially disambiguating street names from highway ways in the current dataset,
 * using a two-stage grouping (raw connected components + conservative post-merge),
 * and for resolving local same-name street chains from a concrete seed way.
 */
final class StreetNameCollector {

    // Intentionally larger so short mapping gaps/junction splits do not fragment one logical street.
    private static final double COMPONENT_ENDPOINT_CONNECT_DISTANCE_METERS = 75.0;
    private static final double COMPONENT_ENDPOINT_TO_SEGMENT_CONNECT_DISTANCE_METERS = 40.0;
    private static final double COMPONENT_GROUP_MERGE_STRICT_LINK_DISTANCE_METERS = 75.0;
    private static final double COMPONENT_GROUP_MERGE_MAX_LINK_DISTANCE_METERS = 240.0;
    private static final double COMPONENT_GROUP_MERGE_MAX_CENTROID_DISTANCE_METERS = 700.0;
    private static final double COMPONENT_GROUP_MERGE_DIRECTION_MIN_COS = 0.70;
    private static final double COMPONENT_GROUP_MERGE_CONNECTOR_MIN_COS = 0.24;
    private static final double COMPONENT_GROUP_MERGE_LONG_STREET_MAX_LINK_DISTANCE_METERS = 165.0;
    private static final double COMPONENT_GROUP_MERGE_LONG_STREET_DIRECTION_MIN_COS = 0.86;

    private StreetNameCollector() {
        // Utility class
    }

    /**
     * Immutable lookup/index for disambiguated street clusters in the current dataset/view scope.
     */
    static final class StreetIndex {
        private final List<StreetOption> streetOptions;
        private final Map<String, StreetOption> optionsByDisplayName;
        private final Map<String, List<StreetOption>> optionsByBaseStreetName;
        private final Map<Way, StreetOption> optionByWay;
        private final Map<String, List<Way>> waysByBaseStreetName;
        private final Map<String, Way> seedWayByClusterId;
        private final Map<String, EastNorth> clusterCentroids;

        private StreetIndex(List<StreetOption> streetOptions, Map<String, List<StreetOption>> optionsByBaseStreetName,
                Map<Way, StreetOption> optionByWay, Map<String, List<Way>> waysByBaseStreetName,
                Map<String, Way> seedWayByClusterId, Map<String, EastNorth> clusterCentroids) {
            this.streetOptions = streetOptions == null ? List.of() : List.copyOf(streetOptions);
            this.optionsByBaseStreetName = optionsByBaseStreetName == null
                    ? Map.of()
                    : Collections.unmodifiableMap(optionsByBaseStreetName);
            this.optionByWay = optionByWay == null ? Map.of() : Collections.unmodifiableMap(optionByWay);
            this.waysByBaseStreetName = waysByBaseStreetName == null
                    ? Map.of()
                    : Collections.unmodifiableMap(waysByBaseStreetName);
            this.seedWayByClusterId = seedWayByClusterId == null
                    ? Map.of()
                    : Collections.unmodifiableMap(seedWayByClusterId);
            this.clusterCentroids = clusterCentroids == null ? Map.of() : Collections.unmodifiableMap(clusterCentroids);

            Map<String, StreetOption> displayLookup = new HashMap<>();
            for (StreetOption option : this.streetOptions) {
                if (option == null || !option.isValid()) {
                    continue;
                }
                displayLookup.put(option.getDisplayStreetName().toLowerCase(Locale.ROOT), option);
            }
            this.optionsByDisplayName = Collections.unmodifiableMap(displayLookup);
        }

        List<StreetOption> getStreetOptions() {
            return new ArrayList<>(streetOptions);
        }

        StreetOption findByDisplayStreetName(String displayStreetName) {
            String key = normalize(displayStreetName).toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                return null;
            }
            return optionsByDisplayName.get(key);
        }

        StreetOption findByClusterId(String clusterId) {
            String normalizedClusterId = normalize(clusterId);
            if (normalizedClusterId.isEmpty()) {
                return null;
            }
            for (StreetOption option : streetOptions) {
                if (option != null && normalizedClusterId.equals(option.getClusterId())) {
                    return option;
                }
            }
            return null;
        }

        List<StreetOption> getOptionsForBaseStreetName(String baseStreetName) {
            String key = normalize(baseStreetName).toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                return List.of();
            }
            List<StreetOption> options = optionsByBaseStreetName.get(key);
            return options == null ? List.of() : new ArrayList<>(options);
        }

        List<Way> getWaysForStreetOption(StreetOption option) {
            if (option == null || !option.isValid()) {
                return List.of();
            }
            List<Way> ways = new ArrayList<>();
            for (Map.Entry<Way, StreetOption> entry : optionByWay.entrySet()) {
                if (Objects.equals(option, entry.getValue())) {
                    ways.add(entry.getKey());
                }
            }
            return ways;
        }

        List<Way> getLocalStreetChainWays(StreetOption option) {
            return getLocalStreetChainWays(option, null);
        }

        List<Way> getLocalStreetChainWays(StreetOption option, Way preferredSeedWay) {
            if (option == null || !option.isValid()) {
                return List.of();
            }
            return getLocalStreetChainWays(option.getBaseStreetName(), preferredSeedWay, option.getClusterId());
        }

        List<Way> getLocalStreetChainWays(String baseStreetName, Way preferredSeedWay) {
            return getLocalStreetChainWays(baseStreetName, preferredSeedWay, "");
        }

        private List<Way> getLocalStreetChainWays(String baseStreetName, Way preferredSeedWay, String fallbackClusterId) {
            String baseKey = normalize(baseStreetName).toLowerCase(Locale.ROOT);
            List<Way> candidates = resolveCandidatesForLocalChain(baseKey, fallbackClusterId);
            if (candidates == null || candidates.isEmpty()) {
                return List.of();
            }

            Way seed = resolvePreferredSeedWay(candidates, preferredSeedWay, baseStreetName);
            if (seed == null) {
                seed = findSeedWayForClusterId(fallbackClusterId, baseStreetName);
            }
            if (seed == null) {
                Logging.warn("HouseNumberClick: no valid seedWay found for baseStreetName='"
                        + normalize(baseStreetName) + "', clusterId='" + normalize(fallbackClusterId) + "'.");
                return List.of();
            }

            LinkedHashSet<Way> visited = new LinkedHashSet<>();
            Queue<Way> queue = new ArrayDeque<>();
            queue.add(seed);
            visited.add(seed);

            while (!queue.isEmpty()) {
                Way current = queue.poll();
                for (Way candidate : candidates) {
                    if (candidate == null || visited.contains(candidate)) {
                        continue;
                    }
                    if (areWaysSpatiallyConnected(current, candidate)) {
                        visited.add(candidate);
                        queue.add(candidate);
                    }
                }
            }

            return new ArrayList<>(visited);
        }

        private List<Way> resolveCandidatesForLocalChain(String baseKey, String fallbackClusterId) {
            List<Way> baseCandidates = waysByBaseStreetName.get(baseKey);
            if (baseCandidates == null || baseCandidates.isEmpty()) {
                return List.of();
            }
            String normalizedClusterId = normalize(fallbackClusterId);
            if (normalizedClusterId.isEmpty()) {
                return baseCandidates;
            }

            StreetOption option = findByClusterId(normalizedClusterId);
            if (option == null || !option.isValid()) {
                return baseCandidates;
            }

            List<Way> optionWays = getWaysForStreetOption(option);
            return optionWays.isEmpty() ? baseCandidates : optionWays;
        }

        Way findSeedWayForClusterId(String clusterId, String baseStreetName) {
            String normalizedClusterId = normalize(clusterId);
            if (normalizedClusterId.isEmpty()) {
                return null;
            }
            Way clusterSeed = seedWayByClusterId.get(normalizedClusterId);
            if (clusterSeed == null || !clusterSeed.isUsable()) {
                return null;
            }

            String baseKey = normalize(baseStreetName).toLowerCase(Locale.ROOT);
            List<Way> candidates = waysByBaseStreetName.get(baseKey);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }

            if (candidates.contains(clusterSeed)) {
                return clusterSeed;
            }

            for (Way candidate : candidates) {
                if (candidate != null && candidate.getUniqueId() == clusterSeed.getUniqueId()) {
                    return candidate;
                }
            }
            return null;
        }

        Way findNearestWayForBaseStreetName(String baseStreetName, LatLon referencePoint) {
            if (ProjectionRegistry.getProjection() == null) {
                return null;
            }
            String baseKey = normalize(baseStreetName).toLowerCase(Locale.ROOT);
            if (baseKey.isEmpty() || referencePoint == null) {
                return null;
            }
            List<Way> candidates = waysByBaseStreetName.get(baseKey);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }

            return findNearestWay(referencePoint, candidates);
        }

        Way findNearestWayForStreetOption(StreetOption option, LatLon referencePoint) {
            if (option == null || !option.isValid() || referencePoint == null || ProjectionRegistry.getProjection() == null) {
                return null;
            }
            List<Way> candidates = getWaysForStreetOption(option);
            if (candidates.isEmpty()) {
                return null;
            }

            return findNearestWay(referencePoint, candidates);
        }

        private Way findNearestWay(LatLon referencePoint, List<Way> candidates) {
            if (referencePoint == null || candidates == null || candidates.isEmpty() || ProjectionRegistry.getProjection() == null) {
                return null;
            }

            EastNorth referenceEastNorth = ProjectionRegistry.getProjection().latlon2eastNorth(referencePoint);
            if (referenceEastNorth == null) {
                return null;
            }

            Way nearest = null;
            double bestDistanceSquared = Double.POSITIVE_INFINITY;
            for (Way candidate : candidates) {
                if (candidate == null || !candidate.isUsable()) {
                    continue;
                }
                double distanceSquared = distanceSquaredToWay(referenceEastNorth, candidate);
                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared;
                    nearest = candidate;
                }
            }
            return nearest;
        }

        StreetOption resolveForAddressPrimitive(OsmPrimitive primitive) {
            String baseStreetName = primitive == null ? "" : normalize(primitive.get("addr:street"));
            return resolveForBaseStreetAndPrimitive(baseStreetName, primitive);
        }

        StreetOption resolveForBaseStreetAndPrimitive(String baseStreetName, OsmPrimitive primitive) {
            List<StreetOption> options = getOptionsForBaseStreetName(baseStreetName);
            if (options.isEmpty()) {
                return null;
            }
            if (options.size() == 1 || primitive == null) {
                return options.get(0);
            }

            EastNorth primitivePoint = resolvePrimitivePoint(primitive);
            if (primitivePoint == null) {
                return options.get(0);
            }

            StreetOption bestOption = options.get(0);
            double bestDistanceSquared = Double.POSITIVE_INFINITY;
            for (StreetOption option : options) {
                EastNorth centroid = clusterCentroids.get(option.getClusterId());
                if (centroid == null) {
                    continue;
                }
                double dx = primitivePoint.east() - centroid.east();
                double dy = primitivePoint.north() - centroid.north();
                double distanceSquared = (dx * dx) + (dy * dy);
                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared;
                    bestOption = option;
                }
            }
            return bestOption;
        }

        private EastNorth resolvePrimitivePoint(OsmPrimitive primitive) {
            if (primitive == null) {
                return null;
            }
            BBox bbox = primitive.getBBox();
            if (bbox == null || !bbox.isValid()) {
                return null;
            }
            LatLon center = bbox.getCenter();
            if (center == null || ProjectionRegistry.getProjection() == null) {
                return null;
            }
            return ProjectionRegistry.getProjection().latlon2eastNorth(center);
        }

        private Way resolvePreferredSeedWay(List<Way> candidates, Way preferredSeedWay, String baseStreetName) {
            if (preferredSeedWay == null || !preferredSeedWay.isUsable()) {
                return null;
            }
            if (!normalize(preferredSeedWay.get("name")).equalsIgnoreCase(normalize(baseStreetName))) {
                return null;
            }
            if (candidates.contains(preferredSeedWay)) {
                return preferredSeedWay;
            }
            for (Way candidate : candidates) {
                if (candidate == null) {
                    continue;
                }
                if (candidate.getUniqueId() == preferredSeedWay.getUniqueId()) {
                    return candidate;
                }
            }
            return null;
        }

        private double distanceSquaredToWay(EastNorth referencePoint, Way way) {
            double bestDistanceSquared = Double.POSITIVE_INFINITY;
            List<org.openstreetmap.josm.data.osm.Node> nodes = way.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                return bestDistanceSquared;
            }

            for (int i = 0; i < nodes.size(); i++) {
                EastNorth nodePoint = toEastNorth(nodes.get(i));
                if (nodePoint == null) {
                    continue;
                }
                double nodeDistanceSquared = distanceSquared(referencePoint, nodePoint);
                if (nodeDistanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = nodeDistanceSquared;
                }
                if (i == 0) {
                    continue;
                }
                EastNorth previousPoint = toEastNorth(nodes.get(i - 1));
                if (previousPoint == null) {
                    continue;
                }
                double segmentDistanceSquared = distanceSquaredToSegment(referencePoint, previousPoint, nodePoint);
                if (segmentDistanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = segmentDistanceSquared;
                }
            }
            return bestDistanceSquared;
        }

        private EastNorth toEastNorth(org.openstreetmap.josm.data.osm.Node node) {
            if (node == null || node.getCoor() == null || ProjectionRegistry.getProjection() == null) {
                return null;
            }
            return ProjectionRegistry.getProjection().latlon2eastNorth(node.getCoor());
        }

        private double distanceSquared(EastNorth first, EastNorth second) {
            if (first == null || second == null) {
                return Double.POSITIVE_INFINITY;
            }
            double dx = first.east() - second.east();
            double dy = first.north() - second.north();
            return (dx * dx) + (dy * dy);
        }

        private double distanceSquaredToSegment(EastNorth point, EastNorth segmentStart, EastNorth segmentEnd) {
            if (point == null || segmentStart == null || segmentEnd == null) {
                return Double.POSITIVE_INFINITY;
            }
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
                return distanceSquared(point, segmentStart);
            }
            double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
            t = Math.max(0.0, Math.min(1.0, t));
            double projectionX = ax + (t * dx);
            double projectionY = ay + (t * dy);
            double ex = px - projectionX;
            double ey = py - projectionY;
            return (ex * ex) + (ey * ey);
        }
    }

    static StreetIndex collectStreetIndex(DataSet dataSet) {
        if (dataSet == null) {
            return new StreetIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        Collection<Way> candidateWays = getWaysFromCurrentView(dataSet);
        Map<String, List<Way>> waysByBaseStreetName = new HashMap<>();
        for (Way way : candidateWays) {
            if (way == null || !way.isUsable() || !way.hasTag("highway")) {
                continue;
            }
            String baseStreetName = normalize(way.get("name"));
            if (baseStreetName.isEmpty()) {
                continue;
            }
            waysByBaseStreetName.computeIfAbsent(baseStreetName, ignored -> new ArrayList<>()).add(way);
        }

        List<String> sortedBaseStreetNames = new ArrayList<>(waysByBaseStreetName.keySet());
        sortedBaseStreetNames.sort(Collator.getInstance());

        List<StreetOption> streetOptions = new ArrayList<>();
        Map<String, List<StreetOption>> optionsByBaseStreetName = new LinkedHashMap<>();
        Map<Way, StreetOption> optionByWay = new IdentityHashMap<>();
        Map<String, List<Way>> waysByBaseStreetNameIndex = new LinkedHashMap<>();
        Map<String, Way> seedWayByClusterId = new HashMap<>();
        Map<String, EastNorth> clusterCentroids = new HashMap<>();

        for (String baseStreetName : sortedBaseStreetNames) {
            List<Way> namedWays = waysByBaseStreetName.getOrDefault(baseStreetName, List.of());
            waysByBaseStreetNameIndex.put(baseStreetName.toLowerCase(Locale.ROOT), List.copyOf(namedWays));
            List<List<Way>> rawComponents = splitIntoConnectedComponents(namedWays);
            List<List<Way>> mergedComponents = mergeConnectedComponents(rawComponents);
            mergedComponents.sort(Comparator.comparing(StreetNameCollector::computeComponentSortKey));
            if (Logging.isDebugEnabled() && (rawComponents.size() > 1 || mergedComponents.size() > 1)) {
                Logging.debug("HouseNumberClick cluster build: base='" + baseStreetName
                        + "', rawComponents=" + rawComponents.size()
                        + ", mergedGroups=" + mergedComponents.size()
                        + ", rawSizes=" + formatComponentSizes(rawComponents)
                        + ", mergedSizes=" + formatComponentSizes(mergedComponents) + ".");
            }

            List<StreetOption> optionsForBaseStreet = new ArrayList<>();
            for (int i = 0; i < mergedComponents.size(); i++) {
                int clusterIndex = i + 1;
                String clusterId = buildClusterId(baseStreetName, clusterIndex);
                String displayStreetName = mergedComponents.size() <= 1
                        ? baseStreetName
                        : (clusterIndex == 1 ? baseStreetName : baseStreetName + " [" + clusterIndex + "]");
                StreetOption option = new StreetOption(baseStreetName, displayStreetName, clusterId);
                streetOptions.add(option);
                optionsForBaseStreet.add(option);
                clusterCentroids.put(clusterId, computeComponentCentroid(mergedComponents.get(i)));
                Way seed = findDeterministicSeedWay(mergedComponents.get(i));
                if (seed != null) {
                    seedWayByClusterId.put(clusterId, seed);
                }
                for (Way way : mergedComponents.get(i)) {
                    optionByWay.put(way, option);
                }
            }
            optionsByBaseStreetName.put(baseStreetName.toLowerCase(Locale.ROOT), List.copyOf(optionsForBaseStreet));
        }

        return new StreetIndex(streetOptions, optionsByBaseStreetName, optionByWay,
                waysByBaseStreetNameIndex, seedWayByClusterId, clusterCentroids);
    }

    static List<String> collectStreetNames(DataSet dataSet) {
        Set<String> names = new TreeSet<>(Collator.getInstance());
        for (StreetOption option : collectStreetIndex(dataSet).getStreetOptions()) {
            names.add(option.getDisplayStreetName());
        }
        return new ArrayList<>(names);
    }

    private static List<List<Way>> splitIntoConnectedComponents(List<Way> ways) {
        if (ways == null || ways.isEmpty()) {
            return List.of();
        }

        List<List<Way>> components = new ArrayList<>();
        Set<Way> visited = new LinkedHashSet<>();
        for (Way start : ways) {
            if (start == null || visited.contains(start)) {
                continue;
            }
            List<Way> component = new ArrayList<>();
            Queue<Way> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                Way current = queue.poll();
                component.add(current);
                for (Way candidate : ways) {
                    if (candidate == null || visited.contains(candidate)) {
                        continue;
                    }
                    if (areWaysSpatiallyConnected(current, candidate)) {
                        visited.add(candidate);
                        queue.add(candidate);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private static List<List<Way>> mergeConnectedComponents(List<List<Way>> rawComponents) {
        if (rawComponents == null || rawComponents.isEmpty()) {
            return List.of();
        }
        if (rawComponents.size() == 1) {
            List<List<Way>> single = new ArrayList<>();
            single.add(new ArrayList<>(rawComponents.get(0)));
            return single;
        }

        List<List<Way>> groups = new ArrayList<>();
        for (List<Way> component : rawComponents) {
            if (component != null && !component.isEmpty()) {
                groups.add(new ArrayList<>(component));
            }
        }
        if (groups.size() <= 1) {
            return groups;
        }

        boolean mergedInPass;
        do {
            mergedInPass = false;
            for (int i = 0; i < groups.size() && !mergedInPass; i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    MergeDecision mergeDecision = shouldMergeComponentGroups(groups.get(i), groups.get(j));
                    logMergeDecision(groups.get(i), groups.get(j), mergeDecision);
                    if (!mergeDecision.accepted) {
                        continue;
                    }
                    groups.get(i).addAll(groups.get(j));
                    groups.remove(j);
                    mergedInPass = true;
                    break;
                }
            }
        } while (mergedInPass);

        return groups;
    }

    private static MergeDecision shouldMergeComponentGroups(List<Way> firstGroup, List<Way> secondGroup) {
        double linkDistanceMeters = computeComponentLinkDistanceMeters(firstGroup, secondGroup);
        if (!Double.isFinite(linkDistanceMeters)) {
            return MergeDecision.reject("invalid link distance", linkDistanceMeters,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        if (linkDistanceMeters <= COMPONENT_GROUP_MERGE_STRICT_LINK_DISTANCE_METERS) {
            return MergeDecision.accept("strong link distance", linkDistanceMeters,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        if (linkDistanceMeters > COMPONENT_GROUP_MERGE_MAX_LINK_DISTANCE_METERS) {
            return MergeDecision.reject("link distance too large", linkDistanceMeters,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        ComponentProfile firstProfile = computeComponentProfile(firstGroup);
        ComponentProfile secondProfile = computeComponentProfile(secondGroup);
        if (firstProfile == null || secondProfile == null) {
            return MergeDecision.reject("missing component profile", linkDistanceMeters,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        if (firstProfile.centroid == null || secondProfile.centroid == null
                || firstProfile.direction == null || secondProfile.direction == null) {
            return MergeDecision.reject("incomplete component profile", linkDistanceMeters,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        double centroidDistanceMeters = firstProfile.centroid.greatCircleDistance(secondProfile.centroid);
        double directionAlignment = Math.abs(dot(firstProfile.direction, secondProfile.direction));
        if (directionAlignment < COMPONENT_GROUP_MERGE_DIRECTION_MIN_COS) {
            return MergeDecision.reject("direction mismatch", linkDistanceMeters,
                    centroidDistanceMeters, directionAlignment, Double.NaN, Double.NaN);
        }

        DirectionVector connectorDirection = normalizeDirection(toMeterVector(firstProfile.centroid, secondProfile.centroid));
        if (connectorDirection == null) {
            return MergeDecision.reject("missing connector direction", linkDistanceMeters,
                    centroidDistanceMeters, directionAlignment, Double.NaN, Double.NaN);
        }
        double connectorAlignmentFirst = Math.abs(dot(connectorDirection, firstProfile.direction));
        double connectorAlignmentSecond = Math.abs(dot(connectorDirection, secondProfile.direction));
        if (connectorAlignmentFirst < COMPONENT_GROUP_MERGE_CONNECTOR_MIN_COS
                || connectorAlignmentSecond < COMPONENT_GROUP_MERGE_CONNECTOR_MIN_COS) {
            return MergeDecision.reject("connector mismatch", linkDistanceMeters,
                    centroidDistanceMeters, directionAlignment, connectorAlignmentFirst, connectorAlignmentSecond);
        }

        if (centroidDistanceMeters > COMPONENT_GROUP_MERGE_MAX_CENTROID_DISTANCE_METERS) {
            boolean strongLongStreetSignal = linkDistanceMeters <= COMPONENT_GROUP_MERGE_LONG_STREET_MAX_LINK_DISTANCE_METERS
                    && directionAlignment >= COMPONENT_GROUP_MERGE_LONG_STREET_DIRECTION_MIN_COS;
            if (!strongLongStreetSignal) {
                return MergeDecision.reject("centroid too large (weak link/direction)", linkDistanceMeters,
                        centroidDistanceMeters, directionAlignment, connectorAlignmentFirst, connectorAlignmentSecond);
            }
            return MergeDecision.accept("long-range match (link + direction)", linkDistanceMeters,
                    centroidDistanceMeters, directionAlignment, connectorAlignmentFirst, connectorAlignmentSecond);
        }

        return MergeDecision.accept("direction + connector match", linkDistanceMeters,
                centroidDistanceMeters, directionAlignment, connectorAlignmentFirst, connectorAlignmentSecond);
    }

    private static ComponentProfile computeComponentProfile(List<Way> componentWays) {
        if (componentWays == null || componentWays.isEmpty()) {
            return null;
        }
        LatLon centroid = computeComponentCentroidLatLon(componentWays);
        DirectionVector direction = computeComponentDirection(componentWays);
        return new ComponentProfile(centroid, direction);
    }

    private static LatLon computeComponentCentroidLatLon(List<Way> componentWays) {
        if (componentWays == null || componentWays.isEmpty()) {
            return null;
        }
        double latSum = 0.0;
        double lonSum = 0.0;
        int count = 0;
        for (Way way : componentWays) {
            if (way == null) {
                continue;
            }
            for (org.openstreetmap.josm.data.osm.Node node : way.getNodes()) {
                if (node == null || node.getCoor() == null) {
                    continue;
                }
                latSum += node.getCoor().lat();
                lonSum += node.getCoor().lon();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return new LatLon(latSum / count, lonSum / count);
    }

    private static DirectionVector computeComponentDirection(List<Way> componentWays) {
        double bestLengthSquared = -1.0;
        DirectionVector bestDirection = null;
        for (Way way : componentWays) {
            if (way == null) {
                continue;
            }
            List<org.openstreetmap.josm.data.osm.Node> nodes = way.getNodes();
            for (int i = 1; i < nodes.size(); i++) {
                LatLon start = nodes.get(i - 1) == null ? null : nodes.get(i - 1).getCoor();
                LatLon end = nodes.get(i) == null ? null : nodes.get(i).getCoor();
                DirectionVector direction = toMeterVector(start, end);
                if (direction == null) {
                    continue;
                }
                double lengthSquared = (direction.dx * direction.dx) + (direction.dy * direction.dy);
                if (lengthSquared <= bestLengthSquared) {
                    continue;
                }
                DirectionVector normalized = normalizeDirection(direction);
                if (normalized != null) {
                    bestLengthSquared = lengthSquared;
                    bestDirection = normalized;
                }
            }
        }
        return bestDirection;
    }

    private static double computeComponentLinkDistanceMeters(List<Way> firstGroup, List<Way> secondGroup) {
        if (firstGroup == null || secondGroup == null || firstGroup.isEmpty() || secondGroup.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Way firstWay : firstGroup) {
            if (firstWay == null) {
                continue;
            }
            for (Way secondWay : secondGroup) {
                if (secondWay == null) {
                    continue;
                }
                best = Math.min(best, computeWayLinkDistanceMeters(firstWay, secondWay));
                if (best <= 0.0) {
                    return 0.0;
                }
            }
        }
        return best;
    }

    private static double computeWayLinkDistanceMeters(Way firstWay, Way secondWay) {
        if (firstWay == null || secondWay == null) {
            return Double.POSITIVE_INFINITY;
        }
        for (org.openstreetmap.josm.data.osm.Node firstNode : firstWay.getNodes()) {
            if (firstNode != null && secondWay.getNodes().contains(firstNode)) {
                return 0.0;
            }
        }

        double best = Double.POSITIVE_INFINITY;
        List<org.openstreetmap.josm.data.osm.Node> firstEndpoints = collectEndpoints(firstWay);
        List<org.openstreetmap.josm.data.osm.Node> secondEndpoints = collectEndpoints(secondWay);
        for (org.openstreetmap.josm.data.osm.Node firstEndpoint : firstEndpoints) {
            LatLon firstCoor = firstEndpoint == null ? null : firstEndpoint.getCoor();
            if (firstCoor == null) {
                continue;
            }
            for (org.openstreetmap.josm.data.osm.Node secondEndpoint : secondEndpoints) {
                LatLon secondCoor = secondEndpoint == null ? null : secondEndpoint.getCoor();
                if (secondCoor == null) {
                    continue;
                }
                best = Math.min(best, firstCoor.greatCircleDistance(secondCoor));
            }
        }

        best = Math.min(best, computeEndpointToWaySegmentDistanceMeters(firstWay, secondWay));
        best = Math.min(best, computeEndpointToWaySegmentDistanceMeters(secondWay, firstWay));
        return best;
    }

    private static double computeEndpointToWaySegmentDistanceMeters(Way endpointWay, Way geometryWay) {
        if (endpointWay == null || geometryWay == null) {
            return Double.POSITIVE_INFINITY;
        }
        List<org.openstreetmap.josm.data.osm.Node> endpoints = collectEndpoints(endpointWay);
        List<org.openstreetmap.josm.data.osm.Node> geometryNodes = geometryWay.getNodes();
        if (endpoints.isEmpty() || geometryNodes.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }

        double best = Double.POSITIVE_INFINITY;
        for (org.openstreetmap.josm.data.osm.Node endpoint : endpoints) {
            LatLon endpointCoor = endpoint == null ? null : endpoint.getCoor();
            if (endpointCoor == null) {
                continue;
            }
            for (int i = 1; i < geometryNodes.size(); i++) {
                LatLon start = geometryNodes.get(i - 1) == null ? null : geometryNodes.get(i - 1).getCoor();
                LatLon end = geometryNodes.get(i) == null ? null : geometryNodes.get(i).getCoor();
                if (start == null || end == null) {
                    continue;
                }
                best = Math.min(best, distanceMetersToSegment(endpointCoor, start, end));
            }
        }
        return best;
    }

    private static Way findDeterministicSeedWay(List<Way> ways) {
        if (ways == null || ways.isEmpty()) {
            return null;
        }
        Way best = null;
        for (Way way : ways) {
            if (way == null || !way.isUsable()) {
                continue;
            }
            if (best == null || way.getUniqueId() < best.getUniqueId()) {
                best = way;
            }
        }
        return best;
    }

    private static void logMergeDecision(List<Way> firstGroup, List<Way> secondGroup, MergeDecision mergeDecision) {
        if (!Logging.isDebugEnabled()) {
            return;
        }
        if (mergeDecision == null) {
            return;
        }
        Logging.debug("HouseNumberClick cluster merge: "
                + (mergeDecision.accepted ? "accepted" : "rejected")
                + " (" + mergeDecision.reason + "), sizes="
                + (firstGroup == null ? 0 : firstGroup.size()) + "+"
                + (secondGroup == null ? 0 : secondGroup.size())
                + ", linkDistance=" + formatMetric(mergeDecision.linkDistanceMeters)
                + "m, centroidDistance=" + formatMetric(mergeDecision.centroidDistanceMeters)
                + "m, directionAlignment=" + formatMetric(mergeDecision.directionAlignment)
                + ", connectorAlignment=" + formatMetric(mergeDecision.connectorAlignmentFirst)
                + "/" + formatMetric(mergeDecision.connectorAlignmentSecond)
                + ".");
    }

    private static String formatMetric(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static boolean areWaysSpatiallyConnected(Way first, Way second) {
        if (first == null || second == null) {
            return false;
        }
        if (first == second) {
            return true;
        }

        for (org.openstreetmap.josm.data.osm.Node firstNode : first.getNodes()) {
            if (firstNode == null) {
                continue;
            }
            if (second.getNodes().contains(firstNode)) {
                return true;
            }
        }

        List<org.openstreetmap.josm.data.osm.Node> firstEndpoints = collectEndpoints(first);
        List<org.openstreetmap.josm.data.osm.Node> secondEndpoints = collectEndpoints(second);
        for (org.openstreetmap.josm.data.osm.Node firstEndpoint : firstEndpoints) {
            if (firstEndpoint == null || firstEndpoint.getCoor() == null) {
                continue;
            }
            for (org.openstreetmap.josm.data.osm.Node secondEndpoint : secondEndpoints) {
                if (secondEndpoint == null || secondEndpoint.getCoor() == null) {
                    continue;
                }
                double distanceMeters = firstEndpoint.getCoor().greatCircleDistance(secondEndpoint.getCoor());
                if (distanceMeters <= COMPONENT_ENDPOINT_CONNECT_DISTANCE_METERS) {
                    return true;
                }
            }
        }

        // Bridges short mapper gaps where one segment endpoint lands near the middle of another segment.
        return hasEndpointNearWaySegment(first, second)
                || hasEndpointNearWaySegment(second, first);
    }

    private static boolean hasEndpointNearWaySegment(Way endpointWay, Way geometryWay) {
        if (endpointWay == null || geometryWay == null) {
            return false;
        }
        List<org.openstreetmap.josm.data.osm.Node> endpoints = collectEndpoints(endpointWay);
        List<org.openstreetmap.josm.data.osm.Node> geometryNodes = geometryWay.getNodes();
        if (endpoints.isEmpty() || geometryNodes.size() < 2) {
            return false;
        }

        double maxDistanceSquared = COMPONENT_ENDPOINT_TO_SEGMENT_CONNECT_DISTANCE_METERS
                * COMPONENT_ENDPOINT_TO_SEGMENT_CONNECT_DISTANCE_METERS;
        for (org.openstreetmap.josm.data.osm.Node endpoint : endpoints) {
            LatLon endpointCoor = endpoint == null ? null : endpoint.getCoor();
            if (endpointCoor == null) {
                continue;
            }
            for (int i = 1; i < geometryNodes.size(); i++) {
                org.openstreetmap.josm.data.osm.Node startNode = geometryNodes.get(i - 1);
                org.openstreetmap.josm.data.osm.Node endNode = geometryNodes.get(i);
                LatLon segmentStart = startNode == null ? null : startNode.getCoor();
                LatLon segmentEnd = endNode == null ? null : endNode.getCoor();
                if (segmentStart == null || segmentEnd == null) {
                    continue;
                }
                double distanceMeters = distanceMetersToSegment(endpointCoor, segmentStart, segmentEnd);
                double distanceSquared = distanceMeters * distanceMeters;
                if (distanceSquared <= maxDistanceSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distanceMetersToSegment(LatLon point, LatLon segmentStart, LatLon segmentEnd) {
        if (point == null || segmentStart == null || segmentEnd == null) {
            return Double.POSITIVE_INFINITY;
        }
        double meanLatitudeRadians = Math.toRadians((point.lat() + segmentStart.lat() + segmentEnd.lat()) / 3.0);
        double metersPerDegreeLat = 111_132.0;
        double metersPerDegreeLon = 111_320.0 * Math.cos(meanLatitudeRadians);
        if (Math.abs(metersPerDegreeLon) < 1e-9) {
            return Double.POSITIVE_INFINITY;
        }

        double px = point.lon() * metersPerDegreeLon;
        double py = point.lat() * metersPerDegreeLat;
        double ax = segmentStart.lon() * metersPerDegreeLon;
        double ay = segmentStart.lat() * metersPerDegreeLat;
        double bx = segmentEnd.lon() * metersPerDegreeLon;
        double by = segmentEnd.lat() * metersPerDegreeLat;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = (dx * dx) + (dy * dy);
        if (lengthSquared <= 0.0) {
            double ex = px - ax;
            double ey = py - ay;
            return Math.sqrt((ex * ex) + (ey * ey));
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double projectionX = ax + (t * dx);
        double projectionY = ay + (t * dy);
        double ex = px - projectionX;
        double ey = py - projectionY;
        return Math.sqrt((ex * ex) + (ey * ey));
    }

    private static DirectionVector toMeterVector(LatLon start, LatLon end) {
        if (start == null || end == null) {
            return null;
        }
        double meanLatitudeRadians = Math.toRadians((start.lat() + end.lat()) / 2.0);
        double metersPerDegreeLat = 111_132.0;
        double metersPerDegreeLon = 111_320.0 * Math.cos(meanLatitudeRadians);
        if (Math.abs(metersPerDegreeLon) < 1e-9) {
            return null;
        }
        double dx = (end.lon() - start.lon()) * metersPerDegreeLon;
        double dy = (end.lat() - start.lat()) * metersPerDegreeLat;
        return new DirectionVector(dx, dy);
    }

    private static DirectionVector normalizeDirection(DirectionVector vector) {
        if (vector == null) {
            return null;
        }
        double length = Math.hypot(vector.dx, vector.dy);
        if (length <= 1e-9) {
            return null;
        }
        return new DirectionVector(vector.dx / length, vector.dy / length);
    }

    private static double dot(DirectionVector first, DirectionVector second) {
        if (first == null || second == null) {
            return 0.0;
        }
        return (first.dx * second.dx) + (first.dy * second.dy);
    }

    private static List<org.openstreetmap.josm.data.osm.Node> collectEndpoints(Way way) {
        if (way == null || way.getNodesCount() == 0) {
            return List.of();
        }
        if (way.getNodesCount() == 1) {
            return List.of(way.firstNode());
        }
        return List.of(way.firstNode(), way.lastNode());
    }

    private static String computeComponentSortKey(List<Way> component) {
        EastNorth centroid = computeComponentCentroid(component);
        if (centroid == null) {
            return "~";
        }
        return String.format(Locale.ROOT, "%020.4f|%020.4f", centroid.north(), centroid.east());
    }

    private static EastNorth computeComponentCentroid(List<Way> component) {
        if (component == null || component.isEmpty()) {
            return null;
        }
        if (ProjectionRegistry.getProjection() == null) {
            return null;
        }
        double eastSum = 0.0;
        double northSum = 0.0;
        int points = 0;
        for (Way way : component) {
            if (way == null) {
                continue;
            }
            for (org.openstreetmap.josm.data.osm.Node node : way.getNodes()) {
                if (node == null || node.getCoor() == null) {
                    continue;
                }
                EastNorth eastNorth = ProjectionRegistry.getProjection().latlon2eastNorth(node.getCoor());
                if (eastNorth == null) {
                    continue;
                }
                eastSum += eastNorth.east();
                northSum += eastNorth.north();
                points++;
            }
        }
        if (points == 0) {
            return null;
        }
        return new EastNorth(eastSum / points, northSum / points);
    }

    private static String formatComponentSizes(List<List<Way>> components) {
        List<String> sizes = new ArrayList<>();
        for (List<Way> component : components) {
            sizes.add(Integer.toString(component == null ? 0 : component.size()));
        }
        return String.join(",", sizes);
    }

    private static String buildClusterId(String baseStreetName, int clusterIndex) {
        return normalize(baseStreetName).toLowerCase(Locale.ROOT) + "#" + clusterIndex;
    }

    private static Collection<Way> getWaysFromCurrentView(DataSet dataSet) {
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            Bounds bounds = map.mapView.getRealBounds();
            if (bounds != null) {
                return dataSet.searchWays(bounds.toBBox());
            }
        }
        return dataSet.getWays();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Lightweight merge-profile for one raw street component.
     */
    private static final class ComponentProfile {
        private final LatLon centroid;
        private final DirectionVector direction;

        private ComponentProfile(LatLon centroid, DirectionVector direction) {
            this.centroid = centroid;
            this.direction = direction;
        }
    }

    /**
     * Normalized 2D direction vector in local meter space.
     */
    private static final class DirectionVector {
        private final double dx;
        private final double dy;

        private DirectionVector(double dx, double dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /**
     * Decision payload for one merge evaluation, including metrics for debug logging.
     */
    private static final class MergeDecision {
        private final boolean accepted;
        private final String reason;
        private final double linkDistanceMeters;
        private final double centroidDistanceMeters;
        private final double directionAlignment;
        private final double connectorAlignmentFirst;
        private final double connectorAlignmentSecond;

        private MergeDecision(boolean accepted, String reason, double linkDistanceMeters,
                double centroidDistanceMeters, double directionAlignment,
                double connectorAlignmentFirst, double connectorAlignmentSecond) {
            this.accepted = accepted;
            this.reason = reason;
            this.linkDistanceMeters = linkDistanceMeters;
            this.centroidDistanceMeters = centroidDistanceMeters;
            this.directionAlignment = directionAlignment;
            this.connectorAlignmentFirst = connectorAlignmentFirst;
            this.connectorAlignmentSecond = connectorAlignmentSecond;
        }

        private static MergeDecision accept(String reason, double linkDistanceMeters,
                double centroidDistanceMeters, double directionAlignment,
                double connectorAlignmentFirst, double connectorAlignmentSecond) {
            return new MergeDecision(true, reason, linkDistanceMeters,
                    centroidDistanceMeters, directionAlignment,
                    connectorAlignmentFirst, connectorAlignmentSecond);
        }

        private static MergeDecision reject(String reason, double linkDistanceMeters,
                double centroidDistanceMeters, double directionAlignment,
                double connectorAlignmentFirst, double connectorAlignmentSecond) {
            return new MergeDecision(false, reason, linkDistanceMeters,
                    centroidDistanceMeters, directionAlignment,
                    connectorAlignmentFirst, connectorAlignmentSecond);
        }
    }
}
