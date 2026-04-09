package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.spi.preferences.Config;

public final class HouseNumberClickRiskRegressionTests {

    private static final String RELATION_SCAN_LIMIT_KEY = BuildingResolver.PREF_RELATION_SCAN_LIMIT;
    private static final String WAY_SCAN_LIMIT_KEY = BuildingResolver.PREF_WAY_SCAN_LIMIT;

    private HouseNumberClickRiskRegressionTests() {
        // Utility class
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            ensurePreferences();
            run("AddressSelection normalizes values and step", HouseNumberClickRiskRegressionTests::testAddressSelectionNormalization);
            run("HouseNumberService normalizes and sanitizes step", HouseNumberClickRiskRegressionTests::testHouseNumberNormalizationAndStepSanitizing);
            run("HouseNumberService apply increment keeps existing behavior", HouseNumberClickRiskRegressionTests::testHouseNumberIncrementAfterApplyRules);
            run("HouseNumberService number and letter part updates", HouseNumberClickRiskRegressionTests::testHouseNumberPartUpdateRules);
            run("AddressReadbackService reads address tags from building", HouseNumberClickRiskRegressionTests::testAddressReadbackFromBuilding);
            run("AddressReadbackService street fallback only returns street", HouseNumberClickRiskRegressionTests::testAddressReadbackStreetFallback);
            run("AddressReadbackService candidate fallback order", HouseNumberClickRiskRegressionTests::testAddressReadbackCandidateOrderAndMissingTags);
            run("PostcodeCollector collects sorted visible postcodes", HouseNumberClickRiskRegressionTests::testPostcodeCollectorCollectsSortedVisiblePostcodes);
            run("AddressConflictService detects street and postcode conflicts", HouseNumberClickRiskRegressionTests::testAddressConflictDetection);
            run("AddressConflictService handles missing tags and partial differences", HouseNumberClickRiskRegressionTests::testAddressConflictEdgeCases);
            run("AddressConflictService building-type yes overwrite is ignored", HouseNumberClickRiskRegressionTests::testAddressConflictBuildingTypeYesOverwriteIgnored);
            run("Overwrite warning supports independent street and postcode suppression", HouseNumberClickRiskRegressionTests::testOverwriteWarningSupportsIndependentStreetAndPostcodeSuppression);
            run("ConflictDialogModelBuilder keeps field order and value mapping", HouseNumberClickRiskRegressionTests::testConflictDialogModelBuilderMapping);
            run("ConflictDialogModelBuilder handles empty analysis", HouseNumberClickRiskRegressionTests::testConflictDialogModelBuilderEmpty);
            run("HouseNumberOverview duplicate marker ignores mixed variants", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerIgnoresMixedVariants);
            run("HouseNumberOverview duplicate marker tracks exact repeats", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerTracksExactRepeats);
            run("HouseNumberOverview duplicate rows expose grouped primitives", HouseNumberClickRiskRegressionTests::testOverviewDuplicateRowCarriesGroupedPrimitives);
            run("DataSet transition detection is stable", HouseNumberClickRiskRegressionTests::testDataSetChangeDetection);
            run("Single split fails without dataset", HouseNumberClickRiskRegressionTests::testSingleSplitFailsWithoutDataset);
            run("Single split fails for open way", HouseNumberClickRiskRegressionTests::testSingleSplitFailsForOpenWay);
            run("Single split fails without building tag", HouseNumberClickRiskRegressionTests::testSingleSplitFailsWithoutBuildingTag);
            run("Single split fails with zero intersections", HouseNumberClickRiskRegressionTests::testSingleSplitFailsWithZeroIntersections);
            run("Single split fails with one intersection", HouseNumberClickRiskRegressionTests::testSingleSplitFailsWithOneIntersection);
            run("Single split fails with more than two intersections", HouseNumberClickRiskRegressionTests::testSingleSplitFailsWithMoreThanTwoIntersections);
            run("Single split succeeds with exactly two intersections", HouseNumberClickRiskRegressionTests::testSingleSplitSucceedsWithTwoIntersections);
            run("Single split reuses exact corner nodes", HouseNumberClickRiskRegressionTests::testSingleSplitReusesExactCornerNodes);
            run("Single split reuses one snapped node and inserts one node", HouseNumberClickRiskRegressionTests::testSingleSplitReusesOneNodeAndInsertsOne);
            run("Single split inserts node when snap is outside tolerance", HouseNumberClickRiskRegressionTests::testSingleSplitOutsideToleranceInsertsNode);
            run("Single split adjacency protection remains active with snapping", HouseNumberClickRiskRegressionTests::testSingleSplitAdjacencyProtectionWithSnap);
            run("Terrace split succeeds with parts=2", HouseNumberClickRiskRegressionTests::testTerraceSplitSucceedsWithTwoParts);
            run("Terrace split succeeds with parts=4", HouseNumberClickRiskRegressionTests::testTerraceSplitSucceedsWithFourParts);
            run("Terrace split orientation supports non-rectangular outlines", HouseNumberClickRiskRegressionTests::testTerraceSplitOrientationSupportsNonRectangularOutlines);
            run("Terrace split fails for invalid parts", HouseNumberClickRiskRegressionTests::testTerraceSplitFailsForInvalidParts);
            run("Terrace split result order is deterministic", HouseNumberClickRiskRegressionTests::testTerraceSplitOrderIsDeterministic);
            run("Split building button triggers internal flow hook", HouseNumberClickRiskRegressionTests::testSplitBuildingButtonTriggersInternalFlowHook);
            run("Create row houses button triggers internal flow hook with parts", HouseNumberClickRiskRegressionTests::testCreateRowHousesButtonTriggersInternalFlowHook);
            run("Dialog split actions fail cleanly without dataset", HouseNumberClickRiskRegressionTests::testDialogSplitActionsFailWithoutDataset);
            run("New dialog split paths avoid bridge and detector", HouseNumberClickRiskRegressionTests::testDialogSplitPathsAvoidBridgeAndDetector);
            run("Split flow returns to street mode on success", HouseNumberClickRiskRegressionTests::testSplitFlowReturnsToStreetModeOnSuccess);
            run("Split flow returns to street mode on failure", HouseNumberClickRiskRegressionTests::testSplitFlowReturnsToStreetModeOnFailure);
            run("Split flow returns to street mode on cancel", HouseNumberClickRiskRegressionTests::testSplitFlowReturnsToStreetModeOnCancel);
            run("Split flow keeps mode state signaling consistent", HouseNumberClickRiskRegressionTests::testSplitFlowModeStateSignalingConsistency);
            run("Scan limit defaults are used when unset", HouseNumberClickRiskRegressionTests::testScanLimitDefaultsWhenUnset);
            run("Invalid scan limit preferences fall back to defaults", HouseNumberClickRiskRegressionTests::testInvalidScanLimitPreferencesFallBack);
            run("Duplicate click detection blocks true duplicates", HouseNumberClickRiskRegressionTests::testDuplicateClicksAreDetected);
            run("Duplicate click detection keeps rapid distinct clicks", HouseNumberClickRiskRegressionTests::testRapidDistinctClicksAreKept);
            run("Ctrl has priority over Alt split activation", HouseNumberClickRiskRegressionTests::testCtrlHasPriorityOverAltActivation);
            run("Temporary Alt split exits on Alt release", HouseNumberClickRiskRegressionTests::testTemporaryAltSplitExitsOnAltRelease);
            run("Ctrl cursor uses custom magnifier without arrow asset fallback", HouseNumberClickRiskRegressionTests::testCtrlCursorUsesCustomMagnifier);
            run("Split cursor hotspot keeps scalp tip shifted left", HouseNumberClickRiskRegressionTests::testSplitCursorHotspotShiftedLeft);
            run("Terrace split flow completes immediately on successful click", HouseNumberClickRiskRegressionTests::testTerraceSplitCompletesImmediatelyOnSuccess);
            run("Rectangularize option is propagated to temporary line split mode", HouseNumberClickRiskRegressionTests::testRectangularizePreferencePropagation);
            run("Rectangularize skips triangle split results", HouseNumberClickRiskRegressionTests::testRectangularizeCandidateGuard);
            run("House-number cursor label depends on complete address inputs", HouseNumberClickRiskRegressionTests::testHouseNumberCursorLabelCompletenessGuard);
            run("Street mode blocks apply when postcode is not selected", HouseNumberClickRiskRegressionTests::testPostcodeSelectionGuard);
            run("Main dialog close cleanup is safe", HouseNumberClickRiskRegressionTests::testMainDialogCloseCleanupIsSafe);
            run("Rescan refresh entrypoint is safe", HouseNumberClickRiskRegressionTests::testRescanRefreshEntrypointIsSafe);
            run("Create building overview layer entrypoint is safe", HouseNumberClickRiskRegressionTests::testCreateBuildingOverviewLayerEntrypointIsSafe);
            run("Table click continue hook is safe", HouseNumberClickRiskRegressionTests::testTableClickContinueHookIsSafe);
            run("Street navigation order matches street-count sorting", HouseNumberClickRiskRegressionTests::testStreetNavigationOrderMatchesStreetCountsSorting);
            run("Street zoom fallback collects only usable named highway ways", HouseNumberClickRiskRegressionTests::testStreetZoomFallbackWayMatching);
            run("Building overview collector filters tiny buildings and keeps addressed state", HouseNumberClickRiskRegressionTests::testBuildingOverviewCollectorFilteringAndClassification);
            System.out.println("All HouseNumberClick risk regression tests passed.");
        } catch (Throwable t) {
            exitCode = 1;
            t.printStackTrace(System.err);
        } finally {
            // JOSM helper threads can keep the JVM alive after tests complete.
            System.exit(exitCode);
        }
    }

    private static void testHouseNumberNormalizationAndStepSanitizing() {
        HouseNumberService service = new HouseNumberService();

        assertEquals("12a", service.normalize(" 12a "), "house number should be trimmed");
        assertEquals(1, service.normalizeIncrementStep(999), "invalid increment step should normalize to +1");
        assertEquals(1, service.sanitizeIncrementStepForHouseNumber("12a", 2), "letter house numbers must force +1");
        assertEquals(-2, service.sanitizeIncrementStepForHouseNumber("12", -2), "numeric house numbers keep valid selected step");
    }

    private static void testHouseNumberIncrementAfterApplyRules() {
        HouseNumberService service = new HouseNumberService();

        assertEquals("14", service.incrementAfterSuccessfulApply("12", 2), "numeric house number should increment by selected step");
        assertEquals("12b", service.incrementAfterSuccessfulApply("12a", 2), "numeric+letter should increment letter part only");
        assertEquals("b", service.incrementAfterSuccessfulApply("a", 2), "letter-only house number should increment letter sequence");
        assertEquals(null, service.incrementAfterSuccessfulApply("x/1", 1), "unsupported format should remain non-incrementable");
    }

    private static void testHouseNumberPartUpdateRules() {
        HouseNumberService service = new HouseNumberService();

        assertEquals("13a", service.incrementNumberPartByOne("12a"), "number-part increment should preserve suffix");
        assertEquals("11a", service.decrementNumberPartByOne("12a"), "number-part decrement should preserve suffix");
        assertEquals("12b", service.incrementLetterPartByOne("12a"), "letter-part increment should advance suffix");
        assertEquals("12", service.decrementLetterPartByOne("12a"), "letter-part decrement should drop suffix at boundary");
        assertEquals("12a", service.toggleLetterSuffix("12"), "toggle should add default suffix");
        assertEquals("12", service.toggleLetterSuffix("12a"), "toggle should remove existing suffix");
    }

    private static void testAddressReadbackFromBuilding() {
        AddressReadbackService service = new AddressReadbackService();
        Way building = new Way();
        building.put("addr:street", " Example Street ");
        building.put("addr:postcode", " 12345 ");
        building.put("addr:housenumber", " 77b ");

        AddressReadbackService.AddressReadbackResult result = service.readFromBuilding(building, "house");
        assertEquals("Example Street", result.getStreet(), "street should be trimmed from building tag");
        assertEquals("12345", result.getPostcode(), "postcode should be trimmed from building tag");
        assertEquals("house", result.getBuildingType(), "building type should pass through unchanged");
        assertEquals("77b", result.getHouseNumber(), "house number should be trimmed from building tag");
        assertEquals("address-tags", result.getSource(), "source should mark address-tag readback");
    }

    private static void testAddressReadbackStreetFallback() {
        AddressReadbackService service = new AddressReadbackService();
        AddressReadbackService.AddressReadbackResult result = service.readFromStreetFallback(" Example Avenue ", " 99999 ", "residential");

        assertEquals("Example Avenue", result.getStreet(), "street fallback should be trimmed");
        assertEquals("", result.getPostcode(), "street fallback should not provide a postcode override");
        assertEquals("", result.getBuildingType(), "street fallback should not provide a building-type override");
        assertEquals("", result.getHouseNumber(), "street fallback should not provide a house-number override");
        assertEquals("street-fallback", result.getSource(), "source should mark street fallback");
        assertEquals(null, service.readFromStreetFallback("   ", "99999", "residential"), "empty fallback street should produce no readback result");
    }

    private static void testAddressReadbackCandidateOrderAndMissingTags() {
        AddressReadbackService service = new AddressReadbackService();

        Way unnamedHighway = new Way();
        unnamedHighway.put("highway", "residential");

        Way namedHighway = new Way();
        namedHighway.put("highway", "residential");
        namedHighway.put("name", " First Valid Street ");

        Way namedNonHighway = new Way();
        namedNonHighway.put("name", "Ignored Name");

        String street = service.resolveStreetNameFromCandidates(List.of(namedNonHighway, unnamedHighway, namedHighway));
        assertEquals("First Valid Street", street, "resolver should choose first usable named highway candidate");

        Way noNameHighway = new Way();
        noNameHighway.put("highway", "service");
        assertEquals(null, service.resolveStreetNameFromCandidates(List.of(noNameHighway, new Node())),
                "resolver should return null when no candidate has a valid street name");
    }

    private static void testAddressConflictDetection() {
        AddressConflictService service = new AddressConflictService();
        Way building = new Way();
        building.put("addr:street", "Old Street");
        building.put("addr:postcode", "12345");
        building.put("addr:housenumber", "12");
        building.put("building", "residential");

        AddressConflictService.ConflictAnalysis analysis = service.analyze(building, "New Street", "54321", "34", "house");
        assertTrue(analysis.hasConflict(), "different street/postcode should trigger overwrite conflict");
        assertEquals("Old Street", analysis.getOverwrittenStreet(), "existing street should be used as overwritten street");
        assertEquals("12345", analysis.getOverwrittenPostcode(), "existing postcode should be used as overwritten postcode");
        assertEquals(4, analysis.getDifferingFields().size(), "street, postcode, housenumber and building diffs should be listed");
        assertEquals("addr:street", analysis.getDifferingFields().get(0).getKey(), "street diff should appear first");
        assertEquals("addr:postcode", analysis.getDifferingFields().get(1).getKey(), "postcode diff should appear second");
        assertEquals("addr:housenumber", analysis.getDifferingFields().get(2).getKey(), "housenumber diff should appear third");
        assertEquals("building", analysis.getDifferingFields().get(3).getKey(), "building diff should appear fourth");
    }

    private static void testPostcodeCollectorCollectsSortedVisiblePostcodes() {
        DataSet dataSet = new DataSet();

        Way buildingA = createClosedBuilding("Example Street", "1");
        buildingA.put("addr:postcode", "20000");
        Way buildingB = createClosedBuilding("Example Street", "2");
        buildingB.put("addr:postcode", "10000");
        Way buildingDuplicate = createClosedBuilding("Example Street", "3");
        buildingDuplicate.put("addr:postcode", "20000");
        Way buildingWithoutPostcode = createClosedBuilding("Example Street", "4");

        Relation buildingRelation = new Relation();
        buildingRelation.put("type", "multipolygon");
        buildingRelation.put("building", "yes");
        buildingRelation.put("addr:postcode", "30000");

        dataSet.addPrimitiveRecursive(buildingA);
        dataSet.addPrimitiveRecursive(buildingB);
        dataSet.addPrimitiveRecursive(buildingDuplicate);
        dataSet.addPrimitiveRecursive(buildingWithoutPostcode);
        dataSet.addPrimitive(buildingRelation);

        List<String> postcodes = PostcodeCollector.collectVisiblePostcodes(dataSet);
        assertEquals(List.of("10000", "20000", "30000"), postcodes,
                "postcode collector should return distinct, sorted visible building postcodes");
    }

    private static void testAddressConflictEdgeCases() {
        AddressConflictService service = new AddressConflictService();

        Way buildingWithoutStreet = new Way();
        buildingWithoutStreet.put("addr:postcode", "12345");
        AddressConflictService.ConflictAnalysis noStreetConflict = service.analyze(buildingWithoutStreet, "Any Street", "", "", "");
        assertFalse(noStreetConflict.hasConflict(), "missing existing street should not trigger street conflict");
        assertEquals("Any Street", noStreetConflict.getOverwrittenStreet(), "fallback overwritten street should use proposed street");
        assertEquals("12345", noStreetConflict.getOverwrittenPostcode(), "existing postcode should remain overwritten postcode fallback");

        Way buildingWithOnlyHouseNumber = new Way();
        buildingWithOnlyHouseNumber.put("addr:housenumber", "10");
        AddressConflictService.ConflictAnalysis onlyHouseDiff = service.analyze(buildingWithOnlyHouseNumber, "", "", "11", "");
        assertFalse(onlyHouseDiff.hasConflict(), "house number difference alone should not trigger conflict dialog");
        assertEquals(1, onlyHouseDiff.getDifferingFields().size(), "house number difference should still be listed");
        assertEquals("addr:housenumber", onlyHouseDiff.getDifferingFields().get(0).getKey(), "listed diff should be housenumber");

        Way identical = new Way();
        identical.put("addr:street", "Same");
        identical.put("addr:postcode", "11111");
        identical.put("addr:housenumber", "3");
        AddressConflictService.ConflictAnalysis identicalAnalysis = service.analyze(identical, "Same", "11111", "3", "");
        assertFalse(identicalAnalysis.hasConflict(), "identical values should not trigger conflict");
        assertEquals(0, identicalAnalysis.getDifferingFields().size(), "identical values should produce no differing fields");

        AddressConflictService.ConflictAnalysis missingBuilding = service.analyze(null, "Street", "77777", "", "");
        assertEquals("77777", missingBuilding.getOverwrittenPostcode(),
                "missing building should expose proposed postcode as overwrite context fallback");
    }

    private static void testAddressConflictBuildingTypeYesOverwriteIgnored() {
        AddressConflictService service = new AddressConflictService();

        Way building = new Way();
        building.put("building", "yes");
        AddressConflictService.ConflictAnalysis ignoredYesOverwrite =
                service.analyze(building, "", "", "", "house");
        assertFalse(ignoredYesOverwrite.hasConflict(), "overwriting building=yes should not trigger warning");
        assertEquals(0, ignoredYesOverwrite.getDifferingFields().size(), "building=yes overwrite should not add dialog rows");

        building.put("building", "residential");
        AddressConflictService.ConflictAnalysis realTypeOverwrite =
                service.analyze(building, "", "", "", "house");
        assertTrue(realTypeOverwrite.hasConflict(), "overwriting a specific building type should trigger warning");
        assertEquals(1, realTypeOverwrite.getDifferingFields().size(), "specific building-type overwrite should add one dialog row");
        assertEquals("building", realTypeOverwrite.getDifferingFields().get(0).getKey(), "building overwrite row should use building key");
    }

    private static void testOverwriteWarningSupportsIndependentStreetAndPostcodeSuppression() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");

        assertTrue(source.contains("Do not warn again for street:"),
                "overwrite dialog should offer a street-specific suppression option");
        assertTrue(source.contains("Do not warn again for postcode:"),
                "overwrite dialog should offer a postcode-specific suppression option");
        assertTrue(source.contains("shouldShowOverwriteWarning"),
                "warning decision should be derived from field-specific suppression checks");
    }

    private static void testConflictDialogModelBuilderMapping() {
        AddressConflictService service = new AddressConflictService();
        ConflictDialogModelBuilder builder = new ConflictDialogModelBuilder();

        Way building = new Way();
        building.put("addr:street", "Old Street");
        building.put("addr:postcode", "12345");
        building.put("addr:housenumber", "9");

        AddressConflictService.ConflictAnalysis analysis = service.analyze(building, "New Street", "54321", "10", "");
        ConflictDialogModelBuilder.DialogModel model = builder.build(analysis, value -> "[" + value + "]");

        assertEquals(3, model.getRows().size(), "all differing fields should be present in dialog model");
        assertEquals("addr:street", model.getRows().get(0).getField(), "street row should be first");
        assertEquals("addr:postcode", model.getRows().get(1).getField(), "postcode row should be second");
        assertEquals("addr:housenumber", model.getRows().get(2).getField(), "housenumber row should be third");
        assertEquals("[Old Street]", model.getRows().get(0).getExisting(), "existing value should map to dialog row");
        assertEquals("[New Street]", model.getRows().get(0).getProposed(), "proposed value should map to dialog row");
    }

    private static void testConflictDialogModelBuilderEmpty() {
        ConflictDialogModelBuilder builder = new ConflictDialogModelBuilder();
        AddressConflictService.ConflictAnalysis analysis = new AddressConflictService.ConflictAnalysis(false, "", List.of());

        ConflictDialogModelBuilder.DialogModel model = builder.build(analysis, value -> value);
        assertTrue(model.isEmpty(), "empty analysis should produce empty dialog model");
        assertEquals(0, model.getRows().size(), "empty model should contain no rows");
    }

    private static void testOverviewDuplicateMarkerIgnoresMixedVariants() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1b"));

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, "Example Street");
        String oddValue = firstNonEmptyOddValue(rows);
        assertEquals("1", oddValue, "mixed variants without exact duplicates should show only the base number");
    }

    private static void testOverviewDuplicateMarkerTracksExactRepeats() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1a"));

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, "Example Street");
        String oddValue = firstNonEmptyOddValue(rows);
        assertEquals("1 (dup)", oddValue, "exact duplicate values should show compact duplicate marker");
    }

    private static void testOverviewDuplicateRowCarriesGroupedPrimitives() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2b"));

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, "Example Street");
        HouseNumberOverviewRow row = firstRowWithOddValuePrefix(rows, "2");
        assertTrue(row != null, "expected overview row for base number 2");
        assertTrue(row.isEvenDuplicate(), "even row for base 2 should be marked as duplicate");
        assertEquals(5, row.getEvenPrimitives().size(), "duplicate row should include all base-number primitives for grouped zoom");
    }

    private static void testAddressSelectionNormalization() {
        StreetModeController.AddressSelection selection =
                new StreetModeController.AddressSelection("  Main Street  ", " 12345 ", " house ", " 12a ", 99);

        assertEquals("Main Street", selection.getStreetName(), "street should be trimmed");
        assertEquals("12345", selection.getPostcode(), "postcode should be trimmed");
        assertEquals("house", selection.getBuildingType(), "building type should be trimmed");
        assertEquals("12a", selection.getHouseNumber(), "house number should be trimmed");
        assertEquals(1, selection.getHouseNumberIncrementStep(), "invalid step should normalize to +1");
    }

    private static Way createClosedBuilding(String street, String houseNumber) {
        return createClosedBuildingWithSize(street, houseNumber, 0.0001);
    }

    private static Way createClosedBuildingWithSize(String street, String houseNumber, double size) {
        Way way = new Way();
        Node n1 = new Node(new LatLon(0.0, 0.0));
        Node n2 = new Node(new LatLon(0.0, size));
        Node n3 = new Node(new LatLon(size, size));
        Node n4 = new Node(new LatLon(size, 0.0));
        List<Node> nodes = new ArrayList<>();
        nodes.add(n1);
        nodes.add(n2);
        nodes.add(n3);
        nodes.add(n4);
        nodes.add(n1);
        way.setNodes(nodes);
        way.put("building", "yes");
        if (street != null && !street.isBlank()) {
            way.put("addr:street", street);
        }
        if (houseNumber != null && !houseNumber.isBlank()) {
            way.put("addr:housenumber", houseNumber);
        }
        return way;
    }

    private static String firstNonEmptyOddValue(List<HouseNumberOverviewRow> rows) {
        if (rows == null) {
            return "";
        }
        for (HouseNumberOverviewRow row : rows) {
            if (row == null) {
                continue;
            }
            String value = row.getOddValue();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static HouseNumberOverviewRow firstRowWithOddValuePrefix(List<HouseNumberOverviewRow> rows, String prefix) {
        if (rows == null) {
            return null;
        }
        for (HouseNumberOverviewRow row : rows) {
            if (row == null) {
                continue;
            }
            String value = row.getOddValue();
            if (value != null && value.startsWith(prefix)) {
                return row;
            }
            value = row.getEvenValue();
            if (value != null && value.startsWith(prefix)) {
                return row;
            }
        }
        return null;
    }

    private static void testDataSetChangeDetection() {
        DataSet first = new DataSet();
        DataSet second = new DataSet();

        assertTrue(StreetSelectionDialog.isDataSetChanged(first, second), "different DataSet instances must trigger reset");
        assertTrue(StreetSelectionDialog.isDataSetChanged(first, null), "moving to null DataSet must trigger reset");
        assertTrue(StreetSelectionDialog.isDataSetChanged(null, first), "moving from null DataSet must trigger reset");
        assertFalse(StreetSelectionDialog.isDataSetChanged(first, first), "same DataSet instance must not trigger reset");
        assertFalse(StreetSelectionDialog.isDataSetChanged(null, null), "both null should be treated as unchanged");
    }

    private static void testScanLimitDefaultsWhenUnset() {
        Config.getPref().put(RELATION_SCAN_LIMIT_KEY, null);
        Config.getPref().put(WAY_SCAN_LIMIT_KEY, null);

        assertEquals(
                HouseNumberClickStreetMapMode.DEFAULT_RELATION_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredRelationScanLimit(),
                "relation scan limit should use default when preference is missing"
        );
        assertEquals(
                HouseNumberClickStreetMapMode.DEFAULT_WAY_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredWayScanLimit(),
                "way scan limit should use default when preference is missing"
        );
    }

    private static void testInvalidScanLimitPreferencesFallBack() {
        Config.getPref().put(RELATION_SCAN_LIMIT_KEY, "not-a-number");
        Config.getPref().put(WAY_SCAN_LIMIT_KEY, "-9");

        assertEquals(
                HouseNumberClickStreetMapMode.DEFAULT_RELATION_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredRelationScanLimit(),
                "invalid relation limit should fall back to default"
        );
        assertEquals(
                HouseNumberClickStreetMapMode.DEFAULT_WAY_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredWayScanLimit(),
                "invalid way limit should fall back to default"
        );
    }

    private static void testDuplicateClicksAreDetected() throws Exception {
        HouseNumberClickStreetMapMode mode = new HouseNumberClickStreetMapMode(new StreetModeController());

        MouseEvent first = newMouseRelease(1000L, 50, 50, MouseEvent.BUTTON1, 0);
        MouseEvent duplicate = newMouseRelease(1060L, 50, 50, MouseEvent.BUTTON1, 0);

        assertFalse(invokeDuplicateCheck(mode, first), "first click must not be duplicate");
        assertTrue(invokeDuplicateCheck(mode, duplicate), "same click fingerprint in short window must be duplicate");
    }

    private static void testRapidDistinctClicksAreKept() throws Exception {
        HouseNumberClickStreetMapMode mode = new HouseNumberClickStreetMapMode(new StreetModeController());

        MouseEvent first = newMouseRelease(2000L, 100, 100, MouseEvent.BUTTON1, 0);
        MouseEvent differentPosition = newMouseRelease(2060L, 101, 100, MouseEvent.BUTTON1, 0);
        MouseEvent differentModifiers = newMouseRelease(2120L, 101, 100, MouseEvent.BUTTON1, MouseEvent.CTRL_DOWN_MASK);

        assertFalse(invokeDuplicateCheck(mode, first), "first click must not be duplicate");
        assertFalse(invokeDuplicateCheck(mode, differentPosition), "different position should not be duplicate");
        assertFalse(invokeDuplicateCheck(mode, differentModifiers), "different modifiers should not be duplicate");
    }

    private static void testCtrlHasPriorityOverAltActivation() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");

        int altBranchIndex = source.indexOf("e.getKeyCode() == KeyEvent.VK_ALT");
        assertTrue(altBranchIndex >= 0, "street mode should handle Alt key presses");

        int ctrlGuardIndex = source.indexOf("if (e.isControlDown())", altBranchIndex);
        assertTrue(ctrlGuardIndex >= 0, "Alt branch should guard Ctrl-first priority");

        int splitActivationIndex = source.indexOf("controller.activateTemporarySplitModeFromAlt()", altBranchIndex);
        assertTrue(splitActivationIndex >= 0, "Alt branch should still support temporary split activation");
        assertTrue(ctrlGuardIndex < splitActivationIndex,
                "Ctrl guard must appear before temporary Alt split activation");

        int branchEnd = source.indexOf("if (id != KeyEvent.KEY_PRESSED", altBranchIndex);
        assertTrue(branchEnd > altBranchIndex, "Alt branch should end before generic shortcut branch");
        String altBranch = source.substring(altBranchIndex, branchEnd);
        assertFalse(altBranch.contains("isTextInputFocused()"),
                "Alt activation should not depend on text focus so it works immediately after start");
    }

    private static void testTemporaryAltSplitExitsOnAltRelease() throws Exception {
        String source = readPluginSource("HouseNumberSplitMapMode.java");

        assertTrue(source.contains("interactionKind == InteractionKind.LINE_SPLIT"),
                "split mode should include line-split specific keyboard handling");
        assertTrue(source.contains("temporaryAltHold"),
                "split mode should track whether activation came from temporary Alt hold");
        assertTrue(source.contains("event.getKeyCode() == KeyEvent.VK_ALT"),
                "split mode should react to Alt key events");
        assertTrue(source.contains("event.getID() == KeyEvent.KEY_RELEASED"),
                "temporary Alt split should react specifically on key release");
        assertTrue(source.contains("completeWithOutcome(StreetModeController.SplitFlowOutcome.CANCELLED)"),
                "Alt release should end temporary split flow and return to street mode");
    }

    private static void testCtrlCursorUsesCustomMagnifier() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");

        int methodStart = source.indexOf("private Cursor createCtrlZoomCursor()");
        assertTrue(methodStart >= 0, "street mode should provide a Ctrl cursor method");

        int methodEnd = source.indexOf("private Cursor createHouseNumberCursor()", methodStart);
        assertTrue(methodEnd > methodStart, "Ctrl cursor method should appear before house-number cursor method");

        String methodBody = source.substring(methodStart, methodEnd);
        assertTrue(methodBody.contains("new BufferedImage"),
                "Ctrl cursor should be rendered from a custom magnifier image");
        assertTrue(methodBody.contains("Toolkit.getDefaultToolkit()"),
                "Ctrl cursor should build from toolkit custom-cursor support");
        assertTrue(methodBody.contains("g.drawOval"),
                "Ctrl cursor should draw a magnifier lens shape");
        assertTrue(methodBody.contains("g.fillOval(lensCenterX - lensRadius"),
                "Ctrl cursor should fill the lens interior for contrast");
        assertFalse(methodBody.contains("ImageProvider.getCursor"),
                "Ctrl cursor should no longer use arrow-based JOSM zoom cursor assets");
    }

    private static void testSplitCursorHotspotShiftedLeft() throws Exception {
        String source = readPluginSource("HouseNumberSplitMapMode.java");
        assertTrue(source.contains("private static final int CURSOR_HOTSPOT_X = 7;"),
                "split cursor hotspot should remain shifted left for accurate scalp-tip drawing");
    }

    private static void testTerraceSplitCompletesImmediatelyOnSuccess() throws Exception {
        String source = readPluginSource("HouseNumberSplitMapMode.java");

        int terraceBranchStart = source.indexOf("if (interactionKind == InteractionKind.TERRACE_CLICK)");
        assertTrue(terraceBranchStart >= 0, "split mode should contain dedicated terrace-click handling");

        int branchEnd = source.indexOf("// If press was missed during a fast mode switch", terraceBranchStart);
        assertTrue(branchEnd > terraceBranchStart, "terrace branch should end before line-split fallback branch");

        String terraceBranch = source.substring(terraceBranchStart, branchEnd);
        assertTrue(terraceBranch.contains("completeWithOutcome(StreetModeController.SplitFlowOutcome.SUCCESS)"),
                "successful terrace-click split should complete flow and return to street mode immediately");
    }

    private static void testRectangularizePreferencePropagation() throws Exception {
        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        String controllerSource = readPluginSource("StreetModeController.java");

        assertTrue(dialogSource.contains("streetModeController.setRectangularizeAfterLineSplit"),
                "dialog should push make-rectangular checkbox state into controller preference");
        assertTrue(controllerSource.contains("void setRectangularizeAfterLineSplit(boolean makeRectangular)"),
                "controller should expose a setter for line-split rectangularize preference");
    }

    private static void testRectangularizeCandidateGuard() {
        Way triangle = new Way();
        Node t1 = new Node(new LatLon(0.0, 0.0));
        Node t2 = new Node(new LatLon(0.0, 0.0001));
        Node t3 = new Node(new LatLon(0.0001, 0.0));
        triangle.setNodes(List.of(t1, t2, t3, t1));
        triangle.put("building", "yes");

        Way rectangle = new Way();
        Node r1 = new Node(new LatLon(0.0, 0.0));
        Node r2 = new Node(new LatLon(0.0, 0.0001));
        Node r3 = new Node(new LatLon(0.0001, 0.0001));
        Node r4 = new Node(new LatLon(0.0001, 0.0));
        rectangle.setNodes(List.of(r1, r2, r3, r4, r1));
        rectangle.put("building", "yes");

        assertFalse(StreetModeController.isRectangularizeCandidate(triangle),
                "triangle split results must not be rectangularized");
        assertTrue(StreetModeController.isRectangularizeCandidate(rectangle),
                "ways with at least four unique corners remain rectangularize candidates");
    }

    private static void testHouseNumberCursorLabelCompletenessGuard() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");

        assertTrue(source.contains("private boolean hasCompleteAddressInputForApply()"),
                "street mode should define a completeness guard for cursor label rendering");
        assertTrue(source.contains("boolean showHouseNumberLabel = hasCompleteAddressInputForApply()"),
                "house-number cursor should gate label drawing by complete address inputs");
        assertTrue(source.contains("g.drawRoundRect(labelBoxX, labelBoxY, labelBoxWidth, labelBoxHeight, 6, 6);"),
                "house-number cursor should keep an empty placeholder box when label text is hidden");
    }

    private static void testPostcodeSelectionGuard() {
        assertFalse(HouseNumberClickStreetMapMode.isPostcodeSelected(null), "null postcode must be rejected");
        assertFalse(HouseNumberClickStreetMapMode.isPostcodeSelected("   "), "blank postcode must be rejected");
        assertTrue(HouseNumberClickStreetMapMode.isPostcodeSelected("12345"), "non-empty postcode must be accepted");
    }

    private static void testMainDialogCloseCleanupIsSafe() {
        StreetModeController controller = new StreetModeController();
        controller.onMainDialogClosed();
        assertTrue(true, "main dialog close cleanup should complete without exceptions");
    }

    private static void testRescanRefreshEntrypointIsSafe() {
        StreetModeController controller = new StreetModeController();
        controller.rescanPluginData();
        assertTrue(true, "rescan refresh entrypoint should complete without exceptions");
    }

    private static void testCreateBuildingOverviewLayerEntrypointIsSafe() {
        StreetModeController controller = new StreetModeController();
        controller.createBuildingOverviewLayer();
        assertTrue(true, "overview layer entrypoint should complete without exceptions");
    }

    private static void testTableClickContinueHookIsSafe() {
        StreetModeController controller = new StreetModeController();
        controller.continueWorkingFromTableInteraction();
        assertTrue(true, "table click continue hook should complete without exceptions");
    }

    private static void testStreetNavigationOrderMatchesStreetCountsSorting() {
        List<StreetHouseNumberCountRow> rows = List.of(
                new StreetHouseNumberCountRow("Zulu Street", 99, false),
                new StreetHouseNumberCountRow("Bravo Street", 5, false),
                new StreetHouseNumberCountRow("alpha street", 1, false),
                new StreetHouseNumberCountRow("  Charlie Street  ", 7, false)
        );

        List<String> ordered = StreetHouseNumberCountDialog.buildStreetNavigationOrder(rows);
        assertEquals(4, ordered.size(), "all rows should be included in navigation order");
        assertEquals("alpha street", ordered.get(0), "street list should start alphabetically");
        assertEquals("Bravo Street", ordered.get(1), "street names should remain alphabetic regardless of count");
        assertEquals("Charlie Street", ordered.get(2), "street names should be trimmed for navigation order");
        assertEquals("Zulu Street", ordered.get(3), "highest counts should not override alphabetical order");
    }

    private static void testStreetZoomFallbackWayMatching() {
        DataSet dataSet = new DataSet();

        Way matching = createOpenStreetWay("  Sample Street  ", true);
        Way otherName = createOpenStreetWay("Other Street", true);
        Way notHighway = createOpenNonHighwayWay("Sample Street", true);
        Way deletedMatching = createOpenStreetWay("Sample Street", true);
        deletedMatching.setDeleted(true);

        dataSet.addPrimitiveRecursive(matching);
        dataSet.addPrimitiveRecursive(otherName);
        dataSet.addPrimitiveRecursive(notHighway);
        dataSet.addPrimitiveRecursive(deletedMatching);

        List<org.openstreetmap.josm.data.osm.OsmPrimitive> matchingWays =
                StreetModeController.collectStreetWayFallbackPrimitives(dataSet, "sample street");

        assertEquals(1, matchingWays.size(), "only usable named highway ways should match fallback street zoom");
        assertTrue(matchingWays.contains(matching), "matching highway way should be part of fallback selection");
    }

    private static void testBuildingOverviewCollectorFilteringAndClassification() {
        DataSet dataSet = new DataSet();

        Way addressedLarge = createClosedBuildingWithSize("Example Street", "1", 0.0002);
        Way unaddressedLarge = createClosedBuildingWithSize(null, null, 0.0002);
        Way addressedTiny = createClosedBuildingWithSize("Example Street", "99", 0.00001);

        dataSet.addPrimitiveRecursive(addressedLarge);
        dataSet.addPrimitiveRecursive(unaddressedLarge);
        dataSet.addPrimitiveRecursive(addressedTiny);

        BuildingOverviewCollector collector = new BuildingOverviewCollector();
        List<BuildingOverviewCollector.BuildingOverviewEntry> entries = collector.collect(dataSet);

        assertEquals(2, entries.size(), "collector should skip buildings below minimum area");

        boolean containsAddressedLarge = false;
        boolean containsUnaddressedLarge = false;
        boolean containsAddressedTiny = false;
        for (BuildingOverviewCollector.BuildingOverviewEntry entry : entries) {
            if (entry.getPrimitive() == addressedLarge && entry.hasHouseNumber()) {
                containsAddressedLarge = true;
            }
            if (entry.getPrimitive() == unaddressedLarge && !entry.hasHouseNumber()) {
                containsUnaddressedLarge = true;
            }
            if (entry.getPrimitive() == addressedTiny) {
                containsAddressedTiny = true;
            }
        }

        assertTrue(containsAddressedLarge, "large addressed building should be included and marked addressed");
        assertTrue(containsUnaddressedLarge, "large unaddressed building should be included and marked unaddressed");
        assertFalse(containsAddressedTiny, "tiny building should be excluded by minimum area filter");
    }

    private static Way createOpenStreetWay(String streetName, boolean withHighwayTag) {
        Way way = createOpenBaseWay();
        if (withHighwayTag) {
            way.put("highway", "residential");
        }
        way.put("name", streetName);
        return way;
    }

    private static Way createOpenNonHighwayWay(String streetName, boolean withBuildingTag) {
        Way way = createOpenBaseWay();
        if (withBuildingTag) {
            way.put("building", "yes");
        }
        way.put("name", streetName);
        return way;
    }

    private static Way createOpenBaseWay() {
        Way way = new Way();
        Node n1 = new Node(new LatLon(1.0, 1.0));
        Node n2 = new Node(new LatLon(1.0, 1.0001));
        way.setNodes(List.of(n1, n2));
        return way;
    }

    private static void clearTestPrefs() {
        Config.getPref().put(RELATION_SCAN_LIMIT_KEY, null);
        Config.getPref().put(WAY_SCAN_LIMIT_KEY, null);
    }

    private static void ensurePreferences() {
        if (Config.getPref() != null) {
            return;
        }
        Preferences preferences = new Preferences();
        preferences.enableSaveOnPut(false);
        Config.setPreferencesInstance(preferences);
    }

    private static MouseEvent newMouseRelease(long when, int x, int y, int button, int modifiersEx) {
        Component source = new Component() {
            private static final long serialVersionUID = 1L;
        };
        return new MouseEvent(source, MouseEvent.MOUSE_RELEASED, when, modifiersEx, x, y, 1, false, button);
    }

    private static boolean invokeDuplicateCheck(HouseNumberClickStreetMapMode mode, MouseEvent event) throws Exception {
        Method method = HouseNumberClickStreetMapMode.class.getDeclaredMethod("isDuplicateReleaseEvent", MouseEvent.class);
        method.setAccessible(true);
        Object result = method.invoke(mode, event);
        return Boolean.TRUE.equals(result);
    }

    private static void run(String name, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            System.out.println("[PASS] " + name);
        } finally {
            clearTestPrefs();
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=<" + expected + "> actual=<" + actual + ">");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void testSingleSplitFailsWithoutDataset() {
        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                null,
                null,
                new LatLon(0.0, 0.0),
                new LatLon(1.0, 1.0),
                SplitContext.empty()
        );
        assertFalse(result.isSuccess(), "split should fail when dataset is missing");
    }

    private static void testSingleSplitFailsForOpenWay() {
        DataSet dataSet = new DataSet();
        Way openWay = createOpenStreetWay("Open Way", true);
        openWay.put("building", "yes");
        dataSet.addPrimitiveRecursive(openWay);

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                openWay,
                new LatLon(0.0, 0.0),
                new LatLon(1.0, 1.0),
                SplitContext.empty()
        );
        assertFalse(result.isSuccess(), "split should fail for open ways");
    }

    private static void testSingleSplitFailsWithoutBuildingTag() {
        DataSet dataSet = new DataSet();
        Way buildingLike = createClosedWay(
                List.of(
                        new LatLon(0.0, 0.0),
                        new LatLon(0.0, 0.0001),
                        new LatLon(0.0001, 0.0001),
                        new LatLon(0.0001, 0.0),
                        new LatLon(0.0, 0.0)
                ),
                false
        );
        dataSet.addPrimitiveRecursive(buildingLike);

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                buildingLike,
                new LatLon(-0.0001, -0.0001),
                new LatLon(0.0002, 0.0002),
                SplitContext.empty()
        );
        assertFalse(result.isSuccess(), "split should fail when building tag is missing");
    }

    private static void testSingleSplitFailsWithZeroIntersections() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(1.0, 1.0),
                new LatLon(1.1, 1.1),
                SplitContext.empty()
        );
        assertFalse(result.isSuccess(), "split should fail when line does not intersect building");
    }

    private static void testSingleSplitFailsWithOneIntersection() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(-0.0002, -0.0002),
                new LatLon(0.0, 0.0),
                SplitContext.empty()
        );
        assertFalse(result.isSuccess(), "split should fail when line touches building only once");
    }

    private static void testSingleSplitFailsWithMoreThanTwoIntersections() {
        DataSet dataSet = new DataSet();
        Way building = createConcaveBuildingForMultiIntersection();
        dataSet.addPrimitiveRecursive(building);

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(0.0002, -0.0001),
                new LatLon(0.0002, 0.0005),
                SplitContext.empty()
        );
        assertFalse(result.isSuccess(), "split should fail when line intersects building more than twice");
    }

    private static void testSingleSplitSucceedsWithTwoIntersections() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(-0.0001, -0.0001),
                new LatLon(0.0002, 0.0002),
                SplitContext.empty()
        );

        assertTrue(result.isSuccess(), "split should succeed for exactly two valid intersections");
        assertEquals(2, result.getResultWays().size(), "split should expose two result ways");
        assertEquals(0, dataSet.getSelectedWays().size(), "service split should not change selection directly");
    }

    private static void testSingleSplitReusesExactCornerNodes() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);
        int nodesBefore = dataSet.getNodes().size();

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(-0.0001, -0.0001),
                new LatLon(0.0002, 0.0002),
                SplitContext.empty()
        );

        assertTrue(result.isSuccess(), "split should succeed when line intersects exact corners");
        assertEquals(nodesBefore, dataSet.getNodes().size(), "exact-corner split should not insert new nodes");
    }

    private static void testSingleSplitReusesOneNodeAndInsertsOne() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);
        int nodesBefore = dataSet.getNodes().size();

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(-0.000042, -0.0001),
                new LatLon(0.000096, 0.0002),
                SplitContext.empty()
        );

        assertTrue(result.isSuccess(), "split should succeed for mixed reuse/insert intersections");
        assertEquals(nodesBefore + 1, dataSet.getNodes().size(), "exactly one new node should be inserted");
        assertEquals(2, result.getResultWays().size(), "split should still produce two result ways");
    }

    private static void testSingleSplitOutsideToleranceInsertsNode() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);
        int nodesBefore = dataSet.getNodes().size();

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(-0.000020, -0.0001),
                new LatLon(0.000085, 0.0002),
                SplitContext.empty()
        );

        assertTrue(result.isSuccess(), "split should still succeed when intersection is outside snap tolerance");
        assertEquals(nodesBefore + 2, dataSet.getNodes().size(), "both intersections should create new nodes when outside tolerance");
    }

    private static void testSingleSplitAdjacencyProtectionWithSnap() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);
        int nodesBefore = dataSet.getNodes().size();

        SingleBuildingSplitService service = new SingleBuildingSplitService();
        SingleSplitResult result = service.splitBuilding(
                dataSet,
                building,
                new LatLon(0.000004, -0.0001),
                new LatLon(0.000004, 0.0002),
                SplitContext.empty()
        );

        assertFalse(result.isSuccess(), "split should fail when snapped nodes become adjacent on outline");
        assertEquals(nodesBefore, dataSet.getNodes().size(), "adjacency rejection should not leave inserted nodes behind");
    }

    private static void testTerraceSplitSucceedsWithTwoParts() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);

        TerraceSplitService service = new TerraceSplitService();
        TerraceSplitResult result = service.splitBuildingIntoTerrace(
                dataSet,
                building,
                new TerraceSplitRequest(2),
                SplitContext.empty()
        );

        assertTrue(result.isSuccess(), "terrace split should succeed for parts=2");
        assertEquals(2, result.getResultWays().size(), "parts=2 should create exactly two result ways");
    }

    private static void testTerraceSplitSucceedsWithFourParts() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);

        TerraceSplitService service = new TerraceSplitService();
        TerraceSplitResult result = service.splitBuildingIntoTerrace(
                dataSet,
                building,
                new TerraceSplitRequest(4),
                SplitContext.empty()
        );

        assertTrue(result.isSuccess(), "terrace split should succeed for parts=4");
        assertEquals(4, result.getResultWays().size(), "parts=4 should create exactly four result ways");
    }

    private static void testTerraceSplitOrientationSupportsNonRectangularOutlines() throws Exception {
        String source = readPluginSource("TerraceSplitService.java");
        assertFalse(source.contains("corners.size() != 4"),
                "terrace split orientation should not be limited to 4-corner buildings");
        assertTrue(source.contains("findLongestEdgeDirection"),
                "terrace split orientation should derive axis from longest edge direction");
    }

    private static void testTerraceSplitFailsForInvalidParts() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Split Street", "1");
        dataSet.addPrimitiveRecursive(building);

        TerraceSplitService service = new TerraceSplitService();
        TerraceSplitResult result = service.splitBuildingIntoTerrace(
                dataSet,
                building,
                new TerraceSplitRequest(1),
                SplitContext.empty()
        );

        assertFalse(result.isSuccess(), "terrace split should fail for parts < 2");
    }

    private static void testTerraceSplitOrderIsDeterministic() {
        DataSet firstDataSet = new DataSet();
        Way firstBuilding = createClosedBuilding("Split Street", "1");
        firstDataSet.addPrimitiveRecursive(firstBuilding);

        DataSet secondDataSet = new DataSet();
        Way secondBuilding = createClosedBuilding("Split Street", "1");
        secondDataSet.addPrimitiveRecursive(secondBuilding);

        TerraceSplitService service = new TerraceSplitService();
        TerraceSplitResult firstResult = service.splitBuildingIntoTerrace(
                firstDataSet,
                firstBuilding,
                new TerraceSplitRequest(4),
                SplitContext.empty()
        );
        TerraceSplitResult secondResult = service.splitBuildingIntoTerrace(
                secondDataSet,
                secondBuilding,
                new TerraceSplitRequest(4),
                SplitContext.empty()
        );

        assertTrue(firstResult.isSuccess(), "first terrace split should succeed");
        assertTrue(secondResult.isSuccess(), "second terrace split should succeed");
        assertEquals(
                terraceOrderSignature(firstResult.getResultWays()),
                terraceOrderSignature(secondResult.getResultWays()),
                "result ordering should remain stable across identical splits"
        );
    }

    private static void testSplitBuildingButtonTriggersInternalFlowHook() {
        boolean[] called = {false};
        boolean result = StreetSelectionDialog.runSplitBuildingAction(() -> {
            called[0] = true;
            return true;
        });

        assertTrue(called[0], "split building action should invoke internal flow callback");
        assertTrue(result, "split building action should return callback result");
    }

    private static void testCreateRowHousesButtonTriggersInternalFlowHook() {
        int[] partsSeen = {-1};
        TerraceSplitResult result = StreetSelectionDialog.runCreateRowHousesAction("4", parts -> {
            partsSeen[0] = parts;
            return TerraceSplitResult.success("ok", List.of());
        });

        assertTrue(result.isSuccess(), "create row houses action should forward valid parts");
        assertEquals(4, partsSeen[0], "create row houses action should pass parsed parts to internal flow");
    }

    private static void testDialogSplitActionsFailWithoutDataset() {
        StreetModeController controller = new StreetModeController();

        boolean splitModeActivated = controller.startInternalSingleSplitFlowFromDialog();
        assertFalse(splitModeActivated, "split building action should fail cleanly without dataset");

        TerraceSplitResult terraceResult = controller.executeInternalTerraceSplitFromDialog(4);
        assertFalse(terraceResult.isSuccess(), "create row houses action should fail cleanly without dataset");
        assertEquals("No editable dataset is available.", terraceResult.getMessage(), "missing dataset failure should be explicit");
    }

    private static void testDialogSplitPathsAvoidBridgeAndDetector() throws Exception {
        Path dialogPath = Path.of("src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", "StreetSelectionDialog.java");
        String source = Files.readString(dialogPath);

        assertFalse(source.contains("activateBuildingSplitterWithAddress("), "new split dialog path must not call bridge activation");
        assertTrue(source.contains("startInternalSingleSplitFlowFromDialog"), "split building button should use internal split flow");
        assertTrue(source.contains("executeInternalTerraceSplitFromDialog"), "create row houses button should use internal terrace flow");
    }

    private static String readPluginSource(String fileName) throws Exception {
        Path path = Path.of("src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", fileName);
        return Files.readString(path);
    }

    private static void testSplitFlowReturnsToStreetModeOnSuccess() {
        StreetModeController controller = new StreetModeController();
        controller.activate(new StreetModeController.AddressSelection("Main Street", "12345", "house", "1", 1));

        int[] returnCalls = {0};
        controller.setSplitFlowReturnHookForTesting(() -> returnCalls[0]++);

        controller.onInternalSplitFlowFinished(StreetModeController.SplitFlowOutcome.SUCCESS);

        assertEquals(1, returnCalls[0], "split success should trigger one return-to-street-mode attempt");
        assertEquals(StreetModeController.SplitFlowOutcome.SUCCESS, controller.getLastSplitFlowOutcomeForTesting(),
                "last split outcome should track success");
    }

    private static void testSplitFlowReturnsToStreetModeOnFailure() {
        StreetModeController controller = new StreetModeController();
        controller.activate(new StreetModeController.AddressSelection("Main Street", "12345", "house", "1", 1));

        int[] returnCalls = {0};
        controller.setSplitFlowReturnHookForTesting(() -> returnCalls[0]++);

        controller.onInternalSplitFlowFinished(StreetModeController.SplitFlowOutcome.FAILED);

        assertEquals(1, returnCalls[0], "split failure should still trigger return-to-street-mode attempt");
        assertEquals(StreetModeController.SplitFlowOutcome.FAILED, controller.getLastSplitFlowOutcomeForTesting(),
                "last split outcome should track failure");
    }

    private static void testSplitFlowReturnsToStreetModeOnCancel() {
        StreetModeController controller = new StreetModeController();
        controller.activate(new StreetModeController.AddressSelection("Main Street", "12345", "house", "1", 1));

        int[] returnCalls = {0};
        controller.setSplitFlowReturnHookForTesting(() -> returnCalls[0]++);

        controller.onInternalSplitFlowFinished(StreetModeController.SplitFlowOutcome.CANCELLED);

        assertEquals(1, returnCalls[0], "split cancel should trigger return-to-street-mode attempt");
        assertEquals(StreetModeController.SplitFlowOutcome.CANCELLED, controller.getLastSplitFlowOutcomeForTesting(),
                "last split outcome should track cancel");
    }

    private static void testSplitFlowModeStateSignalingConsistency() {
        StreetModeController controller = new StreetModeController();
        List<Boolean> states = new ArrayList<>();
        controller.setModeStateListener(states::add);
        controller.activate(new StreetModeController.AddressSelection("Main Street", "12345", "house", "1", 1));

        controller.onInternalSplitFlowFinished(StreetModeController.SplitFlowOutcome.FAILED);

        assertFalse(states.isEmpty(), "mode state listener should receive updates");
        assertEquals(Boolean.FALSE, states.get(states.size() - 1),
                "after split completion without map context the mode should be signaled as paused");
    }

    private static String terraceOrderSignature(List<Way> ways) {
        List<String> signatures = new ArrayList<>();
        for (Way way : ways) {
            signatures.add(String.format("%.10f:%.10f", wayCentroidLat(way), wayCentroidLon(way)));
        }
        return String.join("|", signatures);
    }

    private static double wayCentroidLat(Way way) {
        List<Node> nodes = openRingNodes(way);
        if (nodes.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double sum = 0.0;
        int count = 0;
        for (Node node : nodes) {
            if (node == null || node.getCoor() == null) {
                continue;
            }
            sum += node.getCoor().lat();
            count++;
        }
        return count == 0 ? Double.POSITIVE_INFINITY : sum / count;
    }

    private static double wayCentroidLon(Way way) {
        List<Node> nodes = openRingNodes(way);
        if (nodes.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double sum = 0.0;
        int count = 0;
        for (Node node : nodes) {
            if (node == null || node.getCoor() == null) {
                continue;
            }
            sum += node.getCoor().lon();
            count++;
        }
        return count == 0 ? Double.POSITIVE_INFINITY : sum / count;
    }

    private static List<Node> openRingNodes(Way way) {
        List<Node> nodes = new ArrayList<>(way.getNodes());
        if (nodes.size() > 1 && nodes.get(0).equals(nodes.get(nodes.size() - 1))) {
            nodes.remove(nodes.size() - 1);
        }
        return nodes;
    }

    private static Way createClosedWay(List<LatLon> coordinates, boolean addBuildingTag) {
        Way way = new Way();
        List<Node> nodes = new ArrayList<>();
        for (LatLon coordinate : coordinates) {
            nodes.add(new Node(coordinate));
        }
        way.setNodes(nodes);
        if (addBuildingTag) {
            way.put("building", "yes");
        }
        return way;
    }

    private static Way createConcaveBuildingForMultiIntersection() {
        return createClosedWay(
                List.of(
                        new LatLon(0.0, 0.0),
                        new LatLon(0.0, 0.0004),
                        new LatLon(0.0004, 0.0004),
                        new LatLon(0.0004, 0.0003),
                        new LatLon(0.0001, 0.0003),
                        new LatLon(0.0001, 0.0002),
                        new LatLon(0.0004, 0.0002),
                        new LatLon(0.0004, 0.0001),
                        new LatLon(0.0001, 0.0001),
                        new LatLon(0.0001, 0.0),
                        new LatLon(0.0, 0.0)
                ),
                true
        );
    }
}
