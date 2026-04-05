package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.spi.preferences.Config;

public final class HouseNumberClickRiskRegressionTests {

    private static final String HANDOFF_STREET_KEY = "housenumberclick.buildingsplitter.handoff.street";
    private static final String HANDOFF_POSTCODE_KEY = "housenumberclick.buildingsplitter.handoff.postcode";
    private static final String HANDOFF_PENDING_KEY = "housenumberclick.buildingsplitter.handoff.pending";
    private static final String HANDOFF_TIMESTAMP_KEY = "housenumberclick.buildingsplitter.handoff.timestamp";
    private static final String HANDOFF_SESSION_KEY = "housenumberclick.buildingsplitter.handoff.session";
    private static final String FORCE_PREFERENCE_FALLBACK_KEY = "housenumberclick.buildingsplitter.forcePreferenceFallback";
    private static final String RELATION_SCAN_LIMIT_KEY = BuildingResolver.PREF_RELATION_SCAN_LIMIT;
    private static final String WAY_SCAN_LIMIT_KEY = BuildingResolver.PREF_WAY_SCAN_LIMIT;

    private HouseNumberClickRiskRegressionTests() {
        // Utility class
    }

    public static void main(String[] args) throws Exception {
        ensurePreferences();
        run("AddressSelection normalizes values and step", HouseNumberClickRiskRegressionTests::testAddressSelectionNormalization);
        run("HouseNumberService normalizes and sanitizes step", HouseNumberClickRiskRegressionTests::testHouseNumberNormalizationAndStepSanitizing);
        run("HouseNumberService apply increment keeps existing behavior", HouseNumberClickRiskRegressionTests::testHouseNumberIncrementAfterApplyRules);
        run("HouseNumberService number and letter part updates", HouseNumberClickRiskRegressionTests::testHouseNumberPartUpdateRules);
        run("AddressReadbackService reads address tags from building", HouseNumberClickRiskRegressionTests::testAddressReadbackFromBuilding);
        run("AddressReadbackService street fallback keeps postcode/buildingType", HouseNumberClickRiskRegressionTests::testAddressReadbackStreetFallback);
        run("AddressReadbackService candidate fallback order", HouseNumberClickRiskRegressionTests::testAddressReadbackCandidateOrderAndMissingTags);
        run("AddressConflictService detects street and postcode conflicts", HouseNumberClickRiskRegressionTests::testAddressConflictDetection);
        run("AddressConflictService handles missing tags and partial differences", HouseNumberClickRiskRegressionTests::testAddressConflictEdgeCases);
        run("ConflictDialogModelBuilder keeps field order and value mapping", HouseNumberClickRiskRegressionTests::testConflictDialogModelBuilderMapping);
        run("ConflictDialogModelBuilder handles empty analysis", HouseNumberClickRiskRegressionTests::testConflictDialogModelBuilderEmpty);
        run("HouseNumberOverview duplicate marker ignores mixed variants", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerIgnoresMixedVariants);
        run("HouseNumberOverview duplicate marker tracks exact repeats", HouseNumberClickRiskRegressionTests::testOverviewDuplicateMarkerTracksExactRepeats);
        run("HouseNumberOverview duplicate rows expose grouped primitives", HouseNumberClickRiskRegressionTests::testOverviewDuplicateRowCarriesGroupedPrimitives);
        run("DataSet transition detection is stable", HouseNumberClickRiskRegressionTests::testDataSetChangeDetection);
        run("BuildingSplitter stale fallback is discarded", HouseNumberClickRiskRegressionTests::testStaleFallbackIsCleared);
        run("BuildingSplitter fresh fallback is kept", HouseNumberClickRiskRegressionTests::testFreshFallbackIsKept);
        run("Successful reflection handoff clears fallback", HouseNumberClickRiskRegressionTests::testReflectionHandoffClearsFallback);
        run("Scan limit defaults are used when unset", HouseNumberClickRiskRegressionTests::testScanLimitDefaultsWhenUnset);
        run("Invalid scan limit preferences fall back to defaults", HouseNumberClickRiskRegressionTests::testInvalidScanLimitPreferencesFallBack);
        run("Duplicate click detection blocks true duplicates", HouseNumberClickRiskRegressionTests::testDuplicateClicksAreDetected);
        run("Duplicate click detection keeps rapid distinct clicks", HouseNumberClickRiskRegressionTests::testRapidDistinctClicksAreKept);
        System.out.println("All HouseNumberClick risk regression tests passed.");
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
        assertEquals("99999", result.getPostcode(), "postcode should keep current value");
        assertEquals("residential", result.getBuildingType(), "building type should keep current value");
        assertEquals("1", result.getHouseNumber(), "street fallback should reset house number to 1");
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

        AddressConflictService.ConflictAnalysis analysis = service.analyze(building, "New Street", "54321", "34");
        assertTrue(analysis.hasConflict(), "different street/postcode should trigger overwrite conflict");
        assertEquals("Old Street", analysis.getOverwrittenStreet(), "existing street should be used as overwritten street");
        assertEquals(3, analysis.getDifferingFields().size(), "street, postcode and housenumber diffs should be listed");
        assertEquals("addr:street", analysis.getDifferingFields().get(0).getKey(), "street diff should appear first");
        assertEquals("addr:postcode", analysis.getDifferingFields().get(1).getKey(), "postcode diff should appear second");
        assertEquals("addr:housenumber", analysis.getDifferingFields().get(2).getKey(), "housenumber diff should appear third");
    }

