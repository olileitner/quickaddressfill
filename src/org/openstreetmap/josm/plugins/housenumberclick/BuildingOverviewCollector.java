package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * canonicalizing relation/outer-way representations and considering linked read-only address carriers.
 */
final class BuildingOverviewCollector {

    static final double MIN_BUILDING_AREA = 25.0;
    private static final double METERS_PER_DEGREE_LAT = 111_132.0;
    private static final double METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0;
    private final AddressEntryCollector addressEntryCollector = new AddressEntryCollector();

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

        List<AddressEntry> addressEntries = addressEntryCollector.collect(dataSet);
        Map<String, AddressDuplicateAnalyzer.DuplicateAddressGroupStats> duplicateAddressGroups =
                AddressDuplicateAnalyzer.buildDuplicateGroups(addressEntries);
        Map<Long, List<AddressEntry>> indirectEntriesByBuildingId = new HashMap<>();
        for (AddressEntry entry : addressEntries) {
            if (entry == null || !entry.isIndirectBuildingAddress() || entry.getAssociatedBuilding() == null) {
                continue;
            }
            long buildingId = entry.getAssociatedBuilding().getUniqueId();
            indirectEntriesByBuildingId.computeIfAbsent(buildingId, ignored -> new ArrayList<>()).add(entry);
        }

