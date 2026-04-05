package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        for (Way way : dataSet.getWays()) {
            collectPrimitive(way, countsByStreet);
        }
        for (Relation relation : dataSet.getRelations()) {
            collectPrimitive(relation, countsByStreet);
        }

        List<StreetHouseNumberCountRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countsByStreet.entrySet()) {
            rows.add(new StreetHouseNumberCountRow(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    private void collectPrimitive(OsmPrimitive primitive, Map<String, Integer> countsByStreet) {
        // Count view uses the same addressed-building filter as overlay/overview collectors.
        if (!AddressedBuildingMatcher.isAddressedBuilding(primitive)) {
            return;
        }

        String street = normalize(primitive.get("addr:street"));
        countsByStreet.put(street, countsByStreet.getOrDefault(street, 0) + 1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}


