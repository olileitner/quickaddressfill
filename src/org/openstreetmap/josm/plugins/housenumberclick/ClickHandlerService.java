package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;

/**
 * Encapsulates click interaction flow for applying tags (including optional city/country),
 * reading addresses, and conflict handling.
 */
final class ClickHandlerService {

    private static final String DEFAULT_STREET_PICKED_HOUSE_NUMBER = "1";

    interface InteractionPort {
        boolean shouldShowOverwriteWarning(
                AddressConflictService.ConflictAnalysis conflictAnalysis,
                String overwrittenStreet,
                String overwrittenPostcode,
                String overwrittenCity,
                String overwrittenCountry
        );

        boolean confirmOverwrite(
                AddressConflictService.ConflictAnalysis conflictAnalysis,
                String overwrittenStreet,
                String overwrittenPostcode,
                String overwrittenCity,
                String overwrittenCountry
        );

        void notifyUser(String message);

        String displayValue(String value);

        void updateStatusLine(String message);


        void notifyAddressApplied();

        void notifyBuildingTypeConsumed();

        void updateAddressValues(String streetName, String postcode, String city, String country,
                String buildingType, String houseNumber);

        void rememberStreetInteraction(Way streetWay, LatLon interactionPoint);
    }

    /**
     * Result of a primary (apply) click, including outcome, resolution stats, and next UI state.
     */
    static final class PrimaryClickResult {
        private final String outcome;
        private final BuildingResolver.BuildingResolutionResult resolution;
        private final boolean applied;
        private final String nextBuildingType;
        private final String appliedStreet;
        private final String appliedHouseNumber;

        private PrimaryClickResult(
                String outcome,
                BuildingResolver.BuildingResolutionResult resolution,
                boolean applied,
                String nextBuildingType,
                String appliedStreet,
                String appliedHouseNumber
        ) {
            this.outcome = outcome;
            this.resolution = resolution == null ? BuildingResolver.BuildingResolutionResult.notEvaluated() : resolution;
            this.applied = applied;
            this.nextBuildingType = nextBuildingType;
            this.appliedStreet = appliedStreet;
            this.appliedHouseNumber = appliedHouseNumber;
        }

        String getOutcome() {
            return outcome;
        }

        BuildingResolver.BuildingResolutionResult getResolution() {
            return resolution;
        }

        boolean isApplied() {
            return applied;
        }

        String getNextBuildingType() {
            return nextBuildingType;
        }

        String getAppliedStreet() {
            return appliedStreet;
        }

        String getAppliedHouseNumber() {
            return appliedHouseNumber;
        }
    }

    /**
     * Lightweight result of non-primary click flows with outcome and resolver diagnostics.
     */
    static final class ClickResult {
        private final String outcome;
        private final BuildingResolver.BuildingResolutionResult resolution;

        private ClickResult(String outcome, BuildingResolver.BuildingResolutionResult resolution) {
            this.outcome = outcome;
            this.resolution = resolution == null ? BuildingResolver.BuildingResolutionResult.notEvaluated() : resolution;
        }

        String getOutcome() {
            return outcome;
        }

        BuildingResolver.BuildingResolutionResult getResolution() {
            return resolution;
        }
    }

    private final StreetModeController controller;
    private final BuildingResolver buildingResolver;
    private final AddressReadbackService addressReadbackService;
    private final AddressConflictService addressConflictService;

    ClickHandlerService(
            StreetModeController controller,
            BuildingResolver buildingResolver,
            AddressReadbackService addressReadbackService,
            AddressConflictService addressConflictService
    ) {
        this.controller = controller;
        this.buildingResolver = buildingResolver;
        this.addressReadbackService = addressReadbackService;
        this.addressConflictService = addressConflictService;
    }

