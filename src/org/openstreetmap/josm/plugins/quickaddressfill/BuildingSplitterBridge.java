package org.openstreetmap.josm.plugins.quickaddressfill;

import java.util.Locale;

import javax.swing.Action;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.Logging;

final class BuildingSplitterBridge {

    private static final String TARGET_PLUGIN_NAME = "buildingsplitter";

    private BuildingSplitterBridge() {
        // Utility class
    }

    static boolean activateBuildingSplitter() {
        try {
            if (!BuildingSplitterDetector.isBuildingSplitterAvailable()) {
                Logging.debug("QuickAddressFill: BuildingSplitter activation skipped because plugin is not available.");
                return false;
            }

            MapFrame map = MainApplication.getMap();
            if (map == null || map.allMapModeButtons == null) {
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
                    Logging.debug("QuickAddressFill: BuildingSplitter map mode detected and activated.");
                    return true;
                }
            }
            Logging.debug("QuickAddressFill: BuildingSplitter activation failed after scanning map modes.");
        } catch (RuntimeException ex) {
            Logging.debug("QuickAddressFill: BuildingSplitter activation failed due to runtime error: {0}", ex.getMessage());
            return false;
        }

        return false;
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
}

