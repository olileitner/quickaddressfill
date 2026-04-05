package org.openstreetmap.josm.plugins.housenumberclick;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

final class HouseNumberOverlayEntry {

    private final OsmPrimitive primitive;
    private final String houseNumber;
    private final int numberPart;
    private final String suffixPart;
    private final EastNorth labelPoint;
    private final int stableIndex;

    HouseNumberOverlayEntry(OsmPrimitive primitive, String houseNumber, int numberPart, String suffixPart,
            EastNorth labelPoint, int stableIndex) {
        this.primitive = primitive;
        this.houseNumber = houseNumber;
        this.numberPart = numberPart;
        this.suffixPart = suffixPart;
        this.labelPoint = labelPoint;
        this.stableIndex = stableIndex;
    }

    OsmPrimitive getPrimitive() {
        return primitive;
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
}

