package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Renders street-specific house-number highlights with optional directly connected driveway context and
 * optional connection lines in a dedicated layer.
 */
final class HouseNumberOverlayLayer extends Layer {

    private static final String LOG_PREFIX = "HouseNumberClick overlay diagnostics";

    private static final Font TEXT_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final Color BUBBLE_FILL_COLOR = new Color(255, 255, 220, 225);
    private static final Color BUBBLE_BORDER_COLOR = new Color(45, 45, 45, 210);
    private static final Color ODD_BUBBLE_FILL_COLOR = new Color(255, 236, 208, 230);
    private static final Color ODD_BUBBLE_BORDER_COLOR = new Color(182, 132, 74, 220);
    private static final Color EVEN_BUBBLE_FILL_COLOR = new Color(220, 234, 255, 230);
    private static final Color EVEN_BUBBLE_BORDER_COLOR = new Color(92, 126, 170, 220);
    private static final Color ODD_LINE_COLOR = new Color(193, 146, 88, 190);
    private static final Color EVEN_LINE_COLOR = new Color(98, 134, 179, 190);
    private static final Color MISSING_POSTCODE_BUBBLE_FILL_COLOR = new Color(255, 238, 150, 235);
    private static final Color MISSING_POSTCODE_BUBBLE_BORDER_COLOR = new Color(196, 162, 36, 230);
    private static final Color DUPLICATE_BUBBLE_FILL_COLOR = new Color(255, 175, 175, 235);
    private static final Color DUPLICATE_BUBBLE_BORDER_COLOR = new Color(195, 20, 20, 235);
    private static final Color TEXT_COLOR = new Color(10, 10, 10, 230);
    private static final Color STREET_HIGHLIGHT_COLOR = new Color(245, 210, 45, 190);
    private static final float STREET_HIGHLIGHT_WIDTH = 14.0f;
    private static final Color DRIVEWAY_HIGHLIGHT_COLOR = new Color(245, 210, 45, 95);
    private static final float DRIVEWAY_HIGHLIGHT_WIDTH = 8.0f;
    private static final long CACHE_REFRESH_INTERVAL_NANOS = 500_000_000L;

    private final HouseNumberOverlayCollector collector;
    private StreetOption selectedStreet;
    private StreetNameCollector.StreetIndex selectedStreetIndex;
    private Way selectedSeedWayHint;
    private boolean houseNumberLabelsEnabled;
    private boolean connectionLinesEnabled;
    private boolean separateEvenOddConnectionLinesEnabled;
    private DataSet cachedDataSet;
    private String cachedStreetClusterId = "";
    private long cachedSeedWayId;
    private List<HouseNumberOverlayEntry> cachedEntries = List.of();
    private Set<String> cachedDuplicateAddresses = Set.of();
    private long lastCacheRefreshNanos;
    private boolean cacheDirty = true;
    private String lastPaintDiagnosticKey = "";
    private String lastHighlightDiagnosticKey = "";
    private String lastEntryDiagnosticKey = "";

    HouseNumberOverlayLayer() {
        super(I18n.tr("House number overlay"));
        this.collector = new HouseNumberOverlayCollector();
    }

    void updateSettings(StreetOption selectedStreet, StreetNameCollector.StreetIndex selectedStreetIndex,
            Way seedWayHint,
            boolean houseNumberLabelsEnabled, boolean connectionLinesEnabled,
            boolean separateEvenOddConnectionLinesEnabled) {
        String normalizedClusterId = selectedStreet == null ? "" : normalize(selectedStreet.getClusterId());
        String previousClusterId = this.selectedStreet == null ? "" : normalize(this.selectedStreet.getClusterId());
        if (!normalizedClusterId.equals(previousClusterId)) {
            cacheDirty = true;
        }
        this.selectedStreet = selectedStreet;
        this.selectedStreetIndex = selectedStreetIndex;
        this.selectedSeedWayHint = seedWayHint;
        this.houseNumberLabelsEnabled = houseNumberLabelsEnabled;
        this.connectionLinesEnabled = connectionLinesEnabled;
        this.separateEvenOddConnectionLinesEnabled = connectionLinesEnabled && separateEvenOddConnectionLinesEnabled;
        invalidate();
    }

