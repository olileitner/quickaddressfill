package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

/**
 * Collects building diagnostics used by completeness and postcode overview layers,
 * canonicalizing relation/outer-way representations of the same real building.
 */
final class BuildingOverviewCollector {

    static final double MIN_BUILDING_AREA = 25.0;
    private static final double METERS_PER_DEGREE_LAT = 111_132.0;
    private static final double METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0;

    List<BuildingOverviewEntry> collect(DataSet dataSet) {
        if (dataSet == null) {
            return List.of();
        }

        int rawScannedPrimitives = 0;
        List<CandidateEntry> candidates = new ArrayList<>();
        Map<Long, CandidateEntry> canonicalCandidatesByPrimitiveId = new HashMap<>();
        for (Way way : dataSet.getWays()) {
            rawScannedPrimitives++;
            collectPrimitive(canonicalCandidatesByPrimitiveId, way);
        }
        for (Relation relation : dataSet.getRelations()) {
            rawScannedPrimitives++;
            collectPrimitive(canonicalCandidatesByPrimitiveId, relation);
        }
        candidates.addAll(canonicalCandidatesByPrimitiveId.values());

        Map<String, Integer> duplicateAddressCounts = new HashMap<>();
        Map<String, DuplicateAddressGroupStats> duplicateAddressGroups = new HashMap<>();
        for (CandidateEntry candidate : candidates) {
            String duplicateBaseKey = candidate.duplicateAddressBaseKey;
            if (!duplicateBaseKey.isEmpty()) {
                duplicateAddressCounts.merge(duplicateBaseKey, 1, Integer::sum);
                duplicateAddressGroups
                        .computeIfAbsent(duplicateBaseKey, ignored -> new DuplicateAddressGroupStats())
                        .addCandidate(candidate.duplicateAddressCityKey);
            }
        }
        List<BuildingOverviewEntry> entries = new ArrayList<>(candidates.size());
        for (CandidateEntry candidate : candidates) {
            DuplicateAddressGroupStats duplicateGroup = duplicateAddressGroups.get(candidate.duplicateAddressBaseKey);
            boolean hasDuplicateExactAddress = duplicateGroup != null
                    && duplicateGroup.hasDuplicateFor(candidate.duplicateAddressCityKey);
            entries.add(new BuildingOverviewEntry(
                    candidate.primitive,
                    candidate.hasHouseNumber,
                    candidate.hasNoAddressData,
                    candidate.hasMissingRequiredAddressFields,
                    candidate.hasMissingStreet,
                    candidate.hasMissingPostcode,
                    candidate.hasMissingHouseNumber,
                    candidate.hasMissingCity,
                    candidate.hasMissingCountry,
                    candidate.hasOnlyCountryMissing,
                    candidate.hasMisplacedHouseNumber,
                    hasDuplicateExactAddress
            ));
        }
        return entries;
    }

