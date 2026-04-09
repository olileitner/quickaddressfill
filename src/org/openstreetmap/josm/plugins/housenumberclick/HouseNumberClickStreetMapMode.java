package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.BasicStroke;
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
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.JTextComponent;

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

final class HouseNumberClickStreetMapMode extends MapMode {

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
    private String warningSuppressedPostcode;
    private boolean ctrlDispatcherRegistered;
    private boolean ctrlPressedForCursor;
    private long lastClickWhen;
    private int lastClickX = Integer.MIN_VALUE;
    private int lastClickY = Integer.MIN_VALUE;
    private int lastClickModifiers;
    private int lastClickButton;

    private static final class ClickResolutionStats {
        private String outcome = "unknown";
        private BuildingResolver.BuildingResolutionResult resolution = BuildingResolver.BuildingResolutionResult.notEvaluated();
    }

    HouseNumberClickStreetMapMode(StreetModeController controller) {
        super(
                I18n.tr("HouseNumberClick Street Mode"),
                "housenumberclick",
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
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    controller.deactivate();
                    e.consume();
                }
            }
        };
    }

    void setAddressValues(String streetName, String postcode, String buildingType, String houseNumber, int houseNumberIncrementStep) {
        String normalizedStreet = normalize(streetName);
        String normalizedPostcode = normalize(postcode);
        if (!normalizedStreet.equals(this.streetName)) {
            warningSuppressedStreet = null;
            warningSuppressedPostcode = null;
        }
        if (!normalizedPostcode.equals(this.postcode)) {
            warningSuppressedPostcode = null;
        }
        this.streetName = normalizedStreet;
        this.postcode = normalizedPostcode;
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
        ctrlPressedForCursor = false;
        registerCtrlKeyDispatcher();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.addKeyListener(escListener);
            map.mapView.addMouseListener(this);
            map.mapView.requestFocusInWindow();
        }
        controller.notifyModeStateChanged(true);
        refreshModePresentation(null);
    }

    @Override
    public void exitMode() {
        ctrlPressedForCursor = false;
        unregisterCtrlKeyDispatcher();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.removeKeyListener(escListener);
            map.mapView.removeMouseListener(this);
            map.mapView.setCursor(Cursor.getDefaultCursor());
        }
        if (map != null && map.statusLine != null) {
            map.statusLine.setHelpText(this, I18n.tr("HouseNumberClick PAUSED"));
        }
        controller.notifyModeStateChanged(false);
        super.exitMode();
    }

    private boolean handleGlobalKeyEvent(KeyEvent e) {
        if (e == null) {
            return false;
        }
        if (!isModeActiveOnMap(MainApplication.getMap())) {
            return false;
        }

        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                ctrlPressedForCursor = true;
                updateHouseNumberCursor();
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                ctrlPressedForCursor = false;
                updateHouseNumberCursor();
            }
            return false;
        }
 
        int id = e.getID();
        if (!e.isConsumed() && id == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ALT) {
            if (e.isControlDown()) {
                // Ctrl readback has priority over temporary Alt split.
                return false;
            }
            // Alt split should be available immediately after mode activation,
            // independent of current dialog/text focus.
            if (controller.activateTemporarySplitModeFromAlt()) {
                e.consume();
                return true;
            }
            return false;
        }

        if (id != KeyEvent.KEY_PRESSED || e.isConsumed()) {
            return false;
        }
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
            return false;
        }
        if (isTextInputFocused()) {
            return false;
        }
        if (!handleHouseNumberShortcut(e)) {
            return false;
        }

        e.consume();
        return true;
    }

    private boolean handleHouseNumberShortcut(KeyEvent e) {
        if (isPlusShortcut(e)) {
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
            return true;
        }
        if (e.getKeyCode() == KeyEvent.VK_L) {
            if (toggleLetterSuffixOnHouseNumber()) {
                refreshModePresentation(I18n.tr("House number letter toggle applied."));
            } else {
                updateStatusLine(I18n.tr("House number letter toggle is only available for numeric house numbers."));
            }
            return true;
        }
        if (isMinusShortcut(e)) {
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
            return true;
        }
        return false;
    }

    private boolean isTextInputFocused() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return false;
        }
        if (focusOwner instanceof JTextComponent) {
            return true;
        }
        for (Component component = focusOwner; component != null; component = component.getParent()) {
            if (component instanceof JComboBox && ((JComboBox<?>) component).isEditable()) {
                return true;
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
        return I18n.tr("Left-click applies tags, right-click creates row houses, Ctrl+left-click reads building data or street name, hold Alt for temporary line split actions, + / - change number or suffix, L toggles letter suffix.");
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
        if (e != null && (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger())) {
            long startedAtNanos = System.nanoTime();
            ClickResolutionStats stats = new ClickResolutionStats();
            try {
                handleTerraceRightClick(e, stats);
            } catch (RuntimeException ex) {
                Logging.warn("HouseNumberClick StreetMapMode.mouseReleased: failure while processing right-click terrace split");
                Logging.debug(ex);
                updateStatusLine(I18n.tr("Row-house split failed. See log for details."));
                stats.outcome = "runtime-error";
            } finally {
                logClickDiagnostics(startedAtNanos, e, stats);
            }
            return;
        }

        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        if (isDuplicateReleaseEvent(e)) {
            Logging.debug("HouseNumberClick StreetMapMode.mouseReleased: duplicate release suppressed at {0},{1}",
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
                    "HouseNumberClick StreetMapMode.mouseReleased: failure while processing click, control={0}, street={1}, postcode={2}, houseNumber={3}",
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
        if (!isPostcodeSelected(postcode)) {
            stats.outcome = "no-postcode-selected";
            updateStatusLine(I18n.tr("No postcode selected."));
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
                addressConflictService.analyze(building, streetName, postcode, houseNumber, buildingType);
        String overwrittenStreet = conflictAnalysis.getOverwrittenStreet();
        String overwrittenPostcode = conflictAnalysis.getOverwrittenPostcode();
        if (conflictAnalysis.hasConflict() && shouldShowOverwriteWarning(conflictAnalysis, overwrittenStreet, overwrittenPostcode)) {
            if (!confirmOverwrite(conflictAnalysis, overwrittenStreet, overwrittenPostcode)) {
                stats.outcome = "overwrite-cancelled";
                updateStatusLine(I18n.tr("Overwrite cancelled."));
                e.consume();
                return;
            }
        }

        String appliedStreet = normalize(streetName);
        String appliedHouseNumber = normalize(houseNumber);
        boolean buildingTypeWasUsed = !normalize(buildingType).isEmpty();
        OsmPrimitive writeTarget = resolveWriteTargetForApply(building);
        BuildingTagApplier.applyAddress(writeTarget, streetName, postcode, buildingType, houseNumber);
        controller.onAddressApplied();
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet != null) {
            dataSet.setSelected(Collections.emptyList());
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

    private void handleTerraceRightClick(MouseEvent e, ClickResolutionStats stats) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            stats.outcome = "map-unavailable";
            return;
        }

        BuildingResolver.BuildingResolutionResult resolution = buildingResolver.resolveAtClick(map, e);
        stats.resolution = resolution;
        OsmPrimitive building = resolution.getBuilding();
        Way targetWay = resolveTerraceTargetWay(building);
        if (targetWay == null) {
            stats.outcome = "no-building-hit";
            updateStatusLine(I18n.tr("No building detected"));
            return;
        }

        TerraceSplitResult result = controller.executeInternalTerraceSplitAtClick(
                targetWay,
                controller.getConfiguredTerraceParts()
        );
        if (!result.isSuccess()) {
            stats.outcome = "terrace-split-failed";
            return;
        }

        stats.outcome = "terrace-split-applied";
        updateStatusLine(I18n.tr("Row houses created ({0} parts).", controller.getConfiguredTerraceParts()));
        e.consume();
    }

    private Way resolveTerraceTargetWay(OsmPrimitive building) {
        if (building instanceof Way) {
            Way way = (Way) building;
            if (way.isUsable() && way.isClosed() && way.hasKey("building")) {
                return way;
            }
            return null;
        }
        OsmPrimitive selectionTarget = getSelectionTarget(building);
        if (selectionTarget instanceof Way) {
            Way way = (Way) selectionTarget;
            if (way.isUsable() && way.isClosed() && way.hasKey("building")) {
                return way;
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

    private OsmPrimitive resolveWriteTargetForApply(OsmPrimitive building) {
        if (!(building instanceof Way) || !building.isUsable()) {
            return building;
        }

        Way way = (Way) building;
        for (OsmPrimitive referrer : way.getReferrers()) {
            if (!(referrer instanceof Relation)) {
                continue;
            }
            Relation relation = (Relation) referrer;
            if (!relation.isUsable()) {
                continue;
            }
            if (relation.hasTag("type", "multipolygon") && relation.hasTag("building")) {
                return relation;
            }
        }
        return building;
    }

    private boolean confirmOverwrite(
            AddressConflictService.ConflictAnalysis conflictAnalysis,
            String overwrittenStreet,
            String overwrittenPostcode
    ) {
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
        comparisonTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int column
            ) {
                Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String existing = normalize(table.getValueAt(row, 1) == null ? "" : String.valueOf(table.getValueAt(row, 1)));
                String proposed = normalize(value == null ? "" : String.valueOf(value));
                boolean hasChangedValue = !proposed.isEmpty() && !proposed.equals(existing);
                if (!isSelected && hasChangedValue) {
                    component.setBackground(new java.awt.Color(255, 244, 204));
                } else {
                    component.setBackground(table.getBackground());
                }
                return component;
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(comparisonTable);
        tableScrollPane.setPreferredSize(new Dimension(480, 96));

        boolean streetConflict = hasDifferingField(conflictAnalysis, "addr:street");
        boolean postcodeConflict = hasDifferingField(conflictAnalysis, "addr:postcode");
        JCheckBox suppressStreetCheckbox = streetConflict
                ? new JCheckBox(I18n.tr("Do not warn again for street: {0}.", displayValue(overwrittenStreet)))
                : null;
        JCheckBox suppressPostcodeCheckbox = postcodeConflict
                ? new JCheckBox(I18n.tr("Do not warn again for postcode: {0}.", displayValue(overwrittenPostcode)))
                : null;

        List<Object> content = new ArrayList<>();
        content.add(I18n.tr("<html><b>The following existing values will be overwritten:</b></html>"));
        content.add(tableScrollPane);
        content.add(I18n.tr("Do you want to apply the new values?"));
        if (suppressStreetCheckbox != null) {
            content.add(suppressStreetCheckbox);
        }
        if (suppressPostcodeCheckbox != null) {
            content.add(suppressPostcodeCheckbox);
        }

        JOptionPane optionPane = new JOptionPane(content.toArray(new Object[0]), JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        optionPane.setInitialValue(JOptionPane.YES_OPTION);
        JDialog dialog = optionPane.createDialog(MainApplication.getMainFrame(), I18n.tr("HouseNumberClick - Overwrite Warning"));
        dialog.setVisible(true);

        Object selectedValue = optionPane.getValue();
        int result = selectedValue instanceof Integer ? (Integer) selectedValue : JOptionPane.NO_OPTION;

        if (result == JOptionPane.YES_OPTION) {
            if (suppressStreetCheckbox != null && suppressStreetCheckbox.isSelected()) {
                warningSuppressedStreet = normalize(overwrittenStreet);
            }
            if (suppressPostcodeCheckbox != null && suppressPostcodeCheckbox.isSelected()) {
                warningSuppressedPostcode = normalize(overwrittenPostcode);
            }
        }
        return result == JOptionPane.YES_OPTION;
    }

    private boolean shouldShowOverwriteWarning(
            AddressConflictService.ConflictAnalysis conflictAnalysis,
            String overwrittenStreet,
            String overwrittenPostcode
    ) {
        boolean streetNeedsWarning = hasDifferingField(conflictAnalysis, "addr:street")
                && !isStreetWarningSuppressed(overwrittenStreet);
        boolean postcodeNeedsWarning = hasDifferingField(conflictAnalysis, "addr:postcode")
                && !isPostcodeWarningSuppressed(overwrittenPostcode);
        boolean buildingNeedsWarning = hasDifferingField(conflictAnalysis, "building");
        return streetNeedsWarning || postcodeNeedsWarning || buildingNeedsWarning;
    }

    private boolean hasDifferingField(AddressConflictService.ConflictAnalysis conflictAnalysis, String key) {
        if (conflictAnalysis == null || key == null) {
            return false;
        }
        for (AddressConflictService.ConflictField field : conflictAnalysis.getDifferingFields()) {
            if (field != null && key.equals(field.getKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStreetWarningSuppressed(String overwrittenStreet) {
        String normalizedOverwrittenStreet = normalize(overwrittenStreet);
        return !normalizedOverwrittenStreet.isEmpty()
                && normalizedOverwrittenStreet.equals(normalize(warningSuppressedStreet));
    }

    private boolean isPostcodeWarningSuppressed(String overwrittenPostcode) {
        String normalizedOverwrittenPostcode = normalize(overwrittenPostcode);
        return !normalizedOverwrittenPostcode.isEmpty()
                && normalizedOverwrittenPostcode.equals(normalize(warningSuppressedPostcode));
    }


    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isPostcodeSelected(String postcode) {
        return postcode != null && !postcode.trim().isEmpty();
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
            map.statusLine.setHelpText(this, I18n.tr("HouseNumberClick PAUSED"));
            return;
        }

        String baseText = I18n.tr(
                "HouseNumberClick ACTIVE | Street: {0} | Postcode: {1} | Nr: {2} | Step: {3}",
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
        map.mapView.setCursor(ctrlPressedForCursor ? createCtrlZoomCursor() : createHouseNumberCursor());
    }

    private Cursor createCtrlZoomCursor() {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension bestSize = toolkit.getBestCursorSize(48, 48);
            int width = bestSize != null && bestSize.width > 0 ? bestSize.width : 48;
            int height = bestSize != null && bestSize.height > 0 ? bestSize.height : 48;

            int lensRadius = Math.max(10, Math.min(width, height) / 5);
            int lensCenterX = Math.max(lensRadius + 4, width / 2 - 4);
            int lensCenterY = Math.max(lensRadius + 4, height / 2 - 6);
            int hotspotX = lensCenterX;
            int hotspotY = lensCenterY;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Requested palette: black lens interior, white ring and white handle.
            g.setColor(new java.awt.Color(20, 20, 20, 245));
            g.fillOval(lensCenterX - lensRadius, lensCenterY - lensRadius, lensRadius * 2, lensRadius * 2);
            g.setColor(new java.awt.Color(250, 250, 250, 250));
            g.setStroke(new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(lensCenterX - lensRadius, lensCenterY - lensRadius, lensRadius * 2, lensRadius * 2);


            int handleStartX = lensCenterX + lensRadius - 1;
            int handleStartY = lensCenterY + lensRadius - 1;
            int handleEndX = Math.min(width - 4, handleStartX + Math.max(8, lensRadius));
            int handleEndY = Math.min(height - 4, handleStartY + Math.max(8, lensRadius));
            g.setColor(new java.awt.Color(250, 250, 250, 250));
            g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(handleStartX, handleStartY, handleEndX, handleEndY);
            g.fillOval(handleEndX - 2, handleEndY - 2, 4, 4);
            g.dispose();

            return toolkit.createCustomCursor(image, new Point(hotspotX, hotspotY), "hnc-magnifier-cursor");
        } catch (RuntimeException ex) {
            Logging.debug(ex);
        }
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private Cursor createHouseNumberCursor() {
        try {
            String label = normalize(houseNumber);
            boolean showHouseNumberLabel = hasCompleteAddressInputForApply() && !label.isEmpty();

            int width = 48;
            int height = 48;
            int centerX = width / 2;
            int tipY = height - 2;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            java.awt.Color arrowColor = new java.awt.Color(245, 245, 245, 240);
            int labelBoxWidth = 22;
            int labelBoxHeight = 16;
            int labelBoxX = centerX - (labelBoxWidth / 2);
            int labelBoxY = 2;

            if (showHouseNumberLabel) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                FontMetrics metrics = g.getFontMetrics();
                int textWidth = metrics.stringWidth(label);
                int textX = Math.max(1, (width - textWidth) / 2);
                int textY = 14;
                int dynamicBoxWidth = textWidth + 6;
                int dynamicBoxX = textX - 3;

                g.setColor(new java.awt.Color(255, 255, 220, 235));
                g.fillRoundRect(dynamicBoxX, labelBoxY, dynamicBoxWidth, labelBoxHeight, 6, 6);
                g.setColor(java.awt.Color.BLACK);
                g.drawRoundRect(dynamicBoxX, labelBoxY, dynamicBoxWidth, labelBoxHeight, 6, 6);
                g.drawString(label, textX, textY);
            } else {
                // Keep an empty placeholder box so the cursor layout stays stable when number is unavailable.
                g.setColor(arrowColor);
                g.drawRoundRect(labelBoxX, labelBoxY, labelBoxWidth, labelBoxHeight, 6, 6);
            }

            g.setColor(arrowColor);
            g.drawLine(centerX, 20, centerX, 36);
            Polygon arrowHead = new Polygon();
            arrowHead.addPoint(centerX, tipY);
            arrowHead.addPoint(centerX - 5, 36);
            arrowHead.addPoint(centerX + 5, 36);
            g.fillPolygon(arrowHead);
            g.dispose();

            return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(centerX, tipY), "hnc-house-number-cursor");
        } catch (RuntimeException ex) {
            Logging.debug(ex);
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private boolean hasCompleteAddressInputForApply() {
        return !normalize(streetName).isEmpty()
                && !normalize(postcode).isEmpty()
                && !normalize(houseNumber).isEmpty();
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
                    "HouseNumberClick click-path: outcome={0}, source={1}, nearestCandidates={2}, relationChecked={3}/{4}, wayChecked={5}/{6}, relationLimitReached={7}, wayLimitReached={8}, control={9}, button={10}, modifiers={11}, x={12}, y={13}, durationMs={14}",
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
                    "HouseNumberClick StreetMapMode.mouseReleased: slow click handling ({0} ms), source={1}, outcome={2}, x={3}, y={4}",
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
