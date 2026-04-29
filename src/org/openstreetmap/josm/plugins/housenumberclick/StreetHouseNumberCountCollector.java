package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.osm.DataSet;
/**
 * Collects per-street address counts from all read-only address carriers with conditional city-aware duplicates.
 */
final class StreetHouseNumberCountCollector {

    private final AddressEntryCollector addressEntryCollector = new AddressEntryCollector();

    List<StreetHouseNumberCountRow> collectRows(DataSet dataSet, StreetNameCollector.StreetIndex streetIndex) {
        if (dataSet == null) {
            return new ArrayList<>();
        }

        StreetNameCollector.StreetIndex effectiveStreetIndex = streetIndex != null
                ? streetIndex
                : StreetNameCollector.collectStreetIndex(dataSet);

        Map<String, StreetOption> optionByCluster = new HashMap<>();
        Map<String, Integer> countsByCluster = new HashMap<>();
        Map<String, Map<String, AddressDuplicateAnalyzer.DuplicateAddressGroupStats>> duplicateAddressGroupsByCluster = new HashMap<>();
        Map<String, Boolean> hasDuplicateByCluster = new HashMap<>();

        for (StreetOption option : effectiveStreetIndex.getStreetOptions()) {
            if (option == null || !option.isValid()) {
                continue;
            }
            optionByCluster.put(option.getClusterId(), option);
            countsByCluster.putIfAbsent(option.getClusterId(), 0);
            hasDuplicateByCluster.putIfAbsent(option.getClusterId(), false);
        }

        for (AddressEntry entry : addressEntryCollector.collect(dataSet)) {
            collectEntry(entry, effectiveStreetIndex, countsByCluster, duplicateAddressGroupsByCluster, hasDuplicateByCluster);
        }

        List<StreetHouseNumberCountRow> rows = new ArrayList<>();
        for (StreetOption option : effectiveStreetIndex.getStreetOptions()) {
            if (option == null || !option.isValid()) {
                continue;
            }
            String clusterId = option.getClusterId();
            rows.add(new StreetHouseNumberCountRow(
                    option,
                    countsByCluster.getOrDefault(clusterId, 0),
                    Boolean.TRUE.equals(hasDuplicateByCluster.get(clusterId))
            ));
        }
        return rows;
    }

    private void collectEntry(AddressEntry entry, StreetNameCollector.StreetIndex streetIndex,
            Map<String, Integer> countsByCluster,
            Map<String, Map<String, AddressDuplicateAnalyzer.DuplicateAddressGroupStats>> duplicateAddressGroupsByCluster,
            Map<String, Boolean> hasDuplicateByCluster) {
        if (entry == null || normalize(entry.getStreet()).isEmpty() || normalize(entry.getHouseNumber()).isEmpty()) {
            return;
        }

        StreetOption option = streetIndex.resolveForBaseStreetAndPrimitive(entry.getStreet(), entry.getPrimitive());
        if (option == null || !option.isValid()) {
            return;
        }

        String clusterId = option.getClusterId();
        countsByCluster.put(clusterId, countsByCluster.getOrDefault(clusterId, 0) + 1);

        String baseAddressKey = AddressDuplicateAnalyzer.normalizeBaseAddressKey(entry);
        if (baseAddressKey.isEmpty()) {
            return;
        }
        String cityKey = normalize(entry.getCity()).toLowerCase(java.util.Locale.ROOT);
        long anchorId = entry.getRealWorldAnchorId();

        Map<String, AddressDuplicateAnalyzer.DuplicateAddressGroupStats> duplicateAddressGroups = duplicateAddressGroupsByCluster
                .computeIfAbsent(clusterId, key -> new HashMap<>());
        AddressDuplicateAnalyzer.DuplicateAddressGroupStats groupStats = duplicateAddressGroups
                .computeIfAbsent(baseAddressKey, key -> new AddressDuplicateAnalyzer.DuplicateAddressGroupStats());
        groupStats.addCandidate(anchorId, cityKey);
        if (groupStats.hasDuplicateFor(anchorId, cityKey)) {
            hasDuplicateByCluster.put(clusterId, true);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

}
