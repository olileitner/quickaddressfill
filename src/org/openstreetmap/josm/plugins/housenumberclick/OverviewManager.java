package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.openstreetmap.josm.data.osm.DataSet;

final class OverviewManager {

    private HouseNumberOverviewDialog houseNumberOverviewDialog;
    private StreetHouseNumberCountDialog streetHouseNumberCountDialog;
    private final HouseNumberOverviewCollector houseNumberOverviewCollector = new HouseNumberOverviewCollector();
    private final StreetHouseNumberCountCollector streetHouseNumberCountCollector = new StreetHouseNumberCountCollector();
    private final StreetCompletenessHeuristic streetCompletenessHeuristic = new StreetCompletenessHeuristic();

    void refreshHouseNumberOverview(
            boolean enabled,
            String currentStreet,
            DataSet editDataSet,
            Runnable continueWorkingCallback
    ) {
        if (!enabled || editDataSet == null) {
            hideHouseNumberOverview();
            return;
        }

        if (houseNumberOverviewDialog == null) {
            houseNumberOverviewDialog = new HouseNumberOverviewDialog(continueWorkingCallback);
        }

        houseNumberOverviewDialog.updateData(
                currentStreet,
                houseNumberOverviewCollector.collectRows(editDataSet, currentStreet),
                streetCompletenessHeuristic.isStreetPossiblyIncomplete(editDataSet, currentStreet)
        );
        houseNumberOverviewDialog.showDialog();
    }

    List<String> refreshStreetHouseNumberCounts(
            boolean enabled,
            DataSet editDataSet,
            Consumer<String> onStreetSelected,
            Runnable onRescanRequested,
            String currentStreet
    ) {
        if (!enabled || editDataSet == null) {
            hideStreetHouseNumberCounts();
            return List.of();
        }

        if (streetHouseNumberCountDialog == null) {
            streetHouseNumberCountDialog = new StreetHouseNumberCountDialog(onStreetSelected, onRescanRequested);
        }

        List<StreetHouseNumberCountRow> rows = streetHouseNumberCountCollector.collectRows(editDataSet);
        List<String> streetNavigationOrder = Collections.unmodifiableList(
                StreetHouseNumberCountDialog.buildStreetNavigationOrder(rows));
        streetHouseNumberCountDialog.updateData(rows);
        streetHouseNumberCountDialog.showDialog();
        highlightStreetInStreetCountDialog(currentStreet);
        return new ArrayList<>(streetNavigationOrder);
    }

    void hideHouseNumberOverview() {
        if (houseNumberOverviewDialog != null) {
            houseNumberOverviewDialog.hideDialog();
        }
    }

    void hideStreetHouseNumberCounts() {
        if (streetHouseNumberCountDialog != null) {
            streetHouseNumberCountDialog.hideDialog();
        }
    }

    void resetSessionPositioningState() {
        if (houseNumberOverviewDialog != null) {
            houseNumberOverviewDialog.resetSessionPositioningState();
        }
        if (streetHouseNumberCountDialog != null) {
            streetHouseNumberCountDialog.resetSessionPositioningState();
        }
    }

    void highlightStreetInStreetCountDialog(String streetName) {
        if (streetHouseNumberCountDialog == null) {
            return;
        }
        streetHouseNumberCountDialog.highlightStreet(streetName);
    }

}