    PrimaryClickResult handlePrimaryClick(
            MapFrame map,
            MouseEvent e,
            String streetName,
            String postcode,
            String city,
            String country,
            String buildingType,
            String houseNumber,
            InteractionPort port
    ) {
        if (normalize(streetName).isEmpty()) {
            String message = org.openstreetmap.josm.tools.I18n.tr("No street selected.");
            port.updateStatusLine(message);
            port.notifyUser(message);
            return rejectedPrimary("no-street-selected", buildingType, streetName, houseNumber);
        }

        if (!isPostcodeSelected(postcode)) {
            String message = org.openstreetmap.josm.tools.I18n.tr("No postcode selected.");
            port.updateStatusLine(message);
            port.notifyUser(message);
            return rejectedPrimary("no-postcode-selected", buildingType, streetName, houseNumber);
        }

        if (houseNumber == null || houseNumber.isBlank()) {
            String message = org.openstreetmap.josm.tools.I18n.tr("No house number selected.");
            port.updateStatusLine(message);
            port.notifyUser(message);
            return rejectedPrimary("no-house-number-selected", buildingType, streetName, houseNumber);
        }
        if (map == null || map.mapView == null) {
            return rejectedPrimary("map-unavailable", buildingType, streetName, houseNumber);
        }

        BuildingResolver.BuildingResolutionResult resolution = buildingResolver.resolveAtClick(map, e);
        OsmPrimitive building = resolution.getBuilding();
        if (building == null) {
            port.updateStatusLine(org.openstreetmap.josm.tools.I18n.tr("No building detected."));
            return rejectedPrimary("no-building-hit", resolution, buildingType, streetName, houseNumber);
        }

        AddressConflictService.ConflictAnalysis conflictAnalysis =
                addressConflictService.analyze(building, streetName, postcode, city, country, houseNumber, buildingType);
        String overwrittenStreet = conflictAnalysis.getOverwrittenStreet();
        String overwrittenPostcode = conflictAnalysis.getOverwrittenPostcode();
        String overwrittenCity = conflictAnalysis.getOverwrittenCity();
        String overwrittenCountry = conflictAnalysis.getOverwrittenCountry();
        if (conflictAnalysis.hasConflict()
                && port.shouldShowOverwriteWarning(
                        conflictAnalysis,
                        overwrittenStreet,
                        overwrittenPostcode,
                        overwrittenCity,
                        overwrittenCountry)) {
            if (!port.confirmOverwrite(
                    conflictAnalysis,
                    overwrittenStreet,
                    overwrittenPostcode,
                    overwrittenCity,
                    overwrittenCountry)) {
                port.updateStatusLine(org.openstreetmap.josm.tools.I18n.tr("Overwrite cancelled."));
                e.consume();
                return rejectedPrimary("overwrite-cancelled", resolution, buildingType, streetName, houseNumber);
            }
        }

        String appliedStreet = normalize(streetName);
        String appliedHouseNumber = normalize(houseNumber);
        boolean buildingTypeWasUsed = !normalize(buildingType).isEmpty();
        OsmPrimitive writeTarget = resolveWriteTargetForApply(building);
        BuildingTagApplier.applyAddress(writeTarget, streetName, postcode, city, country, buildingType, houseNumber);
        port.notifyAddressApplied();

        DataSet dataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (dataSet != null) {
            dataSet.setSelected(Collections.emptyList());
        }

        String nextBuildingType = buildingType;
        if (buildingTypeWasUsed) {
            nextBuildingType = "";
            port.notifyBuildingTypeConsumed();
        }

        e.consume();
        return new PrimaryClickResult("applied", resolution, true, nextBuildingType, appliedStreet, appliedHouseNumber);
    }

    ClickResult handleSecondaryClick(
            MapFrame map,
            MouseEvent e,
            String postcode,
            String buildingType,
            InteractionPort port
    ) {
        if (map == null || map.mapView == null) {
            return clickResult("map-unavailable", BuildingResolver.BuildingResolutionResult.notEvaluated());
        }

        Way clickedStreetWay = resolveStreetWayAtClick(map, e);
        LatLon clickPoint = resolveClickPoint(map, e);

        BuildingResolver.BuildingResolutionResult resolution = buildingResolver.resolveAtClick(map, e);
        OsmPrimitive building = resolution.getBuilding();
        if (building == null) {
            AddressReadbackService.AddressReadbackResult readback = addressReadbackService.readFromStreetFallback(
                    addressReadbackService.resolveStreetNameAtClick(map, e),
                    postcode,
                    buildingType
            );
            if (readback != null) {
                port.rememberStreetInteraction(clickedStreetWay, clickPoint);
                String streetPickedHouseNumber = normalize(readback.getHouseNumber());
                if (streetPickedHouseNumber.isEmpty()) {
                    streetPickedHouseNumber = DEFAULT_STREET_PICKED_HOUSE_NUMBER;
                }
                port.updateAddressValues(
                        readback.getStreet(),
                        readback.getPostcode(),
                        "",
                        "",
                        readback.getBuildingType(),
                        streetPickedHouseNumber
                );
                port.updateStatusLine(org.openstreetmap.josm.tools.I18n.tr(
                        "Street name loaded from map: {0}",
                        port.displayValue(readback.getStreet())
                ));
                return clickResult("street-picked", resolution);
            }

            String message = org.openstreetmap.josm.tools.I18n.tr("Please click on a street or a building.");
            port.updateStatusLine(message);
            port.notifyUser(message);
            return clickResult("no-target-hit", resolution);
        }

        AddressReadbackService.AddressReadbackResult readback = addressReadbackService.readFromBuilding(building, buildingType);
        if (isReadbackMissingUsableAddressData(readback)) {
            String message = org.openstreetmap.josm.tools.I18n.tr("No address data found for this building.");
            port.updateStatusLine(message);
            port.notifyUser(message);
            return clickResult("address-data-missing", resolution);
        }

        if (readback != null && !normalize(readback.getStreet()).isEmpty()) {
            port.rememberStreetInteraction(clickedStreetWay, clickPoint);
        }

        port.updateAddressValues(
                readback.getStreet(),
                readback.getPostcode(),
                readback.getCity(),
                readback.getCountry(),
                readback.getBuildingType(),
                readback.getHouseNumber()
        );
        port.updateStatusLine(
                org.openstreetmap.josm.tools.I18n.tr(
                        "Address data loaded: street={0}, postcode={1}, house number={2}",
                        port.displayValue(readback.getStreet()),
                        port.displayValue(readback.getPostcode()),
                        port.displayValue(readback.getHouseNumber())
                )
        );
        e.consume();
        return clickResult("address-picked", resolution);
    }

