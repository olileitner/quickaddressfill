package org.openstreetmap.josm.plugins.quickaddressfill;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.I18n;

final class StreetSelectionDialog {

    private static final String DEFAULT_HOUSE_NUMBER = "1";

    private final StreetModeController streetModeController;
    private final JDialog dialog;
    private final JComboBox<String> streetCombo;
    private final JComboBox<String> buildingTypeCombo;
    private final JTextField postcodeField;
    private final JTextField houseNumberField;
    private JToggleButton minusTwoIncrementButton;
    private JToggleButton minusOneIncrementButton;
    private JToggleButton plusOneIncrementButton;
    private JToggleButton plusTwoIncrementButton;
    private final JLabel modeStateLabel;
    private final JButton continueWorkingButton;
    private final JLabel buildingSplitterStatusLabel;
    private final JButton splitBuildingButton;
    private int houseNumberIncrementStep = 1;
    private String lastSelectedStreet;
    private String rememberedStreet;
    private String rememberedPostcode;
    private String rememberedBuildingType;
    private String rememberedHouseNumber = DEFAULT_HOUSE_NUMBER;
    private int rememberedIncrementStep = 1;
    private boolean updatingInputs;

    private static final int DIALOG_WIDTH = 360;
    private static final int DIALOG_HEIGHT = 390;
    private static final int DIALOG_OFFSET_X = 66;
    private static final int DIALOG_OFFSET_Y = 80;
    private static final List<String> COMMON_BUILDING_TYPES = Arrays.asList(
            "yes", "apartments", "residential", "house", "detached", "terrace", "garage", "garages",
            "retail", "commercial", "industrial", "warehouse", "office", "school", "hospital", "hotel",
            "church", "chapel", "cathedral", "civic", "public", "university", "train_station", "hut",
            "cabin", "greenhouse", "shed", "stable", "farm_auxiliary", "bridge", "bunker", "roof",
            "construction"
    );

