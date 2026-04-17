package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.openstreetmap.josm.data.osm.DataSet;

/**
 * Coordinates overview dialogs and keeps their data synchronized with current plugin state.
 */
final class OverviewManager {

    private HouseNumberOverviewDialog houseNumberOverviewDialog;
    private StreetHouseNumberCountDialog streetHouseNumberCountDialog;
    private final HouseNumberOverviewCollector houseNumberOverviewCollector = new HouseNumberOverviewCollector();
    private final StreetHouseNumberCountCollector streetHouseNumberCountCollector = new StreetHouseNumberCountCollector();
    private final StreetCompletenessHeuristic streetCompletenessHeuristic = new StreetCompletenessHeuristic();

    void refreshHouseNumberOverview(
            boolean enabled,
            StreetOption currentStreet,
            DataSet editDataSet,
            StreetNameCollector.StreetIndex streetIndex,
            Runnable continueWorkingCallback,
            Runnable closeCallback
    ) {
        if (!enabled || editDataSet == null) {
            hideHouseNumberOverview();
            return;
        }

        if (houseNumberOverviewDialog == null) {
            houseNumberOverviewDialog = new HouseNumberOverviewDialog(continueWorkingCallback, closeCallback);
        }

        houseNumberOverviewDialog.updateData(
                currentStreet == null ? "" : currentStreet.getDisplayStreetName(),
                houseNumberOverviewCollector.collectRows(
                        editDataSet,
                        currentStreet,
                        streetIndex
                ),
                streetCompletenessHeuristic.isStreetPossiblyIncomplete(
                        editDataSet,
                        currentStreet == null ? "" : currentStreet.getBaseStreetName()
                )
        );
        houseNumberOverviewDialog.showDialog();
    }

    List<StreetOption> refreshStreetHouseNumberCounts(
            boolean enabled,
            DataSet editDataSet,
            StreetNameCollector.StreetIndex streetIndex,
            Consumer<StreetOption> onStreetSelected,
            Runnable onRescanRequested,
            StreetOption currentStreet,
            Runnable closeCallback
    ) {
        if (!enabled || editDataSet == null) {
            hideStreetHouseNumberCounts();
            return List.of();
        }

        if (streetHouseNumberCountDialog == null) {
            streetHouseNumberCountDialog = new StreetHouseNumberCountDialog(onStreetSelected, onRescanRequested, closeCallback);
        }

        List<StreetHouseNumberCountRow> rows = streetHouseNumberCountCollector.collectRows(editDataSet, streetIndex);
        List<StreetOption> streetNavigationOrder = Collections.unmodifiableList(
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

    void highlightStreetInStreetCountDialog(StreetOption streetOption) {
        if (streetHouseNumberCountDialog == null) {
            return;
        }
        streetHouseNumberCountDialog.highlightStreet(streetOption);
    }

}
