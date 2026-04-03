package org.openstreetmap.josm.plugins.quickaddressfill;

import java.util.Locale;
import java.util.UUID;

import javax.swing.Action;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

final class BuildingSplitterBridge {

    private static final String TARGET_PLUGIN_NAME = "buildingsplitter";
    private static final String ADDRESS_CONTEXT_BRIDGE_CLASS =
        "org.openstreetmap.josm.plugins.buildingsplitter.AddressContextBridge";
    private static final String HANDOFF_STREET_KEY = "quickaddressfill.buildingsplitter.handoff.street";
    private static final String HANDOFF_POSTCODE_KEY = "quickaddressfill.buildingsplitter.handoff.postcode";
    private static final String HANDOFF_PENDING_KEY = "quickaddressfill.buildingsplitter.handoff.pending";
    private static final String HANDOFF_TIMESTAMP_KEY = "quickaddressfill.buildingsplitter.handoff.timestamp";
    private static final String HANDOFF_SESSION_KEY = "quickaddressfill.buildingsplitter.handoff.session";
    private static final String FORCE_PREFERENCE_FALLBACK_KEY =
        "quickaddressfill.buildingsplitter.forcePreferenceFallback";
    private static final long HANDOFF_MAX_AGE_MILLIS = 5L * 60L * 1000L;
    private static final String HANDOFF_SESSION_ID = UUID.randomUUID().toString();

    private BuildingSplitterBridge() {
        // Utility class
    }

    static boolean activateBuildingSplitter() {
        return activateBuildingSplitter("", "");
    }

    static boolean activateBuildingSplitter(String street, String postcode) {
        try {
            clearStalePreferenceFallback();
            if (!BuildingSplitterDetector.isBuildingSplitterAvailable()) {
                Logging.info("QuickAddressFill: BuildingSplitter activation skipped because plugin is not available.");
                return false;
            }

            publishAddressContext(street, postcode);

            MapFrame map = MainApplication.getMap();
            if (map == null || map.allMapModeButtons == null) {
                Logging.info("QuickAddressFill: BuildingSplitter activation failed because map UI is unavailable.");
                return false;
            }

            for (IconToggleButton button : map.allMapModeButtons) {
                if (button == null) {
                    continue;
                }
                Action action = button.getAction();
                if (!(action instanceof MapMode)) {
                    continue;
                }

                MapMode mapMode = (MapMode) action;
                if (!isBuildingSplitterMapMode(mapMode, button.getActionName())) {
                    continue;
                }

                if (map.selectMapMode(mapMode)) {
                    Logging.info("QuickAddressFill: BuildingSplitter map mode detected and activated.");
                    return true;
                }
            }
            Logging.info("QuickAddressFill: BuildingSplitter activation failed after scanning map modes.");
        } catch (RuntimeException ex) {
            Logging.warn("QuickAddressFill BuildingSplitterBridge.activateBuildingSplitter: runtime failure, street={0}, postcode={1}",
                    normalizeHandoffValue(street), normalizeHandoffValue(postcode));
            Logging.debug(ex);
            return false;
        }

        return false;
    }

    private static void publishAddressContext(String street, String postcode) {
        String normalizedStreet = normalizeHandoffValue(street);
        String normalizedPostcode = normalizeHandoffValue(postcode);
        Logging.info("QuickAddressFill: Address context handoff attempt started.");

        if (Config.getPref().getBoolean(FORCE_PREFERENCE_FALLBACK_KEY, false)) {
            Logging.info("QuickAddressFill: Reflection handoff disabled by QA preference; using fallback.");
            writePreferenceFallback(normalizedStreet, normalizedPostcode);
            return;
        }

        try {
            Class<?> bridgeClass = Class.forName(ADDRESS_CONTEXT_BRIDGE_CLASS);
            bridgeClass
                .getMethod("setAddressContext", String.class, String.class)
                .invoke(null, normalizedStreet, normalizedPostcode);
            clearPreferenceFallback();
            Logging.info("QuickAddressFill: Address context reflection handoff succeeded.");
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            Logging.info("QuickAddressFill: Address context reflection handoff unavailable.");
            writePreferenceFallback(normalizedStreet, normalizedPostcode);
        } catch (ReflectiveOperationException | LinkageError ex) {
            Logging.warn("QuickAddressFill: Address context reflection handoff failed.");
            Logging.debug(ex);
            writePreferenceFallback(normalizedStreet, normalizedPostcode);
        }
    }