    ClickResult handleTerraceRightClick(MapFrame map, MouseEvent e, InteractionPort port) {
        if (map == null || map.mapView == null) {
            return clickResult("map-unavailable", BuildingResolver.BuildingResolutionResult.notEvaluated());
        }

        BuildingResolver.BuildingResolutionResult resolution = buildingResolver.resolveAtClick(map, e);
        OsmPrimitive building = resolution.getBuilding();
        Way targetWay = resolveTerraceTargetWay(building);
        if (targetWay == null) {
            port.updateStatusLine(org.openstreetmap.josm.tools.I18n.tr("No building detected."));
            return clickResult("no-building-hit", resolution);
        }

        TerraceSplitResult result = controller.executeInternalTerraceSplitAtClick(
                targetWay,
                controller.getConfiguredTerraceParts()
        );
        if (!result.isSuccess()) {
            return clickResult("terrace-split-failed", resolution);
        }

        port.updateStatusLine(org.openstreetmap.josm.tools.I18n.tr(
                "Row houses created ({0} parts).",
                controller.getConfiguredTerraceParts()
        ));
        e.consume();
        return clickResult("terrace-split-applied", resolution);
    }

    private PrimaryClickResult rejectedPrimary(String outcome, String buildingType, String streetName, String houseNumber) {
        return rejectedPrimary(outcome, BuildingResolver.BuildingResolutionResult.notEvaluated(), buildingType, streetName, houseNumber);
    }

    private PrimaryClickResult rejectedPrimary(
            String outcome,
            BuildingResolver.BuildingResolutionResult resolution,
            String buildingType,
            String streetName,
            String houseNumber
    ) {
        return new PrimaryClickResult(
                outcome,
                resolution,
                false,
                buildingType,
                normalize(streetName),
                normalize(houseNumber)
        );
    }

    private ClickResult clickResult(String outcome, BuildingResolver.BuildingResolutionResult resolution) {
        return new ClickResult(outcome, resolution);
    }

    private boolean isReadbackMissingUsableAddressData(AddressReadbackService.AddressReadbackResult readback) {
        if (readback == null) {
            return true;
        }
        return normalize(readback.getStreet()).isEmpty()
                && normalize(readback.getPostcode()).isEmpty()
                && normalize(readback.getCity()).isEmpty()
                && normalize(readback.getCountry()).isEmpty()
                && normalize(readback.getHouseNumber()).isEmpty();
    }

    private Way resolveTerraceTargetWay(OsmPrimitive building) {
        if (building instanceof Way) {
            Way way = (Way) building;
            if (way.isUsable() && way.isClosed() && way.hasKey("building")) {
                return way;
            }
            return null;
        }

        OsmPrimitive selectionTarget = getSelectionTarget(building);
        if (selectionTarget instanceof Way) {
            Way way = (Way) selectionTarget;
            if (way.isUsable() && way.isClosed() && way.hasKey("building")) {
                return way;
            }
        }
        return null;
    }

    private OsmPrimitive getSelectionTarget(OsmPrimitive building) {
        if (!(building instanceof Relation)) {
            return building;
        }

        Relation relation = (Relation) building;
        List<Way> outers = new ArrayList<>();
        for (RelationMember member : relation.getMembers()) {
            String role = member.getRole();
            if (member.isWay() && (role == null || role.isEmpty() || "outer".equals(role))) {
                outers.add(member.getWay());
            }
        }

        if (!outers.isEmpty()) {
            return outers.get(0);
        }
        return building;
    }

    private OsmPrimitive resolveWriteTargetForApply(OsmPrimitive building) {
        if (!(building instanceof Way) || !building.isUsable()) {
            return building;
        }

        Way way = (Way) building;
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (!(referrer instanceof Relation)) {
                continue;
            }
            Relation relation = (Relation) referrer;
            if (!relation.isUsable()) {
                continue;
            }
            if (relation.hasTag("type", "multipolygon") && relation.hasTag("building")) {
                return relation;
            }
        }
        return building;
    }

    private boolean isPostcodeSelected(String postcode) {
        return postcode != null && !postcode.trim().isEmpty();
    }

    private Way resolveStreetWayAtClick(MapFrame map, MouseEvent event) {
        if (map == null || map.mapView == null || event == null) {
            return null;
        }
        List<OsmPrimitive> nearby = map.mapView.getAllNearest(event.getPoint(), this::isNamedStreetWay);
        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Way && isNamedStreetWay(primitive)) {
                return (Way) primitive;
            }
        }
        return null;
    }

    private boolean isNamedStreetWay(OsmPrimitive primitive) {
        return primitive instanceof Way
                && primitive.isUsable()
                && primitive.hasTag("highway")
                && !normalize(primitive.get("name")).isEmpty();
    }

    private LatLon resolveClickPoint(MapFrame map, MouseEvent event) {
        if (map == null || map.mapView == null || event == null) {
            return null;
        }
        return map.mapView.getLatLon(event.getX(), event.getY());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
