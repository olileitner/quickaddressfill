package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

final class StreetModeController {

    static final class AddressSelection {
        private final String streetName;
        private final String postcode;
        private final String buildingType;
        private final String houseNumber;
        private final int houseNumberIncrementStep;

        AddressSelection(String streetName, String postcode, String buildingType, String houseNumber, int houseNumberIncrementStep) {
            this.streetName = normalize(streetName);
            this.postcode = normalize(postcode);
            this.buildingType = normalize(buildingType);
            this.houseNumber = normalize(houseNumber);
            this.houseNumberIncrementStep = normalizeIncrementStep(houseNumberIncrementStep);
        }

        String getStreetName() {
            return streetName;
        }

        String getPostcode() {
            return postcode;
        }

        String getBuildingType() {
            return buildingType;
        }

        String getHouseNumber() {
            return houseNumber;
        }

        int getHouseNumberIncrementStep() {
            return houseNumberIncrementStep;
        }
    }

    private HouseNumberClickStreetMapMode streetMapMode;
    private HouseNumberOverlayLayer houseNumberOverlayLayer;
    private HouseNumberOverviewDialog houseNumberOverviewDialog;
    private StreetHouseNumberCountDialog streetHouseNumberCountDialog;
    private final HouseNumberOverlayCollector houseNumberOverlayCollector = new HouseNumberOverlayCollector();
    private final HouseNumberOverviewCollector houseNumberOverviewCollector = new HouseNumberOverviewCollector();
    private final StreetHouseNumberCountCollector streetHouseNumberCountCollector = new StreetHouseNumberCountCollector();
    private List<String> streetNavigationOrder = List.of();
    private String currentStreet = "";
    private String currentPostcode = "";
    private boolean houseNumberOverlayEnabled;
    private boolean connectionLinesEnabled;
    private boolean separateEvenOddConnectionLinesEnabled;
    private boolean houseNumberOverviewEnabled;
    private boolean streetHouseNumberCountsEnabled;
    private boolean zoomToSelectedStreetEnabled;
    private HouseNumberUpdateListener houseNumberUpdateListener;
    private AddressValuesReadListener addressValuesReadListener;
    private BuildingTypeConsumedListener buildingTypeConsumedListener;
    private ModeStateListener modeStateListener;

    interface HouseNumberUpdateListener {
        void onHouseNumberUpdated(String houseNumber);
    }

    interface AddressValuesReadListener {
        void onAddressValuesRead(String streetName, String postcode, String buildingType, String houseNumber);
    }

    interface BuildingTypeConsumedListener {
        void onBuildingTypeConsumed();
    }

    interface ModeStateListener {
        void onModeStateChanged(boolean active);
    }

    boolean isActive() {
        MapFrame map = MainApplication.getMap();
        return map != null && streetMapMode != null && map.mapMode == streetMapMode;
    }

    void activate(String streetName, String postcode, String buildingType, String houseNumber, int houseNumberIncrementStep) {
        activate(new AddressSelection(streetName, postcode, buildingType, houseNumber, houseNumberIncrementStep));
    }

    void activate(AddressSelection selection) {
        if (selection == null) {
            Logging.warn("HouseNumberClick StreetModeController.activate: called with null selection.");
            return;
        }

        currentStreet = selection.getStreetName();
        currentPostcode = selection.getPostcode();
        if (currentStreet.isEmpty()) {
            refreshOverlayLayer();
            refreshHouseNumberOverview();
            refreshStreetHouseNumberCounts();
            Logging.debug("HouseNumberClick StreetModeController.activate: skipped because street is empty.");
            return;
        }

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            Logging.info("HouseNumberClick StreetModeController.activate: map/mapView unavailable, street={0}, postcode={1}",
                    currentStreet, currentPostcode);
            return;
        }

        if (streetMapMode == null) {
            try {
                streetMapMode = new HouseNumberClickStreetMapMode(this);
            } catch (RuntimeException ex) {
                Logging.warn("HouseNumberClick StreetModeController.activate: failed to create map mode, street={0}, postcode={1}",
                        currentStreet, currentPostcode);
                Logging.debug(ex);
                new Notification(I18n.tr("Street Mode could not be started."))
                        .setDuration(Notification.TIME_SHORT)
                        .show();
                return;
            }
        }

