package org.openstreetmap.josm.plugins.quickaddressfill;

import java.util.Locale;

import javax.swing.Action;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;

final class BuildingSplitterBridge {

    private static final String TARGET_PLUGIN_NAME = "buildingsplitter";

    private BuildingSplitterBridge() {
        // Utility class
    }

    static boolean activateBuildingSplitter() {
        try {
            if (!BuildingSplitterDetector.isBuildingSplitterAvailable()) {
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
                    return true;
                }
            }
        } catch (RuntimeException ex) {
            return false;
        }

        return false;
    }

    private static boolean isBuildingSplitterMapMode(MapMode mapMode, String actionName) {
        if (mapMode == null) {
            return false;
        }

        String className = normalize(mapMode.getClass().getName());
        if (containsBuildingSplitter(className)) {
            return true;
        }

        String simpleName = normalize(mapMode.getClass().getSimpleName());
        if (containsBuildingSplitter(simpleName)) {
            return true;
        }

        String modeName = normalize((String) mapMode.getValue(Action.NAME));
        if (containsBuildingSplitter(modeName)) {
            return true;
        }

        String normalizedActionName = normalize(actionName);
        return containsBuildingSplitter(normalizedActionName);
    }

    private static boolean containsBuildingSplitter(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.contains(TARGET_PLUGIN_NAME)) {
            return true;
        }
        String collapsed = value.replace("-", "").replace("_", "").replace(" ", "");
        if (collapsed.contains(TARGET_PLUGIN_NAME)) {
            return true;
        }
        return value.contains("building") && value.contains("split");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