    private void collectPrimitive(Map<Long, CandidateEntry> canonicalCandidatesByPrimitiveId, OsmPrimitive primitive) {
        if (!AddressedBuildingMatcher.isBuildingGeometry(primitive)) {
            return;
        }

        OsmPrimitive canonicalPrimitive = resolveCanonicalPrimitive(primitive);
        if (canonicalPrimitive == null || !AddressedBuildingMatcher.isBuildingGeometry(canonicalPrimitive)) {
            return;
        }
        long canonicalId = canonicalPrimitive.getUniqueId();
        if (canonicalCandidatesByPrimitiveId.containsKey(canonicalId)) {
            return;
        }

        double primitiveArea = computeArea(canonicalPrimitive);
        if (primitiveArea < MIN_BUILDING_AREA) {
            return;
        }

        String street = normalize(canonicalPrimitive.get("addr:street"));
        String postcode = normalize(canonicalPrimitive.get("addr:postcode"));
        String houseNumber = normalize(canonicalPrimitive.get("addr:housenumber"));
        String city = normalize(canonicalPrimitive.get("addr:city"));
        String country = normalize(canonicalPrimitive.get("addr:country"));

        boolean hasStreet = !street.isEmpty();
        boolean hasPostcode = !postcode.isEmpty();
        boolean hasHouseNumber = !houseNumber.isEmpty();
        boolean hasCity = !city.isEmpty();
        boolean hasCountry = !country.isEmpty();
        boolean hasNoAddressData = !hasStreet && !hasPostcode && !hasHouseNumber;
        boolean hasCompleteAddressData = hasStreet && hasPostcode && hasHouseNumber;
        boolean hasMissingRequiredAddressFields = !hasNoAddressData && !hasCompleteAddressData;
        boolean hasMissingStreet = hasMissingRequiredAddressFields && !hasStreet;
        boolean hasMissingPostcode = hasMissingRequiredAddressFields && !hasPostcode;
        boolean hasMissingHouseNumber = hasMissingRequiredAddressFields && !hasHouseNumber;
        boolean hasMissingCity = !hasNoAddressData && !hasCity;
        boolean hasMissingCountry = !hasNoAddressData && !hasCountry;
        boolean hasOnlyCountryMissing = hasStreet && hasPostcode && hasHouseNumber && hasCity && !hasCountry;
        boolean hasMisplacedHouseNumber = !hasHouseNumber && hasMisplacedHouseNumber(canonicalPrimitive);
        String duplicateAddressBaseKey = hasHouseNumber ? buildDuplicateAddressBaseKey(canonicalPrimitive) : "";
        String duplicateAddressCityKey = hasHouseNumber ? buildDuplicateAddressCityKey(canonicalPrimitive) : "";
        canonicalCandidatesByPrimitiveId.put(canonicalId, new CandidateEntry(
                canonicalPrimitive,
                hasHouseNumber,
                hasNoAddressData,
                hasMissingRequiredAddressFields,
                hasMissingStreet,
                hasMissingPostcode,
                hasMissingHouseNumber,
                hasMissingCity,
                hasMissingCountry,
                hasOnlyCountryMissing,
                hasMisplacedHouseNumber,
                duplicateAddressBaseKey,
                duplicateAddressCityKey
        ));
    }

    private OsmPrimitive resolveCanonicalPrimitive(OsmPrimitive primitive) {
        if (!(primitive instanceof Way) || !primitive.isUsable()) {
            return primitive;
        }
        Way way = (Way) primitive;
        Relation canonicalRelation = findAddressedOuterMultipolygonRelation(way);
        return canonicalRelation != null ? canonicalRelation : primitive;
    }