    private static void testAddressConflictEdgeCases() {
        AddressConflictService service = new AddressConflictService();

        Way buildingWithoutStreet = new Way();
        buildingWithoutStreet.put("addr:postcode", "12345");
        AddressConflictService.ConflictAnalysis noStreetConflict = service.analyze(buildingWithoutStreet, "Any Street", "", "");
        assertFalse(noStreetConflict.hasConflict(), "missing existing street should not trigger street conflict");
        assertEquals("Any Street", noStreetConflict.getOverwrittenStreet(), "fallback overwritten street should use proposed street");

        Way buildingWithOnlyHouseNumber = new Way();
        buildingWithOnlyHouseNumber.put("addr:housenumber", "10");
        AddressConflictService.ConflictAnalysis onlyHouseDiff = service.analyze(buildingWithOnlyHouseNumber, "", "", "11");
        assertFalse(onlyHouseDiff.hasConflict(), "house number difference alone should not trigger conflict dialog");
        assertEquals(1, onlyHouseDiff.getDifferingFields().size(), "house number difference should still be listed");
        assertEquals("addr:housenumber", onlyHouseDiff.getDifferingFields().get(0).getKey(), "listed diff should be housenumber");

        Way identical = new Way();
        identical.put("addr:street", "Same");
        identical.put("addr:postcode", "11111");
        identical.put("addr:housenumber", "3");
        AddressConflictService.ConflictAnalysis identicalAnalysis = service.analyze(identical, "Same", "11111", "3");
        assertFalse(identicalAnalysis.hasConflict(), "identical values should not trigger conflict");
        assertEquals(0, identicalAnalysis.getDifferingFields().size(), "identical values should produce no differing fields");
    }

