package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.event.ActionEvent;
import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.I18n;

/**
 * Main toolbar/menu action that follows JOSM tool availability (enabled only with an editable
 * dataset/layer), opens the street selection dialog (including optional country prefill and
 * constrained likely-country code options), keeps dialog pause/resume state in sync with
 * temporary edit-layer loss/recovery, initializes persistent sidebar overview dialogs, and
 * activates street mode.
 */
public class HouseNumberClickAction extends JosmAction {

    private final StreetModeController streetModeController;
    private final HouseNumberClickSidebarController sidebarController;
    private final StreetSelectionDialog streetSelectionDialog;
    private final CountryDetectionService countryDetectionService;

    public HouseNumberClickAction() {
        super(I18n.tr("HouseNumberClick"), "housenumberclick", I18n.tr("Open HouseNumberClick street dialog"), null, true);
        this.sidebarController = new HouseNumberClickSidebarController();
        this.streetModeController = new StreetModeController(sidebarController);
        this.streetSelectionDialog = new StreetSelectionDialog(streetModeController);
        this.countryDetectionService = new CountryDetectionService();
        updateEnabledState();
    }

    void onMapFrameInitialized(MapFrame newFrame) {
        if (newFrame != null) {
            streetModeController.attachSidebarDialogs(newFrame);
        }
    }

    @Override
    protected boolean listenToLayerChange() {
        return true;
    }

    @Override
    protected void updateEnabledState() {
        boolean wasEnabled = isEnabled();
        boolean hasEditDataSet = MainApplication.getLayerManager() != null
                && MainApplication.getLayerManager().getEditDataSet() != null;
        setEnabled(hasEditDataSet);
        if (wasEnabled && !hasEditDataSet && streetSelectionDialog != null) {
            streetSelectionDialog.onEditLayerUnavailable();
        } else if (!wasEnabled && hasEditDataSet && streetSelectionDialog != null) {
            streetSelectionDialog.onEditLayerAvailable();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (dataSet == null) {
            StreetSelectionDialog.showNoDataSetMessage();
            return;
        }

        List<StreetOption> streetOptions = StreetNameCollector.collectStreetIndex(dataSet).getStreetOptions();
        List<String> detectedPostcodes = PostcodeCollector.collectVisiblePostcodes(dataSet);
        String detectedCountry = countryDetectionService.detectConfidentCountry(dataSet);
        List<String> likelyCountryCodes = countryDetectionService.collectLikelyCountryCodes(dataSet, 10);
        streetSelectionDialog.showDialog(dataSet, streetOptions, detectedPostcodes, detectedCountry, likelyCountryCodes);
    }
}
