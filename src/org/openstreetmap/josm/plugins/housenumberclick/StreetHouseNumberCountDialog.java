package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

/**
 * Dialog that lists streets with address counts, selection shortcuts, and rescan controls.
 */
final class StreetHouseNumberCountDialog {

    private static final int DIALOG_OFFSET_X = 66;
    private static final int DIALOG_OFFSET_Y = 80;
    private static final Dimension DIALOG_MINIMUM_SIZE = new Dimension(300, 440);
    private static final Dimension DIALOG_SIZE = new Dimension(320, 500);

    private final JDialog dialog;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> tableRowSorter;
    private final JTable table;
    private final List<StreetHouseNumberCountRow> currentRows = new ArrayList<>();
    private final Consumer<StreetOption> streetClickListener;
    private final Runnable rescanListener;
    private final Runnable closeListener;
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
                .comparing((StreetHouseNumberCountRow row) -> normalizeStreetName(row.getDisplayStreetName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> normalizeStreetName(row.getDisplayStreetName()), Comparator.naturalOrder())
                .thenComparing(Comparator.comparingInt(StreetHouseNumberCountRow::getCount).reversed()));
        return sortedRows;
    }

    static List<StreetOption> buildStreetNavigationOrder(List<StreetHouseNumberCountRow> rows) {
        List<StreetHouseNumberCountRow> sortedRows = sortRowsForDisplay(rows);
        List<StreetOption> orderedStreetOptions = new ArrayList<>(sortedRows.size());
        for (StreetHouseNumberCountRow row : sortedRows) {
            StreetOption option = row.getStreetOption();
            if (option != null && option.isValid()) {
                orderedStreetOptions.add(option);
            }
        }
        return orderedStreetOptions;
    }

    StreetHouseNumberCountDialog(Consumer<StreetOption> streetClickListener, Runnable rescanListener, Runnable closeListener) {
        this.streetClickListener = streetClickListener;
        this.rescanListener = rescanListener;
        this.closeListener = closeListener;

        Frame owner = MainApplication.getMainFrame();
        this.dialog = new JDialog(owner, I18n.tr("Number Counts"), false);
        this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        this.dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (StreetHouseNumberCountDialog.this.closeListener != null) {
                    StreetHouseNumberCountDialog.this.closeListener.run();
                }
            }
        });

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
        this.dialog.setMinimumSize(DIALOG_MINIMUM_SIZE);
        this.dialog.setSize(DIALOG_SIZE);
    }

    void updateData(List<StreetHouseNumberCountRow> rows) {
        currentRows.clear();
        tableModel.setRowCount(0);
        for (StreetHouseNumberCountRow row : sortRowsForDisplay(rows)) {
            currentRows.add(row);
            tableModel.addRow(new Object[] {row.getDisplayStreetName(), row.getCount()});
        }

        tableRowSorter.setSortKeys(Arrays.asList(
                new RowSorter.SortKey(0, SortOrder.ASCENDING),
                new RowSorter.SortKey(1, SortOrder.DESCENDING)
        ));
        tableRowSorter.sort();
    }

    void highlightStreet(StreetOption streetOption) {
        if (streetOption == null || currentRows.isEmpty()) {
            table.clearSelection();
            return;
        }

        String selectedClusterId = normalizeStreetName(streetOption.getClusterId());
        String selectedDisplayStreet = normalizeStreetName(streetOption.getDisplayStreetName());

        for (int modelRow = 0; modelRow < currentRows.size(); modelRow++) {
            StreetHouseNumberCountRow row = currentRows.get(modelRow);
            if (row == null) {
                continue;
            }
            StreetOption rowOption = row.getStreetOption();
            if (rowOption == null) {
                continue;
            }
            boolean sameCluster = !selectedClusterId.isEmpty()
                    && normalizeStreetName(rowOption.getClusterId()).equalsIgnoreCase(selectedClusterId);
            boolean sameDisplay = normalizeStreetName(rowOption.getDisplayStreetName()).equalsIgnoreCase(selectedDisplayStreet);
            if (!sameCluster && !sameDisplay) {
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

        StreetOption streetOption = countRow.getStreetOption();
        if (streetOption == null || !streetOption.isValid()) {
            return;
        }
        streetClickListener.accept(streetOption);
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

