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
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

final class QuickAddressFillStreetMapMode extends MapMode {

    private static final long DUPLICATE_CLICK_WINDOW_MILLIS = 120L;
    static final String PREF_RELATION_SCAN_LIMIT = BuildingResolver.PREF_RELATION_SCAN_LIMIT;
    static final String PREF_WAY_SCAN_LIMIT = BuildingResolver.PREF_WAY_SCAN_LIMIT;
    static final int DEFAULT_RELATION_SCAN_CANDIDATES = BuildingResolver.DEFAULT_RELATION_SCAN_CANDIDATES;
    static final int DEFAULT_WAY_SCAN_CANDIDATES = BuildingResolver.DEFAULT_WAY_SCAN_CANDIDATES;
    private static final long SLOW_CLICK_LOG_THRESHOLD_MILLIS = 40L;

    private final StreetModeController controller;
    private final BuildingResolver buildingResolver;
    private final HouseNumberService houseNumberService;
    private final AddressReadbackService addressReadbackService;
    private final AddressConflictService addressConflictService;
    private final ConflictDialogModelBuilder conflictDialogModelBuilder;
    private final KeyAdapter escListener;
    private final KeyEventDispatcher ctrlKeyDispatcher;
    private String streetName;
    private String postcode;
    private String buildingType;
    private String houseNumber;
    private int houseNumberIncrementStep = 1;
    private String warningSuppressedStreet;
    private boolean controlPressed;
    private boolean ctrlDispatcherRegistered;
    private long lastClickWhen;
    private int lastClickX = Integer.MIN_VALUE;
    private int lastClickY = Integer.MIN_VALUE;
    private int lastClickModifiers;
    private int lastClickButton;

    private static final class ClickResolutionStats {
        private String outcome = "unknown";
        private BuildingResolver.BuildingResolutionResult resolution = BuildingResolver.BuildingResolutionResult.notEvaluated();
    }

