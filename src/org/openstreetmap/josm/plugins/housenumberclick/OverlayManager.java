package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.tools.I18n;

/**
 * Manages creation, refresh, visibility, and teardown of plugin-owned map overlay layers.
 */
final class OverlayManager {

    private HouseNumberOverlayLayer houseNumberOverlayLayer;
    private BuildingOverviewLayer buildingOverviewLayer;
    private DuplicateAddressOverviewLayer duplicateAddressOverviewLayer;
    private PostcodeOverviewLayer postcodeOverviewLayer;
    private ReferenceStreetLayer referenceStreetLayer;
    private BuildingOverviewLayer.MissingField completenessMissingField = BuildingOverviewLayer.MissingField.POSTCODE;

    void refreshOverlayLayer(
            String currentStreet,
            boolean overlayEnabled,
            boolean connectionLinesEnabled,
            boolean separateEvenOddConnectionLinesEnabled
    ) {
        if (normalize(currentStreet).isEmpty()) {
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
                overlayEnabled,
                connectionLinesEnabled,
                separateEvenOddConnectionLinesEnabled
        );
        ensureReferenceLayerBelowOverlay(layerManager);
        ensureOverlayLayerAboveOverviewLayers(layerManager);
        map.mapView.repaint();
    }

    void showReferenceStreetLayer(String streetName, DataSet referenceDataSet) {
        LayerManager layerManager = MainApplication.getLayerManager();
        MapFrame map = MainApplication.getMap();
        if (layerManager == null || map == null || map.mapView == null) {
            return;
        }

        if (referenceStreetLayer == null || !layerManager.containsLayer(referenceStreetLayer)) {
            referenceStreetLayer = new ReferenceStreetLayer();
            layerManager.addLayer(referenceStreetLayer, false);
        }

        referenceStreetLayer.updateReferenceStreet(streetName, referenceDataSet);
        ensureReferenceLayerBelowOverlay(layerManager);
        map.mapView.repaint();
    }


