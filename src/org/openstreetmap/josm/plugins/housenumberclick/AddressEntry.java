package org.openstreetmap.josm.plugins.housenumberclick;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Read-only normalized address carrier entry used by collectors/layers for counts, overlays, and duplicate checks.
 */
final class AddressEntry {

    enum CarrierType {
        BUILDING,
        ENTRANCE_NODE,
        ADDRESS_NODE,
        OTHER_OBJECT
    }

    private final OsmPrimitive primitive;
    private final OsmPrimitive sourcePrimitive;
    private final CarrierType carrierType;
    private final OsmPrimitive associatedBuilding;
    private final String houseNumber;
    private final String street;
    private final String postcode;
    private final String city;
    private final String country;
    private final EastNorth labelPoint;

    AddressEntry(
            OsmPrimitive primitive,
            OsmPrimitive sourcePrimitive,
            CarrierType carrierType,
            OsmPrimitive associatedBuilding,
            String houseNumber,
            String street,
            String postcode,
            String city,
            String country,
            EastNorth labelPoint
    ) {
        this.primitive = primitive;
        this.sourcePrimitive = sourcePrimitive != null ? sourcePrimitive : primitive;
        this.carrierType = carrierType != null ? carrierType : CarrierType.OTHER_OBJECT;
        this.associatedBuilding = associatedBuilding;
        this.houseNumber = normalize(houseNumber);
        this.street = normalize(street);
        this.postcode = normalize(postcode);
        this.city = normalize(city);
        this.country = normalize(country);
        this.labelPoint = labelPoint;
    }

    OsmPrimitive getPrimitive() {
        return primitive;
    }

    OsmPrimitive getSourcePrimitive() {
        return sourcePrimitive;
    }

    CarrierType getCarrierType() {
        return carrierType;
    }

    OsmPrimitive getAssociatedBuilding() {
        return associatedBuilding;
    }

    String getHouseNumber() {
        return houseNumber;
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

    EastNorth getLabelPoint() {
        return labelPoint;
    }

    boolean isWritableTarget() {
        return carrierType == CarrierType.BUILDING;
    }

    boolean isAddressPoint() {
        return carrierType == CarrierType.ADDRESS_NODE || carrierType == CarrierType.ENTRANCE_NODE;
    }

    boolean isEntranceAddress() {
        return carrierType == CarrierType.ENTRANCE_NODE;
    }

    boolean isIndirectBuildingAddress() {
        return carrierType != CarrierType.BUILDING && associatedBuilding != null;
    }

    long getRealWorldAnchorId() {
        OsmPrimitive anchor = associatedBuilding != null ? associatedBuilding : primitive;
        return anchor == null ? 0L : anchor.getUniqueId();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

