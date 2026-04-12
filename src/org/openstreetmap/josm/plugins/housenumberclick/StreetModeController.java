package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.actions.OrthogonalizeAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DataSourceListener;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.util.GuiHelper;
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
    private final ReferenceStreetFetchService referenceStreetFetchService = new ReferenceStreetFetchService();
    private final StreetCompletenessHeuristic streetCompletenessHeuristic = new StreetCompletenessHeuristic();
    private final Map<String, DataSet> referenceStreetCache = new HashMap<>();
    private final Set<String> referenceStreetLoadsInProgress = new HashSet<>();
    private boolean rectangularizeAfterLineSplit;
    private int configuredTerraceParts = 2;
    private AddressSelection lastSelection = new AddressSelection("", "", "", "", 1);
    private boolean houseNumberOverlayEnabled;
    private boolean connectionLinesEnabled;
    private boolean separateEvenOddConnectionLinesEnabled;
    private boolean houseNumberOverviewEnabled;
    private boolean streetHouseNumberCountsEnabled;
    private boolean zoomToSelectedStreetEnabled;
    private String visibleReferenceStreetKey = "";
    private String lastReferenceSyncStreetKey = "";
    private DataSet observedDataSourceDataSet;
    private boolean dataSourceRescanQueued;
    private HouseNumberUpdateListener houseNumberUpdateListener;
    private AddressValuesReadListener addressValuesReadListener;
    private BuildingTypeConsumedListener buildingTypeConsumedListener;
    private ModeStateListener modeStateListener;
    private TerracePartsUpdateListener terracePartsUpdateListener;

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

    interface TerracePartsUpdateListener {
        void onTerracePartsUpdated(int parts);
    }

    private final DataSourceListener dataSourceRefreshListener = event -> onDataSourceChanged();

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
        syncDataSourceListenerBinding();
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
        syncReferenceStreetVisibilityForCurrentStreet();
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

    void setTerracePartsUpdateListener(TerracePartsUpdateListener listener) {
        this.terracePartsUpdateListener = listener;
        if (terracePartsUpdateListener != null) {
            terracePartsUpdateListener.onTerracePartsUpdated(configuredTerraceParts);
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
        syncDataSourceListenerBinding();
        refreshHouseNumberOverview();
    }

    void setZoomToSelectedStreetEnabled(boolean enabled) {
        zoomToSelectedStreetEnabled = enabled;
    }

    void setStreetHouseNumberCountsEnabled(boolean enabled) {
        streetHouseNumberCountsEnabled = enabled;
        syncDataSourceListenerBinding();
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
        syncReferenceStreetVisibilityForCurrentStreet();
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
        syncReferenceStreetVisibilityForCurrentStreet();
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
        syncReferenceStreetVisibilityForCurrentStreet();
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
        removeVisibleReferenceLayer("main dialog closed");
        synchronized (referenceStreetCache) {
            referenceStreetCache.clear();
        }
        synchronized (referenceStreetLoadsInProgress) {
            referenceStreetLoadsInProgress.clear();
        }
        lastReferenceSyncStreetKey = "";
        unbindDataSourceListener();
        deactivate();
    }

    private void onDataSourceChanged() {
        if (!houseNumberOverviewEnabled && !streetHouseNumberCountsEnabled) {
            return;
        }
        if (dataSourceRescanQueued) {
            return;
        }
        dataSourceRescanQueued = true;
        GuiHelper.runInEDT(() -> {
            dataSourceRescanQueued = false;
            syncDataSourceListenerBinding();
            rescanPluginData();
        });
    }

    private void syncDataSourceListenerBinding() {
        DataSet activeDataSet = getActiveEditDataSet();
        boolean shouldListen = (houseNumberOverviewEnabled || streetHouseNumberCountsEnabled) && activeDataSet != null;
        if (!shouldListen) {
            unbindDataSourceListener();
            return;
        }
        if (observedDataSourceDataSet == activeDataSet) {
            return;
        }
        unbindDataSourceListener();
        observedDataSourceDataSet = activeDataSet;
        observedDataSourceDataSet.addDataSourceListener(dataSourceRefreshListener);
    }

    private void unbindDataSourceListener() {
        if (observedDataSourceDataSet != null) {
            observedDataSourceDataSet.removeDataSourceListener(dataSourceRefreshListener);
            observedDataSourceDataSet = null;
        }
    }

    void loadReferenceStreet(String streetName) {
        String normalizedStreet = normalize(streetName);
        if (normalizedStreet.isEmpty()) {
            showShortNotification("Select a street first.");
            return;
        }
        requestReferenceStreetLoad(normalizedStreet, true);
    }

    private void syncReferenceStreetVisibilityForCurrentStreet() {
        String currentStreet = normalize(navigationService.getCurrentStreet());
        String currentStreetKey = streetKey(currentStreet);
        if (!currentStreetKey.equals(lastReferenceSyncStreetKey)) {
            Logging.debug("HouseNumberClick reference auto: current street changed to='" + currentStreet + "'.");
            lastReferenceSyncStreetKey = currentStreetKey;
        }

        if (currentStreetKey.isEmpty()) {
            removeVisibleReferenceLayer("selection changed");
            return;
        }

        DataSet editDataSet = getActiveEditDataSet();
        if (editDataSet == null) {
            removeVisibleReferenceLayer("selection changed");
            return;
        }

        boolean incomplete = streetCompletenessHeuristic.isStreetPossiblyIncomplete(editDataSet, currentStreet);
        Logging.debug("HouseNumberClick reference auto: street='" + currentStreet + "', incomplete=" + incomplete + ".");
        if (!incomplete) {
            removeVisibleReferenceLayer("street is complete");
            return;
        }

        DataSet cached;
        synchronized (referenceStreetCache) {
            cached = referenceStreetCache.get(currentStreetKey);
        }
        if (cached != null) {
            Logging.debug("HouseNumberClick reference auto: cached reference reused for street='" + currentStreet + "'.");
            showReferenceForCurrentStreet(currentStreet, currentStreetKey, cached);
            return;
        }

        if (isReferenceLoadInProgress(currentStreetKey)) {
            Logging.debug("HouseNumberClick reference auto: auto-load skipped for street='" + currentStreet + "' (already running).");
            return;
        }

        Logging.debug("HouseNumberClick reference auto: auto-load started for street='" + currentStreet + "'.");
        requestReferenceStreetLoad(currentStreet, false);
    }

    private void requestReferenceStreetLoad(String normalizedStreet, boolean manualRequest) {
        String streetKey = streetKey(normalizedStreet);
        if (streetKey.isEmpty()) {
            return;
        }

        DataSet cached;
        synchronized (referenceStreetCache) {
            cached = referenceStreetCache.get(streetKey);
        }
        if (cached != null) {
            Logging.debug("HouseNumberClick reference auto: cached reference reused for street='" + normalizedStreet + "'.");
            showReferenceForCurrentStreet(normalizedStreet, streetKey, cached);
            if (manualRequest) {
                String loadedMessage = cached.getWays().isEmpty()
                        ? I18n.tr("No reference street geometry found for {0}.", normalizedStreet)
                        : I18n.tr("Street reference loaded for {0}.", normalizedStreet);
                new Notification(loadedMessage)
                        .setDuration(Notification.TIME_SHORT)
                        .show();
            }
            return;
        }

        DataSet editDataSet = getActiveEditDataSet();
        if (editDataSet == null) {
            if (manualRequest) {
                showShortNotification("No editable dataset is available.");
            }
            return;
        }

        if (!tryStartReferenceLoad(streetKey)) {
            Logging.debug("HouseNumberClick reference auto: auto-load skipped for street='" + normalizedStreet + "' (already running).");
            return;
        }

        Thread loadThread = new Thread(() -> {
            try {
                DataSet referenceData = referenceStreetFetchService.loadReferenceStreet(editDataSet, normalizedStreet);
                synchronized (referenceStreetCache) {
                    referenceStreetCache.put(streetKey, referenceData != null ? referenceData : new DataSet());
                }
                GuiHelper.runInEDT(() -> {
                    showReferenceForCurrentStreet(normalizedStreet, streetKey, referenceData);
                    if (manualRequest) {
                        String loadedMessage = referenceData == null || referenceData.getWays().isEmpty()
                                ? I18n.tr("No reference street geometry found for {0}.", normalizedStreet)
                                : I18n.tr("Street reference loaded for {0}.", normalizedStreet);
                        new Notification(loadedMessage)
                                .setDuration(Notification.TIME_SHORT)
                                .show();
                    }
                });
            } catch (Exception ex) {
                Logging.warn("HouseNumberClick reference load failed for street={0} | category={1} | diagnostics={2}",
                        normalizedStreet,
                        classifyReferenceLoadIssue(ex),
                        summarizeExceptionChain(ex));
                Logging.debug(ex);
                if (manualRequest) {
                    GuiHelper.runInEDT(() -> showReferenceLoadFailure(normalizedStreet));
                }
            } finally {
                finishReferenceLoad(streetKey);
            }
        }, "hnc-reference-street-loader-" + streetKey);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void showReferenceForCurrentStreet(String streetName, String streetKey, DataSet referenceData) {
        String currentStreetKey = streetKey(navigationService.getCurrentStreet());
        DataSet editDataSet = getActiveEditDataSet();
        boolean currentStreetIncomplete = editDataSet != null
                && !currentStreetKey.isEmpty()
                && streetCompletenessHeuristic.isStreetPossiblyIncomplete(editDataSet, navigationService.getCurrentStreet());
        if (!streetKey.equals(currentStreetKey) || !currentStreetIncomplete) {
            Logging.debug("HouseNumberClick reference auto: auto-load skipped for street='" + streetName
                    + "' (selection changed before completion).");
            return;
        }
        if (referenceData == null || referenceData.getWays().isEmpty()) {
            removeVisibleReferenceLayer("selection changed");
            return;
        }
        overlayManager.showReferenceStreetLayer(streetName, referenceData);
        visibleReferenceStreetKey = streetKey;
    }

    private void removeVisibleReferenceLayer(String reason) {
        if (visibleReferenceStreetKey.isEmpty()) {
            overlayManager.removeReferenceStreetLayer();
            return;
        }
        Logging.debug("HouseNumberClick reference auto: visible reference removed because " + reason + ".");
        overlayManager.removeReferenceStreetLayer();
        visibleReferenceStreetKey = "";
    }

    private boolean tryStartReferenceLoad(String streetKey) {
        synchronized (referenceStreetLoadsInProgress) {
            if (referenceStreetLoadsInProgress.contains(streetKey)) {
                return false;
            }
            referenceStreetLoadsInProgress.add(streetKey);
            return true;
        }
    }

    private void finishReferenceLoad(String streetKey) {
        synchronized (referenceStreetLoadsInProgress) {
            referenceStreetLoadsInProgress.remove(streetKey);
        }
    }

    private boolean isReferenceLoadInProgress(String streetKey) {
        synchronized (referenceStreetLoadsInProgress) {
            return referenceStreetLoadsInProgress.contains(streetKey);
        }
    }

    private String streetKey(String streetName) {
        return normalize(streetName).toLowerCase(Locale.ROOT);
    }

    private void showReferenceLoadFailure(String streetName) {
        showShortNotification(I18n.tr("Failed to load street reference for {0}. See log for details.", streetName));
    }

    private String summarizeExceptionChain(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String primary = throwable.getClass().getSimpleName() + ": " + nonEmptyMessage(throwable.getMessage());
        Throwable cause = throwable.getCause();
        if (cause == null) {
            return primary;
        }
        return primary + " | cause=" + cause.getClass().getSimpleName() + ": " + nonEmptyMessage(cause.getMessage());
    }

    private String classifyReferenceLoadIssue(Throwable throwable) {
        String diagnostics = summarizeExceptionChain(throwable).toLowerCase(Locale.ROOT);
        if (diagnostics.contains("malformedurl") || diagnostics.contains("no protocol")
                || diagnostics.contains("illegalargumentexception")) {
            return "api";
        }
        if (diagnostics.contains("sockettimeout") || diagnostics.contains("connect timed out")
                || diagnostics.contains("read timed out")) {
            return "timeout";
        }
        if (diagnostics.contains("ssl") || diagnostics.contains("handshake")) {
            return "ssl";
        }
        if (diagnostics.contains("osmtransferexception") || diagnostics.contains("http") || diagnostics.contains("429")
                || diagnostics.contains("403") || diagnostics.contains("500")) {
            return "transport";
        }
        if (diagnostics.contains("illegaldata") || diagnostics.contains("parse")) {
            return "parse";
        }
        return "plugin";
    }

    private String nonEmptyMessage(String message) {
        String normalized = normalize(message);
        return normalized.isEmpty() ? "(no message)" : normalized;
    }

    void setRectangularizeAfterLineSplit(boolean makeRectangular) {
        rectangularizeAfterLineSplit = makeRectangular;
    }

    SingleSplitResult executeInternalSingleSplit(LatLon lineStart, LatLon lineEnd) {
        int undoStartSize = UndoRedoHandler.getInstance().getUndoCommands().size();
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
            mergeUndoStepsSince(undoStartSize, "Line split");
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
        if (parts >= 1 && configuredTerraceParts != parts) {
            configuredTerraceParts = parts;
            if (terracePartsUpdateListener != null) {
                terracePartsUpdateListener.onTerracePartsUpdated(configuredTerraceParts);
            }
        }
    }

    TerraceSplitResult executeInternalTerraceSplitAtClick(Way clickedBuilding, int parts) {
        int undoStartSize = UndoRedoHandler.getInstance().getUndoCommands().size();
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

        mergeUndoStepsSince(undoStartSize, "Create row houses");

        dataSet.setSelected(result.getResultWays());
        return result;
    }

    private void mergeUndoStepsSince(int undoStartSize, String commandName) {
        List<Command> undoCommands = UndoRedoHandler.getInstance().getUndoCommands();
        int undoEndSize = undoCommands.size();
        int addedCount = undoEndSize - undoStartSize;
        if (addedCount <= 1) {
            return;
        }

        List<Command> addedCommands = new ArrayList<>(undoCommands.subList(undoStartSize, undoEndSize));
        UndoRedoHandler.getInstance().undo(addedCount);
        UndoRedoHandler.getInstance().add(new SequenceCommand(commandName, addedCommands));
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