    private Relation findAddressedOuterMultipolygonRelation(Way way) {
        if (way == null || !way.isUsable()) {
            return null;
        }
        Relation best = null;
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (!(referrer instanceof Relation)) {
                continue;
            }
            Relation relation = (Relation) referrer;
            if (!isAddressedOuterMultipolygonRelationForWay(relation, way)) {
                continue;
            }
            if (best == null || relation.getUniqueId() < best.getUniqueId()) {
                best = relation;
            }
        }
        return best;
    }

    private boolean isAddressedOuterMultipolygonRelationForWay(Relation relation, Way way) {
        if (relation == null || way == null || !relation.isUsable()) {
            return false;
        }
        if (!relation.hasTag("type", "multipolygon") || !relation.hasTag("building")) {
            return false;
        }
        for (RelationMember member : relation.getMembers()) {
            if (member == null || !member.isWay() || member.getWay() != way) {
                continue;
            }
            String role = normalize(member.getRole());
            if (role.isEmpty() || "outer".equals(role)) {
                return true;
            }
        }
        return false;
    }

    private String buildDuplicateAddressBaseKey(OsmPrimitive primitive) {
        String street = normalize(primitive.get("addr:street"));
        String postcode = normalize(primitive.get("addr:postcode"));
        String houseNumber = normalize(primitive.get("addr:housenumber"));
        if (street.isEmpty() || postcode.isEmpty() || houseNumber.isEmpty()) {
            return "";
        }

        return street.toLowerCase(Locale.ROOT)
                + "|" + postcode.toLowerCase(Locale.ROOT)
                + "|" + houseNumber.toLowerCase(Locale.ROOT);
    }

    private String buildDuplicateAddressCityKey(OsmPrimitive primitive) {
        return normalize(primitive.get("addr:city")).toLowerCase(Locale.ROOT);
    }

    private boolean hasMisplacedHouseNumber(OsmPrimitive primitive) {
        if (!(primitive instanceof Relation)) {
            return false;
        }
        Relation relation = (Relation) primitive;
        if (!relation.isUsable() || !relation.hasTag("type", "multipolygon") || !relation.hasTag("building")) {
            return false;
        }

        for (RelationMember member : relation.getMembers()) {
            if (member == null || !member.isWay()) {
                continue;
            }
            String role = normalize(member.getRole());
            if (!"outer".equals(role)) {
                continue;
            }

            Way outerWay = member.getWay();
            if (outerWay == null || !outerWay.isUsable()) {
                continue;
            }
            if (!normalize(outerWay.get("addr:housenumber")).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private double computeArea(OsmPrimitive primitive) {
        if (primitive instanceof Way) {
            return computeWayArea((Way) primitive);
        }
        if (primitive instanceof Relation) {
            return computeRelationOuterArea((Relation) primitive);
        }
        return 0.0;
    }

    private double computeRelationOuterArea(Relation relation) {
        if (relation == null || !relation.isUsable()) {
            return 0.0;
        }

        double totalArea = 0.0;
        for (RelationMember member : relation.getMembers()) {
            if (member == null || !member.isWay()) {
                continue;
            }
            String role = normalize(member.getRole());
            if (!role.isEmpty() && !"outer".equals(role)) {
                continue;
            }
            totalArea += computeWayArea(member.getWay());
        }
        return totalArea;
    }

    private double computeWayArea(Way way) {
        if (way == null || !way.isUsable() || !way.isClosed()) {
            return 0.0;
        }

        List<Node> nodes = way.getNodes();
        if (nodes.size() < 4) {
            return 0.0;
        }

        double latSum = 0.0;
        int coordinateCount = 0;
        for (int i = 0; i < nodes.size() - 1; i++) {
            Node node = nodes.get(i);
            LatLon coor = node != null ? node.getCoor() : null;
            if (node == null || !node.isUsable() || coor == null) {
                return 0.0;
            }
            latSum += coor.lat();
            coordinateCount++;
        }
        if (coordinateCount == 0) {
            return 0.0;
        }

        double meanLatitudeRadians = Math.toRadians(latSum / coordinateCount);
        double metersPerDegreeLon = METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(meanLatitudeRadians);
        if (Math.abs(metersPerDegreeLon) < 1e-9) {
            return 0.0;
        }

        double twiceArea = 0.0;
        for (int i = 0; i < nodes.size() - 1; i++) {
            Node current = nodes.get(i);
            Node next = nodes.get(i + 1);
            if (current == null || next == null || !current.isUsable() || !next.isUsable()) {
                return 0.0;
            }

            LatLon currentCoor = current.getCoor();
            LatLon nextCoor = next.getCoor();
            if (currentCoor == null || nextCoor == null) {
                return 0.0;
            }

            double currentX = currentCoor.lon() * metersPerDegreeLon;
            double currentY = currentCoor.lat() * METERS_PER_DEGREE_LAT;
            double nextX = nextCoor.lon() * metersPerDegreeLon;
            double nextY = nextCoor.lat() * METERS_PER_DEGREE_LAT;

            twiceArea += (currentX * nextY) - (nextX * currentY);
        }

        return Math.abs(twiceArea) / 2.0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Public entry used by overview layers to render completeness and diagnostics for one building.
     */
    static final class BuildingOverviewEntry {
        private final OsmPrimitive primitive;
        private final boolean hasHouseNumber;
        private final boolean hasNoAddressData;
        private final boolean hasMissingRequiredAddressFields;
        private final boolean hasMissingStreet;
        private final boolean hasMissingPostcode;
        private final boolean hasMissingHouseNumber;
        private final boolean hasMissingCity;
        private final boolean hasMissingCountry;
        private final boolean hasOnlyCountryMissing;
        private final boolean hasMisplacedHouseNumber;
        private final boolean hasDuplicateExactAddress;

        BuildingOverviewEntry(
                OsmPrimitive primitive,
                boolean hasHouseNumber,
                boolean hasNoAddressData,
                boolean hasMissingRequiredAddressFields,
                boolean hasMissingStreet,
                boolean hasMissingPostcode,
                boolean hasMissingHouseNumber,
                boolean hasMissingCity,
                boolean hasMissingCountry,
                boolean hasOnlyCountryMissing,
                boolean hasMisplacedHouseNumber,
                boolean hasDuplicateExactAddress
        ) {
            this.primitive = primitive;
            this.hasHouseNumber = hasHouseNumber;
            this.hasNoAddressData = hasNoAddressData;
            this.hasMissingRequiredAddressFields = hasMissingRequiredAddressFields;
            this.hasMissingStreet = hasMissingStreet;
            this.hasMissingPostcode = hasMissingPostcode;
            this.hasMissingHouseNumber = hasMissingHouseNumber;
            this.hasMissingCity = hasMissingCity;
            this.hasMissingCountry = hasMissingCountry;
            this.hasOnlyCountryMissing = hasOnlyCountryMissing;
            this.hasMisplacedHouseNumber = hasMisplacedHouseNumber;
            this.hasDuplicateExactAddress = hasDuplicateExactAddress;
        }

        OsmPrimitive getPrimitive() {
            return primitive;
        }

        boolean hasHouseNumber() {
            return hasHouseNumber;
        }

        boolean hasNoAddressData() {
            return hasNoAddressData;
        }

        boolean hasMissingRequiredAddressFields() {
            return hasMissingRequiredAddressFields;
        }

        boolean hasMissingStreet() {
            return hasMissingStreet;
        }

        boolean hasMissingPostcode() {
            return hasMissingPostcode;
        }

        boolean hasMissingHouseNumber() {
            return hasMissingHouseNumber;
        }

        boolean hasMissingCity() {
            return hasMissingCity;
        }

        boolean hasMissingCountry() {
            return hasMissingCountry;
        }

        boolean hasOnlyCountryMissing() {
            return hasOnlyCountryMissing;
        }

        boolean hasMisplacedHouseNumber() {
            return hasMisplacedHouseNumber;
        }

        boolean hasDuplicateExactAddress() {
            return hasDuplicateExactAddress;
        }
    }

    /**
     * Internal collection-stage representation before duplicate-address evaluation is finalized.
     */
    private static final class CandidateEntry {
        private final OsmPrimitive primitive;
        private final boolean hasHouseNumber;
        private final boolean hasNoAddressData;
        private final boolean hasMissingRequiredAddressFields;
        private final boolean hasMissingStreet;
        private final boolean hasMissingPostcode;
        private final boolean hasMissingHouseNumber;
        private final boolean hasMissingCity;
        private final boolean hasMissingCountry;
        private final boolean hasOnlyCountryMissing;
        private final boolean hasMisplacedHouseNumber;
        private final String duplicateAddressBaseKey;
        private final String duplicateAddressCityKey;

        CandidateEntry(
                OsmPrimitive primitive,
                boolean hasHouseNumber,
                boolean hasNoAddressData,
                boolean hasMissingRequiredAddressFields,
                boolean hasMissingStreet,
                boolean hasMissingPostcode,
                boolean hasMissingHouseNumber,
                boolean hasMissingCity,
                boolean hasMissingCountry,
                boolean hasOnlyCountryMissing,
                boolean hasMisplacedHouseNumber,
                String duplicateAddressBaseKey,
                String duplicateAddressCityKey
        ) {
            this.primitive = primitive;
            this.hasHouseNumber = hasHouseNumber;
            this.hasNoAddressData = hasNoAddressData;
            this.hasMissingRequiredAddressFields = hasMissingRequiredAddressFields;
            this.hasMissingStreet = hasMissingStreet;
            this.hasMissingPostcode = hasMissingPostcode;
            this.hasMissingHouseNumber = hasMissingHouseNumber;
            this.hasMissingCity = hasMissingCity;
            this.hasMissingCountry = hasMissingCountry;
            this.hasOnlyCountryMissing = hasOnlyCountryMissing;
            this.hasMisplacedHouseNumber = hasMisplacedHouseNumber;
            this.duplicateAddressBaseKey = duplicateAddressBaseKey;
            this.duplicateAddressCityKey = duplicateAddressCityKey;
        }
    }

    /**
     * Aggregated duplicate-match statistics for one street+postcode+housenumber group.
     */
    private static final class DuplicateAddressGroupStats {
        private int totalCount;
        private int missingCityCount;
        private final Map<String, Integer> cityCounts = new HashMap<>();

        private void addCandidate(String cityKey) {
            totalCount++;
            if (cityKey == null || cityKey.isEmpty()) {
                missingCityCount++;
                return;
            }
            cityCounts.merge(cityKey, 1, Integer::sum);
        }

        private boolean hasDuplicateFor(String cityKey) {
            if (totalCount <= 1) {
                return false;
            }
            if (cityKey == null || cityKey.isEmpty()) {
                return true;
            }
            if (missingCityCount > 0) {
                return true;
            }
            return cityCounts.getOrDefault(cityKey, 0) > 1;
        }
    }
}