    void invalidateDataCache() {
        cacheDirty = true;
        invalidate();
    }

    @Override
    public void paint(Graphics2D graphics, MapView mapView, Bounds bounds) {
        if (mapView == null || selectedStreet == null || normalize(selectedStreet.getBaseStreetName()).isEmpty()) {
            logPaintReasonOnce("skip:no-selected-street",
                    LOG_PREFIX + ": paint skipped -> no selected street (base/display/cluster unavailable).");
            return;
        }

        DataSet dataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (dataSet == null) {
            logPaintReasonOnce("skip:no-dataset", LOG_PREFIX + ": paint skipped -> no active dataset.");
            return;
        }

        logPaintReasonOnce("paint:active:" + normalize(selectedStreet.getClusterId()),
                LOG_PREFIX + ": paint active for base='" + normalize(selectedStreet.getBaseStreetName())
                        + "', display='" + normalize(selectedStreet.getDisplayStreetName())
                        + "', cluster='" + normalize(selectedStreet.getClusterId()) + "'.");

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(TEXT_FONT);

        drawSelectedStreetHighlight(g, mapView, dataSet);

        if (!houseNumberLabelsEnabled) {
            g.dispose();
            return;
        }

        refreshCacheIfNeeded(dataSet);
        List<HouseNumberOverlayEntry> entries = cachedEntries;
        if (entries.isEmpty()) {
            logEntryReasonOnce("entries:empty:" + normalize(selectedStreet.getClusterId()),
                    LOG_PREFIX + ": no house-number entries to draw for cluster='"
                            + normalize(selectedStreet.getClusterId()) + "'.");
            g.dispose();
            return;
        }

        logEntryReasonOnce("entries:draw:" + normalize(selectedStreet.getClusterId()) + ":" + entries.size(),
                LOG_PREFIX + ": drawing " + entries.size() + " house-number entries for cluster='"
                        + normalize(selectedStreet.getClusterId()) + "'.");

        if (connectionLinesEnabled && entries.size() > 1) {
            drawConnectionLines(g, mapView, entries);
        }
        drawBubblesAndLabels(g, mapView, entries, cachedDuplicateAddresses);
        g.dispose();
    }

    private void refreshCacheIfNeeded(DataSet dataSet) {
        long now = System.nanoTime();
        boolean needsRefresh = cacheDirty
                || dataSet != cachedDataSet
                || !normalize(selectedStreet.getClusterId()).equals(cachedStreetClusterId)
                || resolveSeedWayUniqueId(selectedSeedWayHint) != cachedSeedWayId
                || now - lastCacheRefreshNanos >= CACHE_REFRESH_INTERVAL_NANOS;
        if (!needsRefresh) {
            return;
        }

        StreetNameCollector.StreetIndex effectiveStreetIndex = selectedStreetIndex != null
                ? selectedStreetIndex
                : StreetNameCollector.collectStreetIndex(dataSet);
        List<HouseNumberOverlayEntry> entries = collector.collect(dataSet, selectedStreet, effectiveStreetIndex, selectedSeedWayHint);
        cachedEntries = Collections.unmodifiableList(entries);
        cachedDuplicateAddresses = Collections.unmodifiableSet(collectDuplicateAddressKeys(entries));
        cachedDataSet = dataSet;
        cachedStreetClusterId = normalize(selectedStreet.getClusterId());
        cachedSeedWayId = resolveSeedWayUniqueId(selectedSeedWayHint);
        lastCacheRefreshNanos = now;
        cacheDirty = false;
    }

