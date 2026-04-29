package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Shared duplicate-address grouping with conditional city matching and co-located carrier suppression.
 */
final class AddressDuplicateAnalyzer {

    private AddressDuplicateAnalyzer() {
        // Utility class
    }

    static Map<String, DuplicateAddressGroupStats> buildDuplicateGroups(Iterable<AddressEntry> entries) {
        Map<String, DuplicateAddressGroupStats> groups = new HashMap<>();
        if (entries == null) {
            return groups;
        }
        for (AddressEntry entry : entries) {
            String baseKey = normalizeBaseAddressKey(entry);
            if (baseKey.isEmpty()) {
                continue;
            }
            groups.computeIfAbsent(baseKey, ignored -> new DuplicateAddressGroupStats())
                    .addCandidate(entry.getRealWorldAnchorId(), normalize(entry.getCity()).toLowerCase(Locale.ROOT));
        }
        return groups;
    }

    static String normalizeBaseAddressKey(AddressEntry entry) {
        if (entry == null) {
            return "";
        }
        String street = normalize(entry.getStreet());
        String postcode = normalize(entry.getPostcode());
        String houseNumber = normalize(entry.getHouseNumber());
        if (street.isEmpty() || postcode.isEmpty() || houseNumber.isEmpty()) {
            return "";
        }
        return street.toLowerCase(Locale.ROOT)
                + "|" + postcode.toLowerCase(Locale.ROOT)
                + "|" + houseNumber.toLowerCase(Locale.ROOT);
    }

    static boolean isHardDuplicate(AddressEntry entry, Map<String, DuplicateAddressGroupStats> groups) {
        if (entry == null || groups == null) {
            return false;
        }
        String baseKey = normalizeBaseAddressKey(entry);
        if (baseKey.isEmpty()) {
            return false;
        }
        DuplicateAddressGroupStats stats = groups.get(baseKey);
        if (stats == null) {
            return false;
        }
        String cityKey = normalize(entry.getCity()).toLowerCase(Locale.ROOT);
        return stats.hasDuplicateFor(entry.getRealWorldAnchorId(), cityKey);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Aggregated duplicate-match statistics for one street+postcode+housenumber group.
     */
    static final class DuplicateAddressGroupStats {
        private final Set<Long> allAnchors = new HashSet<>();
        private final Set<Long> missingCityAnchors = new HashSet<>();
        private final Map<String, Set<Long>> anchorsByCity = new HashMap<>();

        void addCandidate(long anchorId, String cityKey) {
            if (anchorId == 0L) {
                return;
            }
            allAnchors.add(anchorId);
            if (cityKey == null || cityKey.isEmpty()) {
                missingCityAnchors.add(anchorId);
                return;
            }
            anchorsByCity.computeIfAbsent(cityKey, ignored -> new HashSet<>()).add(anchorId);
        }

        boolean hasDuplicateFor(long anchorId, String cityKey) {
            if (anchorId == 0L || allAnchors.size() <= 1) {
                return false;
            }
            if (cityKey == null || cityKey.isEmpty()) {
                return hasOtherAnchor(allAnchors, anchorId);
            }
            if (hasOtherAnchor(missingCityAnchors, anchorId)) {
                return true;
            }
            Set<Long> sameCity = anchorsByCity.get(cityKey);
            return hasOtherAnchor(sameCity, anchorId);
        }

        private boolean hasOtherAnchor(Set<Long> anchors, long anchorId) {
            if (anchors == null || anchors.isEmpty()) {
                return false;
            }
            return anchors.size() > (anchors.contains(anchorId) ? 1 : 0);
        }
    }
}

