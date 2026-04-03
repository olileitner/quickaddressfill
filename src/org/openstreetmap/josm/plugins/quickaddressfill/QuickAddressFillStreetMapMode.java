package org.openstreetmap.josm.plugins.quickaddressfill;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.I18n;

final class QuickAddressFillStreetMapMode extends MapMode {

    private static final Pattern NUMERIC_HOUSE_NUMBER_PATTERN = Pattern.compile("^(\\d+)$");
    private static final Pattern NUMERIC_WITH_LETTER_SUFFIX_PATTERN = Pattern.compile("^(\\d+)([A-Za-z]+)$");
    private static final Pattern LETTER_HOUSE_NUMBER_PATTERN = Pattern.compile("^([A-Za-z]+)$");
    private static final Pattern HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN = Pattern.compile("^(\\d+)([A-Za-z]+)?$");

    private final StreetModeController controller;
    private final KeyAdapter escListener;
    private final KeyEventDispatcher ctrlKeyDispatcher;
    private String streetName;
    private String postcode;
    private String buildingType;
    private String houseNumber;
    private int houseNumberIncrementStep = 1;
    private String warningSuppressedStreet;
    private long lastHandledMouseWhen;
    private boolean controlPressed;
    private boolean ctrlDispatcherRegistered;

    QuickAddressFillStreetMapMode(StreetModeController controller) {
        super(
                I18n.tr("Quick Address Fill Street Mode"),
                "quickaddressfill",
                I18n.tr("Click buildings to set addr:street"),
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        );
        this.controller = controller;
        this.ctrlKeyDispatcher = this::handleGlobalKeyEvent;
        this.escListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    setControlPressed(true);
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    controller.deactivate();
                    e.consume();
                } else if (isPlusShortcut(e)) {
                    boolean letterMode = hasLetterSuffix(houseNumber);
                    if (letterMode ? incrementHouseNumberLetterByOne() : incrementHouseNumberNumberByOne()) {
                        refreshModePresentation(letterMode
                                ? I18n.tr("House number letter increased.")
                                : I18n.tr("House number increased."));
                    } else {
                        updateStatusLine(letterMode
                                ? I18n.tr("House number letter could not be increased.")
                                : I18n.tr("House number could not be increased."));
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_L) {
                    if (toggleLetterSuffixOnHouseNumber()) {
                        refreshModePresentation(I18n.tr("House number letter toggle applied."));
                    } else {
                        updateStatusLine(I18n.tr("House number letter toggle is only available for numeric house numbers."));
                    }
                    e.consume();
                } else if (isMinusShortcut(e)) {
                    boolean letterMode = hasLetterSuffix(houseNumber);
                    if (letterMode ? decrementHouseNumberLetterByOne() : decrementHouseNumberNumberByOne()) {
                        refreshModePresentation(letterMode
                                ? I18n.tr("House number letter decreased.")
                                : I18n.tr("House number decreased."));
                    } else {
                        updateStatusLine(letterMode
                                ? I18n.tr("House number letter could not be decreased.")
                                : I18n.tr("House number could not be decreased."));
                    }
                    e.consume();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    setControlPressed(false);
                }
            }
        };
    }

    void setAddressValues(String streetName, String postcode, String buildingType, String houseNumber, int houseNumberIncrementStep) {
        String normalizedStreet = normalize(streetName);
        if (!normalizedStreet.equals(this.streetName)) {
            warningSuppressedStreet = null;
        }
        this.streetName = normalizedStreet;
        this.postcode = normalize(postcode);
        this.buildingType = normalize(buildingType);
        this.houseNumber = normalize(houseNumber);
        this.houseNumberIncrementStep = normalizeIncrementStep(houseNumberIncrementStep);
        if (containsLetter(this.houseNumber) && this.houseNumberIncrementStep != 1) {
            this.houseNumberIncrementStep = 1;
        }
        if (isModeActiveOnMap(MainApplication.getMap())) {
            refreshModePresentation(null);
        }
    }

    @Override
    public void enterMode() {
        super.enterMode();
        registerCtrlKeyDispatcher();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.addKeyListener(escListener);
            map.mapView.addMouseListener(this);
            map.mapView.requestFocusInWindow();
        }
        controlPressed = false;
        controller.notifyModeStateChanged(true);
        refreshModePresentation(null);
    }

    @Override
    public void exitMode() {
        unregisterCtrlKeyDispatcher();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.removeKeyListener(escListener);
            map.mapView.removeMouseListener(this);
            map.mapView.setCursor(Cursor.getDefaultCursor());
        }
        controlPressed = false;
        if (map != null && map.statusLine != null) {
            map.statusLine.setHelpText(this, I18n.tr("QAF PAUSED"));
        }
        controller.notifyModeStateChanged(false);
        super.exitMode();
    }

    private boolean handleGlobalKeyEvent(KeyEvent e) {
        if (!isModeActiveOnMap(MainApplication.getMap())) {
            return false;
        }

        int id = e.getID();
        if (id == KeyEvent.KEY_PRESSED || id == KeyEvent.KEY_RELEASED) {
            if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                setControlPressed(id == KeyEvent.KEY_PRESSED);
            } else {
                setControlPressed(e.isControlDown());
            }
        }
        return false;
    }

    private void registerCtrlKeyDispatcher() {
        if (ctrlDispatcherRegistered) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ctrlKeyDispatcher);
        ctrlDispatcherRegistered = true;
    }

    private void unregisterCtrlKeyDispatcher() {
        if (!ctrlDispatcherRegistered) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(ctrlKeyDispatcher);
        ctrlDispatcherRegistered = false;
    }

    @Override
    public String getModeHelpText() {
        return I18n.tr("Left-click applies tags, Ctrl+left-click reads building data or street name, + / - change number or suffix depending on current house number, L toggles letter suffix.");
    }

    private boolean isPlusShortcut(KeyEvent e) {
        int keyCode = e.getKeyCode();
        return keyCode == KeyEvent.VK_PLUS
                || keyCode == KeyEvent.VK_ADD
                || (keyCode == KeyEvent.VK_EQUALS && e.isShiftDown())
                || e.getKeyChar() == '+';
    }

    private boolean isMinusShortcut(KeyEvent e) {
        int keyCode = e.getKeyCode();
        return keyCode == KeyEvent.VK_MINUS
                || keyCode == KeyEvent.VK_SUBTRACT
                || e.getKeyChar() == '-'
                || e.getKeyChar() == '_';
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (e.isControlDown()) {
                handleSecondaryClick(e);
            } else {
                handlePrimaryClick(e);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // Intentionally handled on mouseReleased only to avoid duplicate apply/increment per click.
    }

    private void handlePrimaryClick(MouseEvent e) {
        if (streetName == null || streetName.isBlank()) {
            updateStatusLine(I18n.tr("No street selected."));
            return;
        }
        if (e.getWhen() == lastHandledMouseWhen) {
            return;
        }
        lastHandledMouseWhen = e.getWhen();

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return;
        }

        OsmPrimitive building = resolveBuildingAtClick(map, e);
        if (building == null) {
            updateStatusLine(I18n.tr("No building detected"));
            return;
        }

        String overwrittenStreet = getOverwrittenStreetName(building);
        if (hasOverwriteConflict(building) && !isWarningSuppressedForStreet(overwrittenStreet)) {
            if (!confirmOverwrite(building, overwrittenStreet)) {
                updateStatusLine(I18n.tr("Overwrite cancelled."));
                e.consume();
                return;
            }
        }

        String appliedStreet = normalize(streetName);
        String appliedHouseNumber = normalize(houseNumber);
        boolean buildingTypeWasUsed = !normalize(buildingType).isEmpty();
        BuildingTagApplier.applyAddress(building, streetName, postcode, buildingType, houseNumber);
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet != null) {
            dataSet.setSelected(Collections.singleton(getSelectionTarget(building)));
        }

        if (buildingTypeWasUsed) {
            buildingType = "";
            controller.notifyBuildingTypeConsumed();
        }

        incrementHouseNumberAfterSuccessfulApply();

        String appliedMessage = I18n.tr("Applied: {0}, {1}", displayValue(appliedStreet), displayValue(appliedHouseNumber));
        refreshModePresentation(appliedMessage);
        e.consume();
    }

    private void handleSecondaryClick(MouseEvent e) {
        if (e.getWhen() == lastHandledMouseWhen) {
            return;
        }
        lastHandledMouseWhen = e.getWhen();

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return;
        }

        OsmPrimitive building = resolveBuildingAtClick(map, e);
        if (building == null) {
            String streetFromClick = resolveStreetNameAtClick(map, e);
            if (streetFromClick != null) {
                controller.updateAddressValues(streetFromClick, postcode, buildingType, "1");
                updateStatusLine(I18n.tr("Street name loaded from map: {0}", displayValue(streetFromClick)));
            } else {
                updateStatusLine(I18n.tr("No building detected"));
            }
            return;
        }

        String readStreet = normalize(building.get("addr:street"));
        String readPostcode = normalize(building.get("addr:postcode"));
        String readHouseNumber = normalize(building.get("addr:housenumber"));

        controller.updateAddressValues(readStreet, readPostcode, buildingType, readHouseNumber);

        updateStatusLine(
                I18n.tr(
                        "Address data loaded: street={0}, postcode={1}, house number={2}",
                        displayValue(readStreet),
                        displayValue(readPostcode),
                        displayValue(readHouseNumber)
                )
        );
        e.consume();
    }

    private String resolveStreetNameAtClick(MapFrame map, MouseEvent e) {
        if (map == null || map.mapView == null) {
            return null;
        }
        List<OsmPrimitive> nearby = map.mapView.getAllNearest(e.getPoint(), this::isNamedStreetCandidate);
        if (nearby == null || nearby.isEmpty()) {
            return null;
        }
        for (OsmPrimitive primitive : nearby) {
            if (!(primitive instanceof Way)) {
                continue;
            }
            String name = normalize(primitive.get("name"));
            if (!name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    private OsmPrimitive resolveBuildingAtClick(MapFrame map, MouseEvent e) {
        if (map == null || map.mapView == null) {
            return null;
        }
        List<OsmPrimitive> nearby = map.mapView.getAllNearest(e.getPoint(), this::isBuildingOrBuildingOutlineCandidate);
        OsmPrimitive building = chooseBuilding(nearby);
        if (building != null) {
            return building;
        }

        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            return null;
        }

        Relation relationBuilding = findRelationContainingClick(dataSet, map, e);
        if (relationBuilding != null) {
            return relationBuilding;
        }

        return findWayContainingClick(dataSet, map, e);
    }

    private Relation findRelationContainingClick(DataSet dataSet, MapFrame map, MouseEvent e) {
        if (dataSet == null || map == null || map.mapView == null || map.mapView.getRealBounds() == null) {
            return null;
        }

        for (Relation relation : dataSet.searchRelations(map.mapView.getRealBounds().toBBox())) {
            if (!isBuildingCandidate(relation)) {
                continue;
            }
            for (RelationMember member : relation.getMembers()) {
                String role = member.getRole();
                if (member.isWay() && (role == null || role.isEmpty() || "outer".equals(role)) && containsPoint(member.getWay(), map, e)) {
                    return relation;
                }
            }
        }
        return null;
    }

    private Way findWayContainingClick(DataSet dataSet, MapFrame map, MouseEvent e) {
        if (dataSet == null || map == null || map.mapView == null || map.mapView.getRealBounds() == null) {
            return null;
        }

        Way best = null;
        double bestArea = Double.MAX_VALUE;
        for (Way way : dataSet.searchWays(map.mapView.getRealBounds().toBBox())) {
            if (!isBuildingOrBuildingOutlineCandidate(way)) {
                continue;
            }
            if (!containsPoint(way, map, e)) {
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

    private boolean containsPoint(Way way, MapFrame map, MouseEvent e) {
        if (way == null || map == null || map.mapView == null || !way.isClosed() || way.getNodesCount() < 4) {
            return false;
        }

        LatLon clickLatLon = map.mapView.getLatLon(e.getX(), e.getY());
        if (clickLatLon == null || !way.getBBox().bounds(clickLatLon)) {
            return false;
        }

        Polygon polygon = new Polygon();
        for (Node node : way.getNodes()) {
            if (node == null || node.getCoor() == null) {
                return false;
            }
            java.awt.Point point = map.mapView.getPoint(node);
            if (point == null) {
                return false;
            }
            polygon.addPoint(point.x, point.y);
        }
        return polygon.contains(e.getPoint());
    }

    private boolean isBuildingCandidate(OsmPrimitive primitive) {
        return primitive != null
                && primitive.isUsable()
                && primitive.hasTag("building")
                && (primitive instanceof Way || primitive instanceof Relation);
    }

    private boolean isBuildingOrBuildingOutlineCandidate(OsmPrimitive primitive) {
        if (isBuildingCandidate(primitive)) {
            return true;
        }
        return primitive instanceof Way && isWayInBuildingRelation((Way) primitive);
    }

    private boolean isNamedStreetCandidate(OsmPrimitive primitive) {
        return primitive instanceof Way
                && primitive.isUsable()
                && primitive.hasTag("highway")
                && !normalize(primitive.get("name")).isEmpty();
    }

    private boolean isWayInBuildingRelation(Way way) {
        if (way == null || !way.isUsable()) {
            return false;
        }
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (referrer instanceof Relation && referrer.isUsable() && referrer.hasTag("building")) {
                return true;
            }
        }
        return false;
    }

    private Relation getBuildingRelationForWay(Way way) {
        if (way == null || !way.isUsable()) {
            return null;
        }
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (referrer instanceof Relation && referrer.isUsable() && referrer.hasTag("building")) {
                return (Relation) referrer;
            }
        }
        return null;
    }

    private OsmPrimitive chooseBuilding(List<OsmPrimitive> nearby) {
        if (nearby == null || nearby.isEmpty()) {
            return null;
        }

        // Prefer way buildings so selection feedback is immediately visible on map.
        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Way && primitive.hasTag("building")) {
                return primitive;
            }
        }

        // If only an untagged outer way is hit, resolve to its building relation.
        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Way) {
                Relation relation = getBuildingRelationForWay((Way) primitive);
                if (relation != null) {
                    return relation;
                }
            }
        }

        for (OsmPrimitive primitive : nearby) {
            if (primitive instanceof Relation && primitive.hasTag("building")) {
                return primitive;
            }
        }
        return null;
    }

    private OsmPrimitive getSelectionTarget(OsmPrimitive building) {
        if (!(building instanceof Relation)) {
            return building;
        }

        Relation relation = (Relation) building;
        List<Way> outers = new ArrayList<>();
        for (RelationMember member : relation.getMembers()) {
            String role = member.getRole();
            if (member.isWay() && (role == null || role.isEmpty() || "outer".equals(role))) {
                outers.add(member.getWay());
            }
        }

        if (!outers.isEmpty()) {
            return outers.get(0);
        }
        return building;
    }

    private boolean hasOverwriteConflict(OsmPrimitive building) {
        String existingStreet = normalize(building.get("addr:street"));
        String existingPostcode = normalize(building.get("addr:postcode"));

        boolean streetConflict = !existingStreet.isEmpty() && !existingStreet.equals(streetName);

        // Postcode is considered only when a new postcode is provided in the dialog.
        boolean postcodeConflict = !postcode.isEmpty()
                && !existingPostcode.isEmpty()
                && !existingPostcode.equals(postcode);

        return streetConflict || postcodeConflict;
    }

    private boolean confirmOverwrite(OsmPrimitive building, String overwrittenStreet) {
        String existingStreet = normalize(building.get("addr:street"));
        String existingPostcode = normalize(building.get("addr:postcode"));
        String existingHouseNumber = normalize(building.get("addr:housenumber"));

        List<Object[]> rows = new ArrayList<>();
        if (!existingStreet.isEmpty() && !existingStreet.equals(streetName)) {
            rows.add(new Object[] {"addr:street", displayValue(existingStreet), displayValue(streetName)});
        }
        if (!postcode.isEmpty() && !existingPostcode.isEmpty() && !existingPostcode.equals(postcode)) {
            rows.add(new Object[] {"addr:postcode", displayValue(existingPostcode), displayValue(postcode)});
        }
        if (!houseNumber.isEmpty() && !existingHouseNumber.isEmpty() && !existingHouseNumber.equals(houseNumber)) {
            rows.add(new Object[] {"addr:housenumber", displayValue(existingHouseNumber), displayValue(houseNumber)});
        }

        if (rows.isEmpty()) {
            return true;
        }

        String[] columns = new String[] {
                I18n.tr("Field"),
                I18n.tr("Existing"),
                I18n.tr("New")
        };

        JTable comparisonTable = new JTable(rows.toArray(new Object[0][]), columns);
        comparisonTable.setEnabled(false);
        comparisonTable.setRowSelectionAllowed(false);
        comparisonTable.setFillsViewportHeight(true);

        JScrollPane tableScrollPane = new JScrollPane(comparisonTable);
        tableScrollPane.setPreferredSize(new Dimension(480, 96));

        JCheckBox suppressCheckbox = new JCheckBox(
                I18n.tr("Do not warn again for {0}.", displayValue(overwrittenStreet))
        );
        Object[] content = new Object[] {
                I18n.tr("<html><b>The following existing values will be overwritten:</b></html>"),
                tableScrollPane,
                I18n.tr("Do you want to apply the new values?"),
                suppressCheckbox
        };

        JOptionPane optionPane = new JOptionPane(content, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        optionPane.setInitialValue(JOptionPane.YES_OPTION);
        JDialog dialog = optionPane.createDialog(MainApplication.getMainFrame(), I18n.tr("Quick Address Fill - Overwrite Warning"));
        dialog.setVisible(true);

        Object selectedValue = optionPane.getValue();
        int result = selectedValue instanceof Integer ? (Integer) selectedValue : JOptionPane.NO_OPTION;

        if (result == JOptionPane.YES_OPTION && suppressCheckbox.isSelected()) {
            warningSuppressedStreet = overwrittenStreet;
        }
        return result == JOptionPane.YES_OPTION;
    }

    private boolean isWarningSuppressedForStreet(String overwrittenStreet) {
        return overwrittenStreet != null && overwrittenStreet.equals(warningSuppressedStreet);
    }

    private String getOverwrittenStreetName(OsmPrimitive building) {
        String existingStreet = normalize(building.get("addr:street"));
        if (!existingStreet.isEmpty()) {
            return existingStreet;
        }
        return streetName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? I18n.tr("(empty)") : value;
    }

    private void refreshModePresentation(String actionMessage) {
        updateStatusLine(actionMessage);
        updateHouseNumberCursor();
    }

    private String formatIncrementStep(int step) {
        return step > 0 ? "+" + step : Integer.toString(step);
    }

    private void updateStatusLine(String actionMessage) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.statusLine == null) {
            return;
        }

        if (!isModeActiveOnMap(map)) {
            map.statusLine.setHelpText(this, I18n.tr("QAF PAUSED"));
            return;
        }

        String baseText = I18n.tr(
                "QAF ACTIVE | Street: {0} | Postcode: {1} | Nr: {2} | Step: {3}",
                displayValue(streetName),
                displayValue(postcode),
                displayValue(houseNumber),
                formatIncrementStep(houseNumberIncrementStep)
        );
        String text = actionMessage == null || actionMessage.isBlank()
                ? baseText
                : I18n.tr("{0} | {1}", baseText, actionMessage);
        map.statusLine.setHelpText(this, text);
    }

    private boolean isModeActiveOnMap(MapFrame map) {
        return map != null && map.mapMode == this;
    }

    private void updateHouseNumberCursor() {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || !isModeActiveOnMap(map)) {
            return;
        }
        map.mapView.setCursor(controlPressed ? createMagnifierCursor() : createHouseNumberCursor());
    }

    private void setControlPressed(boolean pressed) {
        if (controlPressed == pressed) {
            return;
        }
        controlPressed = pressed;
        updateHouseNumberCursor();
    }

    private Cursor createHouseNumberCursor() {
        try {
            String label = normalize(houseNumber);
            if (label.isEmpty()) {
                label = "?";
            }

            int width = 48;
            int height = 48;
            int centerX = width / 2;
            int tipY = height - 2;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(label);
            int textX = Math.max(1, (width - textWidth) / 2);
            int textY = 14;

            g.setColor(new java.awt.Color(255, 255, 220, 235));
            g.fillRoundRect(textX - 3, 2, textWidth + 6, 16, 6, 6);
            g.setColor(java.awt.Color.BLACK);
            g.drawRoundRect(textX - 3, 2, textWidth + 6, 16, 6, 6);
            g.drawString(label, textX, textY);

            java.awt.Color lightArrowColor = new java.awt.Color(245, 245, 245, 240);
            g.setColor(lightArrowColor);
            g.drawLine(centerX, 20, centerX, 36);
            Polygon arrowHead = new Polygon();
            arrowHead.addPoint(centerX, tipY);
            arrowHead.addPoint(centerX - 5, 36);
            arrowHead.addPoint(centerX + 5, 36);
            g.fillPolygon(arrowHead);
            g.dispose();

            return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(centerX, tipY), "qaf-house-number-cursor");
        } catch (RuntimeException ex) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private Cursor createMagnifierCursor() {
        try {
            int width = 32;
            int height = 32;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(new java.awt.Color(30, 30, 30, 230));
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawOval(4, 4, 16, 16);
            g.setColor(new java.awt.Color(255, 255, 255, 110));
            g.fillOval(8, 8, 7, 7);

            g.setColor(new java.awt.Color(30, 30, 30, 230));
            g.setStroke(new java.awt.BasicStroke(3f));
            g.drawLine(17, 17, 27, 27);
            g.dispose();

            return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(10, 10), "qaf-magnifier-cursor");
        } catch (RuntimeException ex) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private boolean incrementHouseNumberAfterSuccessfulApply() {
        String next = incrementHouseNumber(houseNumber);
        if (next == null) {
            return false;
        }
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }

    private boolean incrementHouseNumberNumberByOne() {
        String next = incrementHouseNumberNumberPart(houseNumber);
        if (next == null) {
            return false;
        }
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }

    private boolean decrementHouseNumberNumberByOne() {
        String next = decrementHouseNumberNumberPart(houseNumber);
        if (next == null) {
            return false;
        }
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }

    private boolean incrementHouseNumberLetterByOne() {
        String next = incrementHouseNumberLetterPart(houseNumber);
        if (next == null) {
            return false;
        }
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }

    private boolean decrementHouseNumberLetterByOne() {
        String next = decrementHouseNumberLetterPart(houseNumber);
        if (next == null) {
            return false;
        }
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }

    private boolean toggleLetterSuffixOnHouseNumber() {
        String normalized = normalize(houseNumber);
        Matcher matcher = HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return false;
        }

        String prefix = matcher.group(1);
        String suffix = matcher.group(2);
        String next = (suffix == null || suffix.isEmpty()) ? prefix + "a" : prefix;
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }

    private String incrementHouseNumber(String current) {
        String normalized = normalize(current);
        Matcher numericWithLetters = NUMERIC_WITH_LETTER_SUFFIX_PATTERN.matcher(normalized);
        if (numericWithLetters.matches()) {
            String prefix = numericWithLetters.group(1);
            String letters = numericWithLetters.group(2);
            return prefix + incrementLetters(letters);
        }

        Matcher onlyLetters = LETTER_HOUSE_NUMBER_PATTERN.matcher(normalized);
        if (onlyLetters.matches()) {
            return incrementLetters(onlyLetters.group(1));
        }

        Matcher onlyNumber = NUMERIC_HOUSE_NUMBER_PATTERN.matcher(normalized);
        if (!onlyNumber.matches()) {
            return null;
        }

        try {
            long number = Long.parseLong(onlyNumber.group(1));
            long incremented = number + houseNumberIncrementStep;
            if (incremented < 0) {
                return null;
            }
            return Long.toString(incremented);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String incrementHouseNumberNumberPart(String current) {
        String normalized = normalize(current);
        Matcher matcher = HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        String prefix = matcher.group(1);
        String suffix = matcher.group(2);
        try {
            long number = Long.parseLong(prefix);
            return Long.toString(number + 1) + (suffix == null ? "" : suffix);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String decrementHouseNumberNumberPart(String current) {
        String normalized = normalize(current);
        Matcher matcher = HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        String prefix = matcher.group(1);
        String suffix = matcher.group(2);
        String decrementedPrefix = decrementNumericString(prefix);
        if (decrementedPrefix == null) {
            return null;
        }
        return decrementedPrefix + (suffix == null ? "" : suffix);
    }

    private String incrementHouseNumberLetterPart(String current) {
        String normalized = normalize(current);
        Matcher matcher = HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        String prefix = matcher.group(1);
        String suffix = matcher.group(2);
        String incrementedSuffix = (suffix == null || suffix.isEmpty()) ? "a" : incrementLetters(suffix);
        return prefix + incrementedSuffix;
    }

    private String decrementHouseNumberLetterPart(String current) {
        String normalized = normalize(current);
        Matcher matcher = HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        String prefix = matcher.group(1);
        String suffix = matcher.group(2);
        if (suffix == null || suffix.isEmpty()) {
            return null;
        }

        String decrementedSuffix = decrementLetters(suffix);
        if (decrementedSuffix == null) {
            return null;
        }
        return prefix + decrementedSuffix;
    }

    private boolean hasLetterSuffix(String value) {
        Matcher matcher = HOUSE_NUMBER_WITH_OPTIONAL_SUFFIX_PATTERN.matcher(normalize(value));
        return matcher.matches() && matcher.group(2) != null && !matcher.group(2).isEmpty();
    }

    private String decrementNumericString(String value) {
        try {
            long number = Long.parseLong(value);
            if (number <= 0) {
                return null;
            }
            return Long.toString(number - 1);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String incrementLetters(String letters) {
        if (letters == null || letters.isEmpty()) {
            return letters;
        }

        char[] chars = letters.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--) {
            char current = chars[i];
            boolean upperCase = Character.isUpperCase(current);
            char min = upperCase ? 'A' : 'a';
            char max = upperCase ? 'Z' : 'z';

            if (current == max) {
                chars[i] = min;
            } else {
                chars[i] = (char) (current + 1);
                return new String(chars);
            }
        }

        char leading = Character.isUpperCase(chars[0]) ? 'A' : 'a';
        return leading + new String(chars);
    }

    private String decrementLetters(String letters) {
        if (letters == null || letters.isEmpty()) {
            return null;
        }

        char[] chars = letters.toCharArray();
        boolean allMinimum = true;
        for (char current : chars) {
            char min = Character.isUpperCase(current) ? 'A' : 'a';
            if (current != min) {
                allMinimum = false;
                break;
            }
        }

        if (allMinimum) {
            if (chars.length == 1) {
                return "";
            }
            char max = Character.isUpperCase(chars[0]) ? 'Z' : 'z';
            return String.valueOf(max).repeat(chars.length - 1);
        }

        for (int i = chars.length - 1; i >= 0; i--) {
            char current = chars[i];
            char min = Character.isUpperCase(current) ? 'A' : 'a';
            if (current == min) {
                continue;
            }
            chars[i] = (char) (current - 1);
            for (int j = i + 1; j < chars.length; j++) {
                chars[j] = Character.isUpperCase(chars[j]) ? 'Z' : 'z';
            }
            return new String(chars);
        }
        return null;
    }

    private boolean containsLetter(String value) {
        return value != null && value.matches(".*[A-Za-z].*");
    }

    private int normalizeIncrementStep(int step) {
        return step == -2 || step == -1 || step == 1 || step == 2 ? step : 1;
    }
}