    private static void writePreferenceFallback(String street, String postcode) {
        String normalizedStreet = normalizeHandoffValue(street);
        String normalizedPostcode = normalizeHandoffValue(postcode);
        if (normalizedStreet.isEmpty() && normalizedPostcode.isEmpty()) {
            clearPreferenceFallback();
            Logging.info("QuickAddressFill: Preference fallback not written because context is empty.");
            return;
        }

        Config.getPref().put(HANDOFF_STREET_KEY, normalizedStreet);
        Config.getPref().put(HANDOFF_POSTCODE_KEY, normalizedPostcode);
        Config.getPref().putBoolean(HANDOFF_PENDING_KEY, true);
        Config.getPref().put(HANDOFF_TIMESTAMP_KEY, Long.toString(System.currentTimeMillis()));
        Config.getPref().put(HANDOFF_SESSION_KEY, HANDOFF_SESSION_ID);
        Logging.info("QuickAddressFill: Address context preference fallback written.");
    }

    static void clearPreferenceFallback() {
        Config.getPref().put(HANDOFF_STREET_KEY, null);
        Config.getPref().put(HANDOFF_POSTCODE_KEY, null);
        Config.getPref().put(HANDOFF_PENDING_KEY, null);
        Config.getPref().put(HANDOFF_TIMESTAMP_KEY, null);
        Config.getPref().put(HANDOFF_SESSION_KEY, null);
    }

    private static void clearStalePreferenceFallback() {
        if (!Config.getPref().getBoolean(HANDOFF_PENDING_KEY, false)) {
            return;
        }

        String rawTimestamp = normalizeHandoffValue(Config.getPref().get(HANDOFF_TIMESTAMP_KEY, ""));
        long timestamp = parseTimestamp(rawTimestamp);
        String session = normalizeHandoffValue(Config.getPref().get(HANDOFF_SESSION_KEY, ""));
        boolean differentSession = !session.isEmpty() && !HANDOFF_SESSION_ID.equals(session);
        boolean expired = timestamp <= 0 || (System.currentTimeMillis() - timestamp) > HANDOFF_MAX_AGE_MILLIS;

        if (!differentSession && !expired) {
            return;
        }

        Logging.info("QuickAddressFill: Clearing stale BuildingSplitter handoff fallback (differentSession={0}, expired={1}).",
                differentSession, expired);
        clearPreferenceFallback();
    }

    private static long parseTimestamp(String rawTimestamp) {
        if (rawTimestamp.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(rawTimestamp);
        } catch (NumberFormatException ex) {
            Logging.warn("QuickAddressFill BuildingSplitterBridge.parseTimestamp: invalid handoff timestamp: {0}", rawTimestamp);
            Logging.debug(ex);
            return -1L;
        }
    }

    private static boolean isBuildingSplitterMapMode(MapMode mapMode, String actionName) {
        if (mapMode == null) {
            return false;
        }

        // Prioritize user-facing labels first to reduce accidental matches.
        String modeName = normalize((String) mapMode.getValue(Action.NAME));
        if (containsBuildingSplitter(modeName)) {
            return true;
        }

        String normalizedActionName = normalize(actionName);
        if (containsBuildingSplitter(normalizedActionName)) {
            return true;
        }

        String simpleName = normalize(mapMode.getClass().getSimpleName());
        if (containsBuildingSplitter(simpleName)) {
            return true;
        }

        String className = normalize(mapMode.getClass().getName());
        return containsBuildingSplitter(className);
    }

    private static boolean containsBuildingSplitter(String value) {
        if (value.isEmpty()) {
            return false;
        }

        String collapsed = collapseSeparators(value);

        if (isStrongBuildingSplitterMatch(value, collapsed)) {
            return true;
        }

        // Fallback only after stronger checks fail.
        return value.contains("building") && value.contains("split");
    }

    private static boolean isStrongBuildingSplitterMatch(String normalizedValue, String collapsedValue) {
        return TARGET_PLUGIN_NAME.equals(normalizedValue)
                || TARGET_PLUGIN_NAME.equals(collapsedValue)
                || normalizedValue.contains(TARGET_PLUGIN_NAME)
                || collapsedValue.contains(TARGET_PLUGIN_NAME);
    }

    private static String collapseSeparators(String value) {
        return value.replace("-", "").replace("_", "").replace(" ", "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHandoffValue(String value) {
        return value == null ? "" : value.trim();
    }
}
