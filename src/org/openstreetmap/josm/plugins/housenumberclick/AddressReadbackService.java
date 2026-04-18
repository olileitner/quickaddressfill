package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.event.MouseEvent;
import java.util.List;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapFrame;

/**
 * Reads street, postcode, city, country, house number, and building type from clicked buildings
 * or fallback street ways.
 */
final class AddressReadbackService {

    /**
     * Readback payload containing street/address values and the source category.
     */
    static final class AddressReadbackResult {
        private final String street;
        private final String postcode;
        private final String city;
        private final String country;
        private final String buildingType;
        private final String houseNumber;
        private final String source;

        AddressReadbackResult(String street, String postcode, String city, String country,
                String buildingType, String houseNumber, String source) {
            this.street = normalize(street);
            this.postcode = normalize(postcode);
            this.city = normalize(city);
            this.country = normalize(country);
            this.buildingType = normalize(buildingType);
            this.houseNumber = normalize(houseNumber);
            this.source = normalize(source);
        }

        String getStreet() {
            return street;
        }

        String getPostcode() {
            return postcode;
        }

        String getCity() {
            return city;
        }

        String getCountry() {
            return country;
        }

        String getBuildingType() {
            return buildingType;
        }

        String getHouseNumber() {
            return houseNumber;
        }

        String getSource() {
            return source;
        }
    }

    AddressReadbackResult readFromBuilding(OsmPrimitive building, String currentBuildingType) {
        if (building == null) {
            return null;
        }
        String readbackBuildingType = normalize(building.get("building"));
        if (readbackBuildingType.isEmpty()) {
            // Fallback to current UI/controller value only when the clicked object has no building tag.
            readbackBuildingType = normalize(currentBuildingType);
        }
        return new AddressReadbackResult(
                building.get("addr:street"),
                building.get("addr:postcode"),
                building.get("addr:city"),
                building.get("addr:country"),
                readbackBuildingType,
                building.get("addr:housenumber"),
                "address-tags"
        );
    }

    AddressReadbackResult readFromStreetFallback(String streetFromClick, String currentPostcode, String currentBuildingType) {
        String normalizedStreet = normalize(streetFromClick);
        if (normalizedStreet.isEmpty()) {
            return null;
        }
        return new AddressReadbackResult(normalizedStreet, "", "", "", "", "", "street-fallback");
    }

    String resolveStreetNameAtClick(MapFrame map, MouseEvent e) {
        if (map == null || map.mapView == null) {
            return null;
        }
        List<OsmPrimitive> nearby = map.mapView.getAllNearest(e.getPoint(), this::isNamedStreetCandidate);
        return resolveStreetNameFromCandidates(nearby);
    }

    String resolveStreetNameFromCandidates(List<OsmPrimitive> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (OsmPrimitive primitive : candidates) {
            if (!isNamedStreetCandidate(primitive)) {
                continue;
            }
            String name = normalize(primitive.get("name"));
            if (!name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    private boolean isNamedStreetCandidate(OsmPrimitive primitive) {
        return primitive instanceof Way
                && primitive.isUsable()
                && primitive.hasTag("highway")
                && !normalize(primitive.get("name")).isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
