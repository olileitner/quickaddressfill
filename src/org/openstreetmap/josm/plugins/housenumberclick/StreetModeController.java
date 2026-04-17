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
import java.util.Objects;
import java.util.Set;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.actions.OrthogonalizeAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
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

/**
 * Orchestrates Street Mode state, dialog synchronization, seed-aware street highlighting/overlays,
 * explicit street-selection zoom behavior with full selected-street framing,
 * spatially disambiguated street readback selection, and split/address operations.
 */
final class StreetModeController {

    private static final String LOG_PREFIX = "HouseNumberClick street diagnostics";

    /**
     * Immutable current address selection transferred from dialog to map mode.
     */
    static final class AddressSelection {
        private final String streetName;
        private final String displayStreetName;
        private final String streetClusterId;
        private final String postcode;
        private final String buildingType;
        private final String houseNumber;
        private final int houseNumberIncrementStep;

        AddressSelection(String streetName, String postcode, String buildingType, String houseNumber, int houseNumberIncrementStep) {
            this(streetName, streetName, "", postcode, buildingType, houseNumber, houseNumberIncrementStep);
        }

        AddressSelection(String streetName, String displayStreetName, String streetClusterId,
                String postcode, String buildingType, String houseNumber, int houseNumberIncrementStep) {
            this.streetName = normalize(streetName);
            this.displayStreetName = normalize(displayStreetName);
            this.streetClusterId = normalize(streetClusterId);
            this.postcode = normalize(postcode);
            this.buildingType = normalize(buildingType);
            this.houseNumber = normalize(houseNumber);
            this.houseNumberIncrementStep = normalizeIncrementStep(houseNumberIncrementStep);
        }

        String getStreetName() {
            return streetName;
        }

        String getDisplayStreetName() {
            return displayStreetName;
        }

        String getStreetClusterId() {
            return streetClusterId;
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
    private final Map<ReferenceLoadKey, DataSet> referenceStreetCache = new HashMap<>();
    private final Set<ReferenceLoadKey> referenceStreetLoadsInProgress = new HashSet<>();
    private boolean rectangularizeAfterLineSplit;
    private int configuredTerraceParts = 2;
    private BuildingOverviewLayer.MissingField completenessMissingField = BuildingOverviewLayer.MissingField.POSTCODE;
    private AddressSelection lastSelection = new AddressSelection("", "", "", "", 1);
    private boolean houseNumberOverlayEnabled;
    private boolean connectionLinesEnabled;
    private boolean separateEvenOddConnectionLinesEnabled;
    private boolean houseNumberOverviewEnabled;
    private boolean streetHouseNumberCountsEnabled;
    private boolean zoomToSelectedStreetEnabled;
    private String visibleReferenceStreetKey = "";
    private String lastReferenceSyncStreetKey = "";
    private DataSet cachedStreetIndexDataSet;
    private StreetNameCollector.StreetIndex cachedStreetIndex;
    private Way lastStreetSeedWayHint;
    private LatLon lastStreetInteractionPoint;
    private DataSet observedDataSourceDataSet;
    private boolean dataSourceRescanQueued;
    private boolean commandQueueRescanQueued;
    private boolean commandQueueListenerRegistered;
    private HouseNumberUpdateListener houseNumberUpdateListener;
    private AddressValuesReadListener addressValuesReadListener;
    private BuildingTypeConsumedListener buildingTypeConsumedListener;
    private ModeStateListener modeStateListener;
    private TerracePartsUpdateListener terracePartsUpdateListener;
    private StreetSelectionRequestListener streetSelectionRequestListener;
    private HouseNumberOverviewVisibilityListener houseNumberOverviewVisibilityListener;
    private StreetHouseNumberCountsVisibilityListener streetHouseNumberCountsVisibilityListener;

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

    interface StreetSelectionRequestListener {
        void onStreetSelectionRequested(StreetOption streetOption);
    }

    interface HouseNumberOverviewVisibilityListener {
        void onHouseNumberOverviewVisibilityChanged(boolean enabled);
    }

    interface StreetHouseNumberCountsVisibilityListener {
        void onStreetHouseNumberCountsVisibilityChanged(boolean enabled);
    }

    /**
     * Cache/load key combining dataset identity and normalized street name.
     */
    private static final class ReferenceLoadKey {
        private final String datasetContextKey;
        private final String streetKey;

        private ReferenceLoadKey(String datasetContextKey, String streetKey) {
            this.datasetContextKey = normalize(datasetContextKey).toLowerCase(Locale.ROOT);
            this.streetKey = normalize(streetKey).toLowerCase(Locale.ROOT);
        }

        private static ReferenceLoadKey of(String datasetContextKey, String streetName) {
            return new ReferenceLoadKey(datasetContextKey, streetName);
        }

        private boolean isValid() {
            return !datasetContextKey.isEmpty() && !streetKey.isEmpty();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ReferenceLoadKey)) {
                return false;
            }
            ReferenceLoadKey that = (ReferenceLoadKey) other;
            return datasetContextKey.equals(that.datasetContextKey)
                    && streetKey.equals(that.streetKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(datasetContextKey, streetKey);
        }
    }

