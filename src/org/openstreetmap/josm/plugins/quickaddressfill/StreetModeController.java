package org.openstreetmap.josm.plugins.quickaddressfill;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.I18n;

final class StreetModeController {

    private QuickAddressFillStreetMapMode streetMapMode;
    private HouseNumberUpdateListener houseNumberUpdateListener;
    private AddressValuesReadListener addressValuesReadListener;
    private BuildingTypeConsumedListener buildingTypeConsumedListener;
    private ModeStateListener modeStateListener;
    private boolean waitingForBuildingSplitterReturn;
    private MapMode buildingSplitterMode;
    private MapFrame.MapModeChangeListener buildingSplitterReturnListener;

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
        String normalizedStreet = streetName == null ? "" : streetName.trim();
        if (normalizedStreet.isEmpty()) {
            return;
        }

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return;
        }

        if (streetMapMode == null) {
            try {
                streetMapMode = new QuickAddressFillStreetMapMode(this);
            } catch (RuntimeException ex) {
                new Notification(I18n.tr("Street Mode could not be started."))
                        .setDuration(Notification.TIME_SHORT)
                        .show();
                return;
            }
        }

        streetMapMode.setAddressValues(normalizedStreet, postcode, buildingType, houseNumber, houseNumberIncrementStep);
        map.selectMapMode(streetMapMode);
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

    boolean activateBuildingSplitterAndReturn() {
        if (!BuildingSplitterBridge.activateBuildingSplitter()) {
            return false;
        }
        armReturnToQuickAddressFill();
        return true;
    }

    private void armReturnToQuickAddressFill() {
        MapFrame map = MainApplication.getMap();
        if (map == null) {
            return;
        }

        MapMode currentMode = map.mapMode;
        if (currentMode == null || currentMode == streetMapMode) {
            return;
        }

        waitingForBuildingSplitterReturn = true;
        buildingSplitterMode = currentMode;
        ensureBuildingSplitterReturnListenerRegistered();
    }

    private void ensureBuildingSplitterReturnListenerRegistered() {
        if (buildingSplitterReturnListener != null) {
            return;
        }
        buildingSplitterReturnListener = this::onMapModeChanged;
        MapFrame.addMapModeChangeListener(buildingSplitterReturnListener);
    }

    private void onMapModeChanged(MapMode oldMode, MapMode newMode) {
        if (!waitingForBuildingSplitterReturn) {
            return;
        }

        if (newMode == buildingSplitterMode) {
            return;
        }

        disarmReturnToQuickAddressFill();
        MapFrame map = MainApplication.getMap();
        if (map == null || streetMapMode == null) {
            return;
        }

        try {
            map.selectMapMode(streetMapMode);
        } catch (RuntimeException ex) {
            // Keep failure silent to avoid interrupting user workflow.
        }
    }

    private void disarmReturnToQuickAddressFill() {
        waitingForBuildingSplitterReturn = false;
        buildingSplitterMode = null;
        if (buildingSplitterReturnListener != null) {
            MapFrame.removeMapModeChangeListener(buildingSplitterReturnListener);
            buildingSplitterReturnListener = null;
        }
    }

    void deactivate() {
        disarmReturnToQuickAddressFill();
        MapFrame map = MainApplication.getMap();
        if (map != null && streetMapMode != null && map.mapMode == streetMapMode) {
            map.selectSelectTool(false);
        }
    }
}