    private void drawSelectedStreetHighlight(Graphics2D g, MapView mapView, DataSet dataSet) {
        if (g == null || mapView == null || dataSet == null) {
            return;
        }
        StreetNameCollector.StreetIndex streetIndex = selectedStreetIndex != null
                ? selectedStreetIndex
                : StreetNameCollector.collectStreetIndex(dataSet);
        List<Way> highlightedStreetWays = streetIndex.getLocalStreetChainWays(selectedStreet, selectedSeedWayHint);
        Set<Way> highlightedStreetWaySet = new LinkedHashSet<>(highlightedStreetWays);
        Set<Way> highlightedDrivewayWays = collectDirectDrivewayHighlightWays(highlightedStreetWaySet);

        drawHighlightedWaysWithStyle(g, mapView, highlightedDrivewayWays, DRIVEWAY_HIGHLIGHT_COLOR, DRIVEWAY_HIGHLIGHT_WIDTH);
        drawHighlightedWaysWithStyle(g, mapView, highlightedStreetWaySet, STREET_HIGHLIGHT_COLOR, STREET_HIGHLIGHT_WIDTH);

        String selectedCluster = normalize(selectedStreet.getClusterId());
        if (highlightedStreetWaySet.isEmpty()) {
            logHighlightReasonOnce("highlight:none-base:" + selectedCluster,
                    LOG_PREFIX + ": highlight skipped -> no local street-chain ways found for selected cluster='"
                            + selectedCluster + "'.");
            return;
        }
        logHighlightReasonOnce("highlight:ok:" + selectedCluster + ":" + highlightedStreetWaySet.size()
                        + ":driveways:" + highlightedDrivewayWays.size(),
                LOG_PREFIX + ": highlighted " + highlightedStreetWaySet.size() + " primary street way(s) for selected cluster='"
                        + selectedCluster + "' (localChainWays=" + highlightedStreetWays.size()
                        + ", directDriveways=" + highlightedDrivewayWays.size() + ").");
    }

