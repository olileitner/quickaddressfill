package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

final class StreetHouseNumberCountCollector {

    List<StreetHouseNumberCountRow> collectRows(DataSet dataSet) {
        if (dataSet == null) {
            return new ArrayList<>();
        }

        Map<String, Integer> countsByStreet = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Set<String>> seenHouseNumbersByStreet = new HashMap<>();
        Map<String, Boolean> hasDuplicateByStreet = new HashMap<>();
        Set<String> allStreetNames = new HashSet<>();

        for (Way way : dataSet.getWays()) {
            collectStreetName(way, allStreetNames);
            collectPrimitive(way, countsByStreet, seenHouseNumbersByStreet, hasDuplicateByStreet);
        }
        for (Relation relation : dataSet.getRelations()) {
            collectPrimitive(relation, countsByStreet, seenHouseNumbersByStreet, hasDuplicateByStreet);
        }

        // Ensure streets without addressed buildings are visible in the table as count 0.
        for (String streetName : allStreetNames) {
            countsByStreet.putIfAbsent(streetName, 0);
        }

        List<StreetHouseNumberCountRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countsByStreet.entrySet()) {
            rows.add(new StreetHouseNumberCountRow(
                    entry.getKey(),
                    entry.getValue(),
                    Boolean.TRUE.equals(hasDuplicateByStreet.get(entry.getKey()))
            ));
        }
        return rows;
    }

    private void collectPrimitive(OsmPrimitive primitive, Map<String, Integer> countsByStreet,
            Map<String, Set<String>> seenHouseNumbersByStreet, Map<String, Boolean> hasDuplicateByStreet) {
        // Count view uses the same addressed-building filter as overlay/overview collectors.
        if (!AddressedBuildingMatcher.isAddressedBuilding(primitive)) {
            return;
        }

        String street = normalize(primitive.get("addr:street"));
        String houseNumberKey = normalizeHouseNumberKey(primitive.get("addr:housenumber"));
        countsByStreet.put(street, countsByStreet.getOrDefault(street, 0) + 1);

        Set<String> seenHouseNumbers = seenHouseNumbersByStreet.computeIfAbsent(street, key -> new HashSet<>());
        if (!seenHouseNumbers.add(houseNumberKey)) {
            hasDuplicateByStreet.put(street, true);
        }
    }

    private void collectStreetName(Way way, Set<String> allStreetNames) {
        if (way == null || !way.isUsable() || !way.hasTag("highway")) {
            return;
        }
        String street = normalize(way.get("name"));
        if (!street.isEmpty()) {
            allStreetNames.add(street);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeHouseNumberKey(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }
}


