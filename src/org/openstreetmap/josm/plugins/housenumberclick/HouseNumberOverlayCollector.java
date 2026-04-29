package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

/**
 * Collects and normalizes read-only address carriers near the locally resolved selected street segment,
 * including building, entrance-node, address-node, and other address-bearing objects.
 */
final class HouseNumberOverlayCollector {

    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*([^\\d].*)?$");
    private static final double MAX_BUILDING_DISTANCE_TO_SELECTED_STREET_METERS = 400.0;
    private final AddressEntryCollector addressEntryCollector = new AddressEntryCollector();

    List<HouseNumberOverlayEntry> collect(DataSet dataSet, StreetOption selectedStreet,
            StreetNameCollector.StreetIndex streetIndex, Way seedWayHint) {
        if (dataSet == null || selectedStreet == null || !selectedStreet.isValid()) {
            return new ArrayList<>();
        }

        StreetNameCollector.StreetIndex effectiveStreetIndex = streetIndex != null
                ? streetIndex
                : StreetNameCollector.collectStreetIndex(dataSet);
        List<Way> selectedStreetWays = effectiveStreetIndex.getLocalStreetChainWays(selectedStreet, seedWayHint);

        Map<Long, HouseNumberOverlayEntry> entriesByCanonicalPrimitiveId = new LinkedHashMap<>();
        CollectionStats stats = new CollectionStats();
        stats.selectedStreetWays = selectedStreetWays.size();

        for (AddressEntry entry : addressEntryCollector.collect(dataSet)) {
            collectAddressEntry(entriesByCanonicalPrimitiveId, entry, selectedStreet, effectiveStreetIndex, selectedStreetWays, stats);
        }

        List<HouseNumberOverlayEntry> entries = new ArrayList<>(entriesByCanonicalPrimitiveId.values());
        entries.sort(createComparator());
        logCollectionResult(selectedStreet, stats, entries.size());
        return entries;
    }

    private void collectAddressEntry(Map<Long, HouseNumberOverlayEntry> entriesByCanonicalPrimitiveId, AddressEntry entry,
            StreetOption selectedStreet, StreetNameCollector.StreetIndex streetIndex,
            List<Way> selectedStreetWays, CollectionStats stats) {
        stats.scannedPrimitives++;

        if (entry == null || normalize(entry.getHouseNumber()).isEmpty()) {
            stats.rejectedNotAddressedForStreet++;
            return;
        }

        String entryStreet = normalize(entry.getStreet());
        if (entryStreet.isEmpty() || !entryStreet.equalsIgnoreCase(normalize(selectedStreet.getBaseStreetName()))) {
            stats.rejectedNotAddressedForStreet++;
            return;
        }

        OsmPrimitive primitive = entry.getPrimitive();
        if (primitive == null || !primitive.isUsable()) {
            stats.rejectedNotAddressedForStreet++;
            return;
        }
        long canonicalId = primitive.getUniqueId();
        if (entriesByCanonicalPrimitiveId.containsKey(canonicalId)) {
            stats.rejectedCanonicalDuplicate++;
            return;
        }

        StreetOption primitiveStreet = streetIndex
                .resolveForBaseStreetAndPrimitive(entryStreet, primitive);
        if (primitiveStreet == null || !selectedStreet.getClusterId().equals(primitiveStreet.getClusterId())) {
            stats.rejectedNotAddressedForStreet++;
            return;
        }

        HouseNumberOverlayEntry overlayEntry = buildEntry(entry, selectedStreetWays,
                entriesByCanonicalPrimitiveId.size(), stats);
        if (overlayEntry != null) {
            entriesByCanonicalPrimitiveId.put(canonicalId, overlayEntry);
            stats.canonicalizedPrimitives = entriesByCanonicalPrimitiveId.size();
        }
    }

