package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

/**
 * Aggregates and orders house numbers for the selected disambiguated street cluster.
 */
final class HouseNumberOverviewCollector {

    static final String MISSING_NUMBER_PLACEHOLDER = "•";

    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*([A-Za-z]*)");

    List<HouseNumberOverviewRow> collectRows(DataSet dataSet, StreetOption selectedStreet,
            StreetNameCollector.StreetIndex streetIndex) {
        if (dataSet == null || selectedStreet == null || !selectedStreet.isValid()) {
            return new ArrayList<>();
        }

        StreetNameCollector.StreetIndex effectiveStreetIndex = streetIndex != null
                ? streetIndex
                : StreetNameCollector.collectStreetIndex(dataSet);

        Map<Integer, BaseNumberGroup> oddGroups = new TreeMap<>();
        Map<Integer, BaseNumberGroup> evenGroups = new TreeMap<>();

        for (Way way : dataSet.getWays()) {
            collectPrimitive(way, selectedStreet, effectiveStreetIndex, oddGroups, evenGroups);
        }
        for (Relation relation : dataSet.getRelations()) {
            collectPrimitive(relation, selectedStreet, effectiveStreetIndex, oddGroups, evenGroups);
        }

        List<OverviewCellData> oddValues = formatGroups(oddGroups);
        List<OverviewCellData> evenValues = formatGroups(evenGroups);
        return buildRows(oddValues, evenValues);
    }

    private void collectPrimitive(OsmPrimitive primitive, StreetOption selectedStreet,
            StreetNameCollector.StreetIndex streetIndex,
            Map<Integer, BaseNumberGroup> oddGroups, Map<Integer, BaseNumberGroup> evenGroups) {
        // Shared matcher keeps street-specific building filtering consistent across collectors.
        if (!AddressedBuildingMatcher.isAddressedBuildingForStreet(primitive, selectedStreet.getBaseStreetName())) {
            return;
        }
        StreetOption primitiveStreet = streetIndex.resolveForAddressPrimitive(primitive);
        if (primitiveStreet == null || !selectedStreet.getClusterId().equals(primitiveStreet.getClusterId())) {
            return;
        }

        ParsedHouseNumber parsed = parseHouseNumber(primitive.get("addr:housenumber"));
        if (parsed == null) {
            return;
        }

        Map<Integer, BaseNumberGroup> target = parsed.baseNumber % 2 == 0 ? evenGroups : oddGroups;
        BaseNumberGroup group = target.computeIfAbsent(parsed.baseNumber, BaseNumberGroup::new);
        group.addOccurrence(parsed.suffix, primitive, primitive.get("addr:housenumber"));
    }

    private ParsedHouseNumber parseHouseNumber(String rawValue) {
        String value = normalize(rawValue);
        if (value.isEmpty()) {
            return null;
        }

        Matcher matcher = HOUSE_NUMBER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }

        int baseNumber;
        try {
            baseNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        String suffix = normalize(matcher.group(2)).toLowerCase(Locale.ROOT);
        return new ParsedHouseNumber(baseNumber, suffix);
    }

    private List<OverviewCellData> formatGroups(Map<Integer, BaseNumberGroup> groups) {
        List<OverviewCellData> values = new ArrayList<>();
        if (groups.isEmpty()) {
            return values;
        }

        int minBaseNumber = Integer.MAX_VALUE;
        int maxBaseNumber = Integer.MIN_VALUE;
        for (Integer baseNumber : groups.keySet()) {
            if (baseNumber == null) {
                continue;
            }
            if (baseNumber < minBaseNumber) {
                minBaseNumber = baseNumber;
            }
            if (baseNumber > maxBaseNumber) {
                maxBaseNumber = baseNumber;
            }
        }

        if (minBaseNumber == Integer.MAX_VALUE || maxBaseNumber == Integer.MIN_VALUE) {
            return values;
        }

        for (int baseNumber = minBaseNumber; baseNumber <= maxBaseNumber; baseNumber += 2) {
            BaseNumberGroup group = groups.get(baseNumber);
            if (group == null) {
                values.add(new OverviewCellData(MISSING_NUMBER_PLACEHOLDER, null, List.of(), List.of(), false));
            } else {
                values.add(new OverviewCellData(
                        group.formatForOverview(),
                        group.getRepresentativePrimitive(),
                        group.getGroupedPrimitives(),
                        group.getDuplicatePrimitives(),
                        group.hasExactDuplicate()
                ));
            }
        }
        return values;
    }