    private void drawHighlightedWaysWithStyle(Graphics2D g, MapView mapView, Set<Way> ways, Color color, float lineWidth) {
        if (ways == null || ways.isEmpty()) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Way way : ways) {
            drawWayHighlight(g, mapView, way);
        }
    }

    static Set<Way> collectDirectDrivewayHighlightWays(Set<Way> highlightedStreetWays) {
        if (highlightedStreetWays == null || highlightedStreetWays.isEmpty()) {
            return Set.of();
        }
        Set<Node> highlightedStreetNodes = new HashSet<>();
        for (Way way : highlightedStreetWays) {
            if (way == null || !way.isUsable()) {
                continue;
            }
            for (Node node : way.getNodes()) {
                if (node != null && node.isUsable()) {
                    highlightedStreetNodes.add(node);
                }
            }
        }

        LinkedHashSet<Way> drivewayWays = new LinkedHashSet<>();
        for (Node streetNode : highlightedStreetNodes) {
            if (streetNode.getDataSet() == null) {
                continue;
            }
            for (OsmPrimitive referrer : streetNode.getReferrers()) {
                if (!(referrer instanceof Way candidateWay)
                        || highlightedStreetWays.contains(candidateWay)
                        || !isDirectDriveway(candidateWay)) {
                    continue;
                }
                drivewayWays.add(candidateWay);
            }
        }
        return drivewayWays;
    }

    private static boolean isDirectDriveway(Way candidateWay) {
        return candidateWay != null
                && candidateWay.isUsable()
                && candidateWay.hasTag("highway", "service")
                && "driveway".equalsIgnoreCase(normalizeStatic(candidateWay.get("service")));
    }

    private static String normalizeStatic(String value) {
        return value == null ? "" : value.trim();
    }

    private void drawWayHighlight(Graphics2D g, MapView mapView, Way way) {
        Point previous = null;
        for (Node node : way.getNodes()) {
            if (node == null || !node.isUsable()) {
                previous = null;
                continue;
            }
            Point current = mapView.getPoint(node);
            if (!isOnScreen(current, mapView)) {
                previous = null;
                continue;
            }
            if (previous != null) {
                g.drawLine(previous.x, previous.y, current.x, current.y);
            }
            previous = current;
        }
    }

    private void drawConnectionLines(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries) {
        g.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (!separateEvenOddConnectionLinesEnabled) {
            g.setColor(BUBBLE_FILL_COLOR);
            drawConnectionLinePath(g, mapView, entries, null);
            return;
        }

        g.setColor(EVEN_LINE_COLOR);
        drawConnectionLinePath(g, mapView, entries, 0);
        g.setColor(ODD_LINE_COLOR);
        drawConnectionLinePath(g, mapView, entries, 1);
    }

    private void drawConnectionLinePath(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries, Integer parityFilter) {
        Point previous = null;
        for (HouseNumberOverlayEntry entry : entries) {
            if (parityFilter != null) {
                int numberPart = entry.getNumberPart();
                if (numberPart == Integer.MAX_VALUE || Math.abs(numberPart % 2) != parityFilter) {
                    continue;
                }
            }

            Point current = mapView.getPoint(entry.getLabelPoint());
            if (previous != null) {
                if (isOnScreen(previous, mapView) || isOnScreen(current, mapView)) {
                    g.drawLine(previous.x, previous.y, current.x, current.y);
                }
            }
            previous = current;
        }
    }

    private void drawBubblesAndLabels(Graphics2D g, MapView mapView, List<HouseNumberOverlayEntry> entries,
            Set<String> duplicateNumbers) {
        FontMetrics metrics = g.getFontMetrics();

        for (HouseNumberOverlayEntry entry : entries) {
            Point point = mapView.getPoint(entry.getLabelPoint());
            if (!isOnScreen(point, mapView)) {
                continue;
            }

            String label = entry.getHouseNumber();
            int textWidth = metrics.stringWidth(label);
            int textHeight = metrics.getAscent();

            int bubbleWidth = Math.max(26, textWidth + 14);
            int bubbleHeight = Math.max(24, textHeight + 10);
            int x = point.x - bubbleWidth / 2;
            int y = point.y - bubbleHeight / 2;
            int numberPart = entry.getNumberPart();
            boolean missingPostcode = normalize(entry.getPostcode()).isEmpty();
            boolean duplicateHouseNumber = duplicateNumbers.contains(
                    normalizeAddressKey(entry.getStreet(), entry.getPostcode(), entry.getHouseNumber())
            );

            if (duplicateHouseNumber) {
                int ringPadding = 5;
                int outerX = x - ringPadding;
                int outerY = y - ringPadding;
                int outerWidth = bubbleWidth + (ringPadding * 2);
                int outerHeight = bubbleHeight + (ringPadding * 2);
                g.setColor(new Color(
                        DUPLICATE_BUBBLE_BORDER_COLOR.getRed(),
                        DUPLICATE_BUBBLE_BORDER_COLOR.getGreen(),
                        DUPLICATE_BUBBLE_BORDER_COLOR.getBlue(),
                        175
                ));
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(outerX, outerY, outerWidth, outerHeight);
            }

            g.setColor(resolveBubbleFillColor(duplicateHouseNumber, numberPart, missingPostcode));
            g.fillOval(x, y, bubbleWidth, bubbleHeight);
            g.setColor(resolveBubbleBorderColor(duplicateHouseNumber, numberPart, missingPostcode));
            g.setStroke(new BasicStroke(duplicateHouseNumber ? 3.2f : 1.6f));
            g.drawOval(x, y, bubbleWidth, bubbleHeight);

            int textX = point.x - textWidth / 2;
            int textY = point.y + (metrics.getAscent() - metrics.getDescent()) / 2;
            g.setColor(TEXT_COLOR);
            g.drawString(label, textX, textY);
        }
    }

    private Set<String> collectDuplicateAddressKeys(List<HouseNumberOverlayEntry> entries) {
        Map<String, Set<Long>> anchorsByAddress = new HashMap<>();
        for (HouseNumberOverlayEntry entry : entries) {
            String key = normalizeAddressKey(entry.getStreet(), entry.getPostcode(), entry.getHouseNumber());
            if (key.isEmpty()) {
                continue;
            }
            anchorsByAddress
                    .computeIfAbsent(key, ignored -> new HashSet<>())
                    .add(resolveRealWorldAnchorId(entry));
        }

        Set<String> duplicateAddresses = new HashSet<>();
        for (Map.Entry<String, Set<Long>> countEntry : anchorsByAddress.entrySet()) {
            if (countEntry.getValue().size() > 1) {
                duplicateAddresses.add(countEntry.getKey());
            }
        }
        return duplicateAddresses;
    }

    private long resolveRealWorldAnchorId(HouseNumberOverlayEntry entry) {
        if (entry == null) {
            return 0L;
        }
        OsmPrimitive associatedBuilding = entry.getAssociatedBuilding();
        if (associatedBuilding != null && associatedBuilding.isUsable()) {
            return associatedBuilding.getUniqueId();
        }
        OsmPrimitive primitive = entry.getPrimitive();
        return primitive == null ? 0L : primitive.getUniqueId();
    }

    private Color resolveBubbleFillColor(boolean duplicateHouseNumber, int numberPart, boolean missingPostcode) {
        if (duplicateHouseNumber) {
            return DUPLICATE_BUBBLE_FILL_COLOR;
        }
        if (missingPostcode) {
            return MISSING_POSTCODE_BUBBLE_FILL_COLOR;
        }
        if (numberPart == Integer.MAX_VALUE) {
            return BUBBLE_FILL_COLOR;
        }
        return Math.abs(numberPart % 2) == 0 ? EVEN_BUBBLE_FILL_COLOR : ODD_BUBBLE_FILL_COLOR;
    }

    private Color resolveBubbleBorderColor(boolean duplicateHouseNumber, int numberPart, boolean missingPostcode) {
        if (duplicateHouseNumber) {
            return DUPLICATE_BUBBLE_BORDER_COLOR;
        }
        if (missingPostcode) {
            return MISSING_POSTCODE_BUBBLE_BORDER_COLOR;
        }
        if (numberPart == Integer.MAX_VALUE) {
            return BUBBLE_BORDER_COLOR;
        }
        return Math.abs(numberPart % 2) == 0 ? EVEN_BUBBLE_BORDER_COLOR : ODD_BUBBLE_BORDER_COLOR;
    }

    private boolean isOnScreen(Point point, MapView mapView) {
        return point != null
                && point.x >= -40
                && point.y >= -40
                && point.x <= mapView.getWidth() + 40
                && point.y <= mapView.getHeight() + 40;
    }

    @Override
    public Icon getIcon() {
        Icon icon = ImageProvider.get("dialogs", "search");
        return icon != null ? icon : ImageProvider.get("housenumberclick");
    }

    @Override
    public String getToolTipText() {
        return I18n.tr("House number overlay for selected street");
    }

    @Override
    public void mergeFrom(Layer from) {
        // Overlay layer is ephemeral and intentionally not mergeable.
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor visitor) {
        if (visitor == null) {
            return;
        }
        DataSet dataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (dataSet != null) {
            visitor.visit(dataSet.getDataSourceBoundingBox());
        }
    }

    @Override
    public Object getInfoComponent() {
        String displayStreet = selectedStreet == null ? "" : normalize(selectedStreet.getDisplayStreetName());
        return I18n.tr("Street: {0}", displayStreet.isEmpty() ? I18n.tr("(none)") : displayStreet);
    }

    @Override
    public Action[] getMenuEntries() {
        LayerListDialog layerListDialog = LayerListDialog.getInstance();
        if (layerListDialog == null) {
            return new Action[0];
        }
        return new Action[] {
                layerListDialog.createShowHideLayerAction(),
                layerListDialog.createDeleteLayerAction()
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeAddressKey(String street, String postcode, String houseNumber) {
        String normalizedStreet = normalize(street);
        String normalizedPostcode = normalize(postcode);
        String normalizedHouseNumber = normalize(houseNumber);
        if (normalizedStreet.isEmpty() || normalizedPostcode.isEmpty() || normalizedHouseNumber.isEmpty()) {
            return "";
        }
        return normalizedStreet.toLowerCase(Locale.ROOT)
                + "|" + normalizedPostcode.toLowerCase(Locale.ROOT)
                + "|" + normalizedHouseNumber.toLowerCase(Locale.ROOT);
    }

    private long resolveSeedWayUniqueId(Way seedWay) {
        return seedWay == null ? 0L : seedWay.getUniqueId();
    }

    private void logPaintReasonOnce(String key, String message) {
        if (!key.equals(lastPaintDiagnosticKey)) {
            lastPaintDiagnosticKey = key;
        }
    }

    private void logHighlightReasonOnce(String key, String message) {
        if (!key.equals(lastHighlightDiagnosticKey)) {
            lastHighlightDiagnosticKey = key;
        }
    }

    private void logEntryReasonOnce(String key, String message) {
        if (!key.equals(lastEntryDiagnosticKey)) {
            lastEntryDiagnosticKey = key;
        }
    }
}
