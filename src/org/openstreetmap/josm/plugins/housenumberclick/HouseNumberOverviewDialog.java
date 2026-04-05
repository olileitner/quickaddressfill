package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.I18n;

final class HouseNumberOverviewDialog {

    private static final int DIALOG_OFFSET_X = 66;
    private static final int DIALOG_OFFSET_Y = 80;
    private static final java.awt.Color ODD_COLUMN_COLOR = new java.awt.Color(255, 243, 225);
    private static final java.awt.Color EVEN_COLUMN_COLOR = new java.awt.Color(230, 239, 252);
    private static final Color MISSING_DOT_COLOR = new Color(140, 145, 150);

    private final JDialog dialog;
    private final JLabel streetLabel;
    private final DefaultTableModel tableModel;
    private final List<HouseNumberOverviewRow> currentRows = new ArrayList<>();

    HouseNumberOverviewDialog() {
        Frame owner = MainApplication.getMainFrame();
        this.dialog = new JDialog(owner, I18n.tr("House number overview"), false);
        this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        this.streetLabel = new JLabel();
        this.tableModel = new DefaultTableModel(
                new Object[] {I18n.tr("Odd"), I18n.tr("Even")},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(tableModel);
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
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleTableClick(table, e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);

        javax.swing.JPanel content = new javax.swing.JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(streetLabel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);

        this.dialog.getContentPane().add(content, BorderLayout.CENTER);
        this.dialog.setMinimumSize(new Dimension(300, 440));
        this.dialog.setSize(new Dimension(320, 500));
    }

    void updateData(String streetName, List<HouseNumberOverviewRow> rows) {
        String normalizedStreet = normalize(streetName);
        streetLabel.setText(I18n.tr("Street: {0}", normalizedStreet.isEmpty() ? I18n.tr("(none)") : normalizedStreet));

        currentRows.clear();
        tableModel.setRowCount(0);
        if (rows == null) {
            return;
        }

        for (HouseNumberOverviewRow row : rows) {
            if (row == null) {
                continue;
            }
            currentRows.add(row);
            tableModel.addRow(new Object[] {row.getOddValue(), row.getEvenValue()});
        }
    }

    void showDialog() {
        positionTopRightInOwner(MainApplication.getMainFrame());
        if (!dialog.isVisible()) {
            dialog.setVisible(true);
        } else {
            dialog.toFront();
            dialog.requestFocus();
        }
    }

    void hideDialog() {
        dialog.setVisible(false);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void positionTopRightInOwner(Frame owner) {
        if (owner == null) {
            return;
        }
        int x = owner.getX() + owner.getWidth() - dialog.getWidth() - DIALOG_OFFSET_X;
        int y = owner.getY() + DIALOG_OFFSET_Y;
        dialog.setLocation(x, y);
    }

    private void handleTableClick(JTable table, MouseEvent event) {
        int row = table.rowAtPoint(event.getPoint());
        int column = table.columnAtPoint(event.getPoint());
        if (row < 0 || column < 0 || row >= currentRows.size()) {
            return;
        }

        HouseNumberOverviewRow overviewRow = currentRows.get(row);
        OsmPrimitive target = column == 0 ? overviewRow.getOddPrimitive() : overviewRow.getEvenPrimitive();
        if (target == null || !target.isUsable()) {
            return;
        }

        zoomToPrimitive(target);
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
}



