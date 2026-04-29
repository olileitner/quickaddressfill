package org.openstreetmap.josm.plugins.housenumberclick;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Value object describing one rendered house-number label entry with carrier metadata.
 */
final class HouseNumberOverlayEntry {

    private final OsmPrimitive primitive;
    private final String street;
    private final String postcode;
    private final String houseNumber;
    private final int numberPart;
    private final String suffixPart;
    private final EastNorth labelPoint;
    private final int stableIndex;
    private final AddressEntry.CarrierType carrierType;
    private final OsmPrimitive associatedBuilding;
    private final boolean indirectBuildingAddress;

    HouseNumberOverlayEntry(OsmPrimitive primitive, String street, String postcode, String houseNumber, int numberPart, String suffixPart,
            EastNorth labelPoint, int stableIndex, AddressEntry.CarrierType carrierType,
            OsmPrimitive associatedBuilding, boolean indirectBuildingAddress) {
        this.primitive = primitive;
        this.street = street;
        this.postcode = postcode;
        this.houseNumber = houseNumber;
        this.numberPart = numberPart;
        this.suffixPart = suffixPart;
        this.labelPoint = labelPoint;
        this.stableIndex = stableIndex;
        this.carrierType = carrierType != null ? carrierType : AddressEntry.CarrierType.OTHER_OBJECT;
        this.associatedBuilding = associatedBuilding;
        this.indirectBuildingAddress = indirectBuildingAddress;
    }

    OsmPrimitive getPrimitive() {
        return primitive;
    }

    String getStreet() {
        return street;
    }

    String getPostcode() {
        return postcode;
    }

    String getHouseNumber() {
        return houseNumber;
    }

    int getNumberPart() {
        return numberPart;
    }

    String getSuffixPart() {
        return suffixPart;
    }

    EastNorth getLabelPoint() {
        return labelPoint;
    }

    int getStableIndex() {
        return stableIndex;
    }

    AddressEntry.CarrierType getCarrierType() {
        return carrierType;
    }

    OsmPrimitive getAssociatedBuilding() {
        return associatedBuilding;
    }

    boolean isIndirectBuildingAddress() {
        return indirectBuildingAddress;
    }
}