    private static void testConflictDialogModelBuilderMapping() {
        AddressConflictService service = new AddressConflictService();
        ConflictDialogModelBuilder builder = new ConflictDialogModelBuilder();

        Way building = new Way();
        building.put("addr:street", "Old Street");
        building.put("addr:postcode", "12345");
        building.put("addr:housenumber", "9");

        AddressConflictService.ConflictAnalysis analysis = service.analyze(building, "New Street", "54321", "10");
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
        assertEquals("1 (a, b)", oddValue, "mixed variants without exact duplicates must not show xN marker");
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
        assertEquals("1 (a) x2", oddValue, "exact duplicate values should show compact xN marker");
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
        Way way = new Way();
        Node n1 = new Node(new LatLon(0.0, 0.0));
        Node n2 = new Node(new LatLon(0.0, 0.0001));
        Node n3 = new Node(new LatLon(0.0001, 0.0001));
        Node n4 = new Node(new LatLon(0.0001, 0.0));
        List<Node> nodes = new ArrayList<>();
        nodes.add(n1);
        nodes.add(n2);
        nodes.add(n3);
        nodes.add(n4);
        nodes.add(n1);
        way.setNodes(nodes);
        way.put("building", "yes");
        way.put("addr:street", street);
        way.put("addr:housenumber", houseNumber);
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

    private static void testStaleFallbackIsCleared() throws Exception {
        clearHandoffPrefs();
        Config.getPref().put(HANDOFF_STREET_KEY, "Old Street");
        Config.getPref().put(HANDOFF_POSTCODE_KEY, "11111");
        Config.getPref().putBoolean(HANDOFF_PENDING_KEY, true);
        Config.getPref().put(HANDOFF_TIMESTAMP_KEY, Long.toString(System.currentTimeMillis() - (10L * 60L * 1000L)));
        Config.getPref().put(HANDOFF_SESSION_KEY, "other-session");

        invokeClearStaleFallback();

        assertEmpty(Config.getPref().get(HANDOFF_STREET_KEY, ""), "stale street should be cleared");
        assertEmpty(Config.getPref().get(HANDOFF_POSTCODE_KEY, ""), "stale postcode should be cleared");
        assertFalse(Config.getPref().getBoolean(HANDOFF_PENDING_KEY, false), "stale pending flag should be cleared");
    }

    private static void testFreshFallbackIsKept() throws Exception {
        clearHandoffPrefs();
        Config.getPref().put(HANDOFF_STREET_KEY, "Fresh Street");
        Config.getPref().put(HANDOFF_POSTCODE_KEY, "22222");
        Config.getPref().putBoolean(HANDOFF_PENDING_KEY, true);
        Config.getPref().put(HANDOFF_TIMESTAMP_KEY, Long.toString(System.currentTimeMillis()));
        Config.getPref().put(HANDOFF_SESSION_KEY, "");

        invokeClearStaleFallback();

        assertEquals("Fresh Street", Config.getPref().get(HANDOFF_STREET_KEY, ""), "fresh street should stay");
        assertEquals("22222", Config.getPref().get(HANDOFF_POSTCODE_KEY, ""), "fresh postcode should stay");
        assertTrue(Config.getPref().getBoolean(HANDOFF_PENDING_KEY, false), "fresh pending flag should stay");
    }

    private static void testReflectionHandoffClearsFallback() throws Exception {
        clearHandoffPrefs();
        Config.getPref().put(HANDOFF_STREET_KEY, "Will Be Cleared");
        Config.getPref().put(HANDOFF_POSTCODE_KEY, "33333");
        Config.getPref().putBoolean(HANDOFF_PENDING_KEY, true);
        Config.getPref().put(HANDOFF_TIMESTAMP_KEY, Long.toString(System.currentTimeMillis()));
        Config.getPref().put(HANDOFF_SESSION_KEY, "session-for-test");
        Config.getPref().putBoolean(FORCE_PREFERENCE_FALLBACK_KEY, false);

        Method publish = BuildingSplitterBridge.class.getDeclaredMethod("publishAddressContext", String.class, String.class);
        publish.setAccessible(true);
        publish.invoke(null, "Live Street", "44444");

        assertEmpty(Config.getPref().get(HANDOFF_STREET_KEY, ""), "fallback street should be cleared on successful reflection handoff");
        assertEmpty(Config.getPref().get(HANDOFF_POSTCODE_KEY, ""), "fallback postcode should be cleared on successful reflection handoff");
        assertFalse(Config.getPref().getBoolean(HANDOFF_PENDING_KEY, false), "fallback pending should be cleared on successful reflection handoff");
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

    private static void clearHandoffPrefs() {
        BuildingSplitterBridge.clearPreferenceFallback();
        Config.getPref().put(FORCE_PREFERENCE_FALLBACK_KEY, null);
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

    private static void invokeClearStaleFallback() throws Exception {
        Method clear = BuildingSplitterBridge.class.getDeclaredMethod("clearStalePreferenceFallback");
        clear.setAccessible(true);
        clear.invoke(null);
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
            clearHandoffPrefs();
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

    private static void assertEmpty(String actual, String message) {
        if (actual != null && !actual.trim().isEmpty()) {
            throw new AssertionError(message + " actual=<" + actual + ">");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}