    private final DataSourceListener dataSourceRefreshListener = event -> onDataSourceChanged();
    private final UndoRedoHandler.CommandQueueListener commandQueueListener = (undoSize, redoSize) -> onCommandQueueChanged();

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

        Logging.debug(LOG_PREFIX + ": activate selection base='" + normalize(selection.getStreetName())
                + "', display='" + normalize(selection.getDisplayStreetName())
                + "', cluster='" + normalize(selection.getStreetClusterId()) + "'.");

        navigationService.updateFromSelection(selection);
        syncDataSourceListenerBinding();
        registerCommandQueueListener();
        lastSelection = selection;

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

    StreetOption resolveStreetOptionForReadback(String streetName) {
        String normalizedStreet = normalize(streetName);
        if (normalizedStreet.isEmpty()) {
            return null;
        }

        DataSet editDataSet = getActiveEditDataSet();
        StreetNameCollector.StreetIndex streetIndex = getStreetIndex(editDataSet);

        StreetOption byDisplay = streetIndex.findByDisplayStreetName(normalizedStreet);
        if (isExplicitDisplaySelection(byDisplay, normalizedStreet)) {
            return byDisplay;
        }

        List<StreetOption> options = streetIndex.getOptionsForBaseStreetName(normalizedStreet);
        if (options.isEmpty()) {
            return null;
        }
        if (options.size() == 1) {
            return options.get(0);
        }

        LatLon referencePoint = resolveStreetReferencePoint();
        StreetOption nearestByPoint = streetIndex.findNearestOptionForBaseStreetName(normalizedStreet, referencePoint);
        if (isMatchingBaseStreetOption(nearestByPoint, normalizedStreet)) {
            return nearestByPoint;
        }

        if (lastStreetSeedWayHint != null && lastStreetSeedWayHint.isUsable()) {
            StreetOption byWay = streetIndex.findByWay(lastStreetSeedWayHint);
            if (isMatchingBaseStreetOption(byWay, normalizedStreet)) {
                return byWay;
            }

            StreetOption bySeedPrimitive = streetIndex.resolveForBaseStreetAndPrimitive(normalizedStreet, lastStreetSeedWayHint);
            if (isMatchingBaseStreetOption(bySeedPrimitive, normalizedStreet)) {
                return bySeedPrimitive;
            }
        }

        StreetOption byBaseFallback = streetIndex.resolveForBaseStreetAndPrimitive(normalizedStreet, null);
        if (isMatchingBaseStreetOption(byBaseFallback, normalizedStreet)) {
            return byBaseFallback;
        }
        return options.get(0);
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

    void setStreetSelectionRequestListener(StreetSelectionRequestListener listener) {
        this.streetSelectionRequestListener = listener;
    }

    void setHouseNumberOverviewVisibilityListener(HouseNumberOverviewVisibilityListener listener) {
        this.houseNumberOverviewVisibilityListener = listener;
    }

    void setStreetHouseNumberCountsVisibilityListener(StreetHouseNumberCountsVisibilityListener listener) {
        this.streetHouseNumberCountsVisibilityListener = listener;
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

    List<StreetOption> getStreetNavigationOrder() {
        return navigationService.getStreetNavigationOrder();
    }

    void rememberStreetInteraction(Way streetWay, LatLon interactionPoint) {
        if (streetWay != null && streetWay.isUsable()) {
            lastStreetSeedWayHint = streetWay;
        } else {
            // Prevent stale same-name way hints from biasing later readback in a different area.
            lastStreetSeedWayHint = null;
        }
        if (interactionPoint != null) {
            lastStreetInteractionPoint = new LatLon(interactionPoint);
        }
    }

    void zoomToCurrentStreet() {
        if (!zoomToSelectedStreetEnabled || normalize(navigationService.getCurrentStreet()).isEmpty()) {
            return;
        }

        StreetOption selectedStreetOption = resolveCurrentStreetOption(getActiveEditDataSet());
        zoomToStreet(selectedStreetOption, false);
    }

    void zoomToStreet(StreetOption streetOption) {
        zoomToStreet(streetOption, true);
    }

    private void zoomToStreet(StreetOption streetOption, boolean selectFallbackWays) {
        if (streetOption == null || !streetOption.isValid()) {
            return;
        }
        zoomToStreetInternal(streetOption, selectFallbackWays);
    }

    void zoomToStreet(String streetName) {
        zoomToStreet(resolveStreetOptionFromUiValue(streetName));
    }

    private void zoomToStreetInternal(StreetOption streetOption, boolean selectFallbackWays) {
        if (streetOption == null || normalize(streetOption.getBaseStreetName()).isEmpty()) {
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

        StreetNameCollector.StreetIndex zoomStreetIndex = StreetNameCollector.collectStreetIndex(editDataSet, false);
        StreetOption zoomStreetOption = resolveStreetOptionForZoom(streetOption, zoomStreetIndex);
        if (zoomStreetOption == null || !zoomStreetOption.isValid()) {
            zoomStreetOption = streetOption;
        }

        StreetSeedResolution seedResolution = resolveStreetSeedResolution(editDataSet, zoomStreetOption, zoomStreetIndex);
        List<Way> localStreetWays = zoomStreetIndex.getLocalStreetChainWays(zoomStreetOption, seedResolution.seedWay);
        List<HouseNumberOverlayEntry> entries = houseNumberOverlayCollector.collect(
                editDataSet,
                zoomStreetOption,
                zoomStreetIndex,
                seedResolution.seedWay
        );
        List<OsmPrimitive> fallbackStreetWays = List.of();
        List<OsmPrimitive> selectionTargets = new ArrayList<>();

        BoundingXYVisitor visitor = new BoundingXYVisitor();
        for (Way streetWay : localStreetWays) {
            if (streetWay == null || !streetWay.isUsable()) {
                continue;
            }
            streetWay.accept((OsmPrimitiveVisitor) visitor);
            selectionTargets.add(streetWay);
        }

        for (HouseNumberOverlayEntry entry : entries) {
            OsmPrimitive primitive = entry.getPrimitive();
            if (primitive == null || !primitive.isUsable()) {
                continue;
            }
            primitive.accept((OsmPrimitiveVisitor) visitor);
        }

        if (!visitor.hasExtend()) {
            fallbackStreetWays = collectStreetWayFallbackPrimitives(
                    editDataSet,
                    zoomStreetOption,
                    zoomStreetIndex,
                    seedResolution.seedWay
            );
            for (OsmPrimitive primitive : fallbackStreetWays) {
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
        if (selectFallbackWays) {
            List<OsmPrimitive> zoomSelection = !selectionTargets.isEmpty() ? selectionTargets : fallbackStreetWays;
            if (!zoomSelection.isEmpty()) {
                editDataSet.setSelected(zoomSelection);
            }
            map.mapView.repaint();
        }
    }

    private StreetOption resolveStreetOptionForZoom(StreetOption streetOption,
            StreetNameCollector.StreetIndex streetIndex) {
        if (streetOption == null || !streetOption.isValid() || streetIndex == null) {
            return null;
        }
        StreetOption byCluster = streetIndex.findByClusterId(streetOption.getClusterId());
        if (byCluster != null && byCluster.isValid()) {
            return byCluster;
        }
        StreetOption byDisplay = streetIndex.findByDisplayStreetName(streetOption.getDisplayStreetName());
        if (byDisplay != null && byDisplay.isValid()) {
            return byDisplay;
        }
        return streetIndex.resolveForBaseStreetAndPrimitive(streetOption.getBaseStreetName(), null);
    }

    static List<OsmPrimitive> collectStreetWayFallbackPrimitives(DataSet dataSet, String streetName) {
        StreetOption streetOption = new StreetOption(streetName, streetName, "");
        return collectStreetWayFallbackPrimitives(dataSet, streetOption, StreetNameCollector.collectStreetIndex(dataSet), null);
    }

    static List<OsmPrimitive> collectStreetWayFallbackPrimitives(DataSet dataSet, StreetOption streetOption,
            StreetNameCollector.StreetIndex streetIndex) {
        return collectStreetWayFallbackPrimitives(dataSet, streetOption, streetIndex, null);
    }

    static List<OsmPrimitive> collectStreetWayFallbackPrimitives(DataSet dataSet, StreetOption streetOption,
            StreetNameCollector.StreetIndex streetIndex, Way seedWayHint) {
        if (dataSet == null || streetOption == null || normalize(streetOption.getBaseStreetName()).isEmpty()) {
            return List.of();
        }

        List<Way> candidates = streetIndex != null
                ? streetIndex.getLocalStreetChainWays(streetOption, seedWayHint)
                : new ArrayList<>(dataSet.getWays());
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(dataSet.getWays());
        }

        LinkedHashSet<OsmPrimitive> matchingWays = new LinkedHashSet<>();
        for (Way way : candidates) {
            if (way == null || !way.isUsable() || !way.hasTag("highway")) {
                continue;
            }
            if (!normalize(way.get("name")).equalsIgnoreCase(normalize(streetOption.getBaseStreetName()))) {
                continue;
            }
            matchingWays.add(way);
        }
        return new ArrayList<>(matchingWays);
    }

    private void onStreetHouseNumberCountSelected(StreetOption selectedStreetOption) {
        if (selectedStreetOption == null || !selectedStreetOption.isValid()) {
            return;
        }

        StreetOption previousStreetOption = navigationService.getCurrentStreetOption();

        // Keep optional street-based overlays in sync with the row the user clicked.
        navigationService.setCurrentStreetOption(selectedStreetOption);
        lastSelection = new AddressSelection(
                navigationService.getCurrentStreet(),
                navigationService.getCurrentStreetDisplay(),
                navigationService.getCurrentStreetClusterId(),
                navigationService.getCurrentPostcode(),
                lastSelection.getBuildingType(),
                lastSelection.getHouseNumber(),
                lastSelection.getHouseNumberIncrementStep()
        );
        requestMainDialogStreetSelection(selectedStreetOption);
        highlightCurrentStreetInStreetCountDialog();
        refreshOverlayLayer();
        refreshHouseNumberOverview();
        syncReferenceStreetVisibilityForCurrentStreet();
        if (zoomToSelectedStreetEnabled) {
            if (!isSameStreetOptionIdentity(previousStreetOption, selectedStreetOption)) {
                zoomToStreet(selectedStreetOption);
            }
        }
        continueWorkingFromTableInteraction();
    }

    private void requestMainDialogStreetSelection(StreetOption selectedStreetOption) {
        if (streetSelectionRequestListener == null || selectedStreetOption == null || !selectedStreetOption.isValid()) {
            return;
        }
        streetSelectionRequestListener.onStreetSelectionRequested(selectedStreetOption);
    }

    private void highlightCurrentStreetInStreetCountDialog() {
        overviewManager.highlightStreetInStreetCountDialog(resolveCurrentStreetOption(getActiveEditDataSet()));
    }

    void rescanPluginData() {
        // Recompute all collector-driven views so plugin UI reflects latest dataset state.
        invalidateStreetIndexCache();
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

    void togglePostcodeOverviewLayer() {
        overlayManager.togglePostcodeOverviewLayer();
    }

    void toggleDuplicateAddressOverviewLayer() {
        overlayManager.toggleDuplicateAddressOverviewLayer();
    }

    boolean isPostcodeOverviewLayerVisible() {
        return overlayManager.isPostcodeOverviewLayerVisible();
    }

    boolean isDuplicateAddressOverviewLayerVisible() {
        return overlayManager.isDuplicateAddressOverviewLayerVisible();
    }

    void setCompletenessMissingField(BuildingOverviewLayer.MissingField missingField) {
        completenessMissingField = missingField != null ? missingField : BuildingOverviewLayer.MissingField.POSTCODE;
        overlayManager.setCompletenessMissingField(completenessMissingField);
    }

    BuildingOverviewLayer.MissingField getCompletenessMissingField() {
        return completenessMissingField;
    }

    private void refreshOverlayLayer() {
        DataSet editDataSet = getActiveEditDataSet();
        StreetNameCollector.StreetIndex streetIndex = getStreetIndex(editDataSet);
        StreetOption selectedStreetOption = resolveCurrentStreetOption(editDataSet, streetIndex);
        StreetSeedResolution seedResolution = resolveStreetSeedResolution(editDataSet, selectedStreetOption, streetIndex);
        if (selectedStreetOption != null) {
            List<Way> seededChainWays = streetIndex.getLocalStreetChainWays(selectedStreetOption, seedResolution.seedWay);
            String fallbackDetails = "cluster-fallback".equals(seedResolution.source)
                    ? ", resolvedVia=" + seedResolution.resolvedVia + ", seedWasNull=" + (seedResolution.seedWay == null)
                    : "";
            Logging.debug(LOG_PREFIX + ": overlay street-chain seed=" + seedResolution.source + fallbackDetails
                    + ", seedWayId=" + (seedResolution.seedWay == null ? "none" : seedResolution.seedWay.getUniqueId())
                    + ", chainWays=" + seededChainWays.size() + ", selected=" + formatStreetOption(selectedStreetOption) + ".");
        }
        if (selectedStreetOption == null) {
            StreetOption storedOption = navigationService.getCurrentStreetOption();
            Logging.debug(LOG_PREFIX + ": overlay refresh without resolved street; stored="
                    + formatStreetOption(storedOption)
                    + ", currentBase='" + normalize(navigationService.getCurrentStreet()) + "', currentDisplay='"
                    + normalize(navigationService.getCurrentStreetDisplay()) + "', currentCluster='"
                    + normalize(navigationService.getCurrentStreetClusterId()) + "'.");
        } else {
            Logging.debug(LOG_PREFIX + ": overlay refresh using " + formatStreetOption(selectedStreetOption) + ".");
        }
        overlayManager.refreshOverlayLayer(
                selectedStreetOption,
                streetIndex,
                seedResolution.seedWay,
                houseNumberOverlayEnabled,
                connectionLinesEnabled,
                separateEvenOddConnectionLinesEnabled
        );
    }

    private void refreshHouseNumberOverview() {
        DataSet editDataSet = getActiveEditDataSet();
        StreetNameCollector.StreetIndex streetIndex = getStreetIndex(editDataSet);
        StreetOption selectedStreetOption = resolveCurrentStreetOption(editDataSet, streetIndex);
        overviewManager.refreshHouseNumberOverview(
                houseNumberOverviewEnabled,
                selectedStreetOption,
                editDataSet,
                streetIndex,
                this::continueWorkingFromTableInteraction,
                this::onHouseNumberOverviewDialogClosedByUser
        );
    }

    private void refreshStreetHouseNumberCounts() {
        DataSet editDataSet = getActiveEditDataSet();
        StreetNameCollector.StreetIndex streetIndex = getStreetIndex(editDataSet);
        StreetOption currentStreetOption = resolveCurrentStreetOption(editDataSet, streetIndex);
        navigationService.setStreetNavigationOrder(
                overviewManager.refreshStreetHouseNumberCounts(
                        streetHouseNumberCountsEnabled,
                        editDataSet,
                        streetIndex,
                        this::onStreetHouseNumberCountSelected,
                        this::rescanPluginData,
                        currentStreetOption,
                        this::onStreetHouseNumberCountsDialogClosedByUser
                )
        );
        highlightCurrentStreetInStreetCountDialog();
    }

    private void onHouseNumberOverviewDialogClosedByUser() {
        if (!houseNumberOverviewEnabled) {
            return;
        }
        houseNumberOverviewEnabled = false;
        syncDataSourceListenerBinding();
        refreshHouseNumberOverview();
        if (houseNumberOverviewVisibilityListener != null) {
            houseNumberOverviewVisibilityListener.onHouseNumberOverviewVisibilityChanged(false);
        }
    }

    private void onStreetHouseNumberCountsDialogClosedByUser() {
        if (!streetHouseNumberCountsEnabled) {
            return;
        }
        streetHouseNumberCountsEnabled = false;
        syncDataSourceListenerBinding();
        refreshStreetHouseNumberCounts();
        if (streetHouseNumberCountsVisibilityListener != null) {
            streetHouseNumberCountsVisibilityListener.onStreetHouseNumberCountsVisibilityChanged(false);
        }
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
        invalidateStreetIndexCache();
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
        lastStreetSeedWayHint = null;
        lastStreetInteractionPoint = null;
        lastReferenceSyncStreetKey = "";
        unbindDataSourceListener();
        unregisterCommandQueueListener();
        deactivate();
    }

    private void registerCommandQueueListener() {
        if (commandQueueListenerRegistered) {
            return;
        }
        commandQueueListenerRegistered = UndoRedoHandler.getInstance().addCommandQueueListener(commandQueueListener);
    }

    private void unregisterCommandQueueListener() {
        if (!commandQueueListenerRegistered) {
            return;
        }
        UndoRedoHandler.getInstance().removeCommandQueueListener(commandQueueListener);
        commandQueueListenerRegistered = false;
        commandQueueRescanQueued = false;
    }

    private void onCommandQueueChanged() {
        if (!commandQueueListenerRegistered || commandQueueRescanQueued) {
            return;
        }
        commandQueueRescanQueued = true;
        GuiHelper.runInEDT(() -> {
            commandQueueRescanQueued = false;
            overlayManager.invalidateOverlayDataCache();
            rescanPluginData();
            MapFrame map = MainApplication.getMap();
            if (map != null && map.mapView != null) {
                map.mapView.repaint();
            }
        });
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
            invalidateStreetIndexCache();
            invalidateReferenceStreetStateForDataSourceChange();
            syncDataSourceListenerBinding();
            rescanPluginData();
        });
    }

    private void invalidateReferenceStreetStateForDataSourceChange() {
        synchronized (referenceStreetCache) {
            referenceStreetCache.clear();
        }
        synchronized (referenceStreetLoadsInProgress) {
            referenceStreetLoadsInProgress.clear();
        }
        removeVisibleReferenceLayer("data source changed");
        lastReferenceSyncStreetKey = "";
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

        ReferenceLoadKey loadKey = ReferenceLoadKey.of(datasetContextKey(editDataSet), currentStreetKey);
        if (!loadKey.isValid()) {
            return;
        }

        DataSet cached;
        synchronized (referenceStreetCache) {
            cached = referenceStreetCache.get(loadKey);
        }
        if (cached != null) {
            Logging.debug("HouseNumberClick reference auto: cached reference reused for street='" + currentStreet + "'.");
            showReferenceForCurrentStreet(currentStreet, loadKey, cached);
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
        DataSet editDataSet = getActiveEditDataSet();
        if (editDataSet == null) {
            if (manualRequest) {
                showShortNotification("No editable dataset is available.");
            }
            return;
        }

        ReferenceLoadKey loadKey = ReferenceLoadKey.of(datasetContextKey(editDataSet), normalizedStreet);
        if (!loadKey.isValid()) {
            return;
        }

        DataSet cached;
        synchronized (referenceStreetCache) {
            cached = referenceStreetCache.get(loadKey);
        }
        if (cached != null) {
            Logging.debug("HouseNumberClick reference auto: cached reference reused for street='" + normalizedStreet + "'.");
            showReferenceForCurrentStreet(normalizedStreet, loadKey, cached);
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

        if (!tryStartReferenceLoad(loadKey)) {
            Logging.debug("HouseNumberClick reference auto: auto-load skipped for street='" + normalizedStreet + "' (already running).");
            return;
        }

        ReferenceStreetFetchService.ReferenceStreetContext context = buildReferenceStreetContext(editDataSet, normalizedStreet, loadKey.datasetContextKey);

        Thread loadThread = new Thread(() -> {
            try {
                DataSet referenceData = referenceStreetFetchService.loadReferenceStreet(context);
                synchronized (referenceStreetCache) {
                    referenceStreetCache.put(loadKey, referenceData != null ? referenceData : new DataSet());
                }
                GuiHelper.runInEDT(() -> {
                    showReferenceForCurrentStreet(normalizedStreet, loadKey, referenceData);
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
                finishReferenceLoad(loadKey);
            }
        }, "hnc-reference-street-loader-" + loadKey.streetKey + "-" + loadKey.datasetContextKey);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void showReferenceForCurrentStreet(String streetName, ReferenceLoadKey loadKey, DataSet referenceData) {
        String currentStreetKey = streetKey(navigationService.getCurrentStreet());
        String currentContextKey = datasetContextKey(getActiveEditDataSet());
        ReferenceLoadKey currentKey = ReferenceLoadKey.of(currentContextKey, currentStreetKey);
        DataSet editDataSet = getActiveEditDataSet();
        boolean currentStreetIncomplete = editDataSet != null
                && !currentStreetKey.isEmpty()
                && streetCompletenessHeuristic.isStreetPossiblyIncomplete(editDataSet, navigationService.getCurrentStreet());
        if (!loadKey.equals(currentKey) || !currentStreetIncomplete) {
            Logging.debug("HouseNumberClick reference auto: auto-load skipped for street='" + streetName
                    + "' (selection changed before completion).");
            return;
        }
        if (referenceData == null || referenceData.getWays().isEmpty()) {
            removeVisibleReferenceLayer("selection changed");
            return;
        }
        overlayManager.showReferenceStreetLayer(streetName, referenceData);
        visibleReferenceStreetKey = currentStreetKey;
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

    private boolean tryStartReferenceLoad(ReferenceLoadKey loadKey) {
        synchronized (referenceStreetLoadsInProgress) {
            if (referenceStreetLoadsInProgress.contains(loadKey)) {
                return false;
            }
            referenceStreetLoadsInProgress.add(loadKey);
            return true;
        }
    }

    private void finishReferenceLoad(ReferenceLoadKey loadKey) {
        synchronized (referenceStreetLoadsInProgress) {
            referenceStreetLoadsInProgress.remove(loadKey);
        }
    }

    private boolean isReferenceLoadInProgress(String streetKey) {
        DataSet editDataSet = getActiveEditDataSet();
        ReferenceLoadKey loadKey = ReferenceLoadKey.of(datasetContextKey(editDataSet), streetKey);
        if (!loadKey.isValid()) {
            return false;
        }
        synchronized (referenceStreetLoadsInProgress) {
            return referenceStreetLoadsInProgress.contains(loadKey);
        }
    }

    private ReferenceStreetFetchService.ReferenceStreetContext buildReferenceStreetContext(
            DataSet editDataSet,
            String normalizedStreet,
            String datasetContextKey
    ) {
        List<Bounds> boundsCopy = new ArrayList<>();
        for (Bounds bounds : editDataSet.getDataSourceBounds()) {
            if (bounds != null) {
                boundsCopy.add(new Bounds(bounds));
            }
        }

        List<LatLon> localEndpoints = new ArrayList<>();
        List<LatLon> localAllNodes = new ArrayList<>();
        for (Way way : editDataSet.getWays()) {
            if (way == null || !way.isUsable() || !way.hasTag("highway")) {
                continue;
            }
            if (!normalize(way.get("name")).equalsIgnoreCase(normalizedStreet)) {
                continue;
            }

            if (way.getNodesCount() > 0) {
                copyNodeCoor(way.firstNode(), localEndpoints);
                if (way.getNodesCount() > 1) {
                    copyNodeCoor(way.lastNode(), localEndpoints);
                }
            }
            for (Node node : way.getNodes()) {
                copyNodeCoor(node, localAllNodes);
            }
        }

        return new ReferenceStreetFetchService.ReferenceStreetContext(
                normalizedStreet,
                datasetContextKey,
                boundsCopy,
                localEndpoints,
                localAllNodes
        );
    }

    private void copyNodeCoor(Node node, List<LatLon> target) {
        if (node == null || node.getCoor() == null || target == null) {
            return;
        }
        target.add(new LatLon(node.getCoor().lat(), node.getCoor().lon()));
    }

    private String datasetContextKey(DataSet dataSet) {
        return dataSet == null ? "" : "ds@" + Integer.toHexString(System.identityHashCode(dataSet));
    }

    private String streetKey(String streetName) {
        return normalize(streetName).toLowerCase(Locale.ROOT);
    }

    private StreetNameCollector.StreetIndex getStreetIndex(DataSet dataSet) {
        if (dataSet == null) {
            cachedStreetIndexDataSet = null;
            cachedStreetIndex = StreetNameCollector.collectStreetIndex(null);
            return cachedStreetIndex;
        }
        if (cachedStreetIndexDataSet == dataSet && cachedStreetIndex != null) {
            return cachedStreetIndex;
        }
        cachedStreetIndexDataSet = dataSet;
        cachedStreetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        return cachedStreetIndex;
    }

    private void invalidateStreetIndexCache() {
        cachedStreetIndexDataSet = null;
        cachedStreetIndex = null;
    }

    private StreetSeedResolution resolveStreetSeedResolution(DataSet dataSet, StreetOption streetOption,
            StreetNameCollector.StreetIndex streetIndex) {
        if (dataSet == null || streetOption == null || !streetOption.isValid() || streetIndex == null) {
            return StreetSeedResolution.none();
        }

        String baseStreetName = normalize(streetOption.getBaseStreetName());
        List<Way> optionWays = streetIndex.getWaysForStreetOption(streetOption);

        Way directSeedWay = resolveDirectSeedWay(dataSet, baseStreetName, optionWays);
        if (directSeedWay != null) {
            return new StreetSeedResolution(directSeedWay, "direct-way", "direct-selection");
        }

        LatLon referencePoint = resolveStreetReferencePoint();
        if (referencePoint != null) {
            Way nearestSeedWay = streetIndex.findNearestWayForStreetOption(streetOption, referencePoint);
            if (nearestSeedWay != null) {
                return new StreetSeedResolution(nearestSeedWay, "nearest-way", "nearest-search");
            }
        }

        Way clusterSeedWay = streetIndex.findSeedWayForClusterId(streetOption.getClusterId(), baseStreetName);
        if (clusterSeedWay != null) {
            return new StreetSeedResolution(clusterSeedWay, "cluster-fallback", "cluster-seed");
        }

        Way deterministicOptionSeed = resolveDeterministicOptionSeedWay(dataSet, streetOption, streetIndex);
        if (deterministicOptionSeed != null) {
            return new StreetSeedResolution(deterministicOptionSeed, "cluster-fallback", "option-min-id");
        }

        return new StreetSeedResolution(null, "cluster-fallback", "none");
    }

    private Way resolveDeterministicOptionSeedWay(DataSet dataSet, StreetOption streetOption,
            StreetNameCollector.StreetIndex streetIndex) {
        if (dataSet == null || streetOption == null || streetIndex == null) {
            return null;
        }
        List<Way> optionWays = streetIndex.getWaysForStreetOption(streetOption);
        Way best = null;
        for (Way way : optionWays) {
            if (way == null || !way.isUsable() || way.getDataSet() != dataSet) {
                continue;
            }
            if (best == null || way.getUniqueId() < best.getUniqueId()) {
                best = way;
            }
        }
        return best;
    }

    private Way resolveDirectSeedWay(DataSet dataSet, String baseStreetName, List<Way> optionWays) {
        if (dataSet == null || lastStreetSeedWayHint == null || !lastStreetSeedWayHint.isUsable()) {
            return null;
        }
        if (lastStreetSeedWayHint.getDataSet() != dataSet) {
            return null;
        }
        if (!normalize(lastStreetSeedWayHint.get("name")).equalsIgnoreCase(normalize(baseStreetName))) {
            return null;
        }
        if (optionWays == null || optionWays.isEmpty() || !containsWayByUniqueId(optionWays, lastStreetSeedWayHint)) {
            return null;
        }
        return lastStreetSeedWayHint;
    }

    private boolean containsWayByUniqueId(List<Way> ways, Way candidate) {
        if (ways == null || ways.isEmpty() || candidate == null) {
            return false;
        }
        for (Way way : ways) {
            if (way != null && way.getUniqueId() == candidate.getUniqueId()) {
                return true;
            }
        }
        return false;
    }

    private LatLon resolveStreetReferencePoint() {
        if (lastStreetInteractionPoint != null) {
            return new LatLon(lastStreetInteractionPoint);
        }
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return null;
        }
        Bounds visibleBounds = map.mapView.getRealBounds();
        return visibleBounds == null ? null : visibleBounds.getCenter();
    }

    private StreetOption resolveCurrentStreetOption(DataSet dataSet) {
        return resolveCurrentStreetOption(dataSet, getStreetIndex(dataSet));
    }

    private StreetOption resolveCurrentStreetOption(DataSet dataSet, StreetNameCollector.StreetIndex streetIndex) {
        StreetNameCollector.StreetIndex effectiveStreetIndex = streetIndex != null
                ? streetIndex
                : getStreetIndex(dataSet);

        StreetOption storedOption = navigationService.getCurrentStreetOption();
        String clusterId = firstNonEmpty(
                storedOption != null ? storedOption.getClusterId() : "",
                navigationService.getCurrentStreetClusterId()
        );
        String displayStreet = firstNonEmpty(
                storedOption != null ? storedOption.getDisplayStreetName() : "",
                navigationService.getCurrentStreetDisplay()
        );
        String baseStreet = firstNonEmpty(
                storedOption != null ? storedOption.getBaseStreetName() : "",
                navigationService.getCurrentStreet()
        );

        Logging.debug(LOG_PREFIX + ": resolve start stored=" + formatStreetOption(storedOption)
                + ", fallbackBase='" + baseStreet + "', fallbackDisplay='" + displayStreet
                + "', fallbackCluster='" + clusterId + "'.");

        if (!clusterId.isEmpty()) {
            StreetOption byCluster = effectiveStreetIndex.findByClusterId(clusterId);
            if (byCluster != null) {
                Logging.debug(LOG_PREFIX + ": resolve path=cluster hit " + formatStreetOption(byCluster) + ".");
                return writeBackResolvedStreetOptionIfNeeded(storedOption, byCluster, "cluster");
            }
            Logging.debug(LOG_PREFIX + ": resolve path=cluster miss cluster='" + clusterId + "'.");
        }

        if (!displayStreet.isEmpty()) {
            StreetOption byDisplay = effectiveStreetIndex.findByDisplayStreetName(displayStreet);
            if (byDisplay != null) {
                Logging.debug(LOG_PREFIX + ": resolve path=display hit " + formatStreetOption(byDisplay) + ".");
                return writeBackResolvedStreetOptionIfNeeded(storedOption, byDisplay, "display");
            }
            Logging.debug(LOG_PREFIX + ": resolve path=display miss display='" + displayStreet + "'.");
        }

        if (!baseStreet.isEmpty()) {
            StreetOption byBase = effectiveStreetIndex.resolveForBaseStreetAndPrimitive(baseStreet, null);
            if (byBase != null) {
                Logging.debug(LOG_PREFIX + ": resolve path=base hit " + formatStreetOption(byBase) + ".");
                return writeBackResolvedStreetOptionIfNeeded(storedOption, byBase, "base");
            }
            Logging.debug(LOG_PREFIX + ": resolve path=base miss base='" + baseStreet + "'.");
        }

        Logging.debug(LOG_PREFIX + ": resolve failed (no street option resolved from current index).");
        return null;
    }

    private StreetOption writeBackResolvedStreetOptionIfNeeded(StreetOption storedOption, StreetOption resolvedOption, String path) {
        if (resolvedOption == null || !resolvedOption.isValid()) {
            return null;
        }
        if (!isSameStreetOptionIdentity(storedOption, resolvedOption)) {
            navigationService.setCurrentStreetOption(resolvedOption);
            Logging.debug(LOG_PREFIX + ": navigation selection refreshed via " + path
                    + " path -> " + formatStreetOption(resolvedOption) + ".");
        }
        return resolvedOption;
    }

    private String formatStreetOption(StreetOption option) {
        if (option == null) {
            return "(none)";
        }
        return "{base='" + normalize(option.getBaseStreetName())
                + "', display='" + normalize(option.getDisplayStreetName())
                + "', cluster='" + normalize(option.getClusterId()) + "'}";
    }

    private boolean isSameStreetOptionIdentity(StreetOption first, StreetOption second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return normalize(first.getClusterId()).equals(normalize(second.getClusterId()))
                && normalize(first.getBaseStreetName()).equals(normalize(second.getBaseStreetName()))
                && normalize(first.getDisplayStreetName()).equals(normalize(second.getDisplayStreetName()));
    }

    private String firstNonEmpty(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        if (!normalizedPrimary.isEmpty()) {
            return normalizedPrimary;
        }
        return normalize(fallback);
    }

    private StreetOption resolveStreetOptionFromUiValue(String streetValue) {
        String normalizedStreet = normalize(streetValue);
        if (normalizedStreet.isEmpty()) {
            return null;
        }

        DataSet editDataSet = getActiveEditDataSet();
        StreetNameCollector.StreetIndex streetIndex = getStreetIndex(editDataSet);
        StreetOption byDisplay = streetIndex.findByDisplayStreetName(normalizedStreet);
        if (byDisplay != null) {
            return byDisplay;
        }
        return streetIndex.resolveForBaseStreetAndPrimitive(normalizedStreet, null);
    }

    private boolean isMatchingBaseStreetOption(StreetOption option, String baseStreetName) {
        if (option == null || !option.isValid()) {
            return false;
        }
        return normalize(option.getBaseStreetName()).equalsIgnoreCase(normalize(baseStreetName));
    }

    private boolean isExplicitDisplaySelection(StreetOption option, String requestedStreet) {
        if (option == null || !option.isValid()) {
            return false;
        }
        return !normalize(option.getBaseStreetName()).equalsIgnoreCase(normalize(requestedStreet));
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

    /**
     * Scan result describing whether a temporary split line targets exactly one building.
     */
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

    /**
     * Resolved operational seed for local same-name street-chain expansion.
     */
    private static final class StreetSeedResolution {
        private final Way seedWay;
        private final String source;
        private final String resolvedVia;

        private StreetSeedResolution(Way seedWay, String source, String resolvedVia) {
            this.seedWay = seedWay;
            this.source = normalize(source);
            this.resolvedVia = normalize(resolvedVia);
        }

        private static StreetSeedResolution none() {
            return new StreetSeedResolution(null, "none", "none");
        }
    }
}
