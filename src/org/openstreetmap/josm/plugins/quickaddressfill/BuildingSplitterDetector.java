package org.openstreetmap.josm.plugins.quickaddressfill;

import java.util.List;
import java.util.Locale;

import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.PluginInformation;

final class BuildingSplitterDetector {

    private static final String TARGET_PLUGIN_NAME = "buildingsplitter";

    private BuildingSplitterDetector() {
        // Utility class
    }

    static boolean isBuildingSplitterAvailable() {
        try {
            List<PluginInformation> plugins = PluginHandler.getPlugins();
            if (plugins != null && !plugins.isEmpty()) {
                for (PluginInformation plugin : plugins) {
                    String pluginName = normalizePluginName(plugin);
                    if (!matchesTargetPlugin(pluginName)) {
                        continue;
                    }
                    String rawPluginName = plugin == null ? null : plugin.getName();
                    if (rawPluginName != null && PluginHandler.getPlugin(rawPluginName) != null) {
                        return true;
                    }
                }
            }

            // Fallback: check canonical id directly.
            return PluginHandler.getPlugin(TARGET_PLUGIN_NAME) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String normalizePluginName(PluginInformation plugin) {
        if (plugin == null) {
            return "";
        }
        return normalize(plugin.getName());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesTargetPlugin(String pluginName) {
        if (pluginName.isEmpty()) {
            return false;
        }
        if (TARGET_PLUGIN_NAME.equals(pluginName)) {
            return true;
        }
        String collapsedName = pluginName.replace("-", "").replace("_", "");
        return TARGET_PLUGIN_NAME.equals(collapsedName);
    }
}