        streetMapMode.setAddressValues(
                selection.getStreetName(),
                selection.getPostcode(),
                selection.getBuildingType(),
                selection.getHouseNumber(),
                selection.getHouseNumberIncrementStep()
        );
        refreshOverlayLayer();
        refreshHouseNumberOverview();
        refreshStreetHouseNumberCounts();
        map.selectMapMode(streetMapMode);
    }

    boolean activateBuildingSplitterWithCurrentAddress() {
        return activateBuildingSplitterWithAddress(currentStreet, currentPostcode);
    }

    boolean activateBuildingSplitterWithAddress(String street, String postcode) {
        String normalizedStreet = street == null ? "" : street.trim();
        String normalizedPostcode = postcode == null ? "" : postcode.trim();
        boolean activated = BuildingSplitterBridge.activateBuildingSplitter(normalizedStreet, normalizedPostcode);
        if (!activated) {
            Logging.info("HouseNumberClick StreetModeController.activateBuildingSplitterWithAddress: activation failed, street={0}, postcode={1}",
                    normalizedStreet, normalizedPostcode);
        }
        return activated;
    }

    void setHouseNumberUpdateListener(HouseNumberUpdateListener listener) {
        this.houseNumberUpdateListener = listener;
    }

    void updateHouseNumber(String houseNumber) {
        if (houseNumberUpdateListener != null) {
            houseNumberUpdateListener.onHouseNumberUpdated(houseNumber);
        }
    }

    void setAddressValuesReadListener(AddressValuesReadListener listener) {
        this.addressValuesReadListener = listener;
    }

    void updateAddressValues(String streetName, String postcode, String buildingType, String houseNumber) {
        if (addressValuesReadListener != null) {
            addressValuesReadListener.onAddressValuesRead(streetName, postcode, buildingType, houseNumber);
        }
    }

    void setBuildingTypeConsumedListener(BuildingTypeConsumedListener listener) {
        this.buildingTypeConsumedListener = listener;
    }

    void notifyBuildingTypeConsumed() {
        if (buildingTypeConsumedListener != null) {
            buildingTypeConsumedListener.onBuildingTypeConsumed();
        }
    }

    void setModeStateListener(ModeStateListener listener) {
        this.modeStateListener = listener;
        if (modeStateListener != null) {
            modeStateListener.onModeStateChanged(isActive());
        }
    }

    void notifyModeStateChanged(boolean active) {
        if (modeStateListener != null) {
            modeStateListener.onModeStateChanged(active);
        }
    }

    void updateOverlaySettings(boolean overlayEnabled, boolean connectionLinesEnabled, boolean separateEvenOddLinesEnabled) {
        houseNumberOverlayEnabled = overlayEnabled;
        this.connectionLinesEnabled = overlayEnabled && connectionLinesEnabled;
        this.separateEvenOddConnectionLinesEnabled = this.connectionLinesEnabled && separateEvenOddLinesEnabled;
        refreshOverlayLayer();
    }

    void setHouseNumberOverviewEnabled(boolean enabled) {
        houseNumberOverviewEnabled = enabled;
        refreshHouseNumberOverview();
    }

    void setZoomToSelectedStreetEnabled(boolean enabled) {
        zoomToSelectedStreetEnabled = enabled;
    }

    void setStreetHouseNumberCountsEnabled(boolean enabled) {
        streetHouseNumberCountsEnabled = enabled;
        refreshStreetHouseNumberCounts();
    }

    List<String> getStreetNavigationOrder() {
        return new ArrayList<>(streetNavigationOrder);
    }

    void zoomToCurrentStreet() {
        if (!zoomToSelectedStreetEnabled || normalize(currentStreet).isEmpty()) {
            return;
        }

        zoomToStreet(currentStreet);
    }

    void zoomToStreet(String streetName) {
        String normalizedStreet = normalize(streetName);
        if (normalizedStreet.isEmpty()) {
            return;
        }

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || MainApplication.getLayerManager() == null) {
            return;
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (editDataSet == null) {
            return;
        }

        List<HouseNumberOverlayEntry> entries = houseNumberOverlayCollector.collect(
                editDataSet,
                normalizedStreet
        );
        List<OsmPrimitive> fallbackStreetWays = List.of();

        BoundingXYVisitor visitor = new BoundingXYVisitor();
        if (entries.isEmpty()) {
            fallbackStreetWays = collectStreetWayFallbackPrimitives(editDataSet, normalizedStreet);
            for (OsmPrimitive primitive : fallbackStreetWays) {
                primitive.accept((OsmPrimitiveVisitor) visitor);
            }
        } else {
            for (HouseNumberOverlayEntry entry : entries) {
                OsmPrimitive primitive = entry.getPrimitive();
                if (primitive == null || !primitive.isUsable()) {
                    continue;
                }
                primitive.accept((OsmPrimitiveVisitor) visitor);
            }
        }

        if (!visitor.hasExtend()) {
            return;
        }
        visitor.enlargeBoundingBox();
        map.mapView.zoomTo(visitor);
        if (!fallbackStreetWays.isEmpty()) {
            editDataSet.setSelected(fallbackStreetWays);
            map.mapView.repaint();
        }
    }

    static List<OsmPrimitive> collectStreetWayFallbackPrimitives(DataSet dataSet, String streetName) {
        String normalizedStreet = normalize(streetName);
        if (dataSet == null || normalizedStreet.isEmpty()) {
            return List.of();
        }

        Collection<Way> candidates = dataSet.getWays();
        LinkedHashSet<OsmPrimitive> matchingWays = new LinkedHashSet<>();
        for (Way way : candidates) {
            if (way == null || !way.isUsable() || !way.hasTag("highway")) {
                continue;
            }
            if (!normalize(way.get("name")).equalsIgnoreCase(normalizedStreet)) {
                continue;
            }
            matchingWays.add(way);
        }
        return new ArrayList<>(matchingWays);
    }

    private void onStreetHouseNumberCountSelected(String streetName) {
        String normalizedStreet = normalize(streetName);
        if (normalizedStreet.isEmpty()) {
            return;
        }

        // Keep optional street-based overlays in sync with the row the user clicked.
        currentStreet = normalizedStreet;
        highlightCurrentStreetInStreetCountDialog();
        refreshOverlayLayer();
        refreshHouseNumberOverview();
        zoomToStreet(normalizedStreet);
    }

    private void highlightCurrentStreetInStreetCountDialog() {
        if (streetHouseNumberCountDialog == null) {
            return;
        }
        streetHouseNumberCountDialog.highlightStreet(currentStreet);
    }

    private void refreshOverlayLayer() {
        if (!houseNumberOverlayEnabled || normalize(currentStreet).isEmpty()) {
            removeOverlayLayer();
            return;
        }

        LayerManager layerManager = MainApplication.getLayerManager();
        MapFrame map = MainApplication.getMap();
        if (layerManager == null || map == null || map.mapView == null) {
            return;
        }

        if (houseNumberOverlayLayer == null || !layerManager.containsLayer(houseNumberOverlayLayer)) {
            houseNumberOverlayLayer = new HouseNumberOverlayLayer();
            layerManager.addLayer(houseNumberOverlayLayer, false);
        }

        houseNumberOverlayLayer.updateSettings(
                currentStreet,
                connectionLinesEnabled,
                separateEvenOddConnectionLinesEnabled
        );
        map.mapView.repaint();
    }

    private void removeOverlayLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (houseNumberOverlayLayer == null || layerManager == null) {
            return;
        }
        if (layerManager.containsLayer(houseNumberOverlayLayer)) {
            layerManager.removeLayer(houseNumberOverlayLayer);
        }
        houseNumberOverlayLayer = null;
    }

    private void refreshHouseNumberOverview() {
        if (!houseNumberOverviewEnabled || normalize(currentStreet).isEmpty()) {
            hideHouseNumberOverview();
            return;
        }

        if (MainApplication.getLayerManager() == null) {
            hideHouseNumberOverview();
            return;
        }

        if (houseNumberOverviewDialog == null) {
            houseNumberOverviewDialog = new HouseNumberOverviewDialog();
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        houseNumberOverviewDialog.updateData(
                currentStreet,
                houseNumberOverviewCollector.collectRows(editDataSet, currentStreet)
        );
        houseNumberOverviewDialog.showDialog();
    }

    private void refreshStreetHouseNumberCounts() {
        if (!streetHouseNumberCountsEnabled) {
            streetNavigationOrder = List.of();
            hideStreetHouseNumberCounts();
            return;
        }

        if (MainApplication.getLayerManager() == null) {
            streetNavigationOrder = List.of();
            hideStreetHouseNumberCounts();
            return;
        }

        if (streetHouseNumberCountDialog == null) {
            streetHouseNumberCountDialog = new StreetHouseNumberCountDialog(this::onStreetHouseNumberCountSelected);
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        List<StreetHouseNumberCountRow> rows = streetHouseNumberCountCollector.collectRows(editDataSet);
        streetNavigationOrder = Collections.unmodifiableList(
                StreetHouseNumberCountDialog.buildStreetNavigationOrder(rows)
        );
        streetHouseNumberCountDialog.updateData(rows);
        streetHouseNumberCountDialog.showDialog();
        highlightCurrentStreetInStreetCountDialog();
    }

    private void hideHouseNumberOverview() {
        if (houseNumberOverviewDialog != null) {
            houseNumberOverviewDialog.hideDialog();
        }
    }

    private void hideStreetHouseNumberCounts() {
        if (streetHouseNumberCountDialog != null) {
            streetHouseNumberCountDialog.hideDialog();
        }
    }

    void deactivate() {
        MapFrame map = MainApplication.getMap();
        if (map != null && streetMapMode != null && map.mapMode == streetMapMode) {
            map.selectSelectTool(false);
        }
    }

    void onMainDialogClosed() {
        // Closing the main dialog should also close dependent views and clear visual overlays.
        hideHouseNumberOverview();
        hideStreetHouseNumberCounts();
        removeOverlayLayer();
        deactivate();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeIncrementStep(int step) {
        return step == -2 || step == -1 || step == 1 || step == 2 ? step : 1;
    }
}
