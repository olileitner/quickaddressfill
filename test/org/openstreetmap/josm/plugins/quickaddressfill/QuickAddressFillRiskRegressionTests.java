package org.openstreetmap.josm.plugins.quickaddressfill;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.spi.preferences.Config;

public final class QuickAddressFillRiskRegressionTests {

    private static final String HANDOFF_STREET_KEY = "quickaddressfill.buildingsplitter.handoff.street";
    private static final String HANDOFF_POSTCODE_KEY = "quickaddressfill.buildingsplitter.handoff.postcode";
    private static final String HANDOFF_PENDING_KEY = "quickaddressfill.buildingsplitter.handoff.pending";
    private static final String HANDOFF_TIMESTAMP_KEY = "quickaddressfill.buildingsplitter.handoff.timestamp";
    private static final String HANDOFF_SESSION_KEY = "quickaddressfill.buildingsplitter.handoff.session";
    private static final String FORCE_PREFERENCE_FALLBACK_KEY = "quickaddressfill.buildingsplitter.forcePreferenceFallback";
    private static final String RELATION_SCAN_LIMIT_KEY = BuildingResolver.PREF_RELATION_SCAN_LIMIT;
    private static final String WAY_SCAN_LIMIT_KEY = BuildingResolver.PREF_WAY_SCAN_LIMIT;

    private QuickAddressFillRiskRegressionTests() {
        // Utility class
    }

    public static void main(String[] args) throws Exception {
        ensurePreferences();
        run("AddressSelection normalizes values and step", QuickAddressFillRiskRegressionTests::testAddressSelectionNormalization);
        run("DataSet transition detection is stable", QuickAddressFillRiskRegressionTests::testDataSetChangeDetection);
        run("BuildingSplitter stale fallback is discarded", QuickAddressFillRiskRegressionTests::testStaleFallbackIsCleared);
        run("BuildingSplitter fresh fallback is kept", QuickAddressFillRiskRegressionTests::testFreshFallbackIsKept);
        run("Successful reflection handoff clears fallback", QuickAddressFillRiskRegressionTests::testReflectionHandoffClearsFallback);
        run("Scan limit defaults are used when unset", QuickAddressFillRiskRegressionTests::testScanLimitDefaultsWhenUnset);
        run("Invalid scan limit preferences fall back to defaults", QuickAddressFillRiskRegressionTests::testInvalidScanLimitPreferencesFallBack);
        run("Duplicate click detection blocks true duplicates", QuickAddressFillRiskRegressionTests::testDuplicateClicksAreDetected);
        run("Duplicate click detection keeps rapid distinct clicks", QuickAddressFillRiskRegressionTests::testRapidDistinctClicksAreKept);
        System.out.println("All QuickAddressFill risk regression tests passed.");
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
                QuickAddressFillStreetMapMode.DEFAULT_RELATION_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredRelationScanLimit(),
                "relation scan limit should use default when preference is missing"
        );
        assertEquals(
                QuickAddressFillStreetMapMode.DEFAULT_WAY_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredWayScanLimit(),
                "way scan limit should use default when preference is missing"
        );
    }

    private static void testInvalidScanLimitPreferencesFallBack() {
        Config.getPref().put(RELATION_SCAN_LIMIT_KEY, "not-a-number");
        Config.getPref().put(WAY_SCAN_LIMIT_KEY, "-9");

        assertEquals(
                QuickAddressFillStreetMapMode.DEFAULT_RELATION_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredRelationScanLimit(),
                "invalid relation limit should fall back to default"
        );
        assertEquals(
                QuickAddressFillStreetMapMode.DEFAULT_WAY_SCAN_CANDIDATES,
                BuildingResolver.getConfiguredWayScanLimit(),
                "invalid way limit should fall back to default"
        );
    }

    private static void testDuplicateClicksAreDetected() throws Exception {
        QuickAddressFillStreetMapMode mode = new QuickAddressFillStreetMapMode(new StreetModeController());

        MouseEvent first = newMouseRelease(1000L, 50, 50, MouseEvent.BUTTON1, 0);
        MouseEvent duplicate = newMouseRelease(1060L, 50, 50, MouseEvent.BUTTON1, 0);

        assertFalse(invokeDuplicateCheck(mode, first), "first click must not be duplicate");
        assertTrue(invokeDuplicateCheck(mode, duplicate), "same click fingerprint in short window must be duplicate");
    }

    private static void testRapidDistinctClicksAreKept() throws Exception {
        QuickAddressFillStreetMapMode mode = new QuickAddressFillStreetMapMode(new StreetModeController());

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

    private static boolean invokeDuplicateCheck(QuickAddressFillStreetMapMode mode, MouseEvent event) throws Exception {
        Method method = QuickAddressFillStreetMapMode.class.getDeclaredMethod("isDuplicateReleaseEvent", MouseEvent.class);
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


