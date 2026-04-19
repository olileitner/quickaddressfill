package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.openstreetmap.josm.tools.I18n;

/**
 * Sidebar panel for per-street house-number counts with state cards for inactive and no-data cases.
 */
final class StreetCountsPanel extends JPanel {

    static final String CARD_ACTIVE = "active_table";
    static final String CARD_MAIN_DIALOG_CLOSED = "inactive_main_dialog_closed";
    static final String CARD_NO_DATA = "inactive_no_data";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> tableRowSorter;
    private final JTable table;
    private final List<StreetHouseNumberCountRow> currentRows = new ArrayList<>();
    private final Consumer<StreetOption> streetClickListener;
    private final Runnable rescanListener;

    StreetCountsPanel(Consumer<StreetOption> streetClickListener, Runnable rescanListener) {
        super(new BorderLayout());
        this.streetClickListener = streetClickListener;
        this.rescanListener = rescanListener;

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
                return columnIndex == 1 ? Integer.class : String.class;
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
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                handleTableClick(event);
            }
        });

        JButton rescanButton = new JButton(I18n.tr("Rescan"));
        rescanButton.addActionListener(e -> onRescanRequested());

        JPanel activePanel = new JPanel(new BorderLayout(0, 6));
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(rescanButton, BorderLayout.WEST);
        activePanel.add(topPanel, BorderLayout.NORTH);
        activePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        cards.add(activePanel, CARD_ACTIVE);
        cards.add(createHintPanel(I18n.tr("HouseNumberClick is closed. Open the main dialog to use this view.")), CARD_MAIN_DIALOG_CLOSED);
        cards.add(createHintPanel(I18n.tr("No active dataset available.")), CARD_NO_DATA);

        add(cards, BorderLayout.CENTER);
        showMainDialogClosed();
    }

    void showMainDialogClosed() {
        table.clearSelection();
        cardLayout.show(cards, CARD_MAIN_DIALOG_CLOSED);
    }

    void showNoData() {
        table.clearSelection();
        cardLayout.show(cards, CARD_NO_DATA);
    }

    void showActiveRows(List<StreetHouseNumberCountRow> rows, StreetOption highlightedStreet) {
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

        highlightStreet(highlightedStreet);
        cardLayout.show(cards, CARD_ACTIVE);
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

    private static String normalizeStreetName(String value) {
        return value == null ? "" : value.trim();
    }

    private JPanel createHintPanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(message);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private void handleTableClick(java.awt.event.MouseEvent event) {
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
    }

    private void onRescanRequested() {
        if (rescanListener != null) {
            rescanListener.run();
        }
    }
}