        List<BuildingOverviewEntry> entries = new ArrayList<>(candidates.size());
        for (CandidateEntry candidate : candidates) {
            List<AddressEntry> indirectEntries = indirectEntriesByBuildingId.getOrDefault(
                    candidate.primitive.getUniqueId(),
                    List.of()
            );
            EffectiveAddress effectiveAddress = buildEffectiveAddress(candidate, indirectEntries);
            AddressEntry syntheticBuildingEntry = new AddressEntry(
                    candidate.primitive,
                    candidate.primitive,
                    AddressEntry.CarrierType.BUILDING,
                    candidate.primitive,
                    effectiveAddress.houseNumber,
                    effectiveAddress.street,
                    effectiveAddress.postcode,
                    effectiveAddress.city,
                    effectiveAddress.country,
                    null
            );
            boolean hasDuplicateExactAddress = AddressDuplicateAnalyzer.isHardDuplicate(
                    syntheticBuildingEntry,
                    duplicateAddressGroups
            );
            entries.add(new BuildingOverviewEntry(
                    candidate.primitive,
                    effectiveAddress.hasHouseNumber,
                    effectiveAddress.hasNoAddressData,
                    effectiveAddress.hasMissingRequiredAddressFields,
                    effectiveAddress.hasMissingStreet,
                    effectiveAddress.hasMissingPostcode,
                    effectiveAddress.hasMissingHouseNumber,
                    effectiveAddress.hasMissingCity,
                    effectiveAddress.hasMissingCountry,
                    effectiveAddress.hasOnlyCountryMissing,
                    candidate.hasMisplacedHouseNumber,
                    effectiveAddress.hasIndirectAddress,
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

        String houseNumber = normalize(canonicalPrimitive.get("addr:housenumber"));
        boolean hasHouseNumber = !houseNumber.isEmpty();
        boolean hasMisplacedHouseNumber = !hasHouseNumber && hasMisplacedHouseNumber(canonicalPrimitive);
        canonicalCandidatesByPrimitiveId.put(canonicalId, new CandidateEntry(
                canonicalPrimitive,
                hasMisplacedHouseNumber
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

    private EffectiveAddress buildEffectiveAddress(CandidateEntry candidate, List<AddressEntry> indirectEntries) {
        String directStreet = normalize(candidate.primitive.get("addr:street"));
        String directPostcode = normalize(candidate.primitive.get("addr:postcode"));
        String directHouseNumber = normalize(candidate.primitive.get("addr:housenumber"));
        String directCity = normalize(candidate.primitive.get("addr:city"));
        String directCountry = normalize(candidate.primitive.get("addr:country"));

        AddressEntry fallback = findBestIndirectEntry(indirectEntries);
        String street = firstNonEmpty(directStreet, fallback == null ? "" : fallback.getStreet());
        String postcode = firstNonEmpty(directPostcode, fallback == null ? "" : fallback.getPostcode());
        String houseNumber = firstNonEmpty(directHouseNumber, fallback == null ? "" : fallback.getHouseNumber());
        String city = firstNonEmpty(directCity, fallback == null ? "" : fallback.getCity());
        String country = firstNonEmpty(directCountry, fallback == null ? "" : fallback.getCountry());

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
        boolean hasIndirectAddress = !directHouseNumber.isEmpty() ? false : hasHouseNumber && fallback != null;
        return new EffectiveAddress(
                street,
                postcode,
                houseNumber,
                city,
                country,
                hasHouseNumber,
                hasNoAddressData,
                hasMissingRequiredAddressFields,
                hasMissingStreet,
                hasMissingPostcode,
                hasMissingHouseNumber,
                hasMissingCity,
                hasMissingCountry,
                hasOnlyCountryMissing,
                hasIndirectAddress
        );
    }

    private AddressEntry findBestIndirectEntry(List<AddressEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        AddressEntry best = null;
        for (AddressEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (best == null) {
                best = entry;
                continue;
            }
            if (scoreEntry(entry) > scoreEntry(best)) {
                best = entry;
            }
        }
        return best;
    }

    private int scoreEntry(AddressEntry entry) {
        int score = 0;
        if (!normalize(entry.getStreet()).isEmpty()) {
            score++;
        }
        if (!normalize(entry.getPostcode()).isEmpty()) {
            score++;
        }
        if (!normalize(entry.getHouseNumber()).isEmpty()) {
            score++;
        }
        if (!normalize(entry.getCity()).isEmpty()) {
            score++;
        }
        if (!normalize(entry.getCountry()).isEmpty()) {
            score++;
        }
        return score;
    }

    private String firstNonEmpty(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        if (!normalizedPrimary.isEmpty()) {
            return normalizedPrimary;
        }
        return normalize(fallback);
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
        private final boolean hasIndirectAddress;
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
                boolean hasIndirectAddress,
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
            this.hasIndirectAddress = hasIndirectAddress;
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

        boolean hasIndirectAddress() {
            return hasIndirectAddress;
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
        private final boolean hasMisplacedHouseNumber;

        CandidateEntry(
                OsmPrimitive primitive,
                boolean hasMisplacedHouseNumber
        ) {
            this.primitive = primitive;
            this.hasMisplacedHouseNumber = hasMisplacedHouseNumber;
        }
    }

    private static final class EffectiveAddress {
        private final String street;
        private final String postcode;
        private final String houseNumber;
        private final String city;
        private final String country;
        private final boolean hasHouseNumber;
        private final boolean hasNoAddressData;
        private final boolean hasMissingRequiredAddressFields;
        private final boolean hasMissingStreet;
        private final boolean hasMissingPostcode;
        private final boolean hasMissingHouseNumber;
        private final boolean hasMissingCity;
        private final boolean hasMissingCountry;
        private final boolean hasOnlyCountryMissing;
        private final boolean hasIndirectAddress;

        private EffectiveAddress(String street, String postcode, String houseNumber, String city, String country,
                boolean hasHouseNumber, boolean hasNoAddressData, boolean hasMissingRequiredAddressFields,
                boolean hasMissingStreet, boolean hasMissingPostcode, boolean hasMissingHouseNumber,
                boolean hasMissingCity, boolean hasMissingCountry, boolean hasOnlyCountryMissing,
                boolean hasIndirectAddress) {
            this.street = street;
            this.postcode = postcode;
            this.houseNumber = houseNumber;
            this.city = city;
            this.country = country;
            this.hasHouseNumber = hasHouseNumber;
            this.hasNoAddressData = hasNoAddressData;
            this.hasMissingRequiredAddressFields = hasMissingRequiredAddressFields;
            this.hasMissingStreet = hasMissingStreet;
            this.hasMissingPostcode = hasMissingPostcode;
            this.hasMissingHouseNumber = hasMissingHouseNumber;
            this.hasMissingCity = hasMissingCity;
            this.hasMissingCountry = hasMissingCountry;
            this.hasOnlyCountryMissing = hasOnlyCountryMissing;
            this.hasIndirectAddress = hasIndirectAddress;
        }
    }
}