    StreetSelectionDialog(StreetModeController streetModeController) {
        this.streetModeController = streetModeController;

        Frame owner = MainApplication.getMainFrame();
        this.dialog = new JDialog(owner, I18n.tr("Quick Address Fill"), false);
        this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        this.postcodeField = new JTextField();
        this.postcodeField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyAddressChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notifyAddressChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notifyAddressChanged();
            }
        });

        this.houseNumberField = new JTextField();
        this.houseNumberField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                enforcePlusOneForLetterHouseNumbers();
                notifyAddressChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                enforcePlusOneForLetterHouseNumbers();
                notifyAddressChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                enforcePlusOneForLetterHouseNumbers();
                notifyAddressChanged();
            }
        });

        this.streetCombo = new JComboBox<>();
        this.streetCombo.setPrototypeDisplayValue(I18n.tr("Example Street with Longer Name"));
        this.streetCombo.addActionListener(e -> onStreetSelectionChanged());

        this.buildingTypeCombo = createBuildingTypeCombo();
        this.buildingTypeCombo.setToolTipText(I18n.tr("Building type applies to next successful click only"));
        this.streetModeController.setHouseNumberUpdateListener(this::updateHouseNumberFromMode);
        this.streetModeController.setAddressValuesReadListener(this::updateAddressValuesFromMode);
        this.streetModeController.setBuildingTypeConsumedListener(this::consumeBuildingTypeFromMode);

        JButton closeButton = new JButton(I18n.tr("Close"));
        closeButton.addActionListener(e -> closeDialog());

        this.modeStateLabel = new JLabel();
        this.continueWorkingButton = new JButton(I18n.tr("Continue working"));
        this.continueWorkingButton.addActionListener(e -> continueWorking());
        this.streetModeController.setModeStateListener(this::refreshModeStateUi);
        JPanel modeStatePanel = new JPanel(new BorderLayout(8, 0));
        modeStatePanel.add(modeStateLabel, BorderLayout.WEST);
        modeStatePanel.add(continueWorkingButton, BorderLayout.EAST);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 8);
        formPanel.add(new JLabel(I18n.tr("Postcode:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 4, 0);
        formPanel.add(postcodeField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 8);
        formPanel.add(new JLabel(I18n.tr("Street:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        formPanel.add(streetCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(4, 0, 0, 8);
        formPanel.add(new JLabel(I18n.tr("Building type:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 0, 0);
        formPanel.add(buildingTypeCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(4, 0, 0, 8);
        formPanel.add(new JLabel(I18n.tr("House number:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 0, 0);
        formPanel.add(houseNumberField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(4, 0, 0, 8);
        formPanel.add(new JLabel(I18n.tr("Increment:")), gbc);

        JPanel incrementButtonsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints incGbc = new GridBagConstraints();
        incGbc.gridy = 0;
        incGbc.insets = new Insets(0, 0, 0, 4);

        ButtonGroup incrementGroup = new ButtonGroup();
        this.minusTwoIncrementButton = createIncrementButton(-2);
        this.minusOneIncrementButton = createIncrementButton(-1);
        this.plusOneIncrementButton = createIncrementButton(1);
        this.plusTwoIncrementButton = createIncrementButton(2);

        incrementGroup.add(minusTwoIncrementButton);
        incrementGroup.add(minusOneIncrementButton);
        incrementGroup.add(plusOneIncrementButton);
        incrementGroup.add(plusTwoIncrementButton);
        plusOneIncrementButton.setSelected(true);

        incGbc.gridx = 0;
        incrementButtonsPanel.add(minusTwoIncrementButton, incGbc);
        incGbc.gridx = 1;
        incrementButtonsPanel.add(minusOneIncrementButton, incGbc);
        incGbc.gridx = 2;
        incrementButtonsPanel.add(plusOneIncrementButton, incGbc);
        incGbc.gridx = 3;
        incGbc.insets = new Insets(0, 0, 0, 0);
        incrementButtonsPanel.add(plusTwoIncrementButton, incGbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 0, 0);
        formPanel.add(incrementButtonsPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 0);
        formPanel.add(new JLabel(I18n.tr("Click: apply address")), gbc);

        gbc.gridy = 6;
        gbc.insets = new Insets(2, 0, 0, 0);
        formPanel.add(new JLabel(I18n.tr("Ctrl+Click: read address")), gbc);

        gbc.gridy = 7;
        formPanel.add(new JLabel(I18n.tr("+ / -: change number or letter")), gbc);

        gbc.gridy = 8;
        formPanel.add(new JLabel(I18n.tr("L: toggle letter suffix")), gbc);

        JPanel buildingSplitterPanel = new JPanel(new BorderLayout(8, 0));
        this.buildingSplitterStatusLabel = new JLabel();
        this.splitBuildingButton = new JButton(I18n.tr("Split building"));
        this.splitBuildingButton.setEnabled(false);
        this.splitBuildingButton.addActionListener(e -> onSplitBuildingRequested());
        buildingSplitterPanel.add(buildingSplitterStatusLabel, BorderLayout.WEST);
        buildingSplitterPanel.add(splitBuildingButton, BorderLayout.EAST);

        gbc.gridy = 9;
        gbc.insets = new Insets(6, 0, 0, 0);
        formPanel.add(buildingSplitterPanel, gbc);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(modeStatePanel, BorderLayout.NORTH);
        content.add(formPanel, BorderLayout.CENTER);
        content.add(closeButton, BorderLayout.SOUTH);

        this.dialog.getContentPane().add(content, BorderLayout.CENTER);
        this.dialog.setMinimumSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        this.dialog.setSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        positionTopLeftInOwner(owner);
        this.dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });
        registerSplitShortcut();
        refreshBuildingSplitterAvailability();
    }

    private void registerSplitShortcut() {
        String actionKey = "qaf.splitBuildingShortcut";
        dialog.getRootPane()
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), actionKey);
        dialog.getRootPane().getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isTextInputFocused() || splitBuildingButton == null || !splitBuildingButton.isVisible() || !splitBuildingButton.isEnabled()) {
                    return;
                }
                onSplitBuildingRequested();
            }
        });
    }

    private boolean isTextInputFocused() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner instanceof JTextComponent;
    }

    static void showNoDataSetMessage() {
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                I18n.tr("No active dataset available."),
                I18n.tr("Quick Address Fill"),
                JOptionPane.WARNING_MESSAGE
        );
    }

    void showDialog(List<String> streetNames, String suggestedPostcode) {
        if (streetNames == null || streetNames.isEmpty()) {
            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    I18n.tr("No street names found in the current view."),
                    I18n.tr("Quick Address Fill"),
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        String previousStreet = firstNonEmpty(getSelectedStreet(), rememberedStreet);
        updatingInputs = true;

        streetCombo.removeAllItems();
        for (String streetName : streetNames) {
            streetCombo.addItem(streetName);
        }

        if (previousStreet != null) {
            streetCombo.setSelectedItem(previousStreet);
        }
        if (streetCombo.getSelectedItem() == null && streetCombo.getItemCount() > 0) {
            streetCombo.setSelectedIndex(0);
        }

        postcodeField.setText(firstNonEmpty(rememberedPostcode, normalize(suggestedPostcode)));
        buildingTypeCombo.getEditor().setItem(firstNonEmpty(rememberedBuildingType, ""));
        houseNumberField.setText(firstNonEmpty(rememberedHouseNumber, DEFAULT_HOUSE_NUMBER));
        applyIncrementStep(rememberedIncrementStep);
        lastSelectedStreet = getSelectedStreet();
        updatingInputs = false;

        notifyAddressChanged();
        refreshModeStateUi(streetModeController.isActive());
        refreshBuildingSplitterAvailability();

        if (!dialog.isVisible()) {
            positionTopLeftInOwner(MainApplication.getMainFrame());
            dialog.setVisible(true);
        } else {
            dialog.toFront();
            dialog.requestFocus();
        }
    }

    private void notifyAddressChanged() {
        if (updatingInputs) {
            return;
        }
        enforcePlusOneForLetterHouseNumbers();
        rememberCurrentValues();
        String selectedStreet = getSelectedStreet();
        if (selectedStreet != null) {
            streetModeController.activate(
                    selectedStreet,
                    postcodeField.getText(),
                    getSelectedBuildingType(),
                    houseNumberField.getText(),
                    houseNumberIncrementStep
            );
        }
    }

    private void onStreetSelectionChanged() {
        if (updatingInputs) {
            return;
        }

        String selectedStreet = getSelectedStreet();
        if (selectedStreet != null && !selectedStreet.equals(lastSelectedStreet)) {
            boolean wasUpdatingInputs = updatingInputs;
            updatingInputs = true;
            houseNumberField.setText(DEFAULT_HOUSE_NUMBER);
            updatingInputs = wasUpdatingInputs;
        }
        lastSelectedStreet = selectedStreet;
        rememberedStreet = normalize(selectedStreet);
        notifyAddressChanged();
    }

    private JComboBox<String> createBuildingTypeCombo() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.addItem("");
        for (String buildingType : COMMON_BUILDING_TYPES) {
            combo.addItem(buildingType);
        }
        combo.addActionListener(e -> notifyAddressChanged());

        Object editorComponent = combo.getEditor().getEditorComponent();
        if (editorComponent instanceof JTextField) {
            ((JTextField) editorComponent).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    notifyAddressChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    notifyAddressChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    notifyAddressChanged();
                }
            });
        }
        return combo;
    }

    private String getSelectedBuildingType() {
        Object selected = buildingTypeCombo.getEditor().getItem();
        if (selected == null) {
            return "";
        }
        return selected.toString().trim();
    }

    private JToggleButton createIncrementButton(int incrementStep) {
        String label = incrementStep > 0 ? "+" + incrementStep : Integer.toString(incrementStep);
        JToggleButton button = new JToggleButton(label);
        button.setMargin(new Insets(1, 6, 1, 6));
        button.addActionListener(e -> {
            if (updatingInputs) {
                return;
            }
            houseNumberIncrementStep = incrementStep;
            notifyAddressChanged();
        });
        return button;
    }

    private void enforcePlusOneForLetterHouseNumbers() {
        if (!containsLetter(houseNumberField.getText())) {
            return;
        }
        if (houseNumberIncrementStep == 1) {
            return;
        }

        houseNumberIncrementStep = 1;
        if (plusOneIncrementButton != null) {
            boolean wasUpdatingInputs = updatingInputs;
            updatingInputs = true;
            plusOneIncrementButton.setSelected(true);
            updatingInputs = wasUpdatingInputs;
        }
        rememberedIncrementStep = houseNumberIncrementStep;
    }

    private boolean containsLetter(String value) {
        return value != null && value.matches(".*[A-Za-z].*");
    }

    private void updateHouseNumberFromMode(String houseNumber) {
        updatingInputs = true;
        houseNumberField.setText(normalize(houseNumber));
        updatingInputs = false;
        notifyAddressChanged();
    }

    private void updateAddressValuesFromMode(String streetName, String postcode, String buildingType, String houseNumber) {
        updatingInputs = true;
        setStreetSelection(streetName);
        postcodeField.setText(normalize(postcode));
        buildingTypeCombo.getEditor().setItem(normalize(buildingType));
        houseNumberField.setText(normalize(houseNumber));
        lastSelectedStreet = getSelectedStreet();
        updatingInputs = false;
        notifyAddressChanged();
    }

    private void consumeBuildingTypeFromMode() {
        updatingInputs = true;
        buildingTypeCombo.getEditor().setItem("");
        updatingInputs = false;
        rememberCurrentValues();
        notifyAddressChanged();
    }

    private void setStreetSelection(String streetName) {
        String normalizedStreet = streetName == null ? "" : streetName.trim();
        if (normalizedStreet.isEmpty()) {
            return;
        }

        for (int i = 0; i < streetCombo.getItemCount(); i++) {
            String item = streetCombo.getItemAt(i);
            if (normalizedStreet.equals(item)) {
                streetCombo.setSelectedIndex(i);
                return;
            }
        }

        streetCombo.addItem(normalizedStreet);
        streetCombo.setSelectedItem(normalizedStreet);
    }

    private void positionTopLeftInOwner(Frame owner) {
        if (owner == null) {
            return;
        }
        dialog.setLocation(owner.getX() + DIALOG_OFFSET_X, owner.getY() + DIALOG_OFFSET_Y);
    }

    private String getSelectedStreet() {
        Object selected = streetCombo.getSelectedItem();
        return selected instanceof String ? normalize((String) selected) : null;
    }

    private void closeDialog() {
        rememberCurrentValues();
        dialog.setVisible(false);
        streetModeController.deactivate();
    }

    private void continueWorking() {
        streetModeController.activate(
                getSelectedStreet(),
                postcodeField.getText(),
                getSelectedBuildingType(),
                houseNumberField.getText(),
                houseNumberIncrementStep
        );
    }

    private void refreshModeStateUi(boolean active) {
        if (modeStateLabel == null || continueWorkingButton == null) {
            return;
        }
        if (active) {
            modeStateLabel.setText(I18n.tr("Active"));
            modeStateLabel.setForeground(new java.awt.Color(0, 140, 0));
            continueWorkingButton.setVisible(false);
        } else {
            modeStateLabel.setText(I18n.tr("Paused"));
            modeStateLabel.setForeground(new java.awt.Color(150, 60, 60));
            continueWorkingButton.setVisible(true);
        }
    }

    private void refreshBuildingSplitterAvailability() {
        if (buildingSplitterStatusLabel == null || splitBuildingButton == null) {
            return;
        }
        boolean buildingSplitterAvailable = BuildingSplitterDetector.isBuildingSplitterAvailable();
        buildingSplitterStatusLabel.setText(buildingSplitterAvailable ? "" : I18n.tr("Building Splitter: not found"));
        buildingSplitterStatusLabel.setVisible(!buildingSplitterAvailable);
        splitBuildingButton.setVisible(buildingSplitterAvailable);
        splitBuildingButton.setEnabled(buildingSplitterAvailable);
    }

    private void onSplitBuildingRequested() {
        if (streetModeController.activateBuildingSplitterAndReturn()) {
            refreshBuildingSplitterAvailability();
            return;
        }
        refreshBuildingSplitterAvailability();
        new Notification(I18n.tr("Could not activate Building Splitter."))
                .setDuration(Notification.TIME_SHORT)
                .show();
    }

    private void applyIncrementStep(int incrementStep) {
        int normalizedStep = normalizeIncrementStep(incrementStep);
        houseNumberIncrementStep = normalizedStep;
        rememberedIncrementStep = normalizedStep;

        if (minusTwoIncrementButton == null || minusOneIncrementButton == null
                || plusOneIncrementButton == null || plusTwoIncrementButton == null) {
            return;
        }
        minusTwoIncrementButton.setSelected(normalizedStep == -2);
        minusOneIncrementButton.setSelected(normalizedStep == -1);
        plusOneIncrementButton.setSelected(normalizedStep == 1);
        plusTwoIncrementButton.setSelected(normalizedStep == 2);
    }

    private void rememberCurrentValues() {
        rememberedStreet = getSelectedStreet();
        rememberedPostcode = normalize(postcodeField.getText());
        rememberedBuildingType = normalize(getSelectedBuildingType());
        rememberedHouseNumber = normalize(houseNumberField.getText());
        rememberedIncrementStep = houseNumberIncrementStep;
    }

    private int normalizeIncrementStep(int step) {
        return step == -2 || step == -1 || step == 1 || step == 2 ? step : 1;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonEmpty(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        if (!normalizedPrimary.isEmpty()) {
            return normalizedPrimary;
        }
        return normalize(fallback);
    }
}
