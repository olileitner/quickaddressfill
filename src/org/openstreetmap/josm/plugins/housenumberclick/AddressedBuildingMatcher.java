package org.openstreetmap.josm.plugins.housenumberclick;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

final class AddressedBuildingMatcher {

    private AddressedBuildingMatcher() {
        // Utility class
    }

    static boolean isAddressedBuilding(OsmPrimitive primitive) {
        // Base filter shared by all collectors: usable addressed building geometry only.
        if (primitive == null || !primitive.isUsable() || !primitive.hasKey("building")) {
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
        return !houseNumber.isEmpty() && !street.isEmpty();
    }

    static boolean isAddressedBuildingForStreet(OsmPrimitive primitive, String streetName) {
        // Street-scoped variant used when a collector operates on one selected street.
        if (!isAddressedBuilding(primitive)) {
            return false;
        }

        String normalizedStreetName = normalize(streetName);
        if (normalizedStreetName.isEmpty()) {
            return false;
        }
        return normalize(primitive.get("addr:street")).equalsIgnoreCase(normalizedStreetName);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}



