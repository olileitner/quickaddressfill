package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Applies address (including optional city/country) and building tags via JOSM commands,
 * including relation-aware write targets.
 */
final class BuildingTagApplier {

    private BuildingTagApplier() {
        // Utility class
    }

    static void applyAddress(OsmPrimitive building, String streetName, String postcode, String city, String country,
            String buildingType, String houseNumber) {
        String normalizedStreet = normalize(streetName);
        if (building == null || normalizedStreet.isEmpty()) {
            return;
        }

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("addr:street", normalizedStreet);

        String normalizedPostcode = normalize(postcode);
        if (!normalizedPostcode.isEmpty()) {
            tags.put("addr:postcode", normalizedPostcode);
        }

        String normalizedCity = normalize(city);
        if (!normalizedCity.isEmpty()) {
            tags.put("addr:city", normalizedCity);
        }

        String normalizedCountry = normalize(country);
        if (!normalizedCountry.isEmpty()) {
            tags.put("addr:country", normalizedCountry);
        }

        String normalizedBuildingType = normalize(buildingType);
        if (!normalizedBuildingType.isEmpty()) {
            tags.put("building", normalizedBuildingType);
        }

        String normalizedHouseNumber = normalize(houseNumber);
        if (!normalizedHouseNumber.isEmpty()) {
            tags.put("addr:housenumber", normalizedHouseNumber);
        }

        ChangePropertyCommand command = new ChangePropertyCommand(
                Collections.singleton(building),
                tags
        );
        UndoRedoHandler.getInstance().add(command);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
