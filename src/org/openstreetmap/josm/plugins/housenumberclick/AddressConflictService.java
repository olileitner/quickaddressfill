package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

final class AddressConflictService {

    static final class ConflictField {
        private final String key;
        private final String existingValue;
        private final String proposedValue;

        ConflictField(String key, String existingValue, String proposedValue) {
            this.key = key;
            this.existingValue = normalize(existingValue);
            this.proposedValue = normalize(proposedValue);
        }

        String getKey() {
            return key;
        }

        String getExistingValue() {
            return existingValue;
        }

        String getProposedValue() {
            return proposedValue;
        }
    }

    static final class ConflictAnalysis {
        private final boolean hasConflict;
        private final String overwrittenStreet;
        private final List<ConflictField> differingFields;

        ConflictAnalysis(boolean hasConflict, String overwrittenStreet, List<ConflictField> differingFields) {
            this.hasConflict = hasConflict;
            this.overwrittenStreet = normalize(overwrittenStreet);
            this.differingFields = differingFields == null ? List.of() : Collections.unmodifiableList(differingFields);
        }

        boolean hasConflict() {
            return hasConflict;
        }

        String getOverwrittenStreet() {
            return overwrittenStreet;
        }

        List<ConflictField> getDifferingFields() {
            return differingFields;
        }
    }
    ConflictAnalysis analyze(
            OsmPrimitive building,
            String proposedStreet,
            String proposedPostcode,
            String proposedHouseNumber,
            String proposedBuildingType
    ) {
        String normalizedProposedStreet = normalize(proposedStreet);
        if (building == null) {
            return new ConflictAnalysis(false, normalizedProposedStreet, List.of());
        }

        String existingStreet = normalize(building.get("addr:street"));
        String existingPostcode = normalize(building.get("addr:postcode"));
        String existingHouseNumber = normalize(building.get("addr:housenumber"));
        String existingBuildingType = normalize(building.get("building"));

        String normalizedProposedPostcode = normalize(proposedPostcode);
        String normalizedProposedHouseNumber = normalize(proposedHouseNumber);
        String normalizedProposedBuildingType = normalize(proposedBuildingType);

        boolean streetConflict = !existingStreet.isEmpty() && !existingStreet.equals(normalizedProposedStreet);

        // Postcode conflict only matters when a new postcode is explicitly provided.
        boolean postcodeConflict = !normalizedProposedPostcode.isEmpty()
                && !existingPostcode.isEmpty()
                && !existingPostcode.equals(normalizedProposedPostcode);

        boolean houseNumberDiff = !normalizedProposedHouseNumber.isEmpty()
                && !existingHouseNumber.isEmpty()
                && !existingHouseNumber.equals(normalizedProposedHouseNumber);

        boolean buildingTypeConflict = !normalizedProposedBuildingType.isEmpty()
                && !existingBuildingType.isEmpty()
                && !existingBuildingType.equals(normalizedProposedBuildingType)
                && !"yes".equalsIgnoreCase(existingBuildingType);

        List<ConflictField> differingFields = new ArrayList<>();
        if (streetConflict) {
            differingFields.add(new ConflictField("addr:street", existingStreet, normalizedProposedStreet));
        }
        if (postcodeConflict) {
            differingFields.add(new ConflictField("addr:postcode", existingPostcode, normalizedProposedPostcode));
        }
        if (houseNumberDiff) {
            differingFields.add(new ConflictField("addr:housenumber", existingHouseNumber, normalizedProposedHouseNumber));
        }
        if (buildingTypeConflict) {
            differingFields.add(new ConflictField("building", existingBuildingType, normalizedProposedBuildingType));
        }

        String overwrittenStreet = existingStreet.isEmpty() ? normalizedProposedStreet : existingStreet;
        return new ConflictAnalysis(streetConflict || postcodeConflict || buildingTypeConflict, overwrittenStreet, differingFields);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

