package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

final class HouseNumberOverviewCollector {

    static final String MISSING_NUMBER_PLACEHOLDER = "•";

    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*([A-Za-z]*)");

    List<HouseNumberOverviewRow> collectRows(DataSet dataSet, String selectedStreet) {
        String normalizedStreet = normalize(selectedStreet);
        if (dataSet == null || normalizedStreet.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, BaseNumberGroup> oddGroups = new TreeMap<>();
        Map<Integer, BaseNumberGroup> evenGroups = new TreeMap<>();

        for (Way way : dataSet.getWays()) {
            collectPrimitive(way, normalizedStreet, oddGroups, evenGroups);
        }
        for (Relation relation : dataSet.getRelations()) {
            collectPrimitive(relation, normalizedStreet, oddGroups, evenGroups);
        }

        List<OverviewCellData> oddValues = formatGroups(oddGroups);
        List<OverviewCellData> evenValues = formatGroups(evenGroups);
        return buildRows(oddValues, evenValues);
    }

    private void collectPrimitive(OsmPrimitive primitive, String selectedStreet,
            Map<Integer, BaseNumberGroup> oddGroups, Map<Integer, BaseNumberGroup> evenGroups) {
        // Shared matcher keeps street-specific building filtering consistent across collectors.
        if (!AddressedBuildingMatcher.isAddressedBuildingForStreet(primitive, selectedStreet)) {
            return;
        }

        ParsedHouseNumber parsed = parseHouseNumber(primitive.get("addr:housenumber"));
        if (parsed == null) {
            return;
        }

        Map<Integer, BaseNumberGroup> target = parsed.baseNumber % 2 == 0 ? evenGroups : oddGroups;
        BaseNumberGroup group = target.computeIfAbsent(parsed.baseNumber, BaseNumberGroup::new);
        group.addOccurrence(parsed.suffix, primitive);
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
                values.add(new OverviewCellData(MISSING_NUMBER_PLACEHOLDER, null));
            } else {
                values.add(new OverviewCellData(group.formatForOverview(), group.getRepresentativePrimitive()));
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
            rows.add(new HouseNumberOverviewRow(odd.value, even.value, odd.primitive, even.primitive));
        }
        return rows;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ParsedHouseNumber {
        private final int baseNumber;
        private final String suffix;

        ParsedHouseNumber(int baseNumber, String suffix) {
            this.baseNumber = baseNumber;
            this.suffix = suffix;
        }
    }

    private static final class BaseNumberGroup {
        private final int baseNumber;
        private final TreeSet<String> suffixes = new TreeSet<>();
        private OsmPrimitive representativePrimitive;

        BaseNumberGroup(int baseNumber) {
            this.baseNumber = baseNumber;
        }

        void addOccurrence(String suffix, OsmPrimitive primitive) {
            if (suffix != null && !suffix.isBlank()) {
                suffixes.add(suffix);
            }
            if (representativePrimitive == null && primitive != null) {
                representativePrimitive = primitive;
            }
        }

        String formatForOverview() {
            if (suffixes.isEmpty()) {
                return Integer.toString(baseNumber);
            }
            return baseNumber + " (" + String.join(", ", suffixes) + ")";
        }

        OsmPrimitive getRepresentativePrimitive() {
            return representativePrimitive;
        }
    }

    private static final class OverviewCellData {
        private final String value;
        private final OsmPrimitive primitive;

        private OverviewCellData(String value, OsmPrimitive primitive) {
            this.value = value;
            this.primitive = primitive;
        }

        private static OverviewCellData empty() {
            return new OverviewCellData("", null);
        }
    }
}


