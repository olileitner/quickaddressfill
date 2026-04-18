package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

/**
 * Detects a confidently known country for the active dataset to prefill addr:country.
 */
final class CountryDetectionService {

    private static final int MIN_CONFIDENT_ADDRESS_SAMPLES = 3;
    private static final Map<String, String> COUNTRY_NAME_ALIASES = buildCountryNameAliases();

    String detectConfidentCountry(DataSet dataSet) {
        if (dataSet == null) {
            return "";
        }

        Map<String, Integer> addressCountryCounts = new HashMap<>();
        collectAddressCountryCounts(dataSet, addressCountryCounts);

        String confidentByAddresses = pickSingleConfidentCountry(addressCountryCounts, MIN_CONFIDENT_ADDRESS_SAMPLES);
        if (!confidentByAddresses.isEmpty()) {
            return confidentByAddresses;
        }

        Map<String, Integer> boundaryCountryCounts = new HashMap<>();
        collectBoundaryCountryCounts(dataSet, boundaryCountryCounts);
        return pickSingleConfidentCountry(boundaryCountryCounts, 1);
    }

    private void collectAddressCountryCounts(DataSet dataSet, Map<String, Integer> counts) {
        for (Way way : dataSet.getWays()) {
            collectAddressCountryFromPrimitive(way, counts);
        }
        for (Relation relation : dataSet.getRelations()) {
            collectAddressCountryFromPrimitive(relation, counts);
        }
        for (Node node : dataSet.getNodes()) {
            collectAddressCountryFromPrimitive(node, counts);
        }
    }

    private void collectAddressCountryFromPrimitive(OsmPrimitive primitive, Map<String, Integer> counts) {
        if (primitive == null || !primitive.isUsable()) {
            return;
        }
        if (!hasAnyAddressSignal(primitive)) {
            return;
        }
        addNormalizedCountryCount(primitive.get("addr:country"), counts);
    }

    private boolean hasAnyAddressSignal(OsmPrimitive primitive) {
        return !normalize(primitive.get("addr:housenumber")).isEmpty()
                || !normalize(primitive.get("addr:street")).isEmpty()
                || !normalize(primitive.get("addr:postcode")).isEmpty()
                || !normalize(primitive.get("addr:city")).isEmpty()
                || !normalize(primitive.get("addr:country")).isEmpty();
    }

    private void collectBoundaryCountryCounts(DataSet dataSet, Map<String, Integer> counts) {
        for (Relation relation : dataSet.getRelations()) {
            if (relation == null || !relation.isUsable()) {
                continue;
            }
            if (!relation.hasTag("boundary", "administrative") || !relation.hasTag("admin_level", "2")) {
                continue;
            }
            addNormalizedCountryCount(relation.get("ISO3166-1:alpha2"), counts);
            addNormalizedCountryCount(relation.get("addr:country"), counts);
            addNormalizedCountryCount(relation.get("country"), counts);
        }
    }

    private String pickSingleConfidentCountry(Map<String, Integer> counts, int minimumSamples) {
        if (counts == null || counts.size() != 1) {
            return "";
        }
        Map.Entry<String, Integer> entry = counts.entrySet().iterator().next();
        String country = normalize(entry.getKey());
        int sampleCount = entry.getValue() != null ? entry.getValue() : 0;
        if (country.isEmpty() || sampleCount < minimumSamples) {
            return "";
        }
        return country;
    }

    private void addNormalizedCountryCount(String rawCountry, Map<String, Integer> counts) {
        String normalizedCountry = normalizeCountry(rawCountry);
        if (normalizedCountry.isEmpty()) {
            return;
        }
        counts.merge(normalizedCountry, 1, Integer::sum);
    }

    private String normalizeCountry(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return "";
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("UK".equals(upper)) {
            return "GB";
        }

        if (upper.matches("[A-Z]{2}")) {
            return upper;
        }

        String mapped = COUNTRY_NAME_ALIASES.get(upper);
        return mapped == null ? "" : mapped;
    }

    private static Map<String, String> buildCountryNameAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("GERMANY", "DE");
        aliases.put("DEUTSCHLAND", "DE");
        aliases.put("AUSTRIA", "AT");
        aliases.put("OESTERREICH", "AT");
        aliases.put("OSTERREICH", "AT");
        aliases.put("FRANCE", "FR");
        return aliases;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();

        // Minimal umlaut normalization (sufficient for country names)
        v = v.replace("Ä", "AE")
                .replace("Ö", "OE")
                .replace("Ü", "UE")
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");

        return v;
    }
}

