package org.openstreetmap.josm.plugins.quickaddressfill;

import java.awt.event.ActionEvent;
import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

public class QuickAddressFillAction extends JosmAction {

    private final StreetModeController streetModeController;
    private final StreetSelectionDialog streetSelectionDialog;

    public QuickAddressFillAction() {
        super(I18n.tr("Quick Address Fill"), "quickaddressfill", I18n.tr("Open Quick Address Fill street dialog"), null, false);
        this.streetModeController = new StreetModeController();
        this.streetSelectionDialog = new StreetSelectionDialog(streetModeController);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            StreetSelectionDialog.showNoDataSetMessage();
            return;
        }

        List<String> streetNames = StreetNameCollector.collectStreetNames(dataSet);
        String suggestedPostcode = PostcodeCollector.detectUniformVisiblePostcode(dataSet);
        streetSelectionDialog.showDialog(streetNames, suggestedPostcode);
    }
}
