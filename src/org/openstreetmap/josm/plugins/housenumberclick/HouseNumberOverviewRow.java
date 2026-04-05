package org.openstreetmap.josm.plugins.housenumberclick;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

final class HouseNumberOverviewRow {

    private final String oddValue;
    private final String evenValue;
    private final OsmPrimitive oddPrimitive;
    private final OsmPrimitive evenPrimitive;

    HouseNumberOverviewRow(String oddValue, String evenValue, OsmPrimitive oddPrimitive, OsmPrimitive evenPrimitive) {
        this.oddValue = oddValue;
        this.evenValue = evenValue;
        this.oddPrimitive = oddPrimitive;
        this.evenPrimitive = evenPrimitive;
    }

    String getOddValue() {
        return oddValue;
    }

    String getEvenValue() {
        return evenValue;
    }

    OsmPrimitive getOddPrimitive() {
        return oddPrimitive;
    }

    OsmPrimitive getEvenPrimitive() {
        return evenPrimitive;
    }
}