    QuickAddressFillStreetMapMode(StreetModeController controller) {
        super(
                I18n.tr("Quick Address Fill Street Mode"),
                "quickaddressfill",
                I18n.tr("Click buildings to set addr:street"),
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        );
        this.controller = controller;
        this.buildingResolver = new BuildingResolver();
        this.houseNumberService = new HouseNumberService();
        this.addressReadbackService = new AddressReadbackService();
        this.addressConflictService = new AddressConflictService();
        this.conflictDialogModelBuilder = new ConflictDialogModelBuilder();
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
                    boolean letterMode = houseNumberService.hasLetterSuffix(houseNumber);
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
                    boolean letterMode = houseNumberService.hasLetterSuffix(houseNumber);
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
        this.houseNumber = houseNumberService.normalize(houseNumber);
        this.houseNumberIncrementStep = houseNumberService.sanitizeIncrementStepForHouseNumber(this.houseNumber, houseNumberIncrementStep);
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
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        if (isDuplicateReleaseEvent(e)) {
            Logging.debug("QuickAddressFill StreetMapMode.mouseReleased: duplicate release suppressed at {0},{1}",
                    e.getX(), e.getY());
            return;
        }

        long startedAtNanos = System.nanoTime();
        ClickResolutionStats stats = new ClickResolutionStats();
        try {
            if (e.isControlDown()) {
                handleSecondaryClick(e, stats);
            } else {
                handlePrimaryClick(e, stats);
            }
        } catch (RuntimeException ex) {
            Logging.warn(
                    "QuickAddressFill StreetMapMode.mouseReleased: failure while processing click, control={0}, street={1}, postcode={2}, houseNumber={3}",
                    e.isControlDown(),
                    displayValue(streetName),
                    displayValue(postcode),
                    displayValue(houseNumber)
            );
            Logging.debug(ex);
            updateStatusLine(I18n.tr("Address click failed. See log for details."));
            stats.outcome = "runtime-error";
        } finally {
            logClickDiagnostics(startedAtNanos, e, stats);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // Intentionally handled on mouseReleased only to avoid duplicate apply/increment per click.
    }

    private void handlePrimaryClick(MouseEvent e, ClickResolutionStats stats) {
        if (streetName == null || streetName.isBlank()) {
            stats.outcome = "no-street-selected";
            updateStatusLine(I18n.tr("No street selected."));
            return;
        }

        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            stats.outcome = "map-unavailable";
            return;
        }

        BuildingResolver.BuildingResolutionResult resolution = buildingResolver.resolveAtClick(map, e);
        stats.resolution = resolution;
        OsmPrimitive building = resolution.getBuilding();
        if (building == null) {
            stats.outcome = "no-building-hit";
            updateStatusLine(I18n.tr("No building detected"));
            return;
        }

        AddressConflictService.ConflictAnalysis conflictAnalysis =
                addressConflictService.analyze(building, streetName, postcode, houseNumber);
        String overwrittenStreet = conflictAnalysis.getOverwrittenStreet();
        if (conflictAnalysis.hasConflict() && !isWarningSuppressedForStreet(overwrittenStreet)) {
            if (!confirmOverwrite(conflictAnalysis, overwrittenStreet)) {
                stats.outcome = "overwrite-cancelled";
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
        stats.outcome = "applied";
        e.consume();
    }

    private void handleSecondaryClick(MouseEvent e, ClickResolutionStats stats) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            stats.outcome = "map-unavailable";
            return;
        }

        BuildingResolver.BuildingResolutionResult resolution = buildingResolver.resolveAtClick(map, e);
        stats.resolution = resolution;
        OsmPrimitive building = resolution.getBuilding();
        if (building == null) {
            AddressReadbackService.AddressReadbackResult readback = addressReadbackService.readFromStreetFallback(
                    addressReadbackService.resolveStreetNameAtClick(map, e),
                    postcode,
                    buildingType
            );
            if (readback != null) {
                stats.outcome = "street-picked";
                controller.updateAddressValues(
                        readback.getStreet(),
                        readback.getPostcode(),
                        readback.getBuildingType(),
                        readback.getHouseNumber()
                );
                updateStatusLine(I18n.tr("Street name loaded from map: {0}", displayValue(readback.getStreet())));
            } else {
                stats.outcome = "no-building-hit";
                updateStatusLine(I18n.tr("No building detected"));
            }
            return;
        }

        AddressReadbackService.AddressReadbackResult readback = addressReadbackService.readFromBuilding(building, buildingType);
        controller.updateAddressValues(
                readback.getStreet(),
                readback.getPostcode(),
                readback.getBuildingType(),
                readback.getHouseNumber()
        );

        updateStatusLine(
                I18n.tr(
                        "Address data loaded: street={0}, postcode={1}, house number={2}",
                        displayValue(readback.getStreet()),
                        displayValue(readback.getPostcode()),
                        displayValue(readback.getHouseNumber())
                )
        );
        stats.outcome = "address-picked";
        e.consume();
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

    private boolean confirmOverwrite(AddressConflictService.ConflictAnalysis conflictAnalysis, String overwrittenStreet) {
        ConflictDialogModelBuilder.DialogModel dialogModel =
                conflictDialogModelBuilder.build(conflictAnalysis, this::displayValue);

        if (dialogModel.isEmpty()) {
            return true;
        }

        List<Object[]> rows = new ArrayList<>();
        for (ConflictDialogModelBuilder.DialogRow row : dialogModel.getRows()) {
            rows.add(new Object[] {row.getField(), row.getExisting(), row.getProposed()});
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
            Logging.debug(ex);
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
            Logging.debug(ex);
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private boolean isDuplicateReleaseEvent(MouseEvent e) {
        long eventWhen = e.getWhen();
        boolean closeInTime = lastClickWhen > 0 && (eventWhen - lastClickWhen) >= 0
                && (eventWhen - lastClickWhen) <= DUPLICATE_CLICK_WINDOW_MILLIS;
        boolean samePosition = e.getX() == lastClickX && e.getY() == lastClickY;
        boolean sameModifiers = e.getModifiersEx() == lastClickModifiers;
        boolean sameButton = e.getButton() == lastClickButton;
        boolean duplicate = closeInTime && samePosition && sameModifiers && sameButton;

        lastClickWhen = eventWhen;
        lastClickX = e.getX();
        lastClickY = e.getY();
        lastClickModifiers = e.getModifiersEx();
        lastClickButton = e.getButton();

        return duplicate;
    }

    private void logClickDiagnostics(long startedAtNanos, MouseEvent e, ClickResolutionStats stats) {
        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        if (Logging.isDebugEnabled()) {
            Logging.debug(
                    "QuickAddressFill click-path: outcome={0}, source={1}, nearestCandidates={2}, relationChecked={3}/{4}, wayChecked={5}/{6}, relationLimitReached={7}, wayLimitReached={8}, control={9}, button={10}, modifiers={11}, x={12}, y={13}, durationMs={14}",
                    stats.outcome,
                    stats.resolution.getSource(),
                    stats.resolution.getNearestCandidates(),
                    stats.resolution.getRelationCandidatesChecked(),
                    stats.resolution.getRelationScanLimit(),
                    stats.resolution.getWayCandidatesChecked(),
                    stats.resolution.getWayScanLimit(),
                    stats.resolution.isRelationLimitReached(),
                    stats.resolution.isWayLimitReached(),
                    e.isControlDown(),
                    e.getButton(),
                    e.getModifiersEx(),
                    e.getX(),
                    e.getY(),
                    elapsedMillis
            );
        }

        if (elapsedMillis >= SLOW_CLICK_LOG_THRESHOLD_MILLIS) {
            Logging.debug(
                    "QuickAddressFill StreetMapMode.mouseReleased: slow click handling ({0} ms), source={1}, outcome={2}, x={3}, y={4}",
                    elapsedMillis,
                    stats.resolution.getSource(),
                    stats.outcome,
                    e.getX(),
                    e.getY()
            );
        }
    }

    static int getConfiguredRelationScanLimit() {
        return BuildingResolver.getConfiguredRelationScanLimit();
    }

    static int getConfiguredWayScanLimit() {
        return BuildingResolver.getConfiguredWayScanLimit();
    }

    private boolean incrementHouseNumberAfterSuccessfulApply() {
        return applyHouseNumberUpdate(houseNumberService.incrementAfterSuccessfulApply(houseNumber, houseNumberIncrementStep));
    }

    private boolean incrementHouseNumberNumberByOne() {
        return applyHouseNumberUpdate(houseNumberService.incrementNumberPartByOne(houseNumber));
    }

    private boolean decrementHouseNumberNumberByOne() {
        return applyHouseNumberUpdate(houseNumberService.decrementNumberPartByOne(houseNumber));
    }

    private boolean incrementHouseNumberLetterByOne() {
        return applyHouseNumberUpdate(houseNumberService.incrementLetterPartByOne(houseNumber));
    }

    private boolean decrementHouseNumberLetterByOne() {
        return applyHouseNumberUpdate(houseNumberService.decrementLetterPartByOne(houseNumber));
    }

    private boolean toggleLetterSuffixOnHouseNumber() {
        return applyHouseNumberUpdate(houseNumberService.toggleLetterSuffix(houseNumber));
    }

    private boolean applyHouseNumberUpdate(String next) {
        if (next == null) {
            return false;
        }
        houseNumber = next;
        controller.updateHouseNumber(next);
        return true;
    }
}
