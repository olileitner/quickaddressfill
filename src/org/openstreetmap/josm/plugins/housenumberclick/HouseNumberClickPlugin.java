package org.openstreetmap.josm.plugins.housenumberclick;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/**
 * Plugin entry point that wires the main action, registers sidebar dialogs on map-frame creation,
 * and performs one-time toolbar migration.
 */
public class HouseNumberClickPlugin extends Plugin {

    private static final String TOOLBAR_ID = "housenumberclick";
    private final HouseNumberClickAction action;

    public HouseNumberClickPlugin(PluginInformation info) {
        super(info);
        this.action = new HouseNumberClickAction();
        MainApplication.getMenu().dataMenu.add(action);
        ensureToolbarButtonAddedOnce();
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        action.onMapFrameInitialized(newFrame);
    }

    private void ensureToolbarButtonAddedOnce() {
        if (HouseNumberClickPreferences.TOOLBAR_BUTTON_ADDED.get()) {
            return;
        }

        ToolbarPreferences toolbar = MainApplication.getToolbar();
        if (toolbar == null) {
            return;
        }

        toolbar.addCustomButton(TOOLBAR_ID, -1, false);
        HouseNumberClickPreferences.TOOLBAR_BUTTON_ADDED.put(true);
    }
}
