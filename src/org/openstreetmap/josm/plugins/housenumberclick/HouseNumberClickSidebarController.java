package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.List;
import java.util.function.Consumer;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MapFrame;

/**
 * Coordinates persistent right-sidebar overview dialogs and switches their content between active and hint states.
 */
final class HouseNumberClickSidebarController {

    private final HouseNumberOverviewCollector houseNumberOverviewCollector = new HouseNumberOverviewCollector();
    private final StreetHouseNumberCountCollector streetHouseNumberCountCollector = new StreetHouseNumberCountCollector();
    private final StreetCompletenessHeuristic streetCompletenessHeuristic = new StreetCompletenessHeuristic();

    private Consumer<StreetOption> streetSelectionListener = ignored -> { };
    private Runnable rescanListener = () -> { };
    private Runnable streetNumberRowClickListener = () -> { };

    private MapFrame registeredMapFrame;
    private StreetCountsToggleDialog streetCountsToggleDialog;
    private StreetNumbersToggleDialog streetNumbersToggleDialog;
    private boolean mainDialogOpen;

    void setInteractionCallbacks(Consumer<StreetOption> streetSelectionListener, Runnable rescanListener,
            Runnable streetNumberRowClickListener) {
        this.streetSelectionListener = streetSelectionListener != null ? streetSelectionListener : ignored -> { };
        this.rescanListener = rescanListener != null ? rescanListener : () -> { };
        this.streetNumberRowClickListener = streetNumberRowClickListener != null ? streetNumberRowClickListener : () -> { };
    }

    void attachToMapFrame(MapFrame mapFrame) {
        if (mapFrame == null || mapFrame == registeredMapFrame) {
            return;
        }

        registeredMapFrame = mapFrame;
        StreetCountsPanel streetCountsPanel = new StreetCountsPanel(this::onStreetSelectedFromSidebar, this::onRescanRequestedFromSidebar);
        StreetNumbersPanel streetNumbersPanel = new StreetNumbersPanel(this::onStreetNumberRowClickedFromSidebar);
        streetCountsToggleDialog = new StreetCountsToggleDialog(streetCountsPanel);
        streetNumbersToggleDialog = new StreetNumbersToggleDialog(streetNumbersPanel);

        mapFrame.addToggleDialog(streetCountsToggleDialog);
        mapFrame.addToggleDialog(streetNumbersToggleDialog);
        showMainDialogClosed();
    }

    void showMainDialogClosed() {
        mainDialogOpen = false;
        if (streetCountsToggleDialog != null) {
            streetCountsToggleDialog.getPanel().showMainDialogClosed();
        }
        if (streetNumbersToggleDialog != null) {
            streetNumbersToggleDialog.getPanel().showMainDialogClosed();
        }
    }

    void showNoData() {
        if (streetCountsToggleDialog != null) {
            streetCountsToggleDialog.getPanel().showNoData();
        }
        if (streetNumbersToggleDialog != null) {
            streetNumbersToggleDialog.getPanel().showNoData();
        }
    }

    List<StreetOption> showActiveData(DataSet editDataSet, StreetNameCollector.StreetIndex streetIndex, StreetOption currentStreet) {
        mainDialogOpen = true;
        if (editDataSet == null) {
            showNoData();
            return List.of();
        }

        List<StreetHouseNumberCountRow> countRows = streetHouseNumberCountCollector.collectRows(editDataSet, streetIndex);
        List<StreetOption> navigationOrder = StreetCountsPanel.buildStreetNavigationOrder(countRows);
        if (streetCountsToggleDialog != null) {
            streetCountsToggleDialog.getPanel().showActiveRows(countRows, currentStreet);
        }

        if (streetNumbersToggleDialog != null) {
            if (currentStreet == null || !currentStreet.isValid()) {
                streetNumbersToggleDialog.getPanel().showNoStreetSelected();
            } else {
                List<HouseNumberOverviewRow> rows = houseNumberOverviewCollector.collectRows(editDataSet, currentStreet, streetIndex);
                boolean possiblyIncomplete = streetCompletenessHeuristic.isStreetPossiblyIncomplete(
                        editDataSet,
                        currentStreet.getBaseStreetName()
                );
                streetNumbersToggleDialog.getPanel().showActiveRows(
                        currentStreet.getDisplayStreetName(),
                        rows,
                        possiblyIncomplete
                );
            }
        }

        return navigationOrder;
    }

    void highlightStreet(StreetOption currentStreet) {
        if (streetCountsToggleDialog != null) {
            streetCountsToggleDialog.getPanel().highlightStreet(currentStreet);
        }
    }

    boolean isMainDialogOpen() {
        return mainDialogOpen;
    }

    private void onStreetSelectedFromSidebar(StreetOption streetOption) {
        streetSelectionListener.accept(streetOption);
    }

    private void onRescanRequestedFromSidebar() {
        rescanListener.run();
    }

    private void onStreetNumberRowClickedFromSidebar() {
        streetNumberRowClickListener.run();
    }
}

