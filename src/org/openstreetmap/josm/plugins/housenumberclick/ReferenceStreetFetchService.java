package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.OverpassDownloadReader;
import org.openstreetmap.josm.tools.Logging;

final class ReferenceStreetFetchService {

    private static final double EXPAND_RATIO = 0.10;
    private static final double MIN_EXPAND_DEGREES = 0.001;
    private static final double MAX_EXPAND_DEGREES = 0.015;
    private static final double ENDPOINT_NEAR_METERS = 35.0;
    private static final double ANY_NODE_NEAR_METERS = 18.0;

    DataSet loadReferenceStreet(DataSet editDataSet, String streetName) throws Exception {
        String normalizedStreet = normalize(streetName);
        if (editDataSet == null || normalizedStreet.isEmpty()) {
            return new DataSet();
        }

        List<Way> localStreetWays = collectStreetWays(editDataSet, normalizedStreet);
        if (localStreetWays.isEmpty()) {
            // Defensive: without local anchor ways we cannot reliably disambiguate common names.
            return new DataSet();
        }

        Bounds bounds = mergedDataSourceBounds(editDataSet.getDataSourceBounds());
        if (bounds == null) {
            return new DataSet();
        }

        Bounds expandedBounds = expand(bounds);
        Logging.debug(String.format(
                "Reference street fetch: street='%s', bounds=[%.6f,%.6f,%.6f,%.6f]",
                normalizedStreet,
                expandedBounds.getMinLat(),
                expandedBounds.getMinLon(),
                expandedBounds.getMaxLat(),
                expandedBounds.getMaxLon()
        ));
        String query = buildStreetReferenceQuery(normalizedStreet);
        String overpassServer = OverpassDownloadReader.OVERPASS_SERVER.get();

        // API order is (bounds, overpassServer, overpassQuery).
        OverpassDownloadReader reader = new OverpassDownloadReader(expandedBounds, overpassServer, query);
        DataSet downloaded;
        try {
            downloaded = reader.parseOsm(NullProgressMonitor.INSTANCE);
        } catch (Exception ex) {
            throw new Exception(String.format(
                    "Overpass request failed (street='%s', server='%s', bounds=[%.6f,%.6f,%.6f,%.6f], diagnostics=%s)",
                    normalizedStreet,
                    overpassServer,
                    expandedBounds.getMinLat(),
                    expandedBounds.getMinLon(),
                    expandedBounds.getMaxLat(),
                    expandedBounds.getMaxLon(),
                    summarizeExceptionChain(ex)
            ), ex);
        }
        if (downloaded == null) {
            return new DataSet();
        }

        int downloadedStreetWayCount = collectStreetWays(downloaded, normalizedStreet).size();
        int totalDownloadedWayCount = downloaded.getWays().size();
        Logging.debug(String.format(
                "Reference street fetch: street='%s', downloadedWays=%d, totalWaysInDataset=%d",
                normalizedStreet,
                downloadedStreetWayCount,
                totalDownloadedWayCount
        ));

        Set<Way> keptComponent = keepPlausibleConnectedComponent(downloaded, localStreetWays, normalizedStreet);
        Logging.debug(String.format(
                "Reference street fetch: street='%s', keptWays=%d, removedWays=%d",
                normalizedStreet,
                keptComponent.size(),
                Math.max(downloadedStreetWayCount - keptComponent.size(), 0)
        ));
        if (keptComponent.isEmpty()) {
            return new DataSet();
        }
        removeUnkeptWays(downloaded, keptComponent);
        return downloaded;
    }

    private String buildStreetReferenceQuery(String streetName) {
        String escapedStreet = escapeOverpassString(normalize(streetName));
        return "[out:xml][timeout:25];"
                + "way[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|service|living_street)$\"][\"name\"=\""
                + escapedStreet + "\"]({{bbox}});"
                + "(._;>;);"
                + "out meta;";
    }

