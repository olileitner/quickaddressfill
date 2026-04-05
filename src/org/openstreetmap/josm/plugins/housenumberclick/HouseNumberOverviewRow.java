package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.List;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

final class HouseNumberOverviewRow {

    private final String oddValue;
    private final String evenValue;
    private final OsmPrimitive oddPrimitive;
    private final OsmPrimitive evenPrimitive;
    private final List<OsmPrimitive> oddPrimitives;
    private final List<OsmPrimitive> evenPrimitives;
    private final boolean oddDuplicate;
    private final boolean evenDuplicate;

    HouseNumberOverviewRow(String oddValue, String evenValue, OsmPrimitive oddPrimitive, OsmPrimitive evenPrimitive,
            List<OsmPrimitive> oddPrimitives, List<OsmPrimitive> evenPrimitives, boolean oddDuplicate, boolean evenDuplicate) {
        this.oddValue = oddValue;
        this.evenValue = evenValue;
        this.oddPrimitive = oddPrimitive;
        this.evenPrimitive = evenPrimitive;
        this.oddPrimitives = oddPrimitives == null ? List.of() : List.copyOf(oddPrimitives);
        this.evenPrimitives = evenPrimitives == null ? List.of() : List.copyOf(evenPrimitives);
        this.oddDuplicate = oddDuplicate;
        this.evenDuplicate = evenDuplicate;
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

    List<OsmPrimitive> getOddPrimitives() {
        return oddPrimitives;
    }

    List<OsmPrimitive> getEvenPrimitives() {
        return evenPrimitives;
    }

    boolean isOddDuplicate() {
        return oddDuplicate;
    }

    boolean isEvenDuplicate() {
        return evenDuplicate;
    }
}

