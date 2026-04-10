package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.actions.OrthogonalizeAction;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
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
    private final OverlayManager overlayManager = new OverlayManager();
    private final OverviewManager overviewManager = new OverviewManager();
    private final NavigationService navigationService = new NavigationService();
    private final HouseNumberOverlayCollector houseNumberOverlayCollector = new HouseNumberOverlayCollector();
    private final SingleBuildingSplitService singleBuildingSplitService = new SingleBuildingSplitService();
    private final TerraceSplitService terraceSplitService = new TerraceSplitService();
    private final CornerSnapService cornerSnapService = new CornerSnapService();
    private boolean rectangularizeAfterLineSplit;
    private int configuredTerraceParts = 2;
    private AddressSelection lastSelection = new AddressSelection("", "", "", "", 1);
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

        navigationService.updateFromSelection(selection);
        lastSelection = selection;
        if (navigationService.getCurrentStreet().isEmpty()) {
            refreshOverlayLayer();
            refreshHouseNumberOverview();
            refreshStreetHouseNumberCounts();
            Logging.debug("HouseNumberClick StreetModeController.activate: skipped because street is empty.");
            return;
        }

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            Logging.info("HouseNumberClick StreetModeController.activate: map/mapView unavailable, street={0}, postcode={1}",
                    navigationService.getCurrentStreet(), navigationService.getCurrentPostcode());
            return;
        }

        if (streetMapMode == null) {
            try {
                streetMapMode = new HouseNumberClickStreetMapMode(this);
            } catch (RuntimeException ex) {
                Logging.warn("HouseNumberClick StreetModeController.activate: failed to create map mode, street={0}, postcode={1}",
                        navigationService.getCurrentStreet(), navigationService.getCurrentPostcode());
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
        return navigationService.getStreetNavigationOrder();
    }

    void zoomToCurrentStreet() {
        if (!zoomToSelectedStreetEnabled || normalize(navigationService.getCurrentStreet()).isEmpty()) {
            return;
        }

        zoomToStreetInternal(navigationService.getCurrentStreet(), false);
    }

    void zoomToStreet(String streetName) {
        zoomToStreetInternal(streetName, true);
    }

    private void zoomToStreetInternal(String streetName, boolean selectFallbackWays) {
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
        // Selection updates can trigger additional viewport reactions in JOSM.
        // For automatic checkbox-based zoom we keep selection untouched to avoid flicker.
        if (selectFallbackWays && !fallbackStreetWays.isEmpty()) {
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
        navigationService.setCurrentStreet(normalizedStreet);
        lastSelection = new AddressSelection(
                navigationService.getCurrentStreet(),
                navigationService.getCurrentPostcode(),
                lastSelection.getBuildingType(),
                lastSelection.getHouseNumber(),
                lastSelection.getHouseNumberIncrementStep()
        );
        highlightCurrentStreetInStreetCountDialog();
        refreshOverlayLayer();
        refreshHouseNumberOverview();
        zoomToStreet(normalizedStreet);
        continueWorkingFromTableInteraction();
    }

    private void highlightCurrentStreetInStreetCountDialog() {
        overviewManager.highlightStreetInStreetCountDialog(navigationService.getCurrentStreet());
    }

    void rescanPluginData() {
        // Recompute all collector-driven views so plugin UI reflects latest dataset state.
        refreshOverlayLayer();
        refreshHouseNumberOverview();
        refreshStreetHouseNumberCounts();
    }

    void continueWorkingFromTableInteraction() {
        if (lastSelection == null || lastSelection.getStreetName().isEmpty()) {
            return;
        }
        activate(lastSelection);
    }

    void onAddressApplied() {
        overlayManager.invalidateOverlayDataCache();
        refreshOverlayLayer();
    }

    void createBuildingOverviewLayer() {
        overlayManager.createBuildingOverviewLayer();
    }

    void toggleBuildingOverviewLayer() {
        overlayManager.toggleBuildingOverviewLayer();
    }

    boolean isBuildingOverviewLayerVisible() {
        return overlayManager.isBuildingOverviewLayerVisible();
    }

    private void refreshOverlayLayer() {
        overlayManager.refreshOverlayLayer(
                navigationService.getCurrentStreet(),
                houseNumberOverlayEnabled,
                connectionLinesEnabled,
                separateEvenOddConnectionLinesEnabled
        );
    }

    private void refreshHouseNumberOverview() {
        DataSet editDataSet = getActiveEditDataSet();
        overviewManager.refreshHouseNumberOverview(
                houseNumberOverviewEnabled,
                navigationService.getCurrentStreet(),
                editDataSet,
                this::continueWorkingFromTableInteraction
        );
    }

    private void refreshStreetHouseNumberCounts() {
        DataSet editDataSet = getActiveEditDataSet();
        navigationService.setStreetNavigationOrder(
                overviewManager.refreshStreetHouseNumberCounts(
                        streetHouseNumberCountsEnabled,
                        editDataSet,
                        this::onStreetHouseNumberCountSelected,
                        this::rescanPluginData,
                        navigationService.getCurrentStreet()
                )
        );
        highlightCurrentStreetInStreetCountDialog();
    }

    private void hideHouseNumberOverview() {
        overviewManager.hideHouseNumberOverview();
    }

    private void hideStreetHouseNumberCounts() {
        overviewManager.hideStreetHouseNumberCounts();
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
        overviewManager.resetSessionPositioningState();
        overlayManager.removeOverlayLayer();
        deactivate();
    }

    void setRectangularizeAfterLineSplit(boolean makeRectangular) {
        rectangularizeAfterLineSplit = makeRectangular;
    }

    SingleSplitResult executeInternalSingleSplit(LatLon lineStart, LatLon lineEnd) {
        DataSet dataSet = getActiveEditDataSet();
        if (dataSet == null) {
            return failSingleSplit("No editable dataset is available.");
        }
        clearDataSetSelection(dataSet);

        SplitTargetScan splitTargetScan = findSingleSplitTargetWay(dataSet, lineStart, lineEnd);
        if (splitTargetScan.ambiguous) {
            return failSingleSplit("Split line intersects multiple buildings. Draw a line through only one building.");
        }
        if (splitTargetScan.targetWay == null) {
            return failSingleSplit(splitTargetScan.touchOnly
                    ? "Split line only touches building edges. Draw the line through one building."
                    : "Split line does not intersect a building.");
        }

        SplitContext splitContext = new SplitContext(
                navigationService.getCurrentStreet(),
                navigationService.getCurrentPostcode()
        );
        SingleSplitResult result = singleBuildingSplitService.splitBuilding(
                dataSet,
                splitTargetScan.targetWay,
                lineStart,
                lineEnd,
                splitContext
        );

        if (!result.isSuccess()) {
            new Notification(I18n.tr(result.getMessage()))
                    .setDuration(Notification.TIME_SHORT)
                    .show();
        } else {
            if (rectangularizeAfterLineSplit) {
                applyRectangularizeOnWays(result.getResultWays());
            }
            clearDataSetSelection(dataSet);
        }
        return result;
    }

    private SingleSplitResult failSingleSplit(String message) {
        SingleSplitResult failure = SingleSplitResult.failure(message);
        showShortNotification(failure.getMessage());
        return failure;
    }


    int getConfiguredTerraceParts() {
        return configuredTerraceParts;
    }

    void setConfiguredTerraceParts(int parts) {
        if (parts >= 1) {
            configuredTerraceParts = parts;
        }
    }

    TerraceSplitResult executeInternalTerraceSplitAtClick(Way clickedBuilding, int parts) {
        DataSet dataSet = getActiveEditDataSet();
        if (dataSet == null) {
            TerraceSplitResult failure = TerraceSplitResult.failure("No editable dataset is available.");
            showShortNotification(failure.getMessage());
            return failure;
        }
        if (clickedBuilding == null) {
            TerraceSplitResult failure = TerraceSplitResult.failure("Click inside one closed building to create row houses.");
            showShortNotification(failure.getMessage());
            return failure;
        }

        clearDataSetSelection(dataSet);

        SplitContext splitContext = new SplitContext(
                navigationService.getCurrentStreet(),
                navigationService.getCurrentPostcode()
        );
        TerraceSplitResult result = terraceSplitService.splitBuildingIntoTerrace(
                dataSet,
                clickedBuilding,
                new TerraceSplitRequest(parts),
                splitContext
        );
        if (!result.isSuccess()) {
            showShortNotification(result.getMessage());
            return result;
        }

        dataSet.setSelected(result.getResultWays());
        return result;
    }

    private void applyRectangularizeOnWays(List<Way> ways) {
        if (ways == null || ways.isEmpty()) {
            return;
        }
        List<Way> rectangularizeCandidates = new ArrayList<>();
        for (Way way : ways) {
            if (isRectangularizeCandidate(way)) {
                rectangularizeCandidates.add(way);
            }
        }
        if (rectangularizeCandidates.isEmpty()) {
            return;
        }
        try {
            List<OsmPrimitive> orthogonalizeTargets = new ArrayList<>(rectangularizeCandidates);
            SequenceCommand orthogonalizeCommand = OrthogonalizeAction.orthogonalize(orthogonalizeTargets);
            if (orthogonalizeCommand != null) {
                UndoRedoHandler.getInstance().add(orthogonalizeCommand);
            }
        } catch (OrthogonalizeAction.InvalidUserInputException ex) {
            Logging.debug(ex);
            showShortNotification("Rectangularize failed for the split result.");
        }
    }

    static boolean isRectangularizeCandidate(Way way) {
        if (way == null || !way.isClosed()) {
            return false;
        }
        List<Node> nodes = way.getNodes();
        if (nodes == null || nodes.size() < 5) {
            return false;
        }
        LinkedHashSet<Node> uniqueCorners = new LinkedHashSet<>();
        int endExclusive = nodes.size() - 1;
        for (int i = 0; i < endExclusive; i++) {
            Node node = nodes.get(i);
            if (node != null) {
                uniqueCorners.add(node);
            }
        }
        return uniqueCorners.size() >= 4;
    }

    private SplitTargetScan findSingleSplitTargetWay(DataSet dataSet, LatLon lineStart, LatLon lineEnd) {
        if (dataSet == null || lineStart == null || lineEnd == null) {
            return SplitTargetScan.none();
        }

        Way target = null;
        boolean touchedOnly = false;
        boolean ambiguous = false;
        for (Way way : dataSet.getWays()) {
            if (way == null || !way.isUsable() || !way.isClosed() || !way.hasKey("building")) {
                continue;
            }
            IntersectionScanResult scanResult = cornerSnapService.findSplitIntersections(way, lineStart, lineEnd);
            if (!scanResult.isSuccess()) {
                if (scanResult.getMessage() != null && scanResult.getMessage().contains("overlaps building edge")) {
                    touchedOnly = true;
                }
                continue;
            }

            int intersections = scanResult.getIntersections().size();
            if (intersections >= 2) {
                if (target != null) {
                    ambiguous = true;
                    break;
                }
                target = way;
            } else if (intersections == 1) {
                touchedOnly = true;
            }
        }
        return new SplitTargetScan(target, touchedOnly, ambiguous);
    }

    private static final class SplitTargetScan {
        private final Way targetWay;
        private final boolean touchOnly;
        private final boolean ambiguous;

        private SplitTargetScan(Way targetWay, boolean touchOnly, boolean ambiguous) {
            this.targetWay = targetWay;
            this.touchOnly = touchOnly;
            this.ambiguous = ambiguous;
        }

        private static SplitTargetScan none() {
            return new SplitTargetScan(null, false, false);
        }
    }

    private DataSet getActiveEditDataSet() {
        return MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
    }

    private void clearDataSetSelection(DataSet dataSet) {
        if (dataSet != null) {
            dataSet.setSelected(Collections.emptyList());
        }
    }

    private void showShortNotification(String message) {
        new Notification(I18n.tr(message))
                .setDuration(Notification.TIME_SHORT)
                .show();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeIncrementStep(int step) {
        return step == -2 || step == -1 || step == 1 || step == 2 ? step : 1;
    }
}
