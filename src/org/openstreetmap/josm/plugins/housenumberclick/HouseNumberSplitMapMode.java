package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Cursor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

final class HouseNumberSplitMapMode extends MapMode {

    enum InteractionKind {
        LINE_SPLIT,
        TERRACE_CLICK
    }

    private static final int CURSOR_HOTSPOT_X = 15;
    private static final int CURSOR_HOTSPOT_Y = 29;

    private final StreetModeController controller;
    private InteractionKind interactionKind;
    private int terraceParts;
    private final DragLineOverlay dragLineOverlay = new DragLineOverlay();
    private final KeyAdapter splitKeyListener;
    private final KeyEventDispatcher splitKeyDispatcher;
    private LatLon dragStart;
    private LatLon dragCurrent;
    private boolean flowCompleted;
    private boolean dragOverlayAttached;
    private Way activeTerraceSourceBuilding;
    private int activeTerraceUndoStart = -1;
    private boolean splitDispatcherRegistered;

    HouseNumberSplitMapMode(StreetModeController controller, InteractionKind interactionKind, int terraceParts) {
        super(
                I18n.tr("HouseNumberClick Split Mode"),
                "housenumberclick_split",
                interactionKind == InteractionKind.TERRACE_CLICK
                        ? I18n.tr("Click inside one building to create row houses")
                        : I18n.tr("Drag a line across one building to split it"),
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        );
        this.controller = controller;
        this.interactionKind = interactionKind == null ? InteractionKind.LINE_SPLIT : interactionKind;
        this.terraceParts = terraceParts >= 2 ? terraceParts : 2;
        this.splitKeyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                // Handled centrally through the key dispatcher on KEY_PRESSED.
            }
        };
        this.splitKeyDispatcher = this::handleGlobalKeyEvent;
    }

    void configureFor(InteractionKind nextInteractionKind, int nextTerraceParts) {
        this.interactionKind = nextInteractionKind == null ? InteractionKind.LINE_SPLIT : nextInteractionKind;
        this.terraceParts = nextTerraceParts >= 2 ? nextTerraceParts : 2;
        dragStart = null;
        dragCurrent = null;
        activeTerraceSourceBuilding = null;
        activeTerraceUndoStart = -1;
        applyInteractionPresentation();
        repaintMapView();
    }

    InteractionKind getInteractionKind() {
        return interactionKind;
    }

    @Override
    public void enterMode() {
        super.enterMode();
        flowCompleted = false;
        registerSplitKeyDispatcher();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.addMouseListener(this);
            map.mapView.addMouseMotionListener(this);
            map.mapView.addKeyListener(splitKeyListener);
            applyInteractionPresentation();
            map.mapView.requestFocusInWindow();
        }
    }

    @Override
    public void exitMode() {
        boolean notifyCancelled = !flowCompleted;
        unregisterSplitKeyDispatcher();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.removeMouseListener(this);
            map.mapView.removeMouseMotionListener(this);
            map.mapView.removeKeyListener(splitKeyListener);
            if (dragOverlayAttached) {
                map.mapView.removeTemporaryLayer(dragLineOverlay);
                dragOverlayAttached = false;
            }
            map.mapView.setCursor(Cursor.getDefaultCursor());
        }
        dragStart = null;
        dragCurrent = null;
        activeTerraceSourceBuilding = null;
        activeTerraceUndoStart = -1;
        super.exitMode();
        if (notifyCancelled) {
            flowCompleted = true;
            controller.onInternalSplitFlowFinished(StreetModeController.SplitFlowOutcome.CANCELLED);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (interactionKind != InteractionKind.LINE_SPLIT) {
            return;
        }
        if (!isLeftButton(e)) {
            return;
        }
        dragStart = toLatLon(e);
        dragCurrent = dragStart;
        repaintMapView();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (interactionKind != InteractionKind.LINE_SPLIT) {
            return;
        }
        if (dragStart == null) {
            return;
        }
        LatLon next = toLatLon(e);
        if (next != null) {
            dragCurrent = next;
            repaintMapView();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (interactionKind == InteractionKind.TERRACE_CLICK) {
            if (!isLeftButton(e)) {
                return;
            }
            Way clickedBuilding = resolveClickedBuilding(e);
            int undoBaseline = UndoRedoHandler.getInstance().getUndoCommands().size();
            TerraceSplitResult result = controller.executeInternalTerraceSplitAtClick(clickedBuilding, terraceParts);
            if (result.isSuccess()) {
                activeTerraceSourceBuilding = clickedBuilding;
                activeTerraceUndoStart = undoBaseline;
                // Keep terrace mode active; user confirms finish with Enter.
                MapFrame map = MainApplication.getMap();
                if (map != null && map.mapView != null) {
                    map.mapView.requestFocusInWindow();
                }
            } else {
                activeTerraceSourceBuilding = null;
                activeTerraceUndoStart = -1;
            }
            return;
        }

        // Release events can arrive as NOBUTTON on some platforms; rely on left press state.
        if (dragStart == null) {
            return;
        }

        LatLon splitStart = dragStart;
        LatLon dragEnd = toLatLon(e);
        if (dragEnd == null) {
            dragEnd = dragCurrent;
        }

        dragStart = null;
        dragCurrent = null;
        repaintMapView();

        if (dragEnd != null) {
            SingleSplitResult result = controller.executeInternalSingleSplit(splitStart, dragEnd);
            if (result.isSuccess()) {
                completeWithOutcome(StreetModeController.SplitFlowOutcome.SUCCESS);
            }
        } else {
            completeWithOutcome(StreetModeController.SplitFlowOutcome.CANCELLED);
        }
    }

    private void completeWithOutcome(StreetModeController.SplitFlowOutcome outcome) {
        if (flowCompleted) {
            return;
        }
        flowCompleted = true;
        controller.onInternalSplitFlowFinished(outcome);
    }

    private boolean isLeftButton(MouseEvent event) {
        return event != null && SwingUtilities.isLeftMouseButton(event);
    }

    private boolean handleGlobalKeyEvent(KeyEvent event) {
        if (!isModeActiveOnMap(MainApplication.getMap()) || interactionKind != InteractionKind.TERRACE_CLICK || event == null) {
            return false;
        }
        if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()) {
            return false;
        }
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_ENTER || keyCodeToDigit(keyCode) >= 0) {
            handleTerraceModeKey(event);
            event.consume();
            return true;
        }
        return false;
    }

    private void registerSplitKeyDispatcher() {
        if (splitDispatcherRegistered) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(splitKeyDispatcher);
        splitDispatcherRegistered = true;
    }

    private void unregisterSplitKeyDispatcher() {
        if (!splitDispatcherRegistered) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(splitKeyDispatcher);
        splitDispatcherRegistered = false;
    }

    private boolean isModeActiveOnMap(MapFrame map) {
        return map != null && map.mapMode == this;
    }

    private Cursor createSplitCursor() {
        try {
            Icon icon = ImageProvider.get("mapmode", "housenumberclick_split");
            if (icon instanceof ImageIcon) {
                Image image = ((ImageIcon) icon).getImage();
                if (image != null) {
                    return Toolkit.getDefaultToolkit().createCustomCursor(
                            image,
                            new Point(CURSOR_HOTSPOT_X, CURSOR_HOTSPOT_Y),
                            "hnc-split-cursor"
                    );
                }
            }
        } catch (RuntimeException ex) {
            Logging.debug(ex);
        }
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private LatLon toLatLon(MouseEvent event) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || event == null) {
            return null;
        }
        return map.mapView.getLatLon(event.getX(), event.getY());
    }

    private void repaintMapView() {
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.repaint();
        }
    }

    private void applyInteractionPresentation() {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return;
        }

        if (interactionKind == InteractionKind.LINE_SPLIT) {
            if (!dragOverlayAttached) {
                dragOverlayAttached = map.mapView.addTemporaryLayer(dragLineOverlay);
            }
            map.mapView.setCursor(createSplitCursor());
        } else {
            if (dragOverlayAttached) {
                map.mapView.removeTemporaryLayer(dragLineOverlay);
                dragOverlayAttached = false;
            }
            map.mapView.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
    }

    private Way resolveClickedBuilding(MouseEvent event) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || event == null || MainApplication.getLayerManager() == null) {
            return null;
        }
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        LatLon clickLatLon = map.mapView.getLatLon(event.getX(), event.getY());
        if (dataSet == null || clickLatLon == null || map.mapView.getRealBounds() == null) {
            return null;
        }

        Way best = null;
        double bestArea = Double.MAX_VALUE;
        for (Way way : dataSet.searchWays(map.mapView.getRealBounds().toBBox())) {
            if (way == null || !way.isUsable() || !way.isClosed() || !way.hasKey("building")) {
                continue;
            }
            if (!containsClickPoint(way, map, event.getPoint(), clickLatLon)) {
                continue;
            }
            double area = way.getBBox().area();
            if (area < bestArea) {
                best = way;
                bestArea = area;
            }
        }
        return best;
    }

    private void handleTerraceModeKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE) {
            completeWithOutcome(StreetModeController.SplitFlowOutcome.CANCELLED);
            event.consume();
            return;
        }
        if (keyCode == KeyEvent.VK_ENTER) {
            completeWithOutcome(StreetModeController.SplitFlowOutcome.CANCELLED);
            event.consume();
            return;
        }

        int digit = keyCodeToDigit(keyCode);
        if (digit < 0) {
            return;
        }

        // Number keys directly choose parts to avoid accidental multi-digit values (e.g. 2 -> 24).
        terraceParts = Math.max(2, digit);
        rerunActiveTerraceSplit();
        event.consume();
    }

    private void rerunActiveTerraceSplit() {
        if (activeTerraceSourceBuilding == null || activeTerraceUndoStart < 0) {
            return;
        }

        int currentUndoSize = UndoRedoHandler.getInstance().getUndoCommands().size();
        int undoDelta = currentUndoSize - activeTerraceUndoStart;
        if (undoDelta > 0) {
            UndoRedoHandler.getInstance().undo(undoDelta);
        }

        TerraceSplitResult result = controller.executeInternalTerraceSplitAtClick(activeTerraceSourceBuilding, terraceParts);
        if (!result.isSuccess()) {
            activeTerraceSourceBuilding = null;
            activeTerraceUndoStart = -1;
            return;
        }

        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.requestFocusInWindow();
        }
    }

    private int keyCodeToDigit(int keyCode) {
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            return keyCode - KeyEvent.VK_0;
        }
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return keyCode - KeyEvent.VK_NUMPAD0;
        }
        return -1;
    }


    private boolean containsClickPoint(Way way, MapFrame map, Point clickPoint, LatLon clickLatLon) {
        if (way == null || map == null || map.mapView == null || clickPoint == null || clickLatLon == null) {
            return false;
        }
        if (!way.getBBox().bounds(clickLatLon) || way.getNodesCount() < 4) {
            return false;
        }

        Polygon polygon = new Polygon();
        for (Node node : way.getNodes()) {
            if (node == null || node.getCoor() == null) {
                return false;
            }
            Point point = map.mapView.getPoint(node);
            if (point == null) {
                return false;
            }
            polygon.addPoint(point.x, point.y);
        }
        return polygon.contains(clickPoint);
    }

    private final class DragLineOverlay implements MapViewPaintable {
        @Override
        public void paint(Graphics2D graphics, org.openstreetmap.josm.gui.MapView mapView, Bounds bounds) {
            if (dragStart == null || dragCurrent == null || graphics == null || mapView == null) {
                return;
            }

            Point from = mapView.getPoint(dragStart);
            Point to = mapView.getPoint(dragCurrent);
            if (from == null || to == null) {
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(0, 0, 0, 190));
                g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(from.x, from.y, to.x, to.y);

                g.setColor(new Color(255, 255, 255, 230));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(from.x, from.y, to.x, to.y);
            } finally {
                g.dispose();
            }
        }
    }
}