    void removeReferenceStreetLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (referenceStreetLayer == null || layerManager == null) {
            return;
        }
        if (layerManager.containsLayer(referenceStreetLayer)) {
            layerManager.removeLayer(referenceStreetLayer);
        }
        referenceStreetLayer = null;
    }

    void invalidateOverlayDataCache() {
        if (houseNumberOverlayLayer != null) {
            houseNumberOverlayLayer.invalidateDataCache();
        }
    }

    void setCompletenessMissingField(BuildingOverviewLayer.MissingField missingField) {
        completenessMissingField = missingField != null ? missingField : BuildingOverviewLayer.MissingField.POSTCODE;
    }

    void createBuildingOverviewLayer() {
        createBuildingOverviewLayer(completenessMissingField);
    }

    void createBuildingOverviewLayer(BuildingOverviewLayer.MissingField missingField) {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (layerManager == null) {
            return;
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (editDataSet == null) {
            new Notification(I18n.tr("No active dataset available."))
                    .setDuration(Notification.TIME_SHORT)
                    .show();
            return;
        }

        new Notification(I18n.tr("Please wait, this takes a moment."))
                .setDuration(Notification.TIME_SHORT)
                .show();

        removePostcodeOverviewLayer();
        removeDuplicateAddressOverviewLayer();
        removeBuildingOverviewLayer();
        buildingOverviewLayer = new BuildingOverviewLayer(editDataSet, missingField);
        layerManager.addLayer(buildingOverviewLayer, false);
        ensureOverlayLayerAboveOverviewLayers(layerManager);

        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.repaint();
        }
    }

    void createPostcodeOverviewLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (layerManager == null) {
            return;
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (editDataSet == null) {
            new Notification(I18n.tr("No active dataset available."))
                    .setDuration(Notification.TIME_SHORT)
                    .show();
            return;
        }

        new Notification(I18n.tr("Please wait, this takes a moment."))
                .setDuration(Notification.TIME_SHORT)
                .show();

        removeBuildingOverviewLayer();
        removeDuplicateAddressOverviewLayer();
        removePostcodeOverviewLayer();
        postcodeOverviewLayer = new PostcodeOverviewLayer(editDataSet);
        layerManager.addLayer(postcodeOverviewLayer, false);
        ensureOverlayLayerAboveOverviewLayers(layerManager);

        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.repaint();
        }
    }

    void toggleBuildingOverviewLayer() {
        toggleBuildingOverviewLayer(completenessMissingField);
    }

    void toggleBuildingOverviewLayer(BuildingOverviewLayer.MissingField missingField) {
        if (isBuildingOverviewLayerVisible()) {
            removeBuildingOverviewLayer();
            MapFrame map = MainApplication.getMap();
            if (map != null && map.mapView != null) {
                map.mapView.repaint();
            }
            return;
        }
        createBuildingOverviewLayer(missingField);
    }

    void createDuplicateAddressOverviewLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (layerManager == null) {
            return;
        }

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (editDataSet == null) {
            new Notification(I18n.tr("No active dataset available."))
                    .setDuration(Notification.TIME_SHORT)
                    .show();
            return;
        }

        new Notification(I18n.tr("Please wait, this takes a moment."))
                .setDuration(Notification.TIME_SHORT)
                .show();

        removeBuildingOverviewLayer();
        removePostcodeOverviewLayer();
        removeDuplicateAddressOverviewLayer();
        duplicateAddressOverviewLayer = new DuplicateAddressOverviewLayer(editDataSet);
        layerManager.addLayer(duplicateAddressOverviewLayer, false);
        ensureOverlayLayerAboveOverviewLayers(layerManager);

        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.repaint();
        }
    }

    void toggleDuplicateAddressOverviewLayer() {
        if (isDuplicateAddressOverviewLayerVisible()) {
            removeDuplicateAddressOverviewLayer();
            MapFrame map = MainApplication.getMap();
            if (map != null && map.mapView != null) {
                map.mapView.repaint();
            }
            return;
        }
        createDuplicateAddressOverviewLayer();
    }

    void togglePostcodeOverviewLayer() {
        if (isPostcodeOverviewLayerVisible()) {
            removePostcodeOverviewLayer();
            MapFrame map = MainApplication.getMap();
            if (map != null && map.mapView != null) {
                map.mapView.repaint();
            }
            return;
        }
        createPostcodeOverviewLayer();
    }

    boolean isBuildingOverviewLayerVisible() {
        LayerManager layerManager = MainApplication.getLayerManager();
        return layerManager != null
                && buildingOverviewLayer != null
                && layerManager.containsLayer(buildingOverviewLayer);
    }

    boolean isPostcodeOverviewLayerVisible() {
        LayerManager layerManager = MainApplication.getLayerManager();
        return layerManager != null
                && postcodeOverviewLayer != null
                && layerManager.containsLayer(postcodeOverviewLayer);
    }

    boolean isDuplicateAddressOverviewLayerVisible() {
        LayerManager layerManager = MainApplication.getLayerManager();
        return layerManager != null
                && duplicateAddressOverviewLayer != null
                && layerManager.containsLayer(duplicateAddressOverviewLayer);
    }

    void removeOverlayLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (houseNumberOverlayLayer == null || layerManager == null) {
            return;
        }
        if (layerManager.containsLayer(houseNumberOverlayLayer)) {
            layerManager.removeLayer(houseNumberOverlayLayer);
        }
        houseNumberOverlayLayer = null;
    }

    private void removeBuildingOverviewLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (layerManager == null) {
            buildingOverviewLayer = null;
            return;
        }

        List<Layer> layers = new ArrayList<>(layerManager.getLayers());
        for (Layer layer : layers) {
            if (layer instanceof BuildingOverviewLayer && layerManager.containsLayer(layer)) {
                layerManager.removeLayer(layer);
            }
        }
        buildingOverviewLayer = null;
    }

    private void removePostcodeOverviewLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (layerManager == null) {
            postcodeOverviewLayer = null;
            return;
        }

        List<Layer> layers = new ArrayList<>(layerManager.getLayers());
        for (Layer layer : layers) {
            if (layer instanceof PostcodeOverviewLayer && layerManager.containsLayer(layer)) {
                layerManager.removeLayer(layer);
            }
        }
        postcodeOverviewLayer = null;
    }

    private void removeDuplicateAddressOverviewLayer() {
        LayerManager layerManager = MainApplication.getLayerManager();
        if (layerManager == null) {
            duplicateAddressOverviewLayer = null;
            return;
        }

        List<Layer> layers = new ArrayList<>(layerManager.getLayers());
        for (Layer layer : layers) {
            if (layer instanceof DuplicateAddressOverviewLayer && layerManager.containsLayer(layer)) {
                layerManager.removeLayer(layer);
            }
        }
        duplicateAddressOverviewLayer = null;
    }

    private void ensureOverlayLayerAboveOverviewLayers(LayerManager layerManager) {
        if (layerManager == null || houseNumberOverlayLayer == null) {
            return;
        }
        if (!layerManager.containsLayer(houseNumberOverlayLayer)) {
            return;
        }

        Layer targetAbove = resolveTopMostOverviewLayer(layerManager);
        if (targetAbove == null) {
            return;
        }

        List<Layer> layers = layerManager.getLayers();
        int overlayIndex = layers.indexOf(houseNumberOverlayLayer);
        int overviewIndex = layers.indexOf(targetAbove);
        if (overlayIndex < 0 || overviewIndex < 0 || overlayIndex < overviewIndex) {
            return;
        }

        // In JOSM layer index ordering, lower index means visually above.
        layerManager.moveLayer(houseNumberOverlayLayer, Math.max(overviewIndex - 1, 0));
    }

    private Layer resolveTopMostOverviewLayer(LayerManager layerManager) {
        List<Layer> layers = layerManager.getLayers();
        Layer topMost = null;
        int topMostIndex = Integer.MAX_VALUE;

        if (buildingOverviewLayer != null && layerManager.containsLayer(buildingOverviewLayer)) {
            int index = layers.indexOf(buildingOverviewLayer);
            if (index >= 0 && index < topMostIndex) {
                topMostIndex = index;
                topMost = buildingOverviewLayer;
            }
        }

        if (postcodeOverviewLayer != null && layerManager.containsLayer(postcodeOverviewLayer)) {
            int index = layers.indexOf(postcodeOverviewLayer);
            if (index >= 0 && index < topMostIndex) {
                topMostIndex = index;
                topMost = postcodeOverviewLayer;
            }
        }

        if (duplicateAddressOverviewLayer != null && layerManager.containsLayer(duplicateAddressOverviewLayer)) {
            int index = layers.indexOf(duplicateAddressOverviewLayer);
            if (index >= 0 && index < topMostIndex) {
                topMostIndex = index;
                topMost = duplicateAddressOverviewLayer;
            }
        }

        return topMost;
    }

    private void ensureReferenceLayerBelowOverlay(LayerManager layerManager) {
        if (layerManager == null || referenceStreetLayer == null || houseNumberOverlayLayer == null) {
            return;
        }
        if (!layerManager.containsLayer(referenceStreetLayer) || !layerManager.containsLayer(houseNumberOverlayLayer)) {
            return;
        }

        List<Layer> layers = layerManager.getLayers();
        int referenceIndex = layers.indexOf(referenceStreetLayer);
        int overlayIndex = layers.indexOf(houseNumberOverlayLayer);
        if (referenceIndex < 0 || overlayIndex < 0 || referenceIndex > overlayIndex) {
            return;
        }

        // Keep reference behind the main highlight/label overlay.
        layerManager.moveLayer(referenceStreetLayer, Math.min(overlayIndex + 1, layers.size() - 1));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
