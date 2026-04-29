package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

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
            run("CountryDetectionService detects confident single-country datasets", HouseNumberClickRiskRegressionTests::testCountryDetectionServiceConfidentDetection);
            run("AddressConflictService detects street and postcode conflicts", HouseNumberClickRiskRegressionTests::testAddressConflictDetection);
            run("AddressConflictService handles missing tags and partial differences", HouseNumberClickRiskRegressionTests::testAddressConflictEdgeCases);
            run("Empty city/country do not trigger overwrite warnings", HouseNumberClickRiskRegressionTests::testEmptyCityCountryDoNotTriggerOverwriteWarning);
            run("AddressConflictService building-type yes overwrite is ignored", HouseNumberClickRiskRegressionTests::testAddressConflictBuildingTypeYesOverwriteIgnored);
            run("ConflictDialogModelBuilder keeps field order and value mapping", HouseNumberClickRiskRegressionTests::testConflictDialogModelBuilderMapping);
            run("ConflictDialogModelBuilder handles empty analysis", HouseNumberClickRiskRegressionTests::testConflictDialogModelBuilderEmpty);
            run("HouseNumberOverview duplicate marker ignores mixed variants", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerIgnoresMixedVariants);
            run("HouseNumberOverview duplicate marker tracks exact repeats", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerTracksExactRepeats);
            run("HouseNumberOverview local duplicate marker stays city-agnostic", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerIsCityAgnostic);
            run("HouseNumberOverview duplicate rows expose duplicate zoom targets", HouseNumberClickRiskRegressionTests::testOverviewDuplicateRowCarriesGroupedPrimitives);
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
            run("Terrace split fails for invalid parts", HouseNumberClickRiskRegressionTests::testTerraceSplitFailsForInvalidParts);
            run("Terrace split result order is deterministic", HouseNumberClickRiskRegressionTests::testTerraceSplitOrderIsDeterministic);
            run("Scan limit defaults are used when unset", HouseNumberClickRiskRegressionTests::testScanLimitDefaultsWhenUnset);
            run("Invalid scan limit preferences fall back to defaults", HouseNumberClickRiskRegressionTests::testInvalidScanLimitPreferencesFallBack);
            run("Central settings expose expected default values", HouseNumberClickRiskRegressionTests::testCentralSettingsDefaults);
            run("Central settings persist and restore values", HouseNumberClickRiskRegressionTests::testCentralSettingsRoundTrip);
            run("Overlay mode invalid value falls back to default", HouseNumberClickRiskRegressionTests::testOverlayModeInvalidFallback);
            run("Overlay legacy bool preference migrates to integer mode", HouseNumberClickRiskRegressionTests::testOverlayModeLegacyMigration);
            run("Duplicate click detection blocks true duplicates", HouseNumberClickRiskRegressionTests::testDuplicateClicksAreDetected);
            run("Duplicate click detection keeps rapid distinct clicks", HouseNumberClickRiskRegressionTests::testRapidDistinctClicksAreKept);
            run("Address tag removal command clears addr keys", HouseNumberClickRiskRegressionTests::testRemoveAddressTagsClearsAddrKeys);
            run("Ctrl+Right click shortcut removes address tags", HouseNumberClickRiskRegressionTests::testCtrlRightClickShortcutWiring);
            run("Rectangularize skips triangle split results", HouseNumberClickRiskRegressionTests::testRectangularizeCandidateGuard);
            run("Street mode blocks apply when postcode is not selected", HouseNumberClickRiskRegressionTests::testPostcodeSelectionGuard);
            run("Postcode overview source exposes three-state cycle", HouseNumberClickRiskRegressionTests::testPostcodeOverviewThreeStateCycleWiring);
            run("Postcode overview cache and invalidation hooks exist", HouseNumberClickRiskRegressionTests::testPostcodeOverviewCacheInvalidationWiring);
            run("Layer loss pauses dialog instead of closing it", HouseNumberClickRiskRegressionTests::testLayerLossPausesDialogInsteadOfClosing);
            run("Layer recovery resumes paused dialog", HouseNumberClickRiskRegressionTests::testLayerRecoveryResumesPausedDialog);
            run("Dataset replacement reloads visible dialog while enabled", HouseNumberClickRiskRegressionTests::testDatasetReplacementReloadsVisibleDialogWhileEnabled);
            run("Layer-loss cleanup removes all plugin overlays", HouseNumberClickRiskRegressionTests::testLayerLossCleanupRemovesAllPluginOverlays);
            run("Postcode color mapping is deterministic", HouseNumberClickRiskRegressionTests::testPostcodeColorMappingIsDeterministic);
            run("Postcode legend uses top-5 deterministic ordering", HouseNumberClickRiskRegressionTests::testPostcodeLegendTopFiveOrdering);
            run("Postcode schematic clustering filters isolated and tiny groups", HouseNumberClickRiskRegressionTests::testPostcodeSchematicClusterFilteringRules);
            run("Postcode schematic clustering keeps 500m boundary neighbors", HouseNumberClickRiskRegressionTests::testPostcodeSchematicBoundaryDistanceInclusion);
            run("Street navigation order matches street-count sorting", HouseNumberClickRiskRegressionTests::testStreetNavigationOrderMatchesStreetCountsSorting);
            run("Street list selection resets like arrow navigation", HouseNumberClickRiskRegressionTests::testStreetNavigationClearsPostcodeAndHouseNumber);
            run("Street grouping bridges endpoint-to-segment gaps", HouseNumberClickRiskRegressionTests::testStreetGroupingBridgesEndpointToSegmentGaps);
            run("Street grouping merges collinear components after raw split", HouseNumberClickRiskRegressionTests::testStreetGroupingMergesCollinearComponentsAfterRawSplit);
            run("Selected street option keeps full merged local chain", HouseNumberClickRiskRegressionTests::testSelectedStreetOptionKeepsFullMergedLocalChain);
            run("Directly connected driveway is highlighted as secondary way", HouseNumberClickRiskRegressionTests::testDirectlyConnectedDrivewayIsHighlighted);
            run("Parking aisle is excluded from secondary driveway highlight", HouseNumberClickRiskRegressionTests::testParkingAisleIsNotHighlightedAsDriveway);
            run("Service way without driveway value is excluded from secondary highlight", HouseNumberClickRiskRegressionTests::testServiceWithoutDrivewayIsNotHighlighted);
            run("Non-direct driveway is not highlighted", HouseNumberClickRiskRegressionTests::testIndirectDrivewayIsNotHighlighted);
            run("Street grouping keeps distant same-name roads separated", HouseNumberClickRiskRegressionTests::testStreetGroupingKeepsDistantSameNameRoadsSeparated);
            run("Street grouping keeps parallel nearby roads separated", HouseNumberClickRiskRegressionTests::testStreetGroupingKeepsParallelNearbyRoadsSeparated);
            run("Street zoom fallback collects only usable named highway ways", HouseNumberClickRiskRegressionTests::testStreetZoomFallbackWayMatching);
            run("Street-table selection respects auto-zoom option", HouseNumberClickRiskRegressionTests::testStreetTableSelectionRespectsAutoZoomOption);
            run("Sidebar dialog labels and hints wiring exist", HouseNumberClickRiskRegressionTests::testStreetCountDialogTitleAndDimensions);
            run("Dialog bounds persistence and off-screen fallback wiring exist", HouseNumberClickRiskRegressionTests::testDialogBoundsPersistenceWiring);
            run("Main dialog supports collapsible advanced sections", HouseNumberClickRiskRegressionTests::testMainDialogCollapsibleAdvancedSectionsWiring);
            run("Main dialog groups split sections in one horizontal row", HouseNumberClickRiskRegressionTests::testMainDialogSplitSectionsHorizontalRowWiring);
            run("Auto-zoom scope toggle wiring exists", HouseNumberClickRiskRegressionTests::testAutoZoomScopeToggleWiring);
            run("Sidebar ToggleDialog architecture wiring exists", HouseNumberClickRiskRegressionTests::testSidebarToggleDialogArchitectureWiring);
            run("Street counts duplicate marker applies conditional city rule", HouseNumberClickRiskRegressionTests::testStreetHouseNumberCountCollectorConditionalCityRule);
            run("Building overview collector filters tiny buildings and keeps addressed state", HouseNumberClickRiskRegressionTests::testBuildingOverviewCollectorFilteringAndClassification);
            run("Building overview duplicate detection applies conditional city rule", HouseNumberClickRiskRegressionTests::testBuildingOverviewCollectorConditionalCityRule);
            run("Building overview duplicate detection ignores relation/outer self-duplicates", HouseNumberClickRiskRegressionTests::testBuildingOverviewCollectorIgnoresRelationOuterSelfDuplicate);
            run("Standalone address nodes are included in street counts", HouseNumberClickRiskRegressionTests::testStreetCountsIncludeStandaloneAddressNode);
            run("Entrance address nodes are included in overlay", HouseNumberClickRiskRegressionTests::testOverlayIncludesEntranceAddressNode);
            run("Address node inside building marks building as indirectly addressed", HouseNumberClickRiskRegressionTests::testBuildingOverviewMarksIndirectBuildingAddress);
            run("Co-located building and internal node are not treated as hard duplicates", HouseNumberClickRiskRegressionTests::testCoLocatedBuildingAndNodeAreNotHardDuplicates);
            run("Distinct address carriers with same key remain hard duplicates", HouseNumberClickRiskRegressionTests::testDistinctAddressCarriersRemainHardDuplicates);
            run("Address entry collector uses spatial index for node-building association", HouseNumberClickRiskRegressionTests::testAddressEntryCollectorSpatialIndexWiring);
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
        building.put("addr:city", " Sampletown ");
        building.put("addr:country", " DE ");
        building.put("addr:housenumber", " 77b ");
        building.put("building", " garage ");

        AddressReadbackService.AddressReadbackResult result = service.readFromBuilding(building, "house");
        assertEquals("Example Street", result.getStreet(), "street should be trimmed from building tag");
        assertEquals("12345", result.getPostcode(), "postcode should be trimmed from building tag");
        assertEquals("Sampletown", result.getCity(), "city should be trimmed from building tag");
        assertEquals("DE", result.getCountry(), "country should be trimmed from building tag");
        assertEquals("garage", result.getBuildingType(), "building type should be read from clicked building tag");
        assertEquals("77b", result.getHouseNumber(), "house number should be trimmed from building tag");
        assertEquals("address-tags", result.getSource(), "source should mark address-tag readback");

        building.remove("building");
        AddressReadbackService.AddressReadbackResult fallbackResult = service.readFromBuilding(building, "house");
        assertEquals("house", fallbackResult.getBuildingType(),
                "building type should fall back to current value only when building tag is missing");

        building.put("building", "   ");
        AddressReadbackService.AddressReadbackResult blankTagFallbackResult = service.readFromBuilding(building, "residential");
        assertEquals("residential", blankTagFallbackResult.getBuildingType(),
                "building type should fall back to current value when building tag is blank");
    }

    private static void testAddressReadbackStreetFallback() {
        AddressReadbackService service = new AddressReadbackService();
        AddressReadbackService.AddressReadbackResult result = service.readFromStreetFallback(" Example Avenue ", " 99999 ", "residential");

        assertEquals("Example Avenue", result.getStreet(), "street fallback should be trimmed");
        assertEquals("", result.getPostcode(), "street fallback should not provide a postcode override");
        assertEquals("", result.getCity(), "street fallback should not provide a city override");
        assertEquals("", result.getCountry(), "street fallback should not provide a country override");
        assertEquals("", result.getBuildingType(), "street fallback should not provide a building-type override");
        assertEquals("", result.getHouseNumber(), "street fallback should not provide a house-number override");
        assertEquals("street-fallback", result.getSource(), "source should mark street fallback");
        assertEquals(null, service.readFromStreetFallback("   ", "99999", "residential"), "empty fallback street should produce no readback result");
    }

    private static void testStreetFallbackClickReadbackInitializesHouseNumber() throws Exception {
        String source = readPluginSource("ClickHandlerService.java");
        assertTrue(source.contains("DEFAULT_STREET_PICKED_HOUSE_NUMBER = \"1\""),
                "street fallback click readback should define default house number 1");
        assertTrue(source.contains("streetPickedHouseNumber = DEFAULT_STREET_PICKED_HOUSE_NUMBER"),
                "street fallback click readback should apply house number 1 when no house number is available");
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
        building.put("addr:city", "Old City");
        building.put("addr:country", "DE");
        building.put("addr:housenumber", "12");
        building.put("building", "residential");

        AddressConflictService.ConflictAnalysis analysis = service.analyze(building, "New Street", "54321", "New City", "AT", "34", "house");
        assertTrue(analysis.hasConflict(), "different street/postcode should trigger overwrite conflict");
        assertEquals("Old Street", analysis.getOverwrittenStreet(), "existing street should be used as overwritten street");
        assertEquals("12345", analysis.getOverwrittenPostcode(), "existing postcode should be used as overwritten postcode");
        assertEquals("Old City", analysis.getOverwrittenCity(), "existing city should be used as overwritten city");
        assertEquals("DE", analysis.getOverwrittenCountry(), "existing country should be used as overwritten country");
        assertEquals(6, analysis.getDifferingFields().size(), "street, postcode, city, country, housenumber and building diffs should be listed");
        assertEquals("addr:street", analysis.getDifferingFields().get(0).getKey(), "street diff should appear first");
        assertEquals("addr:postcode", analysis.getDifferingFields().get(1).getKey(), "postcode diff should appear second");
        assertEquals("addr:city", analysis.getDifferingFields().get(2).getKey(), "city diff should appear third");
        assertEquals("addr:country", analysis.getDifferingFields().get(3).getKey(), "country diff should appear fourth");
        assertEquals("addr:housenumber", analysis.getDifferingFields().get(4).getKey(), "housenumber diff should appear fifth");
        assertEquals("building", analysis.getDifferingFields().get(5).getKey(), "building diff should appear sixth");
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

    private static void testCountryDetectionServiceConfidentDetection() throws Exception {
        CountryDetectionService service = new CountryDetectionService();

        DataSet deDataSet = new DataSet();
        Way deBuildingA = createClosedBuilding("Example Street", "1");
        deBuildingA.put("addr:country", "de");
        Way deBuildingB = createClosedBuilding("Example Street", "2");
        deBuildingB.put("addr:country", " DE ");
        Way deBuildingC = createClosedBuilding("Example Street", "3");
        deBuildingC.put("addr:country", "Deutschland");
        deDataSet.addPrimitiveRecursive(deBuildingA);
        deDataSet.addPrimitiveRecursive(deBuildingB);
        deDataSet.addPrimitiveRecursive(deBuildingC);
        assertEquals("DE", service.detectConfidentCountry(deDataSet),
                "consistent country values should be normalized and detected as confident country");

        DataSet gbDataSet = new DataSet();
        Way gbBuildingA = createClosedBuilding("Example Road", "1");
        gbBuildingA.put("addr:country", "GB");
        Way gbBuildingB = createClosedBuilding("Example Road", "2");
        gbBuildingB.put("addr:country", "gb");
        Way gbBuildingC = createClosedBuilding("Example Road", "3");
        gbBuildingC.put("addr:country", "UK");
        gbDataSet.addPrimitiveRecursive(gbBuildingA);
        gbDataSet.addPrimitiveRecursive(gbBuildingB);
        gbDataSet.addPrimitiveRecursive(gbBuildingC);
        assertEquals("GB", service.detectConfidentCountry(gbDataSet),
                "GB, gb and UK should normalize to GB");

        DataSet atDataSet = new DataSet();
        Way atBuildingA = createClosedBuilding("Example Gasse", "1");
        atBuildingA.put("addr:country", "Austria");
        Way atBuildingB = createClosedBuilding("Example Gasse", "2");
        atBuildingB.put("addr:country", "Oesterreich");
        Way atBuildingC = createClosedBuilding("Example Gasse", "3");
        atBuildingC.put("addr:country", "AT");
        atDataSet.addPrimitiveRecursive(atBuildingA);
        atDataSet.addPrimitiveRecursive(atBuildingB);
        atDataSet.addPrimitiveRecursive(atBuildingC);
        assertEquals("AT", service.detectConfidentCountry(atDataSet),
                "known country names should map to ISO alpha-2 when unambiguous");

        DataSet unknownNameDataSet = new DataSet();
        Way unknownBuildingA = createClosedBuilding("Unknown Street", "1");
        unknownBuildingA.put("addr:country", "Neverland");
        Way unknownBuildingB = createClosedBuilding("Unknown Street", "2");
        unknownBuildingB.put("addr:country", "Neverland");
        Way unknownBuildingC = createClosedBuilding("Unknown Street", "3");
        unknownBuildingC.put("addr:country", "Neverland");
        unknownNameDataSet.addPrimitiveRecursive(unknownBuildingA);
        unknownNameDataSet.addPrimitiveRecursive(unknownBuildingB);
        unknownNameDataSet.addPrimitiveRecursive(unknownBuildingC);
        assertEquals("", service.detectConfidentCountry(unknownNameDataSet),
                "unknown long country names must not be returned as raw addr:country values");

        DataSet mixedCountryDataSet = new DataSet();
        Way deBuilding = createClosedBuilding("Mixed Street", "1");
        deBuilding.put("addr:country", "DE");
        Way atBuilding = createClosedBuilding("Mixed Street", "2");
        atBuilding.put("addr:country", "AT");
        mixedCountryDataSet.addPrimitiveRecursive(deBuilding);
        mixedCountryDataSet.addPrimitiveRecursive(atBuilding);
        assertEquals("", service.detectConfidentCountry(mixedCountryDataSet),
                "ambiguous country values must not be auto-detected");

        List<String> likelyCountries = service.collectLikelyCountryCodes(atDataSet, 10);
        assertEquals(List.of("AT"), likelyCountries,
                "likely-country list should contain normalized ISO alpha-2 codes only");

        DataSet rankedCountriesDataSet = new DataSet();
        Way rankedDeA = createClosedBuilding("Ranked Street", "1");
        rankedDeA.put("addr:country", "DE");
        Way rankedDeB = createClosedBuilding("Ranked Street", "2");
        rankedDeB.put("addr:country", "Germany");
        Way rankedFr = createClosedBuilding("Ranked Street", "3");
        rankedFr.put("addr:country", "FR");
        Way rankedAt = createClosedBuilding("Ranked Street", "4");
        rankedAt.put("addr:country", "AT");
        rankedCountriesDataSet.addPrimitiveRecursive(rankedDeA);
        rankedCountriesDataSet.addPrimitiveRecursive(rankedDeB);
        rankedCountriesDataSet.addPrimitiveRecursive(rankedFr);
        rankedCountriesDataSet.addPrimitiveRecursive(rankedAt);
        assertEquals(List.of("DE", "AT", "FR"), service.collectLikelyCountryCodes(rankedCountriesDataSet, 10),
                "likely-country list should be ranked by frequency then code");

        DataSet boundaryFallbackDataSet = new DataSet();
        Relation deBoundary = new Relation();
        deBoundary.put("boundary", "administrative");
        deBoundary.put("admin_level", "2");
        deBoundary.put("ISO3166-1:alpha2", "DE");
        boundaryFallbackDataSet.addPrimitive(deBoundary);
        assertEquals("DE", service.detectConfidentCountry(boundaryFallbackDataSet),
                "national admin boundary ISO tag should provide fallback country detection");

        DataSet invalidBoundaryCountryDataSet = new DataSet();
        Relation invalidBoundary = new Relation();
        invalidBoundary.put("boundary", "administrative");
        invalidBoundary.put("admin_level", "2");
        invalidBoundary.put("country", "Neverland");
        invalidBoundaryCountryDataSet.addPrimitive(invalidBoundary);
        assertEquals("", service.detectConfidentCountry(invalidBoundaryCountryDataSet),
                "unknown long boundary country tags must not be used as raw detected country");

        String actionSource = readPluginSource("HouseNumberClickAction.java");
        assertTrue(actionSource.contains("detectConfidentCountry"),
                "main action should use country detection before opening the dialog");
        assertTrue(actionSource.contains("collectLikelyCountryCodes"),
                "main action should collect likely country codes for constrained dialog selection");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        assertTrue(dialogSource.contains("setSelectedCountry(firstNonEmpty(rememberedCountry, detectedCountry))"),
                "dialog country selection should fall back to detected country when remembered value is empty");
        assertTrue(dialogSource.contains("normalizeCountryCode"),
                "dialog country selection should enforce ISO alpha-2 values");
        assertTrue(dialogSource.contains("CountryDetectionService.normalizeCountryCode"),
                "dialog country normalization should reuse CountryDetectionService logic");
    }

    private static void testAddressConflictEdgeCases() {
        AddressConflictService service = new AddressConflictService();

        Way buildingWithoutStreet = new Way();
        buildingWithoutStreet.put("addr:postcode", "12345");
        AddressConflictService.ConflictAnalysis noStreetConflict = service.analyze(buildingWithoutStreet, "Any Street", "", "", "", "", "");
        assertFalse(noStreetConflict.hasConflict(), "missing existing street should not trigger street conflict");
        assertEquals("Any Street", noStreetConflict.getOverwrittenStreet(), "fallback overwritten street should use proposed street");
        assertEquals("12345", noStreetConflict.getOverwrittenPostcode(), "existing postcode should remain overwritten postcode fallback");

        Way buildingWithOnlyHouseNumber = new Way();
        buildingWithOnlyHouseNumber.put("addr:housenumber", "10");
        AddressConflictService.ConflictAnalysis onlyHouseDiff = service.analyze(buildingWithOnlyHouseNumber, "", "", "", "", "11", "");
        assertFalse(onlyHouseDiff.hasConflict(), "house number difference alone should not trigger conflict dialog");
        assertEquals(1, onlyHouseDiff.getDifferingFields().size(), "house number difference should still be listed");
        assertEquals("addr:housenumber", onlyHouseDiff.getDifferingFields().get(0).getKey(), "listed diff should be housenumber");

        Way identical = new Way();
        identical.put("addr:street", "Same");
        identical.put("addr:postcode", "11111");
        identical.put("addr:housenumber", "3");
        AddressConflictService.ConflictAnalysis identicalAnalysis = service.analyze(identical, "Same", "11111", "", "", "3", "");
        assertFalse(identicalAnalysis.hasConflict(), "identical values should not trigger conflict");
        assertEquals(0, identicalAnalysis.getDifferingFields().size(), "identical values should produce no differing fields");

        AddressConflictService.ConflictAnalysis missingBuilding = service.analyze(null, "Street", "77777", "City", "DE", "", "");
        assertEquals("77777", missingBuilding.getOverwrittenPostcode(),
                "missing building should expose proposed postcode as overwrite context fallback");
        assertEquals("City", missingBuilding.getOverwrittenCity(),
                "missing building should expose proposed city as overwrite context fallback");
        assertEquals("DE", missingBuilding.getOverwrittenCountry(),
                "missing building should expose proposed country as overwrite context fallback");
    }

    private static void testEmptyCityCountryDoNotTriggerOverwriteWarning() throws Exception {
        AddressConflictService service = new AddressConflictService();

        Way building = new Way();
        building.put("addr:city", "Old City");
        building.put("addr:country", "DE");

        AddressConflictService.ConflictAnalysis analysis =
                service.analyze(building, "", "", "", "", "", "");
        assertFalse(analysis.hasConflict(),
                "empty city/country proposals must not trigger overwrite warning conflicts");
        assertFalse(containsConflictKey(analysis, "addr:city"),
                "empty city proposal must not produce addr:city differing field");
        assertFalse(containsConflictKey(analysis, "addr:country"),
                "empty country proposal must not produce addr:country differing field");

        String applierSource = readPluginSource("BuildingTagApplier.java");
        assertTrue(applierSource.contains("if (!normalizedCity.isEmpty())"),
                "city should only be written when a non-empty value is provided");
        assertTrue(applierSource.contains("if (!normalizedCountry.isEmpty())"),
                "country should only be written when a non-empty value is provided");
    }

    private static boolean containsConflictKey(AddressConflictService.ConflictAnalysis analysis, String key) {
        if (analysis == null || key == null) {
            return false;
        }
        for (AddressConflictService.ConflictField field : analysis.getDifferingFields()) {
            if (field != null && key.equals(field.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static void testAddressConflictBuildingTypeYesOverwriteIgnored() {
        AddressConflictService service = new AddressConflictService();

        Way building = new Way();
        building.put("building", "yes");
        AddressConflictService.ConflictAnalysis ignoredYesOverwrite =
                service.analyze(building, "", "", "", "", "", "house");
        assertFalse(ignoredYesOverwrite.hasConflict(), "overwriting building=yes should not trigger warning");
        assertEquals(0, ignoredYesOverwrite.getDifferingFields().size(), "building=yes overwrite should not add dialog rows");

        building.put("building", "residential");
        AddressConflictService.ConflictAnalysis realTypeOverwrite =
                service.analyze(building, "", "", "", "", "", "house");
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
        assertTrue(source.contains("Do not warn again for city:"),
                "overwrite dialog should offer a city-specific suppression option");
        assertTrue(source.contains("Do not warn again for country:"),
                "overwrite dialog should offer a country-specific suppression option");
        assertTrue(source.contains("shouldShowOverwriteWarning"),
                "warning decision should be derived from field-specific suppression checks");
        assertTrue(source.contains("isCityWarningSuppressed"),
                "warning decision should include city-specific suppression checks");
        assertTrue(source.contains("isCountryWarningSuppressed"),
                "warning decision should include country-specific suppression checks");
    }

    private static void testConflictDialogModelBuilderMapping() {
        AddressConflictService service = new AddressConflictService();
        ConflictDialogModelBuilder builder = new ConflictDialogModelBuilder();

        Way building = new Way();
        building.put("addr:street", "Old Street");
        building.put("addr:postcode", "12345");
        building.put("addr:city", "Old City");
        building.put("addr:country", "DE");
        building.put("addr:housenumber", "9");

        AddressConflictService.ConflictAnalysis analysis = service.analyze(building, "New Street", "54321", "New City", "AT", "10", "");
        ConflictDialogModelBuilder.DialogModel model = builder.build(analysis, value -> "[" + value + "]");

        assertEquals(5, model.getRows().size(), "all differing fields should be present in dialog model");
        assertEquals("addr:street", model.getRows().get(0).getField(), "street row should be first");
        assertEquals("addr:postcode", model.getRows().get(1).getField(), "postcode row should be second");
        assertEquals("addr:city", model.getRows().get(2).getField(), "city row should be third");
        assertEquals("addr:country", model.getRows().get(3).getField(), "country row should be fourth");
        assertEquals("addr:housenumber", model.getRows().get(4).getField(), "housenumber row should be fifth");
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
        dataSet.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1b"));

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        StreetNameCollector.StreetIndex streetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        StreetOption selectedStreet = resolveStreetOptionForBaseName(streetIndex, "Example Street");
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, selectedStreet, streetIndex);
        String oddValue = firstNonEmptyOddValue(rows);
        assertEquals("1", oddValue, "mixed variants without exact duplicates should show only the base number");
    }

    private static void testOverviewDuplicateMarkerTracksExactRepeats() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "1a"));

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        StreetNameCollector.StreetIndex streetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        StreetOption selectedStreet = resolveStreetOptionForBaseName(streetIndex, "Example Street");
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, selectedStreet, streetIndex);
        String oddValue = firstNonEmptyOddValue(rows);
        assertEquals("1 (dup)", oddValue, "exact duplicate values should show compact duplicate marker");
    }

    private static void testOverviewDuplicateMarkerIsCityAgnostic() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));

        Way first = createClosedBuilding("Example Street", "1");
        first.put("addr:city", "Alpha City");
        Way second = createClosedBuilding("Example Street", "1");
        second.put("addr:city", "Beta City");
        dataSet.addPrimitiveRecursive(first);
        dataSet.addPrimitiveRecursive(second);

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        StreetNameCollector.StreetIndex streetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        StreetOption selectedStreet = resolveStreetOptionForBaseName(streetIndex, "Example Street");
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, selectedStreet, streetIndex);
        String oddValue = firstNonEmptyOddValue(rows);
        assertEquals("1 (dup)", oddValue,
                "selected-street duplicate marker should stay city-agnostic and still mark exact house-number duplicates");
    }

    private static void testOverviewDuplicateRowCarriesGroupedPrimitives() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2a"));
        dataSet.addPrimitiveRecursive(createClosedBuilding("Example Street", "2b"));

        HouseNumberOverviewCollector collector = new HouseNumberOverviewCollector();
        StreetNameCollector.StreetIndex streetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        StreetOption selectedStreet = resolveStreetOptionForBaseName(streetIndex, "Example Street");
        List<HouseNumberOverviewRow> rows = collector.collectRows(dataSet, selectedStreet, streetIndex);
        HouseNumberOverviewRow row = firstRowWithOddValuePrefix(rows, "2");
        assertTrue(row != null, "expected overview row for base number 2");
        assertTrue(row.isEvenDuplicate(), "even row for base 2 should be marked as duplicate");
        assertEquals(5, row.getEvenPrimitives().size(), "duplicate row should keep full base-number primitive grouping");
        assertEquals(4, row.getEvenDuplicatePrimitives().size(),
                "duplicate row should expose exact-duplicate primitives for multi-object duplicate zoom");
    }

    private static void testAddressSelectionNormalization() {
        StreetModeController.AddressSelection selection =
                new StreetModeController.AddressSelection(
                        "  Main Street  ",
                        " Main Street ",
                        " cluster-1 ",
                        " 12345 ",
                        "  Sample City  ",
                        "  DE  ",
                        " house ",
                        " 12a ",
                        99
                );

        assertEquals("Main Street", selection.getStreetName(), "street should be trimmed");
        assertEquals("Main Street", selection.getDisplayStreetName(), "display street should be trimmed");
        assertEquals("cluster-1", selection.getStreetClusterId(), "street cluster id should be trimmed");
        assertEquals("12345", selection.getPostcode(), "postcode should be trimmed");
        assertEquals("Sample City", selection.getCity(), "city should be trimmed");
        assertEquals("DE", selection.getCountry(), "country should be trimmed");
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

    private static StreetOption resolveStreetOptionForBaseName(StreetNameCollector.StreetIndex streetIndex, String baseStreetName) {
        StreetOption selectedStreet = streetIndex == null ? null : streetIndex.resolveForBaseStreetAndPrimitive(baseStreetName, null);
        if (selectedStreet == null) {
            throw new AssertionError("expected StreetOption for base street: " + baseStreetName);
        }
        return selectedStreet;
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

    private static void testCentralSettingsDefaults() {
        clearCentralSettingsPrefs();
        assertEquals(HouseNumberClickPreferences.OverlayMode.CLASSIC, HouseNumberClickPreferences.getOverlayMode(),
                "overlay mode should default to CLASSIC when unset");
        assertTrue(HouseNumberClickPreferences.SHOW_CONNECTION_LINES.get(),
                "connection-lines checkbox default should be true");
        assertTrue(HouseNumberClickPreferences.ZOOM_TO_SELECTED_STREET.get(),
                "auto-zoom checkbox default should be true");
        assertEquals(BuildingOverviewLayer.MissingField.POSTCODE, HouseNumberClickPreferences.getCompletenessMissingField(),
                "completeness missing-field default should be POSTCODE");
    }

    private static void testCentralSettingsRoundTrip() {
        clearCentralSettingsPrefs();

        HouseNumberClickPreferences.setOverlayMode(HouseNumberClickPreferences.OverlayMode.CLUSTERED);
        HouseNumberClickPreferences.SHOW_CONNECTION_LINES.put(false);
        HouseNumberClickPreferences.ZOOM_TO_SELECTED_STREET.put(false);
        HouseNumberClickPreferences.HOUSE_NUMBER_INCREMENT_STEP.put(-2);
        HouseNumberClickPreferences.setCompletenessMissingField(BuildingOverviewLayer.MissingField.ALL);

        assertEquals(HouseNumberClickPreferences.OverlayMode.CLUSTERED, HouseNumberClickPreferences.getOverlayMode(),
                "overlay mode should round-trip through IntegerProperty");
        assertFalse(HouseNumberClickPreferences.SHOW_CONNECTION_LINES.get(),
                "boolean settings should round-trip through BooleanProperty");
        assertFalse(HouseNumberClickPreferences.ZOOM_TO_SELECTED_STREET.get(),
                "second boolean setting should round-trip through BooleanProperty");
        assertEquals(-2, HouseNumberClickPreferences.HOUSE_NUMBER_INCREMENT_STEP.get(),
                "integer settings should round-trip through IntegerProperty");
        assertEquals(BuildingOverviewLayer.MissingField.ALL, HouseNumberClickPreferences.getCompletenessMissingField(),
                "completeness missing-field should persist and restore");
    }

    private static void testOverlayModeInvalidFallback() {
        clearCentralSettingsPrefs();
        HouseNumberClickPreferences.OVERLAY_MODE.put(99);
        assertEquals(HouseNumberClickPreferences.OverlayMode.CLASSIC, HouseNumberClickPreferences.getOverlayMode(),
                "invalid overlay mode values should fall back to CLASSIC");
    }

    private static void testOverlayModeLegacyMigration() {
        clearCentralSettingsPrefs();
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "showOverlay", "false");
        HouseNumberClickPreferences.OVERLAY_MODE_MIGRATION_DONE.put(false);

        assertEquals(HouseNumberClickPreferences.OverlayMode.OFF, HouseNumberClickPreferences.getOverlayMode(),
                "legacy showOverlay=false should migrate to integer overlay OFF mode");
        assertEquals(null, Config.getPref().get(HouseNumberClickPreferences.PREFIX + "showOverlay", null),
                "legacy showOverlay key should be removed after migration");
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

    private static void testCtrlRightClickShortcutWiring() throws Exception {
        String modeSource = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(modeSource.contains("Ctrl+right-click removes address tags"),
                "mode help text should advertise Ctrl+right-click address removal");
        assertTrue(modeSource.contains("handleCtrlRightClick(e, stats)"),
                "mouse release handling should dispatch plain Ctrl+right-click to dedicated handler");
        assertTrue(modeSource.contains("e.getButton() == MouseEvent.BUTTON3"),
                "ctrl-right-click removal should require explicit BUTTON3 to avoid ctrl-left regressions");

        String clickHandlerSource = readPluginSource("ClickHandlerService.java");
        assertTrue(clickHandlerSource.contains("handleCtrlRightClick"),
                "click handler should expose ctrl-right-click interaction flow");
        assertTrue(clickHandlerSource.contains("collectAddressTagsForRemoval"),
                "ctrl-right-click handling should preview addr:* tags before deletion");
        assertTrue(clickHandlerSource.contains("confirmAddressRemoval"),
                "ctrl-right-click handling should require explicit confirmation");
        assertTrue(clickHandlerSource.contains("BuildingTagApplier.removeAddressTags"),
                "ctrl-right-click handling should remove addr:* tags through BuildingTagApplier");
        assertTrue(clickHandlerSource.contains("remove-address-cancelled"),
                "ctrl-right-click flow should keep a dedicated cancelled outcome when user rejects removal");

        assertTrue(modeSource.contains("confirmAddressRemoval("),
                "street mode should implement address-removal confirmation dialog");

        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        assertTrue(dialogSource.contains("DIALOG_HEIGHT = 1000"),
                "main dialog should be 20px higher to fit extended help section");
        assertTrue(dialogSource.contains("Ctrl+Right click"),
                "help section should include Ctrl+Right click hint");
    }

    private static void testRemoveAddressTagsClearsAddrKeys() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuilding("Example Street", "12");
        building.put("addr:postcode", "12345");
        building.put("addr:city", "Exampletown");
        building.put("note", "keep");
        dataSet.addPrimitiveRecursive(building);

        int removed = BuildingTagApplier.removeAddressTags(building);
        assertEquals(4, removed, "removeAddressTags should report all removed addr:* tags");
        assertFalse(building.hasKey("addr:street"), "addr:street should be removed");
        assertFalse(building.hasKey("addr:housenumber"), "addr:housenumber should be removed");
        assertFalse(building.hasKey("addr:postcode"), "addr:postcode should be removed");
        assertFalse(building.hasKey("addr:city"), "addr:city should be removed");
        assertEquals("yes", building.get("building"), "building type must remain untouched");
        assertEquals("keep", building.get("note"), "non-address tags must remain untouched");
    }

    private static void testCtrlHasPriorityOverAltActivation() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");

        int altBranchIndex = source.indexOf("e.getKeyCode() == KeyEvent.VK_ALT");
        assertTrue(altBranchIndex >= 0, "street mode should handle Alt key presses");

        int ctrlGuardIndex = source.indexOf("if (e.isControlDown())", altBranchIndex);
        assertTrue(ctrlGuardIndex >= 0, "Alt branch should guard Ctrl-first priority");

        int splitActivationIndex = source.indexOf("controller.activateTemporarySplitModeFromAlt()", altBranchIndex);
        assertTrue(splitActivationIndex < 0, "Alt branch must not trigger controller split-mode activation anymore");

        int branchEnd = source.indexOf("if (id != KeyEvent.KEY_PRESSED", altBranchIndex);
        assertTrue(branchEnd > altBranchIndex, "Alt branch should end before generic shortcut branch");
        String altBranch = source.substring(altBranchIndex, branchEnd);
        assertFalse(altBranch.contains("isTextInputFocused()"),
                "Alt activation should not depend on text focus so it works immediately after start");
    }

    private static void testTemporaryAltSplitExitsOnAltRelease() throws Exception {
        String streetModeSource = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(streetModeSource.contains("e.getKeyCode() == KeyEvent.VK_ALT"),
                "without split map mode source, Alt handling must still exist in street mode");
        assertTrue(streetModeSource.contains("id == KeyEvent.KEY_RELEASED"),
                "Alt handling should react on key release to cancel inline split-drag state");
        assertTrue(streetModeSource.contains("clearSplitDragState()"),
                "Alt release should clear active inline split-drag state");
        assertTrue(streetModeSource.contains("mouseDragged"),
                "single-mapmode model should handle split drag updates in street mode directly");
    }

    private static void testAltDigitSetsTerracePartsShortcut() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(source.contains("resolveAltPartsShortcut"),
                "street mode should decode Alt+digit shortcuts for terrace part count");
        assertTrue(source.contains("controller.setConfiguredTerraceParts"),
                "Alt+digit shortcut must set terrace parts in controller as single source of truth");
        assertTrue(source.contains("Row-house parts set to {0}."),
                "Alt+digit shortcut should provide status feedback for configured parts");
    }

    private static void testAltDigitShortcutRequiresPlainAlt() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(source.contains("&& !e.isShiftDown())"),
                "Alt+digit shortcut must require plain Alt and ignore Alt+Shift combinations");
    }

    private static void testCtrlShiftClickIsPassedThrough() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");
        int ctrlBranch = source.indexOf("if (e.isControlDown()) {");
        assertTrue(ctrlBranch >= 0, "mouseReleased should branch on Ctrl click state");
        int shiftGuard = source.indexOf("if (e.isShiftDown())", ctrlBranch);
        assertTrue(shiftGuard > ctrlBranch, "Ctrl click branch should guard Shift combinations");
        int readbackCall = source.indexOf("handleSecondaryClick(e, stats);", ctrlBranch);
        assertTrue(readbackCall > shiftGuard,
                "secondary readback handling should happen only after Shift passthrough guard");
    }

    private static void testPrimaryApplyRestoresMapFocusForUndoShortcuts() throws Exception {
        String source = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(source.contains("requestMapFocusForUndoShortcuts();"),
                "successful primary apply should restore map focus so Ctrl+Z reaches JOSM undo");
        assertTrue(source.contains("map.mapView.requestFocusInWindow();"),
                "map focus restore should request focus on mapView");
    }

    private static void testOverlaySelfHealInteractionHooks() throws Exception {
        String modeSource = readPluginSource("HouseNumberClickStreetMapMode.java");
        String controllerSource = readPluginSource("StreetModeController.java");
        String overlayManagerSource = readPluginSource("OverlayManager.java");

        assertTrue(modeSource.contains("controller.ensureOverlayPresentIfEnabled();"),
                "street mode should trigger overlay self-heal checks during active interaction flow");
        assertTrue(modeSource.contains("public void mousePressed(MouseEvent e)"),
                "street mode should still own mouse press handling in single-mode architecture");
        assertTrue(modeSource.contains("public void mouseReleased(MouseEvent e)"),
                "street mode should still own mouse release handling in single-mode architecture");
        assertTrue(modeSource.contains("handleApplicationWindowGainedFocus"),
                "street mode should re-check overlay visibility when application focus returns");

        assertTrue(controllerSource.contains("void ensureOverlayPresentIfEnabled()"),
                "controller should expose a guard entrypoint for restoring missing/hidden overlays");
        assertTrue(controllerSource.contains("!overlayManager.isOverlayLayerMissingOrHidden()"),
                "overlay guard should short-circuit when overlay is already present and visible");

        assertTrue(overlayManagerSource.contains("boolean isOverlayLayerMissingOrHidden()"),
                "overlay manager should provide a missing/hidden check for the overlay layer");
        assertTrue(overlayManagerSource.contains("void showOverlayLayerIfPresent()"),
                "overlay manager should provide a visibility restore helper for existing overlay layer instances");
    }

    private static void testRowHousePartsDialogSyncDefersDocumentMutation() throws Exception {
        String source = readPluginSource("StreetSelectionDialog.java");
        assertTrue(source.contains("updateRowHousePartsFromMode"),
                "dialog should keep a controller-to-field sync path for row-house parts");
        assertTrue(source.contains("SwingUtilities.invokeLater") || source.contains("javax.swing.SwingUtilities.invokeLater"),
                "row-house parts sync should defer document writes to avoid nested document mutation");
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
        Path splitModePath = Path.of(
                "src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", "HouseNumberSplitMapMode.java"
        );
        assertFalse(Files.exists(splitModePath),
                "single-mapmode architecture must not contain HouseNumberSplitMapMode source anymore");

        String source = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(source.contains("createSplitCursor"),
                "split cursor handling must live in HouseNumberClickStreetMapMode");
        assertTrue(source.contains("/images/scalpel_cursor.png"),
                "street mode split cursor should use scalpel asset");
        assertTrue(source.contains("int hotspotX = 4;"),
                "street mode split cursor should keep hotspot aligned to the scalp tip");
    }

    private static void testSplitMapModeIsLineSplitOnly() throws Exception {
        Path splitModePath = Path.of(
                "src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", "HouseNumberSplitMapMode.java"
        );
        assertFalse(Files.exists(splitModePath),
                "single-mapmode architecture must not keep a dedicated split map mode source");

        String source = readPluginSource("HouseNumberClickStreetMapMode.java");
        assertTrue(source.contains("mouseDragged"),
                "line split interactions should be handled inline in street mode");
        assertTrue(source.contains("handleTerraceRightClick"),
                "row-house split should remain handled inline in street mode");
        assertFalse(source.contains("activateTemporarySplitModeFromAlt"),
                "street mode must not switch to a separate temporary split mode");
    }

    private static void testReferenceCacheInvalidationOnDataSourceChange() throws Exception {
        String source = readPluginSource("StreetModeController.java");
        assertTrue(source.contains("invalidateReferenceStreetStateForDataSourceChange();"),
                "data source change flow should invalidate reference street state before rescan");
        assertTrue(source.contains("referenceStreetCache.clear();"),
                "reference cache should be cleared on data source changes");
        assertTrue(source.contains("referenceStreetLoadsInProgress.clear();"),
                "in-progress reference loads should be reset on data source changes");
    }

    private static void testReferenceLoadGenerationGuardOnLifecycle() throws Exception {
        String source = readPluginSource("StreetModeController.java");
        assertTrue(source.contains("private final AtomicLong referenceLoadGeneration = new AtomicLong();"),
                "controller should maintain a generation token for async reference-load lifecycle isolation");
        assertTrue(source.contains("invalidateReferenceLoadGeneration();"),
                "lifecycle cleanup should invalidate reference-load generation");
        assertTrue(source.contains("if (!isReferenceLoadGenerationCurrent(loadGeneration)) {"),
                "async reference-load callbacks should drop stale generation results");
        assertTrue(source.contains("private void invalidateReferenceLoadGeneration()"),
                "controller should expose a dedicated generation invalidation helper");
        assertTrue(source.contains("private boolean isReferenceLoadGenerationCurrent(long generation)"),
                "controller should check generation freshness before applying async results");
        assertTrue(source.contains("if (!isActive()) {"),
                "reference display path should ignore late callbacks once street mode is inactive");
        assertTrue(source.contains("if (editDataSet == null) {"),
                "reference display path should abort when no active edit dataset exists");
    }

    private static void testStreetSelectionReResolutionOrder() throws Exception {
        String source = readPluginSource("StreetModeController.java");
        int resolveStart = source.indexOf("private StreetOption resolveCurrentStreetOption(DataSet dataSet, StreetNameCollector.StreetIndex streetIndex)");
        assertTrue(resolveStart >= 0, "controller should provide street re-resolution against current street index");
        int resolveEnd = source.indexOf("private StreetOption resolveStreetOptionFromUiValue", resolveStart);
        assertTrue(resolveEnd > resolveStart, "re-resolution method should end before UI string adapter");
        String resolveBody = source.substring(resolveStart, resolveEnd);

        int clusterMatch = resolveBody.indexOf("findByClusterId");
        int displayMatch = resolveBody.indexOf("findByDisplayStreetName");
        int baseMatch = resolveBody.indexOf("resolveForBaseStreetAndPrimitive");
        assertTrue(clusterMatch >= 0 && displayMatch > clusterMatch && baseMatch > displayMatch,
                "street re-resolution should follow cluster -> display -> base fallback order");
        assertTrue(resolveBody.contains("navigationService.setCurrentStreetOption(resolvedOption)"),
                "successful re-resolution should refresh stored navigation selection");

        int zoomCurrentStart = source.indexOf("void zoomToCurrentStreet() {");
        int zoomCurrentEnd = source.indexOf("void zoomToStreet(StreetOption streetOption)", zoomCurrentStart);
        assertTrue(zoomCurrentStart >= 0 && zoomCurrentEnd > zoomCurrentStart,
                "zoomToCurrentStreet should exist before StreetOption zoom overload");
        String zoomCurrentBody = source.substring(zoomCurrentStart, zoomCurrentEnd);
        assertFalse(zoomCurrentBody.contains("navigationService.getCurrentStreetOption()"),
                "zoomToCurrentStreet must not directly use possibly stale stored StreetOption");

        assertTrue(source.contains("findNearestWayForStreetOption"),
                "seed resolution should prefer nearest way within the selected street option cluster");
        assertTrue(source.contains("resolveDirectSeedWay(dataSet, baseStreetName, optionWays)"),
                "direct seed hint should be validated against ways of the selected street option");
    }

    private static void testReadbackStreetSelectionUsesSpatialDisambiguation() throws Exception {
        String controllerSource = readPluginSource("StreetModeController.java");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        String collectorSource = readPluginSource("StreetNameCollector.java");

        assertTrue(controllerSource.contains("StreetOption resolveStreetOptionForReadback(String streetName)"),
                "controller should expose dedicated readback street-option resolution");
        int nearestLookupIndex = controllerSource.indexOf("streetIndex.findNearestOptionForBaseStreetName");
        int seedLookupIndex = controllerSource.indexOf("streetIndex.findByWay(lastStreetSeedWayHint)");
        assertTrue(nearestLookupIndex >= 0 && seedLookupIndex > nearestLookupIndex,
                "readback disambiguation should prefer nearest-by-click lookup before any stale seed-way fallback");
        assertTrue(controllerSource.contains("findNearestOptionForBaseStreetName"),
                "readback disambiguation should use nearest-option lookup for ambiguous base names");
        assertTrue(controllerSource.contains("isExplicitDisplaySelection"),
                "readback disambiguation should not treat ambiguous base-name display labels as explicit selection");
        assertTrue(controllerSource.contains("lastStreetSeedWayHint = null;"),
                "controller should clear stale seed-way hints when the current readback click has no usable street way");

        assertTrue(collectorSource.contains("StreetOption findNearestOptionForBaseStreetName(String baseStreetName, LatLon referencePoint)"),
                "street index should support nearest-option lookup for base-name disambiguation");

        assertTrue(dialogSource.contains("resolveStreetOptionForReadback(streetName)"),
                "dialog should request controller-driven readback disambiguation before selecting a street item");
    }

    private static void testUndoQueueChangesTriggerVisualRescanRefresh() throws Exception {
        String source = readPluginSource("StreetModeController.java");
        assertTrue(source.contains("addCommandQueueListener(commandQueueListener)"),
                "controller should bind to undo/redo command queue changes while dialog workflow is active");
        assertTrue(source.contains("onCommandQueueChanged"),
                "controller should react to undo/redo queue changes");
        assertTrue(source.contains("overlayManager.invalidateOverlayDataCache();"),
                "undo/redo queue changes should invalidate overlay cache for immediate visual updates");
        assertTrue(source.contains("rescanPluginData();"),
                "undo/redo queue changes should trigger plugin rescan refresh");
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

    private static void testLayerLossCleanupRemovesAllPluginOverlays() throws Exception {
        String controllerSource = readPluginSource("StreetModeController.java");
        String overlayManagerSource = readPluginSource("OverlayManager.java");

        assertTrue(controllerSource.contains("overlayManager.removeAllPluginLayers();"),
                "main dialog close cleanup should remove all plugin layers through a single teardown entrypoint");
        assertTrue(overlayManagerSource.contains("void removeAllPluginLayers()"),
                "overlay manager should provide a centralized remove-all helper for plugin layers");
        assertTrue(overlayManagerSource.contains("removeOverlayLayer();"),
                "centralized cleanup should include house-number overlay removal");
        assertTrue(overlayManagerSource.contains("removeReferenceStreetLayer();"),
                "centralized cleanup should include reference-street overlay removal");
        assertTrue(overlayManagerSource.contains("removeBuildingOverviewLayer();"),
                "centralized cleanup should include completeness overview removal");
        assertTrue(overlayManagerSource.contains("removePostcodeOverviewLayer();"),
                "centralized cleanup should include postcode overview removal");
        assertTrue(overlayManagerSource.contains("removeDuplicateAddressOverviewLayer();"),
                "centralized cleanup should include duplicate overview removal");
    }

    private static void testLayerLossPausesDialogInsteadOfClosing() throws Exception {
        String actionSource = readPluginSource("HouseNumberClickAction.java");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(actionSource.contains("streetSelectionDialog.onEditLayerUnavailable();"),
                "layer-loss handling should still notify the dialog");
        assertTrue(dialogSource.contains("pausedBecauseNoEditLayer = true;"),
                "dialog should enter an explicit paused state when edit layer is unavailable");
        assertTrue(dialogSource.contains("streetModeController.deactivate();"),
                "dialog should pause interaction mode when edit layer is unavailable");
        assertTrue(dialogSource.contains("MODE_NO_EDIT_LAYER_TEXT"),
                "dialog should expose a dedicated no-edit-layer status text");
        assertTrue(!dialogSource.contains("if (dialog.isVisible()) {\n            closeDialog();\n            return;\n        }"),
                "layer-loss path must no longer hard-close a visible dialog");
    }

    private static void testLayerRecoveryResumesPausedDialog() throws Exception {
        String actionSource = readPluginSource("HouseNumberClickAction.java");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(actionSource.contains("streetSelectionDialog.onEditLayerAvailable();"),
                "action should notify dialog when an edit layer becomes available again");
        assertTrue(dialogSource.contains("void onEditLayerAvailable()"),
                "dialog should expose an explicit resume hook for layer recovery");
        assertTrue(dialogSource.contains("pausedBecauseNoEditLayer = false;"),
                "dialog resume hook should clear paused no-layer state");
        assertTrue(dialogSource.contains("if (dialog.isVisible() && isDataSetChanged(activeDataSet))"),
                "resume hook should detect dataset switches that happened while paused");
        assertTrue(dialogSource.contains("reloadDialogForActiveDataSet(activeDataSet);"),
                "resume hook should rebuild dialog options from the recovered active dataset when needed");
        assertTrue(dialogSource.contains("streetModeController.activate(buildCurrentSelection());"),
                "dialog resume hook should reactivate street mode from current dialog selection");
    }

    private static void testDatasetReplacementReloadsVisibleDialogWhileEnabled() throws Exception {
        String actionSource = readPluginSource("HouseNumberClickAction.java");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(actionSource.contains("private DataSet lastKnownEditDataSet;"),
                "action should track last known edit dataset identity across layer change callbacks");
        assertTrue(actionSource.contains("activeDataSet != lastKnownEditDataSet"),
                "action should detect edit-dataset replacement even when enabled state does not change");
        assertTrue(actionSource.contains("streetSelectionDialog.onActiveEditDataSetChanged(activeDataSet);"),
                "action should notify dialog about dataset replacement while staying enabled");

        assertTrue(dialogSource.contains("void onActiveEditDataSetChanged(DataSet activeDataSet)"),
                "dialog should expose a dedicated hook for active dataset replacement");
        assertTrue(dialogSource.contains("reloadDialogForActiveDataSet(activeDataSet);"),
                "dataset replacement hook should rebuild visible dialog content from the new dataset");
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

    private static void testPostcodeOverviewToggleEntrypointIsSafe() {
        StreetModeController controller = new StreetModeController();
        controller.togglePostcodeOverviewLayer();
        controller.togglePostcodeOverviewLayer();
        assertTrue(true, "postcode overview toggle entrypoint should complete without exceptions");
    }

    private static void testPostcodeColorMappingIsDeterministic() {
        java.awt.Color colorA = PostcodeOverviewLayer.resolveColorForPostcode("12345");
        java.awt.Color colorB = PostcodeOverviewLayer.resolveColorForPostcode("12345");
        java.awt.Color colorC = PostcodeOverviewLayer.resolveColorForPostcode(" 12345 ");
        java.awt.Color missing = PostcodeOverviewLayer.resolveColorForPostcode(" ");

        assertEquals(colorA, colorB, "same postcode should always map to same color");
        assertEquals(colorA, colorC, "postcode color mapping should ignore surrounding whitespace");
        assertTrue(missing.getRed() == 135 && missing.getGreen() == 135 && missing.getBlue() == 135,
                "empty postcode should map to same gray used for no-address-data in completeness layer");
    }

    private static void testPostcodeLegendTopFiveOrdering() {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        counts.put("", 9);
        counts.put("70000", 2);
        counts.put("60000", 4);
        counts.put("50000", 4);
        counts.put("30000", 7);
        counts.put("10000", 7);
        counts.put("20000", 6);

        List<String> top = PostcodeOverviewLayer.sortPostcodesForLegend(counts, 5);
        assertEquals(5, top.size(), "legend should return exactly top five postcodes when enough are present");
        assertEquals(List.of("10000", "30000", "20000", "50000", "60000"), top,
                "legend ordering should be count desc and deterministic by postcode for equal counts");
    }

    private static void testPostcodeOverviewThreeStateCycleWiring() throws Exception {
        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        String controllerSource = readPluginSource("StreetModeController.java");
        String overlaySource = readPluginSource("OverlayManager.java");

        assertTrue(overlaySource.contains("enum PostcodeOverviewMode"),
                "overlay manager should define a dedicated postcode overview mode enum");
        assertTrue(overlaySource.contains("OFF") && overlaySource.contains("BUILDINGS") && overlaySource.contains("SCHEMATIC"),
                "postcode mode enum should expose off, buildings, and schematic states");
        assertTrue(overlaySource.contains("void cyclePostcodeOverviewLayer()"),
                "overlay manager should expose postcode cycle entrypoint");
        assertTrue(controllerSource.contains("void cyclePostcodeOverviewLayer()"),
                "controller should expose postcode cycle method for the dialog");
        assertTrue(dialogSource.contains("streetModeController.cyclePostcodeOverviewLayer();"),
                "dialog postcode button should trigger cycle behavior");
        assertTrue(dialogSource.contains("SHOW_POSTCODE_SCHEMATIC_BUTTON_TEXT"),
                "dialog should provide a dedicated label for switching to schematic postcode areas");
    }

    private static void testPostcodeOverviewCacheInvalidationWiring() throws Exception {
        String postcodeLayerSource = readPluginSource("PostcodeOverviewLayer.java");
        String overlaySource = readPluginSource("OverlayManager.java");

        assertTrue(postcodeLayerSource.contains("void invalidateDataCache()"),
                "postcode overview layer should expose explicit cache invalidation");
        assertTrue(postcodeLayerSource.contains("refreshCacheIfNeeded(mapView);"),
                "postcode overview paint path should rely on cache refresh hook");
        assertTrue(postcodeLayerSource.contains("cachedSchematicRadiusBucket"),
                "schematic area cache should be keyed by a zoom-derived radius bucket");
        assertTrue(postcodeLayerSource.contains("cachedDataSize"),
                "postcode overview cache should track lightweight dataset size for invalidation");
        assertTrue(postcodeLayerSource.contains("computeDataSize(dataSet)"),
                "cache refresh should compute current dataset size before deciding rebuild");
        assertTrue(postcodeLayerSource.contains("getNodes().size() + dataSet.getWays().size() + dataSet.getRelations().size()"),
                "dataset invalidation heuristic should use node/way/relation counts");
        assertTrue(postcodeLayerSource.contains("dataChangeSequence"),
                "postcode overview cache should track lightweight dataset event changes");
        assertTrue(postcodeLayerSource.contains("addDataSetListener(dataSetListener)"),
                "postcode overview should subscribe to dataset change events for in-place edit invalidation");
        assertTrue(postcodeLayerSource.contains("currentDataChangeSequence != cachedDataChangeSequence"),
                "cache refresh should invalidate when local dataset event sequence changes");
        assertTrue(postcodeLayerSource.contains("boolean modeChanged = cachedOverviewMode != overviewMode"),
                "cache refresh should enforce mode-consistent caches");
        assertTrue(postcodeLayerSource.contains("if (cachedSchematicClustersByPostcode.isEmpty())"),
                "schematic cache refresh should skip area rebuild when no dense clusters exist");
        assertTrue(postcodeLayerSource.contains("rebuildBaseCache()"),
                "postcode overview should separate base data rebuilding from paint rendering");
        assertTrue(overlaySource.contains("postcodeOverviewLayer.invalidateDataCache();"),
                "overlay manager invalidation should also invalidate postcode overview caches");
    }

    private static void testPostcodeSchematicClusterFilteringRules() {
        List<LatLon> points = new ArrayList<>();
        LatLon base = new LatLon(52.0, 13.0);

        // Dense cluster (size 4) should survive.
        points.add(base);
        points.add(offsetMeters(base, 40.0, 0.0));
        points.add(offsetMeters(base, 0.0, 40.0));
        points.add(offsetMeters(base, 40.0, 40.0));

        // Isolated single building should be removed.
        points.add(offsetMeters(base, 1500.0, 0.0));

        // Small cluster (size 3) should be removed by min cluster size.
        LatLon smallBase = offsetMeters(base, 3000.0, 0.0);
        points.add(smallBase);
        points.add(offsetMeters(smallBase, 35.0, 0.0));
        points.add(offsetMeters(smallBase, 0.0, 35.0));

        List<List<LatLon>> clusters = PostcodeOverviewLayer.clusterDensePoints(points, 500.0, 4);
        assertEquals(1, clusters.size(), "only the dense cluster should remain after filtering");
        assertEquals(4, clusters.get(0).size(), "the retained dense cluster should keep all four members");
    }

    private static void testPostcodeSchematicBoundaryDistanceInclusion() {
        LatLon base = new LatLon(48.0, 11.0);
        LatLon boundary = offsetMeters(base, 499.0, 0.0);
        List<List<LatLon>> clusters = PostcodeOverviewLayer.clusterDensePoints(List.of(base, boundary), 500.0, 2);

        assertEquals(1, clusters.size(), "points within the 500m threshold should be treated as neighbors");
        assertEquals(2, clusters.get(0).size(), "boundary-distance cluster should include both points");
    }

    private static void testCompletenessLegendLabelsPresent() throws Exception {
        String source = readPluginSource("BuildingOverviewLayer.java");
        assertTrue(source.contains("All address keys present (incl. country)"),
                "completeness legend should contain the All-mode present label");
        assertTrue(source.contains("Address incomplete"),
                "completeness legend should contain the All-mode incomplete label");
        assertTrue(source.contains("hasMissingStreet || hasMissingPostcode || hasMissingHouseNumber || hasMissingCity || hasMissingCountry"),
                "all-mode completeness should treat missing city/country as incomplete");
        assertTrue(source.contains("Only country missing"),
                "completeness legend should expose dedicated only-country-missing category");
        assertTrue(source.contains("Postcode present"),
                "completeness legend should contain postcode-present label");
        assertTrue(source.contains("Postcode missing"),
                "completeness legend should contain postcode-missing label");
        assertTrue(source.contains("Street present"),
                "completeness legend should contain street-present label");
        assertTrue(source.contains("Street missing"),
                "completeness legend should contain street-missing label");
        assertTrue(source.contains("House number present"),
                "completeness legend should contain house-number-present label");
        assertTrue(source.contains("House number missing"),
                "completeness legend should contain house-number-missing label");
        assertTrue(source.contains("City present"),
                "completeness legend should contain city-present label");
        assertTrue(source.contains("City missing"),
                "completeness legend should contain city-missing label");
        assertTrue(source.contains("No Address Data"),
                "completeness legend should contain no-address-data label");

        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        assertTrue(dialogSource.contains("I18n.tr(\"Number\")"),
                "analysis completeness radios should use 'Number' label");
        assertTrue(dialogSource.contains("I18n.tr(\"All\")"),
                "analysis completeness radios should expose an 'All' option");
        assertTrue(dialogSource.contains("I18n.tr(\"City\")"),
                "analysis completeness radios should expose a 'City' option");

        int numberIndex = dialogSource.indexOf("radios.add(completenessHouseNumberRadioButton);");
        int streetIndex = dialogSource.indexOf("radios.add(completenessStreetRadioButton);");
        int postcodeIndex = dialogSource.indexOf("radios.add(completenessPostcodeRadioButton);");
        assertTrue(numberIndex >= 0 && streetIndex > numberIndex && postcodeIndex > streetIndex,
                "analysis completeness radios should be ordered Number, Street, Postcode");

        String duplicatesSource = readPluginSource("DuplicateAddressOverviewLayer.java");
        assertTrue(duplicatesSource.contains("Duplicate address"),
                "duplicate overview should contain duplicate-address label");
    }

    private static void testCompletenessLayerNameConsistency() throws Exception {
        String source = readPluginSource("BuildingOverviewLayer.java");
        assertTrue(source.contains("Completeness overview"),
                "completeness layer should use Completeness overview naming");
    }

    private static void testAnalysisSectionHasNoTextPostcodeLegend() throws Exception {
        String source = readPluginSource("StreetSelectionDialog.java");
        assertFalse(source.contains("Postcode legend: gray = no postcode, same color = same postcode"),
                "analysis section should no longer contain a text postcode legend");
    }

    private static void testOverviewLayerTogglesAreMutuallyExclusive() throws Exception {
        String source = readPluginSource("OverlayManager.java");
        int createBuildingIndex = source.indexOf("void createBuildingOverviewLayer(BuildingOverviewLayer.MissingField missingField)");
        int createPostcodeIndex = source.indexOf("void createPostcodeOverviewLayer()");
        int createDuplicateIndex = source.indexOf("void createDuplicateAddressOverviewLayer()");
        assertTrue(createBuildingIndex >= 0 && createPostcodeIndex >= 0 && createDuplicateIndex >= 0,
                "overlay manager should expose completeness, postcode, and duplicate overview creation paths");

        int createBuildingEnd = source.indexOf("void createPostcodeOverviewLayer()", createBuildingIndex);
        assertTrue(createBuildingEnd > createBuildingIndex,
                "completeness creation method should end before postcode creation method");
        String createBuildingBody = source.substring(createBuildingIndex, createBuildingEnd);
        assertTrue(createBuildingBody.contains("removePostcodeOverviewLayer();"),
                "enabling building overview should disable postcode overview");
        assertTrue(createBuildingBody.contains("removeDuplicateAddressOverviewLayer();"),
                "enabling building overview should disable duplicate overview");

        int createPostcodeEnd = source.indexOf("void toggleBuildingOverviewLayer()", createPostcodeIndex);
        assertTrue(createPostcodeEnd > createPostcodeIndex,
                "postcode creation method should end before building toggle method");
        String createPostcodeBody = source.substring(createPostcodeIndex, createPostcodeEnd);
        assertTrue(createPostcodeBody.contains("removeBuildingOverviewLayer();"),
                "enabling postcode overview should disable building overview");
        assertTrue(createPostcodeBody.contains("removeDuplicateAddressOverviewLayer();"),
                "enabling postcode overview should disable duplicate overview");

        int createDuplicateEnd = source.indexOf("void toggleDuplicateAddressOverviewLayer()", createDuplicateIndex);
        assertTrue(createDuplicateEnd > createDuplicateIndex,
                "duplicate creation method should end before duplicate toggle method");
        String createDuplicateBody = source.substring(createDuplicateIndex, createDuplicateEnd);
        assertTrue(createDuplicateBody.contains("removeBuildingOverviewLayer();"),
                "enabling duplicate overview should disable building overview");
        assertTrue(createDuplicateBody.contains("removePostcodeOverviewLayer();"),
                "enabling duplicate overview should disable postcode overview");
    }

    private static void testTableClickContinueHookIsSafe() {
        StreetModeController controller = new StreetModeController();
        controller.continueWorkingFromTableInteraction();
        assertTrue(true, "table click continue hook should complete without exceptions");
    }

    private static void testStreetTableClickSyncsMainDialogSelection() throws Exception {
        String controllerSource = readPluginSource("StreetModeController.java");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(controllerSource.contains("setStreetSelectionRequestListener"),
                "controller should expose a callback registration for main-dialog street sync");
        assertTrue(controllerSource.contains("requestMainDialogStreetSelection(selectedStreetOption);"),
                "street-table selection flow should request street sync in the main dialog");
        assertTrue(dialogSource.contains("setStreetSelectionRequestListener(this::applyStreetSelectionFromOverview)"),
                "main dialog should register for controller-driven street selection requests");
        assertTrue(dialogSource.contains("setStreetSelection(selectedStreetOption.getDisplayStreetName())"),
                "main dialog callback should apply selected street display name from the table click");
    }

    private static void testStreetTableSelectionRespectsAutoZoomOption() throws Exception {
        String controllerSource = readPluginSource("StreetModeController.java");
        assertTrue(controllerSource.contains("if (zoomToSelectedStreetEnabled)"),
                "street-table selection should only zoom when AutoZoom option is enabled");
        assertTrue(controllerSource.contains("zoomToStreet(selectedStreetOption);"),
                "street-table selection should still zoom to selected street when AutoZoom is enabled");
    }

    private static void testStreetCountDialogTitleAndDimensions() throws Exception {
        String countsToggleSource = readPluginSource("StreetCountsToggleDialog.java");
        String numbersToggleSource = readPluginSource("StreetNumbersToggleDialog.java");
        String countsPanelSource = readPluginSource("StreetCountsPanel.java");
        String numbersPanelSource = readPluginSource("StreetNumbersPanel.java");

        assertTrue(countsToggleSource.contains("I18n.tr(\"Street Counts (House Numbers)\")"),
                "street counts sidebar dialog should expose the Street Counts (House Numbers) title");
        assertTrue(numbersToggleSource.contains("I18n.tr(\"House Numbers (Base Numbers only)\")"),
                "house numbers sidebar dialog should expose the House Numbers (Base Numbers only) title");
        assertTrue(countsToggleSource.contains("\"housenumberclick\""),
                "street counts sidebar dialog should use the plugin icon key");
        assertTrue(numbersToggleSource.contains("\"housenumberclick\""),
                "house numbers sidebar dialog should use the plugin icon key");
        assertTrue(countsToggleSource.contains("createLayout(panel, false, Collections.emptyList())"),
                "street counts sidebar dialog should use ToggleDialog layout helper so header/title bar stays visible");
        assertTrue(numbersToggleSource.contains("createLayout(panel, false, Collections.emptyList())"),
                "house numbers sidebar dialog should use ToggleDialog layout helper so header/title bar stays visible");
        assertTrue(countsPanelSource.contains("HouseNumberClick is closed. Open the main dialog to use this view."),
                "street counts panel should render inactive hint text instead of blank content");
        assertTrue(numbersPanelSource.contains("HouseNumberClick is closed. Open the main dialog to use this view."),
                "street numbers panel should render inactive hint text instead of blank content");
        assertTrue(numbersPanelSource.contains("No street selected."),
                "street numbers panel should render no-street-selected hint text");
    }

    private static void testDialogBoundsPersistenceWiring() throws Exception {
        String preferencesSource = readPluginSource("HouseNumberClickPreferences.java");
        String mainDialogSource = readPluginSource("StreetSelectionDialog.java");
        String boundsManagerSource = readPluginSource("DialogWindowBoundsManager.java");

        assertTrue(preferencesSource.contains("DIALOG_BOUNDS_PREFIX"),
                "preferences should define a dedicated key prefix for persisted dialog bounds");
        assertTrue(preferencesSource.contains("putDialogBounds"),
                "preferences should expose dialog-bounds write helper");
        assertTrue(preferencesSource.contains("getDialogBounds"),
                "preferences should expose dialog-bounds read helper");

        assertTrue(mainDialogSource.contains("DialogWindowBoundsManager.applyStoredBoundsOrDefaults"),
                "main dialog should restore saved bounds with a default fallback path");
        assertTrue(mainDialogSource.contains("DialogWindowBoundsManager.saveDialogBounds"),
                "main dialog should persist bounds when closing");

        assertTrue(boundsManagerSource.contains("isVisibleOnAnyScreen"),
                "dialog bounds manager should validate restored bounds against currently available screens");
        assertTrue(boundsManagerSource.contains("defaultPositioner.run()"),
                "dialog bounds manager should fall back to default position when restored bounds are invalid/off-screen");
    }

    private static void testMainDialogCollapsibleAdvancedSectionsWiring() throws Exception {
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(dialogSource.contains("toggleAdvancedSectionsButton"),
                "main dialog should define a dedicated toggle button for advanced sections");
        assertTrue(dialogSource.contains("collapsibleSectionsPanel"),
                "main dialog should define a dedicated collapsible panel for advanced sections");
        assertTrue(dialogSource.contains("advancedSectionsExpanded"),
                "main dialog should track expanded/collapsed state for advanced sections");
        assertTrue(dialogSource.contains("ADVANCED_SECTIONS_COLLAPSED_TEXT"),
                "main dialog should expose a collapsed toggle label");
        assertTrue(dialogSource.contains("ADVANCED_SECTIONS_EXPANDED_TEXT"),
                "main dialog should expose an expanded toggle label");
        assertTrue(dialogSource.contains("rememberedAdvancedSectionsExpanded"),
                "main dialog should track persisted advanced-section expand/collapse state");
        assertTrue(dialogSource.contains("HouseNumberClickPreferences.ADVANCED_SECTIONS_EXPANDED"),
                "main dialog should load and save expanded-state through centralized preferences");
        assertTrue(dialogSource.contains("updateAdvancedSectionsVisibility()"),
                "main dialog should centralize collapsible section visibility updates");
    }

    private static void testMainDialogSplitSectionsHorizontalRowWiring() throws Exception {
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(dialogSource.contains("createSplitSectionsRow()"),
                "main dialog should define a helper that lays out split sections in one horizontal row");
        assertTrue(dialogSource.contains("collapsibleSectionsPanel.add(createSplitSectionsRow(), advancedSectionGbc);"),
                "advanced section assembly should add the split row container once");
        assertTrue(dialogSource.contains("panel.add(createLineSplitSection(), gbc);"),
                "split row container should include the Line Split section");
        assertTrue(dialogSource.contains("panel.add(createRowHousesSection(), gbc);"),
                "split row container should include the Row Houses section");
    }

    private static void testAutoZoomScopeToggleWiring() throws Exception {
        String dialogSource = readPluginSource("StreetSelectionDialog.java");
        String controllerSource = readPluginSource("StreetModeController.java");

        assertTrue(dialogSource.contains("new JCheckBox(I18n.tr(\"Numbered only\"))"),
                "display section should offer a compact numbered-only scope toggle next to auto-zoom");
        assertTrue(dialogSource.contains("notifyZoomToNumberedBuildingsOnlyChanged"),
                "dialog should propagate numbered-only scope changes into controller state");
        assertTrue(dialogSource.contains("streetModeController.zoomToCurrentStreet();"),
                "toggling numbered-only scope should immediately re-zoom when auto-zoom is enabled");
        assertTrue(dialogSource.contains("HouseNumberClickPreferences.ZOOM_TO_NUMBERED_BUILDINGS_ONLY.get()"),
                "numbered-only scope toggle should initialize from persistent preference defaults");

        assertTrue(controllerSource.contains("private boolean zoomToNumberedBuildingsOnlyEnabled = true;"),
                "controller should keep numbered-only auto-zoom scope enabled by default");
        assertTrue(controllerSource.contains("void setZoomToNumberedBuildingsOnlyEnabled(boolean enabled)"),
                "controller should expose a setter for numbered-only auto-zoom scope");
        assertTrue(controllerSource.contains("if (!zoomToNumberedBuildingsOnlyEnabled)"),
                "zoom path should branch between full-street and numbered-only bounds");
    }

    private static void testSidebarToggleDialogArchitectureWiring() throws Exception {
        String pluginSource = readPluginSource("HouseNumberClickPlugin.java");
        String actionSource = readPluginSource("HouseNumberClickAction.java");
        String controllerSource = readPluginSource("StreetModeController.java");
        String dialogSource = readPluginSource("StreetSelectionDialog.java");

        assertTrue(pluginSource.contains("mapFrameInitialized"),
                "plugin should react to map-frame lifecycle events to wire sidebar dialogs");
        assertTrue(actionSource.contains("onMapFrameInitialized"),
                "action should expose map-frame callback for sidebar registration");
        assertTrue(actionSource.contains("new HouseNumberClickSidebarController()"),
                "action should construct a dedicated sidebar controller");

        assertTrue(controllerSource.contains("attachSidebarDialogs"),
                "street controller should expose sidebar attachment hook");
        assertTrue(controllerSource.contains("refreshSidebarDialogs"),
                "street controller should refresh sidebar content from central state");
        assertTrue(controllerSource.contains("sidebarController.showMainDialogClosed()"),
                "closing the main dialog should keep sidebars and switch them to inactive hint state");

        assertTrue(dialogSource.contains("streetModeController.onMainDialogOpened();"),
                "main dialog show flow should notify controller that main dialog became active");
        assertTrue(!dialogSource.contains("Show overview panel (selected street)"),
                "main dialog should no longer contain selected-street overview visibility checkbox");
        assertTrue(!dialogSource.contains("Show all street counts"),
                "main dialog should no longer contain all-streets-count visibility checkbox");
    }

    private static void testStreetNavigationOrderMatchesStreetCountsSorting() {
        List<StreetHouseNumberCountRow> rows = List.of(
                new StreetHouseNumberCountRow("Zulu Street", 99, false),
                new StreetHouseNumberCountRow("Bravo Street", 5, false),
                new StreetHouseNumberCountRow("alpha street", 1, false),
                new StreetHouseNumberCountRow("  Charlie Street  ", 7, false)
        );

        List<StreetOption> ordered = StreetCountsPanel.buildStreetNavigationOrder(rows);
        assertEquals(4, ordered.size(), "all rows should be included in navigation order");
        assertEquals("alpha street", ordered.get(0).getDisplayStreetName(), "street list should start alphabetically");
        assertEquals("Bravo Street", ordered.get(1).getDisplayStreetName(), "street names should remain alphabetic regardless of count");
        assertEquals("Charlie Street", ordered.get(2).getDisplayStreetName(), "street names should be trimmed for navigation order");
        assertEquals("Zulu Street", ordered.get(3).getDisplayStreetName(), "highest counts should not override alphabetical order");
    }

    private static void testStreetNavigationClearsPostcodeAndHouseNumber() throws Exception {
        StreetSelectionDialog dialog = allocateWithoutConstructor(StreetSelectionDialog.class);
        StreetModeController controller = new StreetModeController();

        JComboBox<String> streetCombo = new JComboBox<>();
        streetCombo.addItem("Alpha Street");
        streetCombo.addItem("Beta Street");
        streetCombo.setSelectedIndex(0);

        JComboBox<String> postcodeCombo = new JComboBox<>();
        postcodeCombo.setEditable(true);
        postcodeCombo.addItem("");
        postcodeCombo.addItem("11111");
        postcodeCombo.setSelectedItem("11111");

        JTextField houseNumberField = new JTextField("7");
        JTextField cityField = new JTextField("Sampletown");

        JComboBox<String> countryCombo = new JComboBox<>();
        countryCombo.addItem("");
        countryCombo.setSelectedItem("");

        JComboBox<String> buildingTypeCombo = new JComboBox<>();
        buildingTypeCombo.setEditable(true);
        buildingTypeCombo.addItem("");
        buildingTypeCombo.getEditor().setItem("house");

        JCheckBox zoomToSelectedStreetCheckbox = new JCheckBox();
        zoomToSelectedStreetCheckbox.setSelected(false);

        List<StreetOption> currentStreetOptions = List.of(
                new StreetOption("Alpha Street", "Alpha Street", "alpha#1"),
                new StreetOption("Beta Street", "Beta Street", "beta#1")
        );

        setField(dialog, "dialogController", new DialogController());
        setField(dialog, "streetModeController", controller);
        setField(dialog, "streetCombo", streetCombo);
        setField(dialog, "postcodeCombo", postcodeCombo);
        setField(dialog, "houseNumberField", houseNumberField);
        setField(dialog, "cityField", cityField);
        setField(dialog, "countryCombo", countryCombo);
        setField(dialog, "buildingTypeCombo", buildingTypeCombo);
        setField(dialog, "zoomToSelectedStreetCheckbox", zoomToSelectedStreetCheckbox);
        setField(dialog, "currentStreetOptions", currentStreetOptions);
        setField(dialog, "houseNumberIncrementStep", 1);
        setField(dialog, "lastSelectedStreet", "Alpha Street");

        // Path 1: direct list selection
        streetCombo.setSelectedIndex(1);
        invokeNoArgMethod(dialog, "onStreetSelectionChanged");
        assertEquals("", extractSelectedPostcode(postcodeCombo),
                "street list selection should clear postcode like arrow navigation");
        assertEquals("", houseNumberField.getText(),
                "street list selection should clear house number like arrow navigation");

        // Reset state
        streetCombo.setSelectedIndex(0);
        setField(dialog, "lastSelectedStreet", "Alpha Street");
        postcodeCombo.setSelectedItem("11111");
        houseNumberField.setText("7");

        // Path 2: arrow navigation (next)
        invokeMethodWithIntArgument(dialog, "navigateStreetByOffset", 1);
        invokeNoArgMethod(dialog, "onStreetSelectionChanged");
        assertEquals("", extractSelectedPostcode(postcodeCombo),
                "arrow navigation should clear postcode");
        assertEquals("", houseNumberField.getText(),
                "arrow navigation should clear house number");
    }

    private static void testStreetSelectionIgnoresTransientPopupHover() throws Exception {
        String source = readPluginSource("StreetSelectionDialog.java");
        assertTrue(source.contains("streetCombo.isPopupVisible() && !changedByNavigation"),
                "street selection handling should ignore transient popup hover/preselection events");
    }

    private static void testStreetGroupingBridgesEndpointToSegmentGaps() {
        DataSet dataSet = new DataSet();

        // Long trunk segment so endpoint-to-endpoint proximity cannot connect the second way.
        Way trunk = createOpenStreetWayWithCoordinates(
                "Example Street",
                new LatLon(0.0, 0.0),
                new LatLon(0.0, 0.01)
        );
        // Starts near the middle of trunk but far from both trunk endpoints.
        Way continuationGap = createOpenStreetWayWithCoordinates(
                "Example Street",
                new LatLon(0.0001, 0.0050),
                new LatLon(0.0006, 0.0050)
        );

        dataSet.addPrimitiveRecursive(trunk);
        dataSet.addPrimitiveRecursive(continuationGap);

        StreetNameCollector.StreetIndex index = StreetNameCollector.collectStreetIndex(dataSet);
        List<StreetOption> options = index.getOptionsForBaseStreetName("Example Street");
        assertEquals(1, options.size(),
                "small endpoint-to-segment gaps should not split one logical street into multiple options");
    }

    private static void testStreetGroupingKeepsDistantSameNameRoadsSeparated() {
        DataSet dataSet = new DataSet();

        Way firstArea = createOpenStreetWayWithCoordinates(
                "Sample Road",
                new LatLon(0.0, 0.0),
                new LatLon(0.0, 0.0010)
        );
        Way secondArea = createOpenStreetWayWithCoordinates(
                "Sample Road",
                new LatLon(0.02, 0.02),
                new LatLon(0.02, 0.0210)
        );

        dataSet.addPrimitiveRecursive(firstArea);
        dataSet.addPrimitiveRecursive(secondArea);

        StreetNameCollector.StreetIndex index = StreetNameCollector.collectStreetIndex(dataSet);
        List<StreetOption> options = index.getOptionsForBaseStreetName("Sample Road");
        assertEquals(2, options.size(),
                "distant same-name roads should remain disambiguated as separate street options");
        assertEquals("Sample Road", options.get(0).getDisplayStreetName(),
                "first disambiguated option should keep base display name");
        assertEquals("Sample Road [2]", options.get(1).getDisplayStreetName(),
                "second disambiguated option should use suffix label");
    }

    private static void testStreetGroupingMergesCollinearComponentsAfterRawSplit() {
        DataSet dataSet = new DataSet();

        Way firstPart = createOpenStreetWayWithCoordinates(
                "Linear Street",
                new LatLon(0.0, 0.0000),
                new LatLon(0.0, 0.0010)
        );
        // Intentional gap > endpoint and endpoint-to-segment thresholds; merge should happen in second stage.
        Way secondPart = createOpenStreetWayWithCoordinates(
                "Linear Street",
                new LatLon(0.0, 0.0022),
                new LatLon(0.0, 0.0032)
        );

        dataSet.addPrimitiveRecursive(firstPart);
        dataSet.addPrimitiveRecursive(secondPart);

        StreetNameCollector.StreetIndex index = StreetNameCollector.collectStreetIndex(dataSet);
        List<StreetOption> options = index.getOptionsForBaseStreetName("Linear Street");
        assertEquals(1, options.size(),
                "second-stage merge should fuse collinear same-name raw components into one street option");
    }

    private static void testSelectedStreetOptionKeepsFullMergedLocalChain() {
        DataSet dataSet = new DataSet();

        Way firstPart = createOpenStreetWayWithCoordinates(
                "Cluster Street",
                new LatLon(0.0, 0.0000),
                new LatLon(0.0, 0.0010)
        );
        // Intentionally separated beyond direct endpoint/segment thresholds.
        Way secondPart = createOpenStreetWayWithCoordinates(
                "Cluster Street",
                new LatLon(0.0, 0.0022),
                new LatLon(0.0, 0.0032)
        );

        dataSet.addPrimitiveRecursive(firstPart);
        dataSet.addPrimitiveRecursive(secondPart);

        StreetNameCollector.StreetIndex index = StreetNameCollector.collectStreetIndex(dataSet);
        StreetOption selectedStreet = resolveStreetOptionForBaseName(index, "Cluster Street");
        List<Way> localChain = index.getLocalStreetChainWays(selectedStreet, firstPart);

        assertEquals(2, localChain.size(),
                "selected disambiguated street option should keep all merged ways in local chain");
        assertTrue(localChain.contains(firstPart), "local chain should include first merged way");
        assertTrue(localChain.contains(secondPart), "local chain should include second merged way");
    }

    private static void testDirectlyConnectedDrivewayIsHighlighted() {
        DataSet dataSet = new DataSet();
        Node streetStart = new Node(new LatLon(0.0, 0.0));
        Node sharedNode = new Node(new LatLon(0.0, 0.0001));
        Node streetEnd = new Node(new LatLon(0.0, 0.0002));
        Way streetWay = createWayWithTags(List.of(streetStart, sharedNode, streetEnd), "residential", null, "Example Street");

        Node drivewayTail = new Node(new LatLon(0.0001, 0.0001));
        Way driveway = createWayWithTags(List.of(sharedNode, drivewayTail), "service", "driveway", null);
        dataSet.addPrimitiveRecursive(streetWay);
        dataSet.addPrimitiveRecursive(driveway);

        Set<Way> highlightedStreetWays = new LinkedHashSet<>();
        highlightedStreetWays.add(streetWay);
        Set<Way> secondaryWays = HouseNumberOverlayLayer.collectDirectDrivewayHighlightWays(highlightedStreetWays);

        assertEquals(Set.of(driveway), secondaryWays,
                "directly connected highway=service + service=driveway should be included as secondary highlight");
    }

    private static void testParkingAisleIsNotHighlightedAsDriveway() {
        DataSet dataSet = new DataSet();
        Node streetStart = new Node(new LatLon(0.0, 0.0));
        Node sharedNode = new Node(new LatLon(0.0, 0.0001));
        Node streetEnd = new Node(new LatLon(0.0, 0.0002));
        Way streetWay = createWayWithTags(List.of(streetStart, sharedNode, streetEnd), "residential", null, "Example Street");

        Node aisleTail = new Node(new LatLon(0.0001, 0.0001));
        Way parkingAisle = createWayWithTags(List.of(sharedNode, aisleTail), "service", "parking_aisle", null);
        dataSet.addPrimitiveRecursive(streetWay);
        dataSet.addPrimitiveRecursive(parkingAisle);

        Set<Way> highlightedStreetWays = new LinkedHashSet<>();
        highlightedStreetWays.add(streetWay);
        Set<Way> secondaryWays = HouseNumberOverlayLayer.collectDirectDrivewayHighlightWays(highlightedStreetWays);

        assertTrue(secondaryWays.isEmpty(), "service=parking_aisle must not be included in driveway highlight");
    }

    private static void testServiceWithoutDrivewayIsNotHighlighted() {
        DataSet dataSet = new DataSet();
        Node streetStart = new Node(new LatLon(0.0, 0.0));
        Node sharedNode = new Node(new LatLon(0.0, 0.0001));
        Node streetEnd = new Node(new LatLon(0.0, 0.0002));
        Way streetWay = createWayWithTags(List.of(streetStart, sharedNode, streetEnd), "residential", null, "Example Street");

        Node serviceTail = new Node(new LatLon(0.0001, 0.0001));
        Way genericService = createWayWithTags(List.of(sharedNode, serviceTail), "service", null, null);
        dataSet.addPrimitiveRecursive(streetWay);
        dataSet.addPrimitiveRecursive(genericService);

        Set<Way> highlightedStreetWays = new LinkedHashSet<>();
        highlightedStreetWays.add(streetWay);
        Set<Way> secondaryWays = HouseNumberOverlayLayer.collectDirectDrivewayHighlightWays(highlightedStreetWays);

        assertTrue(secondaryWays.isEmpty(), "highway=service without service=driveway must not be included");
    }

    private static void testIndirectDrivewayIsNotHighlighted() {
        DataSet dataSet = new DataSet();
        Node streetStart = new Node(new LatLon(0.0, 0.0));
        Node sharedStreetNode = new Node(new LatLon(0.0, 0.0001));
        Node streetEnd = new Node(new LatLon(0.0, 0.0002));
        Way streetWay = createWayWithTags(List.of(streetStart, sharedStreetNode, streetEnd), "residential", null, "Example Street");

        Node bridgeEnd = new Node(new LatLon(0.0001, 0.0001));
        Way yardService = createWayWithTags(List.of(sharedStreetNode, bridgeEnd), "service", "yard", null);

        Node drivewayTail = new Node(new LatLon(0.0002, 0.0001));
        Way indirectDriveway = createWayWithTags(List.of(bridgeEnd, drivewayTail), "service", "driveway", null);
        dataSet.addPrimitiveRecursive(streetWay);
        dataSet.addPrimitiveRecursive(yardService);
        dataSet.addPrimitiveRecursive(indirectDriveway);

        Set<Way> highlightedStreetWays = new LinkedHashSet<>();
        highlightedStreetWays.add(streetWay);
        Set<Way> secondaryWays = HouseNumberOverlayLayer.collectDirectDrivewayHighlightWays(highlightedStreetWays);

        assertFalse(secondaryWays.contains(indirectDriveway), "second-hop driveway must not be included in secondary highlight");
        assertTrue(secondaryWays.isEmpty(), "indirect driveway topology should produce no driveway highlight");
    }

    private static void testStreetGroupingKeepsParallelNearbyRoadsSeparated() {
        DataSet dataSet = new DataSet();

        Way firstParallel = createOpenStreetWayWithCoordinates(
                "Parallel Street",
                new LatLon(0.0000, 0.0000),
                new LatLon(0.0020, 0.0000)
        );
        Way secondParallel = createOpenStreetWayWithCoordinates(
                "Parallel Street",
                new LatLon(0.0000, 0.0007),
                new LatLon(0.0020, 0.0007)
        );

        dataSet.addPrimitiveRecursive(firstParallel);
        dataSet.addPrimitiveRecursive(secondParallel);

        StreetNameCollector.StreetIndex index = StreetNameCollector.collectStreetIndex(dataSet);
        List<StreetOption> options = index.getOptionsForBaseStreetName("Parallel Street");
        assertEquals(2, options.size(),
                "conservative merge should not fuse nearby but clearly parallel separated streets");
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

    private static void testStreetAutoZoomUsesFullDataSetStreetIndex() throws Exception {
        String controllerSource = readPluginSource("StreetModeController.java");
        String collectorSource = readPluginSource("StreetNameCollector.java");

        assertTrue(controllerSource.contains("StreetNameCollector.collectStreetIndex(editDataSet, false)"),
                "street auto-zoom should resolve street index from full dataset scope, not current view only");
        assertTrue(collectorSource.contains("static StreetIndex collectStreetIndex(DataSet dataSet, boolean limitToCurrentView)"),
                "street collector should expose a scope-aware index builder for zoom paths");
    }

    private static void testStreetHouseNumberCountCollectorConditionalCityRule() {
        StreetHouseNumberCountCollector collector = new StreetHouseNumberCountCollector();

        DataSet onlyDifferentCities = new DataSet();
        onlyDifferentCities.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));
        Way cityAOnly = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityAOnly.put("addr:postcode", "12345");
        cityAOnly.put("addr:city", "Alpha City");
        Way cityBOnly = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityBOnly.put("addr:postcode", "12345");
        cityBOnly.put("addr:city", "Beta City");
        onlyDifferentCities.addPrimitiveRecursive(cityAOnly);
        onlyDifferentCities.addPrimitiveRecursive(cityBOnly);

        StreetNameCollector.StreetIndex mismatchIndex = StreetNameCollector.collectStreetIndex(onlyDifferentCities);
        List<StreetHouseNumberCountRow> mismatchRows = collector.collectRows(onlyDifferentCities, mismatchIndex);
        StreetHouseNumberCountRow mismatchRow = findStreetCountRowByDisplayName(mismatchRows, "Example Street");
        assertTrue(mismatchRow != null, "street count row for Example Street should exist");
        assertFalse(mismatchRow.hasDuplicate(),
                "street count duplicate marker must stay false when all matching addresses have conflicting city values");

        DataSet withMissingCityBridge = new DataSet();
        withMissingCityBridge.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));
        Way cityA = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityA.put("addr:postcode", "12345");
        cityA.put("addr:city", "Alpha City");
        Way cityB = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityB.put("addr:postcode", "12345");
        cityB.put("addr:city", "Beta City");
        Way cityMissing = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityMissing.put("addr:postcode", "12345");
        withMissingCityBridge.addPrimitiveRecursive(cityA);
        withMissingCityBridge.addPrimitiveRecursive(cityB);
        withMissingCityBridge.addPrimitiveRecursive(cityMissing);

        StreetNameCollector.StreetIndex bridgeIndex = StreetNameCollector.collectStreetIndex(withMissingCityBridge);
        List<StreetHouseNumberCountRow> bridgeRows = collector.collectRows(withMissingCityBridge, bridgeIndex);
        StreetHouseNumberCountRow bridgeRow = findStreetCountRowByDisplayName(bridgeRows, "Example Street");
        assertTrue(bridgeRow != null, "street count row for Example Street should exist with missing-city bridge");
        assertTrue(bridgeRow.hasDuplicate(),
                "street count duplicate marker must be true when same base address has at least one missing-city counterpart");
    }

    private static void testBuildingOverviewCollectorFilteringAndClassification() {
        DataSet dataSet = new DataSet();

        Way addressedLarge = createClosedBuildingWithSize("Example Street", "1", 0.0002);
        addressedLarge.put("addr:postcode", "12345");
        Way addressedDuplicate = createClosedBuildingWithSize("Example Street", "1", 0.0002);
        addressedDuplicate.put("addr:postcode", "12345");
        Way sameHouseOtherStreet = createClosedBuildingWithSize("Other Street", "1", 0.0002);
        sameHouseOtherStreet.put("addr:postcode", "12345");
        Way sameHouseMissingPostcode = createClosedBuildingWithSize("Example Street", "1", 0.0002);
        Way unaddressedLarge = createClosedBuildingWithSize(null, null, 0.0002);
        Way addressedTiny = createClosedBuildingWithSize("Example Street", "99", 0.00001);

        dataSet.addPrimitiveRecursive(addressedLarge);
        dataSet.addPrimitiveRecursive(addressedDuplicate);
        dataSet.addPrimitiveRecursive(sameHouseOtherStreet);
        dataSet.addPrimitiveRecursive(sameHouseMissingPostcode);
        dataSet.addPrimitiveRecursive(unaddressedLarge);
        dataSet.addPrimitiveRecursive(addressedTiny);

        BuildingOverviewCollector collector = new BuildingOverviewCollector();
        List<BuildingOverviewCollector.BuildingOverviewEntry> entries = collector.collect(dataSet);

        assertEquals(5, entries.size(), "collector should skip buildings below minimum area");

        boolean containsAddressedLarge = false;
        boolean containsAddressedDuplicate = false;
        boolean containsSameHouseOtherStreet = false;
        boolean containsSameHouseMissingPostcode = false;
        boolean containsMissingRequiredAddressFields = false;
        boolean containsUnaddressedLarge = false;
        boolean containsNoAddressData = false;
        boolean containsAddressedTiny = false;
        for (BuildingOverviewCollector.BuildingOverviewEntry entry : entries) {
            if (entry.getPrimitive() == addressedLarge
                    && entry.hasHouseNumber()
                    && entry.hasDuplicateExactAddress()) {
                containsAddressedLarge = true;
            }
            if (entry.getPrimitive() == addressedDuplicate
                    && entry.hasHouseNumber()
                    && entry.hasDuplicateExactAddress()) {
                containsAddressedDuplicate = true;
            }
            if (entry.getPrimitive() == sameHouseOtherStreet
                    && entry.hasHouseNumber()
                    && !entry.hasDuplicateExactAddress()) {
                containsSameHouseOtherStreet = true;
            }
            if (entry.getPrimitive() == sameHouseMissingPostcode
                    && entry.hasHouseNumber()
                    && !entry.hasDuplicateExactAddress()) {
                containsSameHouseMissingPostcode = true;
                if (entry.hasMissingRequiredAddressFields()) {
                    containsMissingRequiredAddressFields = true;
                }
            }
            if (entry.getPrimitive() == unaddressedLarge && !entry.hasHouseNumber()) {
                containsUnaddressedLarge = true;
                if (entry.hasNoAddressData()) {
                    containsNoAddressData = true;
                }
            }
            if (entry.getPrimitive() == addressedTiny) {
                containsAddressedTiny = true;
            }
        }

        assertTrue(containsAddressedLarge,
                "large addressed building with duplicate street/postcode/housenumber should be marked duplicate");
        assertTrue(containsAddressedDuplicate,
                "second duplicate building with same street/postcode/housenumber should be marked duplicate");
        assertTrue(containsSameHouseOtherStreet,
                "same housenumber with different street must not be marked duplicate");
        assertTrue(containsSameHouseMissingPostcode,
                "same housenumber without postcode must not be marked duplicate");
        assertTrue(containsMissingRequiredAddressFields,
                "housenumber without postcode should be flagged as missing required address fields");
        assertTrue(containsUnaddressedLarge, "large unaddressed building should be included and marked unaddressed");
        assertTrue(containsNoAddressData,
                "building without street, postcode and housenumber should be flagged as no address data");
        assertFalse(containsAddressedTiny, "tiny building should be excluded by minimum area filter");
    }

    private static void testBuildingOverviewCollectorConditionalCityRule() {
        BuildingOverviewCollector collector = new BuildingOverviewCollector();

        DataSet onlyDifferentCities = new DataSet();
        Way cityAOnly = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityAOnly.put("addr:postcode", "12345");
        cityAOnly.put("addr:city", "Alpha City");
        Way cityBOnly = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityBOnly.put("addr:postcode", "12345");
        cityBOnly.put("addr:city", "Beta City");
        onlyDifferentCities.addPrimitiveRecursive(cityAOnly);
        onlyDifferentCities.addPrimitiveRecursive(cityBOnly);

        List<BuildingOverviewCollector.BuildingOverviewEntry> onlyDifferentCityEntries = collector.collect(onlyDifferentCities);
        boolean cityAOnlyIsDuplicate = false;
        boolean cityBOnlyIsDuplicate = false;
        for (BuildingOverviewCollector.BuildingOverviewEntry entry : onlyDifferentCityEntries) {
            if (entry.getPrimitive() == cityAOnly) {
                cityAOnlyIsDuplicate = entry.hasDuplicateExactAddress();
            }
            if (entry.getPrimitive() == cityBOnly) {
                cityBOnlyIsDuplicate = entry.hasDuplicateExactAddress();
            }
        }
        assertFalse(cityAOnlyIsDuplicate,
                "same street/postcode/housenumber with different city on both sides must not be duplicate");
        assertFalse(cityBOnlyIsDuplicate,
                "city mismatch between fully city-tagged addresses must block duplicate match");

        DataSet withMissingCityBridge = new DataSet();
        Way cityA = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityA.put("addr:postcode", "12345");
        cityA.put("addr:city", "Alpha City");
        Way cityB = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityB.put("addr:postcode", "12345");
        cityB.put("addr:city", "Beta City");
        Way cityMissing = createClosedBuildingWithSize("Example Street", "8", 0.0002);
        cityMissing.put("addr:postcode", "12345");
        withMissingCityBridge.addPrimitiveRecursive(cityA);
        withMissingCityBridge.addPrimitiveRecursive(cityB);
        withMissingCityBridge.addPrimitiveRecursive(cityMissing);

        List<BuildingOverviewCollector.BuildingOverviewEntry> withMissingCityEntries = collector.collect(withMissingCityBridge);
        boolean cityAIsDuplicate = false;
        boolean cityBIsDuplicate = false;
        boolean cityMissingIsDuplicate = false;
        for (BuildingOverviewCollector.BuildingOverviewEntry entry : withMissingCityEntries) {
            if (entry.getPrimitive() == cityA) {
                cityAIsDuplicate = entry.hasDuplicateExactAddress();
            }
            if (entry.getPrimitive() == cityB) {
                cityBIsDuplicate = entry.hasDuplicateExactAddress();
            }
            if (entry.getPrimitive() == cityMissing) {
                cityMissingIsDuplicate = entry.hasDuplicateExactAddress();
            }
        }
        assertTrue(cityAIsDuplicate,
                "city-tagged address should match duplicate when counterpart has no city (city ignored unless both present)");
        assertTrue(cityBIsDuplicate,
                "second city-tagged address should also match duplicate when a counterpart has no city");
        assertTrue(cityMissingIsDuplicate,
                "address without city should match duplicates against same street/postcode/housenumber regardless of city on other side");
    }

    private static void testBuildingOverviewCollectorIgnoresRelationOuterSelfDuplicate() {
        DataSet dataSet = new DataSet();

        Way outerWay = createClosedBuildingWithSize(null, null, 0.0002);
        outerWay.put("addr:street", "Example Street");
        outerWay.put("addr:postcode", "12345");
        outerWay.put("addr:housenumber", "10");

        Relation relation = new Relation();
        relation.put("type", "multipolygon");
        relation.put("building", "yes");
        relation.put("addr:street", "Example Street");
        relation.put("addr:postcode", "12345");
        relation.put("addr:housenumber", "10");
        relation.addMember(new org.openstreetmap.josm.data.osm.RelationMember("outer", outerWay));

        dataSet.addPrimitiveRecursive(outerWay);
        dataSet.addPrimitive(relation);

        BuildingOverviewCollector collector = new BuildingOverviewCollector();
        List<BuildingOverviewCollector.BuildingOverviewEntry> entries = collector.collect(dataSet);

        assertEquals(1, entries.size(), "relation and addressed outer way should be canonicalized to one real building");
        assertFalse(entries.get(0).hasDuplicateExactAddress(), "canonicalized self-representation must not become duplicate");
    }

    private static void testHouseNumberOverlayCollectorIgnoresRelationOuterSelfDuplicate() throws Exception {
        String source = readPluginSource("HouseNumberOverlayCollector.java");

        assertTrue(source.contains("AddressEntryCollector"),
                "overlay collector should source carriers via AddressEntryCollector");
        assertTrue(source.contains("Map<Long, HouseNumberOverlayEntry> entriesByCanonicalPrimitiveId"),
                "overlay collector should keep one overlay entry per primitive id to avoid self-duplicates");
        assertTrue(source.contains("entriesByCanonicalPrimitiveId.containsKey(canonicalId)"),
                "overlay collector should skip already collected primitive ids");
    }

    private static void testHouseNumberOverlayCollectorKeepsDistinctBuildingDuplicates() throws Exception {
        String source = readPluginSource("HouseNumberOverlayCollector.java");
        String layerSource = readPluginSource("HouseNumberOverlayLayer.java");

        assertTrue(source.contains("Map<Long, HouseNumberOverlayEntry> entriesByCanonicalPrimitiveId"),
                "overlay collector should track one entry per canonical primitive id");
        assertTrue(source.contains("entriesByCanonicalPrimitiveId.containsKey(canonicalId)"),
                "overlay collector should skip already processed canonical primitives");
        assertTrue(source.contains("entriesByCanonicalPrimitiveId.put(canonicalId, overlayEntry)"),
                "overlay collector should add exactly one entry for each canonical primitive");
        assertTrue(layerSource.contains("collectDuplicateAddressKeys"),
                "duplicate detection should remain in the overlay layer and use the collected entries unchanged");
        assertTrue(layerSource.contains("resolveRealWorldAnchorId"),
                "overlay duplicate detection should compare real-world anchors to suppress co-located duplicates");
    }

    private static void testHouseNumberOverlayDuplicateKeyRemainsCityAgnostic() throws Exception {
        String source = readPluginSource("HouseNumberOverlayLayer.java");
        assertTrue(source.contains("private String normalizeAddressKey(String street, String postcode, String houseNumber)"),
                "local selected-street duplicate key should remain based on street, postcode and house number only");
        assertFalse(source.contains("addr:city"),
                "local selected-street duplicate detection must not include addr:city in its key");
    }

    private static void testStreetCountsIncludeStandaloneAddressNode() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));

        Node addressNode = new Node(new LatLon(0.00005, 0.00005));
        addressNode.put("addr:street", "Example Street");
        addressNode.put("addr:postcode", "12345");
        addressNode.put("addr:housenumber", "7");
        dataSet.addPrimitive(addressNode);

        StreetNameCollector.StreetIndex streetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        List<StreetHouseNumberCountRow> rows = new StreetHouseNumberCountCollector().collectRows(dataSet, streetIndex);
        StreetHouseNumberCountRow row = findStreetCountRowByDisplayName(rows, "Example Street");
        assertTrue(row != null, "street count row should exist for standalone address node street");
        assertEquals(1, row.getCount(), "standalone address node should be counted as one address carrier");
    }

    private static void testOverlayIncludesEntranceAddressNode() {
        DataSet dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(createOpenStreetWay("Example Street", true));

        Node entranceNode = new Node(new LatLon(0.00005, 0.00005));
        entranceNode.put("entrance", "main");
        entranceNode.put("addr:street", "Example Street");
        entranceNode.put("addr:postcode", "12345");
        entranceNode.put("addr:housenumber", "9");
        dataSet.addPrimitive(entranceNode);

        StreetNameCollector.StreetIndex streetIndex = StreetNameCollector.collectStreetIndex(dataSet);
        StreetOption selectedStreet = resolveStreetOptionForBaseName(streetIndex, "Example Street");
        List<HouseNumberOverlayEntry> entries = new HouseNumberOverlayCollector().collect(dataSet, selectedStreet, streetIndex, null);

        boolean containsEntrance = false;
        for (HouseNumberOverlayEntry entry : entries) {
            if (entry.getPrimitive() == entranceNode && entry.getCarrierType() == AddressEntry.CarrierType.ENTRANCE_NODE) {
                containsEntrance = true;
            }
        }
        assertTrue(containsEntrance, "overlay should include entrance nodes as read-only address carriers");
    }

    private static void testBuildingOverviewMarksIndirectBuildingAddress() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuildingWithSize(null, null, 0.0002);
        dataSet.addPrimitiveRecursive(building);

        Node addressNode = building.firstNode();
        addressNode.put("addr:street", "Example Street");
        addressNode.put("addr:postcode", "12345");
        addressNode.put("addr:housenumber", "4");

        List<BuildingOverviewCollector.BuildingOverviewEntry> entries = new BuildingOverviewCollector().collect(dataSet);
        BuildingOverviewCollector.BuildingOverviewEntry buildingEntry = null;
        for (BuildingOverviewCollector.BuildingOverviewEntry entry : entries) {
            if (entry.getPrimitive() == building) {
                buildingEntry = entry;
                break;
            }
        }
        assertTrue(buildingEntry != null, "building should appear in overview");
        assertTrue(buildingEntry.hasHouseNumber(), "indirect node address should make building count as addressed");
        assertTrue(buildingEntry.hasIndirectAddress(), "overview should mark building as indirectly addressed via node");
        assertEquals(null, building.get("addr:housenumber"), "indirect addressing must not modify building tags");
    }

    private static void testCoLocatedBuildingAndNodeAreNotHardDuplicates() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuildingWithSize("Example Street", "11", 0.0002);
        building.put("addr:postcode", "12345");
        dataSet.addPrimitiveRecursive(building);

        Node addressNode = building.firstNode();
        addressNode.put("addr:street", "Example Street");
        addressNode.put("addr:postcode", "12345");
        addressNode.put("addr:housenumber", "11");

        List<AddressEntry> entries = new AddressEntryCollector().collect(dataSet);
        Map<String, AddressDuplicateAnalyzer.DuplicateAddressGroupStats> groups = AddressDuplicateAnalyzer.buildDuplicateGroups(entries);

        boolean buildingDuplicate = false;
        boolean nodeDuplicate = false;
        for (AddressEntry entry : entries) {
            if (entry.getPrimitive() == building) {
                buildingDuplicate = AddressDuplicateAnalyzer.isHardDuplicate(entry, groups);
            }
            if (entry.getPrimitive() == addressNode) {
                nodeDuplicate = AddressDuplicateAnalyzer.isHardDuplicate(entry, groups);
            }
        }
        assertFalse(buildingDuplicate, "building + internal node representation should not count as hard duplicate");
        assertFalse(nodeDuplicate, "internal node + building representation should not count as hard duplicate");
    }

    private static void testDistinctAddressCarriersRemainHardDuplicates() {
        DataSet dataSet = new DataSet();
        Way building = createClosedBuildingWithSize("Example Street", "11", 0.0002);
        building.put("addr:postcode", "12345");
        dataSet.addPrimitiveRecursive(building);

        Node standalone = new Node(new LatLon(0.01, 0.01));
        standalone.put("addr:street", "Example Street");
        standalone.put("addr:postcode", "12345");
        standalone.put("addr:housenumber", "11");
        dataSet.addPrimitive(standalone);

        List<AddressEntry> entries = new AddressEntryCollector().collect(dataSet);
        Map<String, AddressDuplicateAnalyzer.DuplicateAddressGroupStats> groups = AddressDuplicateAnalyzer.buildDuplicateGroups(entries);

        boolean buildingDuplicate = false;
        boolean nodeDuplicate = false;
        for (AddressEntry entry : entries) {
            if (entry.getPrimitive() == building) {
                buildingDuplicate = AddressDuplicateAnalyzer.isHardDuplicate(entry, groups);
            }
            if (entry.getPrimitive() == standalone) {
                nodeDuplicate = AddressDuplicateAnalyzer.isHardDuplicate(entry, groups);
            }
        }
        assertTrue(buildingDuplicate, "distinct building and standalone node with same address key must remain hard duplicate");
        assertTrue(nodeDuplicate, "standalone node should participate in hard duplicate detection");
    }

    private static void testAddressEntryCollectorSpatialIndexWiring() throws Exception {
        String source = readPluginSource("AddressEntryCollector.java");
        assertTrue(source.contains("BuildingSpatialIndex"),
                "address entry collector should use a spatial index helper for node-to-building association");
        assertTrue(source.contains("SPATIAL_CELL_SIZE_METERS"),
                "spatial index should use an explicit cell-size configuration");
        assertTrue(source.contains("spatialIndex.query"),
                "node association should query nearby spatial buckets instead of scanning all buildings");
    }

    private static Way createOpenStreetWay(String streetName, boolean withHighwayTag) {
        Way way = createOpenBaseWay();
        if (withHighwayTag) {
            way.put("highway", "residential");
        }
        way.put("name", streetName);
        return way;
    }

    private static Way createOpenStreetWayWithCoordinates(String streetName, LatLon first, LatLon second) {
        Way way = new Way();
        way.setNodes(List.of(new Node(first), new Node(second)));
        way.put("highway", "residential");
        way.put("name", streetName);
        return way;
    }

    private static Way createWayWithTags(List<Node> nodes, String highwayValue, String serviceValue, String nameValue) {
        Way way = new Way();
        way.setNodes(nodes);
        if (highwayValue != null) {
            way.put("highway", highwayValue);
        }
        if (serviceValue != null) {
            way.put("service", serviceValue);
        }
        if (nameValue != null) {
            way.put("name", nameValue);
        }
        return way;
    }

    private static StreetHouseNumberCountRow findStreetCountRowByDisplayName(
            List<StreetHouseNumberCountRow> rows,
            String displayStreetName
    ) {
        if (rows == null || displayStreetName == null) {
            return null;
        }
        for (StreetHouseNumberCountRow row : rows) {
            if (row != null && displayStreetName.equals(row.getDisplayStreetName())) {
                return row;
            }
        }
        return null;
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
        clearCentralSettingsPrefs();
    }

    private static void clearCentralSettingsPrefs() {
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "overlay.mode", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "showOverlay", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "overlay.mode.migrated.v1", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.showConnectionLines", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.showSeparateEvenOddLines", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.showHouseNumberOverview", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.showStreetHouseNumberCounts", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.zoomToSelectedStreet", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.zoomToNumberedBuildingsOnly", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.splitMakeRectangular", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.applyTypeToAll", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.advancedSectionsExpanded", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.houseNumberIncrementStep", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.terraceParts", null);
        Config.getPref().put(HouseNumberClickPreferences.PREFIX + "dialog.completenessMissingField", null);
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

    @SuppressWarnings("unchecked")
    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        Object unsafe = theUnsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (T) allocateInstance.invoke(unsafe, type);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokeNoArgMethod(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invokeMethodWithIntArgument(Object target, String methodName, int argument) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        method.invoke(target, argument);
    }

    private static String extractSelectedPostcode(JComboBox<String> postcodeCombo) {
        Object selected = postcodeCombo.getEditor().getItem();
        return selected instanceof String ? ((String) selected).trim() : "";
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

    private static void testTerraceSplitFailurePathRollsBackCommands() throws Exception {
        String source = readPluginSource("TerraceSplitService.java");
        assertTrue(source.contains("rollbackAndFailure"),
                "terrace split should route failures through a rollback helper");
        assertTrue(source.contains("rollbackCommandsAddedSince(undoStartSize);"),
                "terrace split failure path should rollback all commands since start");
        assertTrue(source.contains("UndoRedoHandler.getInstance().undo(undoCount);"),
                "rollback helper should undo commands added during partial terrace splits");
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

    private static void testDialogSplitStartEntrypointsRemoved() throws Exception {
        Path dialogPath = Path.of("src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", "StreetSelectionDialog.java");
        Path controllerPath = Path.of("src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", "StreetModeController.java");
        String dialogSource = Files.readString(dialogPath);
        String controllerSource = Files.readString(controllerPath);

        assertFalse(dialogSource.contains("runSplitBuildingAction("),
                "dialog should no longer expose split button callback helper");
        assertFalse(dialogSource.contains("runCreateRowHousesAction("),
                "dialog should no longer expose row-house button callback helper");
        assertFalse(dialogSource.contains("onSplitBuildingRequested("),
                "dialog should no longer start split mode directly");
        assertFalse(dialogSource.contains("onCreateRowHousesRequested("),
                "dialog should no longer trigger terrace split start directly");

        assertFalse(controllerSource.contains("startInternalSingleSplitFlowFromDialog("),
                "controller should not keep dialog-only split start path");
        assertFalse(controllerSource.contains("executeInternalTerraceSplitFromDialog("),
                "controller should not keep dialog-only terrace split start path");
    }

    private static String readPluginSource(String fileName) throws Exception {
        Path path = Path.of("src", "org", "openstreetmap", "josm", "plugins", "housenumberclick", fileName);
        return Files.readString(path);
    }

    private static void testSplitModeControllerEntrypointsRemoved() throws Exception {
        String controllerSource = readPluginSource("StreetModeController.java");
        assertFalse(controllerSource.contains("activateTemporarySplitModeFromAlt("),
                "controller should no longer expose Alt-triggered split mode activation");
        assertFalse(controllerSource.contains("activateInternalSplitMode("),
                "controller should no longer expose split mode activation helpers");
        assertFalse(controllerSource.contains("SplitFlowOutcome"),
                "controller should no longer carry split flow mode-switch outcomes");
        assertFalse(controllerSource.contains("onInternalSplitFlowFinished("),
                "controller should no longer model return flow from a separate split mode");
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

    private static LatLon offsetMeters(LatLon origin, double northMeters, double eastMeters) {
        double latOffset = northMeters / 111_132.0;
        double lonScale = Math.cos(Math.toRadians(origin.lat()));
        double lonOffset = lonScale == 0.0 ? 0.0 : eastMeters / (111_320.0 * lonScale);
        return new LatLon(origin.lat() + latOffset, origin.lon() + lonOffset);
    }
}