    private List<HouseNumberOverviewRow> buildRows(List<OverviewCellData> oddValues, List<OverviewCellData> evenValues) {
        List<HouseNumberOverviewRow> rows = new ArrayList<>();
        int rowCount = Math.max(oddValues.size(), evenValues.size());
        for (int i = 0; i < rowCount; i++) {
            OverviewCellData odd = i < oddValues.size() ? oddValues.get(i) : OverviewCellData.empty();
            OverviewCellData even = i < evenValues.size() ? evenValues.get(i) : OverviewCellData.empty();
            rows.add(new HouseNumberOverviewRow(
                    odd.value,
                    even.value,
                    odd.primitive,
                    even.primitive,
                    odd.primitives,
                    even.primitives,
                    odd.duplicatePrimitives,
                    even.duplicatePrimitives,
                    odd.duplicate,
                    even.duplicate
            ));
        }
        return rows;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Parsed house-number token with normalized base number and suffix.
     */
    private static final class ParsedHouseNumber {
        private final int baseNumber;
        private final String suffix;

        ParsedHouseNumber(int baseNumber, String suffix) {
            this.baseNumber = baseNumber;
            this.suffix = suffix;
        }
    }

    /**
     * Groups occurrences of one base number and tracks both grouped and exact-duplicate primitives.
     */
    private static final class BaseNumberGroup {
        private final int baseNumber;
        private final TreeSet<String> suffixes = new TreeSet<>();
        private final Map<String, Integer> exactHouseNumberCounts = new HashMap<>();
        private final Map<String, Set<OsmPrimitive>> exactHouseNumberPrimitives = new HashMap<>();
        private final Set<OsmPrimitive> groupedPrimitives = new LinkedHashSet<>();
        private OsmPrimitive representativePrimitive;

        BaseNumberGroup(int baseNumber) {
            this.baseNumber = baseNumber;
        }

        void addOccurrence(String suffix, OsmPrimitive primitive, String fullHouseNumber) {
            if (suffix != null && !suffix.isBlank()) {
                suffixes.add(suffix);
            }
            String normalizedFullHouseNumber = normalizeHouseNumberKey(fullHouseNumber);
            if (!normalizedFullHouseNumber.isEmpty()) {
                exactHouseNumberCounts.put(
                        normalizedFullHouseNumber,
                        exactHouseNumberCounts.getOrDefault(normalizedFullHouseNumber, 0) + 1
                );
                if (primitive != null) {
                    exactHouseNumberPrimitives
                            .computeIfAbsent(normalizedFullHouseNumber, ignored -> new LinkedHashSet<>())
                            .add(primitive);
                }
            }
            if (representativePrimitive == null && primitive != null) {
                representativePrimitive = primitive;
            }
            if (primitive != null) {
                groupedPrimitives.add(primitive);
            }
        }

        String formatForOverview() {
            String formatted = Integer.toString(baseNumber);
            return hasExactDuplicate() ? formatted + " (dup)" : formatted;
        }

        private int findHighestDuplicateCount() {
            int highest = 0;
            for (Integer count : exactHouseNumberCounts.values()) {
                if (count != null && count > highest) {
                    highest = count;
                }
            }
            return highest;
        }

        boolean hasExactDuplicate() {
            // Overview intentionally flags duplicate *house numbers* per selected street cluster.
            // Full-address duplicate checks (street+postcode+housenumber) remain in overlay/count views.
            return findHighestDuplicateCount() > 1;
        }

        private String normalizeHouseNumberKey(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        OsmPrimitive getRepresentativePrimitive() {
            return representativePrimitive;
        }

        List<OsmPrimitive> getGroupedPrimitives() {
            return new ArrayList<>(groupedPrimitives);
        }

        List<OsmPrimitive> getDuplicatePrimitives() {
            Set<OsmPrimitive> duplicates = new LinkedHashSet<>();
            for (Map.Entry<String, Integer> entry : exactHouseNumberCounts.entrySet()) {
                if (entry == null || entry.getValue() == null || entry.getValue() <= 1) {
                    continue;
                }
                Set<OsmPrimitive> primitives = exactHouseNumberPrimitives.get(entry.getKey());
                if (primitives != null) {
                    duplicates.addAll(primitives);
                }
            }
            return new ArrayList<>(duplicates);
        }
    }

    /**
     * Intermediate formatted cell data used while composing final overview rows.
     */
    private static final class OverviewCellData {
        private final String value;
        private final OsmPrimitive primitive;
        private final List<OsmPrimitive> primitives;
        private final List<OsmPrimitive> duplicatePrimitives;
        private final boolean duplicate;

        private OverviewCellData(String value, OsmPrimitive primitive, List<OsmPrimitive> primitives,
                List<OsmPrimitive> duplicatePrimitives, boolean duplicate) {
            this.value = value;
            this.primitive = primitive;
            this.primitives = primitives == null ? List.of() : primitives;
            this.duplicatePrimitives = duplicatePrimitives == null ? List.of() : duplicatePrimitives;
            this.duplicate = duplicate;
        }

        private static OverviewCellData empty() {
            return new OverviewCellData("", null, List.of(), List.of(), false);
        }
    }
}