    private Set<Way> keepPlausibleConnectedComponent(DataSet downloaded, List<Way> localStreetWays, String normalizedStreet) {
        List<Way> downloadedStreetWays = collectStreetWays(downloaded, normalizedStreet);
        if (downloadedStreetWays.isEmpty()) {
            return Set.of();
        }

        List<LatLon> localEndpoints = collectEndpoints(localStreetWays);
        List<LatLon> localAllNodes = collectAllNodeCoords(localStreetWays);

        Set<Way> seedWays = new HashSet<>();
        for (Way way : downloadedStreetWays) {
            if (way == null) {
                continue;
            }
            if (isWayNearLocalStreet(way, localEndpoints, localAllNodes)) {
                seedWays.add(way);
            }
        }
        if (seedWays.isEmpty()) {
            return Set.of();
        }

        Map<Node, List<Way>> endpointIndex = buildEndpointIndex(downloadedStreetWays);
        Set<Way> keep = new HashSet<>(seedWays);
        ArrayDeque<Way> queue = new ArrayDeque<>(seedWays);
        while (!queue.isEmpty()) {
            Way current = queue.removeFirst();
            for (Node endpoint : endpointsOf(current)) {
                if (endpoint == null) {
                    continue;
                }
                List<Way> connected = endpointIndex.get(endpoint);
                if (connected == null) {
                    continue;
                }
                for (Way neighbor : connected) {
                    if (neighbor != null && keep.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        return keep;
    }

    private void removeUnkeptWays(DataSet dataSet, Set<Way> keepWays) {
        List<PrimitiveId> toRemove = new ArrayList<>();
        for (Way way : dataSet.getWays()) {
            if (way == null || keepWays.contains(way)) {
                continue;
            }
            toRemove.add(way.getPrimitiveId());
        }
        if (!toRemove.isEmpty()) {
            dataSet.removePrimitives(toRemove);
        }
    }

    private boolean isWayNearLocalStreet(Way way, List<LatLon> localEndpoints, List<LatLon> localAllNodes) {
        for (Node endpoint : endpointsOf(way)) {
            LatLon endpointCoor = endpoint != null ? endpoint.getCoor() : null;
            if (isNearAny(endpointCoor, localEndpoints, ENDPOINT_NEAR_METERS)) {
                return true;
            }
        }

        for (Node node : way.getNodes()) {
            LatLon coor = node != null ? node.getCoor() : null;
            if (isNearAny(coor, localAllNodes, ANY_NODE_NEAR_METERS)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearAny(LatLon target, List<LatLon> candidates, double maxMeters) {
        if (target == null || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (LatLon candidate : candidates) {
            if (candidate != null && target.distance(candidate) <= maxMeters) {
                return true;
            }
        }
        return false;
    }

    private Map<Node, List<Way>> buildEndpointIndex(List<Way> ways) {
        Map<Node, List<Way>> index = new HashMap<>();
        for (Way way : ways) {
            for (Node endpoint : endpointsOf(way)) {
                if (endpoint == null) {
                    continue;
                }
                index.computeIfAbsent(endpoint, ignored -> new ArrayList<>()).add(way);
            }
        }
        return index;
    }

    private List<LatLon> collectEndpoints(List<Way> ways) {
        List<LatLon> endpoints = new ArrayList<>();
        for (Way way : ways) {
            for (Node node : endpointsOf(way)) {
                if (node != null && node.getCoor() != null) {
                    endpoints.add(node.getCoor());
                }
            }
        }
        return endpoints;
    }

    private List<LatLon> collectAllNodeCoords(List<Way> ways) {
        List<LatLon> coords = new ArrayList<>();
        for (Way way : ways) {
            if (way == null) {
                continue;
            }
            for (Node node : way.getNodes()) {
                if (node != null && node.getCoor() != null) {
                    coords.add(node.getCoor());
                }
            }
        }
        return coords;
    }

    private List<Node> endpointsOf(Way way) {
        List<Node> endpoints = new ArrayList<>(2);
        if (way == null || way.getNodesCount() == 0) {
            return endpoints;
        }
        endpoints.add(way.firstNode());
        if (way.getNodesCount() > 1) {
            endpoints.add(way.lastNode());
        }
        return endpoints;
    }

    private List<Way> collectStreetWays(DataSet dataSet, String streetName) {
        List<Way> result = new ArrayList<>();
        for (Way way : dataSet.getWays()) {
            if (way == null || !way.isUsable() || !way.hasKey("highway")) {
                continue;
            }
            if (!normalize(way.get("name")).equalsIgnoreCase(streetName)) {
                continue;
            }
            result.add(way);
        }
        return result;
    }

    private String escapeOverpassString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private Bounds mergedDataSourceBounds(Collection<Bounds> boundsCollection) {
        if (boundsCollection == null || boundsCollection.isEmpty()) {
            return null;
        }

        Bounds merged = null;
        for (Bounds bounds : boundsCollection) {
            if (bounds == null) {
                continue;
            }
            if (merged == null) {
                merged = new Bounds(bounds);
            } else {
                merged.extend(bounds);
            }
        }
        return merged;
    }

    private Bounds expand(Bounds bounds) {
        double minLat = bounds.getMinLat();
        double minLon = bounds.getMinLon();
        double maxLat = bounds.getMaxLat();
        double maxLon = bounds.getMaxLon();

        double latSpan = Math.abs(maxLat - minLat);
        double lonSpan = Math.abs(maxLon - minLon);
        double latPad = Math.min(MAX_EXPAND_DEGREES, Math.max(MIN_EXPAND_DEGREES, latSpan * EXPAND_RATIO));
        double lonPad = Math.min(MAX_EXPAND_DEGREES, Math.max(MIN_EXPAND_DEGREES, lonSpan * EXPAND_RATIO));

        return new Bounds(minLat - latPad, minLon - lonPad, maxLat + latPad, maxLon + lonPad);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String summarizeExceptionChain(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String primary = throwable.getClass().getSimpleName() + ": " + nonEmptyMessage(throwable.getMessage());
        Throwable cause = throwable.getCause();
        if (cause == null) {
            return primary;
        }
        return primary + " | cause=" + cause.getClass().getSimpleName() + ": " + nonEmptyMessage(cause.getMessage());
    }

    private String nonEmptyMessage(String message) {
        String normalized = normalize(message);
        return normalized.isEmpty() ? "(no message)" : normalized;
    }
}
