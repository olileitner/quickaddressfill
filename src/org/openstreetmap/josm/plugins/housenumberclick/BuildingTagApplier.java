package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Applies address tags and removes existing addr:* tags via JOSM commands,
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

    static int removeAddressTags(OsmPrimitive building) {
        if (building == null || !building.isUsable()) {
            return 0;
        }

        Map<String, String> tagsToRemove = collectAddressTagsForRemoval(building);

        if (tagsToRemove.isEmpty()) {
            return 0;
        }

        Map<String, String> removalInstruction = new LinkedHashMap<>();
        for (String key : tagsToRemove.keySet()) {
            if (key != null && !key.isEmpty()) {
                removalInstruction.put(key, null);
            }
        }

        ChangePropertyCommand command = new ChangePropertyCommand(
                Collections.singleton(building),
                removalInstruction
        );
        UndoRedoHandler.getInstance().add(command);
        return removalInstruction.size();
    }

    static Map<String, String> collectAddressTagsForRemoval(OsmPrimitive building) {
        Map<String, String> addressTags = new LinkedHashMap<>();
        if (building == null || !building.isUsable()) {
            return addressTags;
        }
        for (String key : building.keySet()) {
            if (key != null && key.startsWith("addr:")) {
                addressTags.put(key, building.get(key));
            }
        }
        return addressTags;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
