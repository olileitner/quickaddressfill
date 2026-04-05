package org.openstreetmap.josm.plugins.housenumberclick;

final class StreetHouseNumberCountRow {

    private final String streetName;
    private final int count;
    private final boolean hasDuplicate;

    StreetHouseNumberCountRow(String streetName, int count, boolean hasDuplicate) {
        this.streetName = normalize(streetName);
        this.count = Math.max(0, count);
        this.hasDuplicate = hasDuplicate;
    }

    String getStreetName() {
        return streetName;
    }

    int getCount() {
        return count;
    }

    boolean hasDuplicate() {
        return hasDuplicate;
    }

    String getDisplayCount() {
        return hasDuplicate ? count + " (dup)" : Integer.toString(count);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