    private Comparator<HouseNumberOverlayEntry> createComparator() {
        return Comparator
                .comparingInt(HouseNumberOverlayEntry::getNumberPart)
                .thenComparing(HouseNumberOverlayEntry::getSuffixPart, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HouseNumberOverlayEntry::getHouseNumber, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(HouseNumberOverlayEntry::getStableIndex);
    }

    private HouseNumberOverlayEntry buildEntry(AddressEntry addressEntry,
            List<Way> selectedStreetWays, int stableIndex, CollectionStats stats) {
        EastNorth labelPoint = addressEntry.getLabelPoint();
        if (labelPoint == null) {
            stats.rejectedMissingLabelPoint++;
            return null;
        }
        if (!isNearSelectedStreet(labelPoint, selectedStreetWays)) {
            stats.rejectedByDistance++;
            return null;
        }

        String street = normalize(addressEntry.getStreet());
        String postcode = normalize(addressEntry.getPostcode());
        if (postcode.isEmpty()) {
            stats.acceptedMissingPostcode++;
        }
        String houseNumber = normalize(addressEntry.getHouseNumber());
        ParsedHouseNumber parsedHouseNumber = parseHouseNumber(houseNumber);
        return new HouseNumberOverlayEntry(
                addressEntry.getPrimitive(),
                street,
                postcode,
                houseNumber,
                parsedHouseNumber.numberPart,
                parsedHouseNumber.suffixPart,
                labelPoint,
                stableIndex,
                addressEntry.getCarrierType(),
                addressEntry.getAssociatedBuilding(),
                addressEntry.isIndirectBuildingAddress()
        );
    }

    private boolean isNearSelectedStreet(EastNorth labelPoint, List<Way> streetWays) {
        if (labelPoint == null || streetWays == null || streetWays.isEmpty()) {
            return false;
        }
        double limitSquared = MAX_BUILDING_DISTANCE_TO_SELECTED_STREET_METERS * MAX_BUILDING_DISTANCE_TO_SELECTED_STREET_METERS;
        double bestSquared = Double.POSITIVE_INFINITY;
        for (Way way : streetWays) {
            if (way == null || !way.isUsable()) {
                continue;
            }
            List<Node> nodes = way.getNodes();
            for (int i = 1; i < nodes.size(); i++) {
                Node first = nodes.get(i - 1);
                Node second = nodes.get(i);
                EastNorth firstPoint = resolveNodeEastNorth(first);
                EastNorth secondPoint = resolveNodeEastNorth(second);
                if (first == null || second == null || firstPoint == null || secondPoint == null) {
                    continue;
                }
                double distanceSquared = distanceSquaredToSegment(labelPoint, firstPoint, secondPoint);
                if (distanceSquared < bestSquared) {
                    bestSquared = distanceSquared;
                    if (bestSquared <= limitSquared) {
                        return true;
                    }
                }
            }
        }
        return bestSquared <= limitSquared;
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

    private void logCollectionResult(StreetOption selectedStreet, CollectionStats stats, int collected) {
        // Debug logging intentionally disabled.
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

    private EastNorth resolveNodeEastNorth(Node node) {
        if (node == null || !node.isUsable()) {
            return null;
        }
        if (org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection() != null) {
            return node.getEastNorth();
        }
        org.openstreetmap.josm.data.coor.LatLon coor = node.getCoor();
        if (coor == null) {
            return null;
        }
        return new EastNorth(coor.lon(), coor.lat());
    }

    /**
     * Parsed representation of a house number split into sortable numeric and suffix parts.
     */
    private static final class ParsedHouseNumber {
        private final int numberPart;
        private final String suffixPart;

        ParsedHouseNumber(int numberPart, String suffixPart) {
            this.numberPart = numberPart;
            this.suffixPart = suffixPart;
        }
    }

    /**
     * Aggregated rejection counters used for overlay collection diagnostics.
     */
    private static final class CollectionStats {
        private int selectedStreetWays;
        private int scannedPrimitives;
        private int canonicalizedPrimitives;
        private int rejectedNotAddressedForStreet;
        private int rejectedByDistance;
        private int rejectedMissingLabelPoint;
        private int rejectedCanonicalDuplicate;
        private int acceptedMissingPostcode;
    }
}
