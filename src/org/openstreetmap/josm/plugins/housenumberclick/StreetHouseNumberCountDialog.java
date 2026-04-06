package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.RowSorter;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
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
    private final JTable table;
    private final List<StreetHouseNumberCountRow> currentRows = new ArrayList<>();
    private final Consumer<String> streetClickListener;
    private final Runnable rescanListener;
    private boolean positionInitializedForSession;

    static List<StreetHouseNumberCountRow> sortRowsForDisplay(List<StreetHouseNumberCountRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<StreetHouseNumberCountRow> sortedRows = new ArrayList<>();
        for (StreetHouseNumberCountRow row : rows) {
            if (row != null) {
                sortedRows.add(row);
            }
        }
        sortedRows.sort(Comparator
                .comparing((StreetHouseNumberCountRow row) -> normalizeStreetName(row.getStreetName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> normalizeStreetName(row.getStreetName()), Comparator.naturalOrder())
                .thenComparing(Comparator.comparingInt(StreetHouseNumberCountRow::getCount).reversed()));
        return sortedRows;
    }

    static List<String> buildStreetNavigationOrder(List<StreetHouseNumberCountRow> rows) {
        List<StreetHouseNumberCountRow> sortedRows = sortRowsForDisplay(rows);
        List<String> orderedStreetNames = new ArrayList<>(sortedRows.size());
        for (StreetHouseNumberCountRow row : sortedRows) {
            String streetName = normalizeStreetName(row.getStreetName());
            if (!streetName.isEmpty()) {
                orderedStreetNames.add(streetName);
            }
        }
        return orderedStreetNames;
    }

    StreetHouseNumberCountDialog(Consumer<String> streetClickListener, Runnable rescanListener) {
        this.streetClickListener = streetClickListener;
        this.rescanListener = rescanListener;

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

        this.table = new JTable(tableModel);
        this.tableRowSorter = new TableRowSorter<>(tableModel);
        tableRowSorter.setComparator(0, String.CASE_INSENSITIVE_ORDER);
        table.setRowSorter(tableRowSorter);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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

        JButton rescanButton = new JButton(I18n.tr("Rescan"));
        rescanButton.addActionListener(e -> onRescanRequested());
        javax.swing.JPanel topPanel = new javax.swing.JPanel(new BorderLayout());
        topPanel.add(rescanButton, BorderLayout.WEST);

        content.add(topPanel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);

        this.dialog.getContentPane().add(content, BorderLayout.CENTER);
        this.dialog.setMinimumSize(new Dimension(320, 360));
        this.dialog.setSize(new Dimension(360, 420));
    }

    void updateData(List<StreetHouseNumberCountRow> rows) {
        currentRows.clear();
        tableModel.setRowCount(0);
        for (StreetHouseNumberCountRow row : sortRowsForDisplay(rows)) {
            currentRows.add(row);
            tableModel.addRow(new Object[] {row.getStreetName(), row.getCount()});
        }

        tableRowSorter.setSortKeys(Arrays.asList(
                new RowSorter.SortKey(0, SortOrder.ASCENDING),
                new RowSorter.SortKey(1, SortOrder.DESCENDING)
        ));
        tableRowSorter.sort();
    }

    void highlightStreet(String streetName) {
        String normalizedStreet = normalizeStreetName(streetName);
        if (normalizedStreet.isEmpty() || currentRows.isEmpty()) {
            table.clearSelection();
            return;
        }

        for (int modelRow = 0; modelRow < currentRows.size(); modelRow++) {
            StreetHouseNumberCountRow row = currentRows.get(modelRow);
            if (row == null) {
                continue;
            }
            if (!normalizeStreetName(row.getStreetName()).equalsIgnoreCase(normalizedStreet)) {
                continue;
            }

            int viewRow = table.convertRowIndexToView(modelRow);
            if (viewRow < 0) {
                continue;
            }
            table.setRowSelectionInterval(viewRow, viewRow);
            Rectangle rowBounds = table.getCellRect(viewRow, 0, true);
            if (rowBounds != null) {
                table.scrollRectToVisible(rowBounds);
            }
            return;
        }

        table.clearSelection();
    }

    private static String normalizeStreetName(String value) {
        return value == null ? "" : value.trim();
    }

    void showDialog() {
        if (!dialog.isVisible()) {
            if (!positionInitializedForSession) {
                positionBottomRightInOwner(MainApplication.getMainFrame());
                positionInitializedForSession = true;
            }
            dialog.setVisible(true);
        } else {
            dialog.toFront();
            dialog.requestFocus();
        }
    }

    void hideDialog() {
        dialog.setVisible(false);
    }

    void resetSessionPositioningState() {
        positionInitializedForSession = false;
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

    private void onRescanRequested() {
        if (rescanListener == null) {
            return;
        }
        rescanListener.run();
        focusMapView();
    }
}



