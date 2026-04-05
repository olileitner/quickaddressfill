package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.RowSorter;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.I18n;

final class StreetHouseNumberCountDialog {

    private static final int DIALOG_OFFSET_X = 66;
    private static final int DIALOG_OFFSET_Y = 80;

    private final JDialog dialog;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> tableRowSorter;
    private final List<StreetHouseNumberCountRow> currentRows = new ArrayList<>();
    private final Consumer<String> streetClickListener;

    StreetHouseNumberCountDialog(Consumer<String> streetClickListener) {
        this.streetClickListener = streetClickListener;

        Frame owner = MainApplication.getMainFrame();
        this.dialog = new JDialog(owner, I18n.tr("Street house number counts"), false);
        this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        this.tableModel = new DefaultTableModel(
                new Object[] {I18n.tr("Street"), I18n.tr("Count")},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        JTable table = new JTable(tableModel);
        this.tableRowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(tableRowSorter);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer centeredRenderer = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < currentRows.size()) {
                    StreetHouseNumberCountRow countRow = currentRows.get(modelRow);
                    if (countRow != null) {
                        setText(countRow.getDisplayCount());
                    }
                }
                return component;
            }
        };
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(1).setCellRenderer(centeredRenderer);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleTableClick(table, event);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        javax.swing.JPanel content = new javax.swing.JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(scrollPane, BorderLayout.CENTER);

        this.dialog.getContentPane().add(content, BorderLayout.CENTER);
        this.dialog.setMinimumSize(new Dimension(320, 360));
        this.dialog.setSize(new Dimension(360, 420));
    }

    void updateData(List<StreetHouseNumberCountRow> rows) {
        currentRows.clear();
        tableModel.setRowCount(0);
        if (rows == null) {
            return;
        }

        for (StreetHouseNumberCountRow row : rows) {
            if (row == null) {
                continue;
            }
            currentRows.add(row);
            tableModel.addRow(new Object[] {row.getStreetName(), row.getCount()});
        }

        tableRowSorter.setSortKeys(Arrays.asList(
                new RowSorter.SortKey(1, SortOrder.DESCENDING),
                new RowSorter.SortKey(0, SortOrder.ASCENDING)
        ));
        tableRowSorter.sort();
    }

    void showDialog() {
        positionBottomRightInOwner(MainApplication.getMainFrame());
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

    private void positionBottomRightInOwner(Frame owner) {
        if (owner == null) {
            return;
        }
        int x = owner.getX() + owner.getWidth() - dialog.getWidth() - DIALOG_OFFSET_X;
        int y = owner.getY() + owner.getHeight() - dialog.getHeight() - DIALOG_OFFSET_Y;
        dialog.setLocation(x, y);
    }

    private void handleTableClick(JTable table, MouseEvent event) {
        int viewRow = table.rowAtPoint(event.getPoint());
        if (viewRow < 0) {
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= currentRows.size()) {
            return;
        }

        StreetHouseNumberCountRow countRow = currentRows.get(modelRow);
        if (countRow == null || streetClickListener == null) {
            return;
        }

        String streetName = countRow.getStreetName();
        if (streetName == null || streetName.isBlank()) {
            return;
        }
        streetClickListener.accept(streetName);
        focusMapView();
    }

    private void focusMapView() {
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.requestFocusInWindow();
        }
    }
}



