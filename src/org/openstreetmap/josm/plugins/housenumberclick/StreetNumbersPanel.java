package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.I18n;

/**
 * Sidebar panel for house numbers of the currently selected street with explicit inactive/no-data cards.
 */
final class StreetNumbersPanel extends JPanel {

    static final String CARD_ACTIVE = "active_table";
    static final String CARD_MAIN_DIALOG_CLOSED = "inactive_main_dialog_closed";
    static final String CARD_NO_DATA = "inactive_no_data";
    static final String CARD_NO_STREET = "inactive_no_street_selected";

    private static final Color ODD_COLUMN_COLOR = new Color(255, 243, 225);
    private static final Color EVEN_COLUMN_COLOR = new Color(230, 239, 252);
    private static final Color MISSING_DOT_COLOR = new Color(140, 145, 150);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel streetLabel = new JLabel();
    private final JLabel incompleteStreetWarningLabel = new JLabel(I18n.tr("Street may be incomplete in loaded data"));
    private final DefaultTableModel tableModel;
    private final List<HouseNumberOverviewRow> currentRows = new ArrayList<>();
    private final JTable table;
    private final Runnable rowClickListener;

    StreetNumbersPanel(Runnable rowClickListener) {
        super(new BorderLayout());
        this.rowClickListener = rowClickListener;

        this.incompleteStreetWarningLabel.setVisible(false);
        this.incompleteStreetWarningLabel.setForeground(new Color(118, 122, 128));
        this.incompleteStreetWarningLabel.setFont(
                this.incompleteStreetWarningLabel.getFont().deriveFont(
                        java.awt.Font.ITALIC,
                        Math.max(11f, this.incompleteStreetWarningLabel.getFont().getSize2D() - 1f)
                )
        );

        this.tableModel = new DefaultTableModel(
                new Object[] {I18n.tr("Odd"), I18n.tr("Even")},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        this.table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer centeredRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    component.setBackground(column == 0 ? ODD_COLUMN_COLOR : EVEN_COLUMN_COLOR);
                    boolean isMissingDot = HouseNumberOverviewCollector.MISSING_NUMBER_PLACEHOLDER.equals(value);
                    component.setForeground(isMissingDot ? MISSING_DOT_COLOR : table.getForeground());
                    component.setFont(isMissingDot ? component.getFont().deriveFont(component.getFont().getSize2D() + 1.0f)
                            : table.getFont());
                }
                return component;
            }
        };
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, centeredRenderer);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                handleTableClick(event);
            }
        });

        JPanel activePanel = new JPanel(new BorderLayout(0, 8));
        JPanel headerPanel = new JPanel(new BorderLayout(0, 2));
        headerPanel.add(streetLabel, BorderLayout.NORTH);
        headerPanel.add(incompleteStreetWarningLabel, BorderLayout.CENTER);
        activePanel.add(headerPanel, BorderLayout.NORTH);
        activePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        cards.add(activePanel, CARD_ACTIVE);
        cards.add(createHintPanel(I18n.tr("HouseNumberClick is closed. Open the main dialog to use this view.")), CARD_MAIN_DIALOG_CLOSED);
        cards.add(createHintPanel(I18n.tr("No active dataset available.")), CARD_NO_DATA);
        cards.add(createHintPanel(I18n.tr("No street selected.")), CARD_NO_STREET);

        add(cards, BorderLayout.CENTER);
        showMainDialogClosed();
    }

    void showMainDialogClosed() {
        currentRows.clear();
        tableModel.setRowCount(0);
        cardLayout.show(cards, CARD_MAIN_DIALOG_CLOSED);
    }

    void showNoData() {
        currentRows.clear();
        tableModel.setRowCount(0);
        cardLayout.show(cards, CARD_NO_DATA);
    }

    void showNoStreetSelected() {
        currentRows.clear();
        tableModel.setRowCount(0);
        cardLayout.show(cards, CARD_NO_STREET);
    }

    void showActiveRows(String streetName, List<HouseNumberOverviewRow> rows, boolean streetPossiblyIncomplete) {
        String normalizedStreet = normalize(streetName);
        streetLabel.setText(I18n.tr("Street: {0}", normalizedStreet.isEmpty() ? I18n.tr("(none)") : normalizedStreet));
        incompleteStreetWarningLabel.setVisible(streetPossiblyIncomplete);

        currentRows.clear();
        tableModel.setRowCount(0);
        if (rows != null) {
            for (HouseNumberOverviewRow row : rows) {
                if (row == null) {
                    continue;
                }
                currentRows.add(row);
                tableModel.addRow(new Object[] {row.getOddValue(), row.getEvenValue()});
            }
        }

        if (currentRows.isEmpty()) {
            cardLayout.show(cards, CARD_NO_DATA);
            return;
        }
        cardLayout.show(cards, CARD_ACTIVE);
    }

    private JPanel createHintPanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(message);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void handleTableClick(java.awt.event.MouseEvent event) {
        int row = table.rowAtPoint(event.getPoint());
        int column = table.columnAtPoint(event.getPoint());
        if (row < 0 || column < 0 || row >= currentRows.size()) {
            return;
        }

        HouseNumberOverviewRow overviewRow = currentRows.get(row);
        boolean duplicate = column == 0 ? overviewRow.isOddDuplicate() : overviewRow.isEvenDuplicate();
        List<OsmPrimitive> duplicateTargets = column == 0
                ? overviewRow.getOddDuplicatePrimitives()
                : overviewRow.getEvenDuplicatePrimitives();
        if (duplicate && duplicateTargets != null && !duplicateTargets.isEmpty()) {
            zoomToPrimitives(duplicateTargets);
        } else {
            OsmPrimitive target = column == 0 ? overviewRow.getOddPrimitive() : overviewRow.getEvenPrimitive();
            if (target == null || !target.isUsable()) {
                return;
            }
            zoomToPrimitive(target);
        }
        focusMapView();
        if (rowClickListener != null) {
            rowClickListener.run();
        }
    }

    private void zoomToPrimitive(OsmPrimitive primitive) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return;
        }

        BoundingXYVisitor visitor = new BoundingXYVisitor();
        primitive.accept((OsmPrimitiveVisitor) visitor);
        map.mapView.zoomTo(visitor);

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (editDataSet != null && primitive.getDataSet() == editDataSet) {
            editDataSet.setSelected(Collections.singleton(primitive));
        }
    }

    private void zoomToPrimitives(List<OsmPrimitive> primitives) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || primitives == null || primitives.isEmpty()) {
            return;
        }

        BoundingXYVisitor visitor = new BoundingXYVisitor();
        Set<OsmPrimitive> usableTargets = new LinkedHashSet<>();
        for (OsmPrimitive primitive : primitives) {
            if (primitive == null || !primitive.isUsable()) {
                continue;
            }
            primitive.accept((OsmPrimitiveVisitor) visitor);
            usableTargets.add(primitive);
        }

        if (!visitor.hasExtend()) {
            return;
        }
        map.mapView.zoomTo(visitor);

        DataSet editDataSet = MainApplication.getLayerManager() != null
                ? MainApplication.getLayerManager().getEditDataSet()
                : null;
        if (editDataSet != null && !usableTargets.isEmpty()) {
            editDataSet.setSelected(usableTargets);
        }
    }

    private void focusMapView() {
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.requestFocusInWindow();
        }
    }
}

