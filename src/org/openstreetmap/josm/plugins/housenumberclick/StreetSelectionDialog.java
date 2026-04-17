package org.openstreetmap.josm.plugins.housenumberclick;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.I18n;

/**
 * Main configuration dialog where users pick street/address settings and receive disambiguated readback updates,
 * while street auto-zoom is limited to explicit street-selection actions.
 */
final class StreetSelectionDialog {

    private static final String DEFAULT_HOUSE_NUMBER = "1";
    private static final String INITIAL_HOUSE_NUMBER = "";

    private final StreetModeController streetModeController;
    private final DialogController dialogController = new DialogController();
    private final JDialog dialog;
    private final JComboBox<String> streetCombo;
    private final JComboBox<String> buildingTypeCombo;
    private final JComboBox<String> postcodeCombo;
    private final JTextField houseNumberField;
    private final JCheckBox applyTypeToAllCheckbox;
    private JToggleButton minusTwoIncrementButton;
    private JToggleButton minusOneIncrementButton;
    private JToggleButton plusOneIncrementButton;
    private JToggleButton plusTwoIncrementButton;
    private final JLabel modeStateLabel;
    private final JButton continueWorkingButton;
    private final JButton createOverviewButton;
    private final JButton createDuplicateOverviewButton;
    private final JButton createPostcodeOverviewButton;
    private final JRadioButton completenessPostcodeRadioButton;
    private final JRadioButton completenessStreetRadioButton;
    private final JRadioButton completenessHouseNumberRadioButton;
    private final JRadioButton completenessAnyRadioButton;
    private final JButton previousStreetButton;
    private final JButton nextStreetButton;
    private final KeyEventDispatcher streetNavigationKeyDispatcher;
    private final JCheckBox showHouseNumberLayerCheckbox;
    private final JCheckBox showConnectionLinesCheckbox;
    private final JCheckBox showSeparateEvenOddConnectionLinesCheckbox;
    private final JCheckBox showHouseNumberOverviewCheckbox;
    private final JCheckBox showStreetHouseNumberCountsCheckbox;
    private final JCheckBox zoomToSelectedStreetCheckbox;
    private final JCheckBox splitMakeRectangularCheckbox;
    private final JButton rowHousePartsMinusButton;
    private final JButton rowHousePartsPlusButton;
    private final JTextField rowHousePartsField;
    private int houseNumberIncrementStep = 1;
    private List<StreetOption> currentStreetOptions = List.of();
    private String lastSelectedStreet;
    private String rememberedStreet;
    private String rememberedPostcode;
    private String rememberedBuildingType;
    private String rememberedHouseNumber = INITIAL_HOUSE_NUMBER;
    private int rememberedIncrementStep = 1;
    private boolean rememberedHouseNumberLayerEnabled = true;
    private boolean rememberedConnectionLinesEnabled = true;
    private boolean rememberedConnectionLinesPreference = true;
    private boolean rememberedSeparateEvenOddLinesEnabled = true;
    private boolean rememberedSeparateEvenOddLinesPreference = true;
    private boolean rememberedHouseNumberOverviewEnabled;
    private boolean rememberedStreetHouseNumberCountsEnabled;
    private boolean rememberedZoomToSelectedStreetEnabled = true;
    private boolean rememberedSplitMakeRectangular;
    private boolean updatingInputs;
    private boolean streetSelectionChangedByNavigation;
    private DataSet rememberedDataSet;
    private boolean streetNavigationDispatcherRegistered;
    private Component lastFocusedDialogInput;

    private static final int DIALOG_WIDTH = 390;
    private static final int DIALOG_HEIGHT = 980;
    private static final int DIALOG_OFFSET_X = 66;
    private static final int DIALOG_OFFSET_Y = 80;
    private static final String SHOW_OVERVIEW_BUTTON_TEXT = I18n.tr("Show completeness");
    private static final String HIDE_OVERVIEW_BUTTON_TEXT = I18n.tr("Hide completeness");
    private static final String SHOW_DUPLICATE_BUTTON_TEXT = I18n.tr("Show duplicates");
    private static final String HIDE_DUPLICATE_BUTTON_TEXT = I18n.tr("Hide duplicates");
    private static final String SHOW_POSTCODE_BUTTON_TEXT = I18n.tr("Show All Postcodes");
    private static final String HIDE_POSTCODE_BUTTON_TEXT = I18n.tr("Hide All Postcodes");
    private static final List<String> COMMON_BUILDING_TYPES = Arrays.asList(
            "yes", "apartments", "residential", "house", "detached", "terrace", "garage", "garages",
            "retail", "commercial", "industrial", "warehouse", "office", "school", "hospital", "hotel",
            "church", "chapel", "cathedral", "civic", "public", "university", "train_station", "hut",
            "cabin", "greenhouse", "shed", "stable", "farm_auxiliary", "bridge", "bunker", "roof",
            "construction"
    );

    StreetSelectionDialog(StreetModeController streetModeController) {
        this.streetModeController = streetModeController;
        this.streetNavigationKeyDispatcher = this::handleGlobalStreetNavigationKeyEvent;

        Frame owner = MainApplication.getMainFrame();
        this.dialog = new JDialog(owner, I18n.tr("HouseNumberClick"), false);
        this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        this.postcodeCombo = new JComboBox<>();
        this.postcodeCombo.setEditable(true);
        this.postcodeCombo.addActionListener(e -> notifyAddressChanged());

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
        this.buildingTypeCombo.setToolTipText(I18n.tr("Set a building type. Use 'Apply type to all' to keep it for multiple clicks."));
        this.applyTypeToAllCheckbox = new JCheckBox(I18n.tr("Apply type to all"));
        this.applyTypeToAllCheckbox.setSelected(false);
        this.applyTypeToAllCheckbox.setToolTipText(I18n.tr("Keep the selected building type after a successful click."));
        this.applyTypeToAllCheckbox.addActionListener(e -> onApplyTypeToAllSelectionChanged());
        this.streetModeController.setHouseNumberUpdateListener(this::updateHouseNumberFromMode);
        this.streetModeController.setAddressValuesReadListener(this::updateAddressValuesFromMode);
        this.streetModeController.setBuildingTypeConsumedListener(this::consumeBuildingTypeFromMode);
        this.streetModeController.setStreetSelectionRequestListener(this::applyStreetSelectionFromOverview);
        this.streetModeController.setHouseNumberOverviewVisibilityListener(this::updateHouseNumberOverviewCheckboxFromController);
        this.streetModeController.setStreetHouseNumberCountsVisibilityListener(this::updateStreetHouseNumberCountsCheckboxFromController);

        JButton closeButton = new JButton(I18n.tr("Close"));
        closeButton.addActionListener(e -> closeDialog());

        this.previousStreetButton = new JButton(I18n.tr("◀ Previous"));
        this.nextStreetButton = new JButton(I18n.tr("Next ▶"));
        this.previousStreetButton.addActionListener(e -> navigateStreetByOffset(-1));
        this.nextStreetButton.addActionListener(e -> navigateStreetByOffset(1));

        this.modeStateLabel = new JLabel();
        this.continueWorkingButton = new JButton(I18n.tr("Resume"));
        this.continueWorkingButton.addActionListener(e -> continueWorking());

        this.completenessPostcodeRadioButton = new JRadioButton(I18n.tr("Postcode"));
        this.completenessStreetRadioButton = new JRadioButton(I18n.tr("Street"));
        this.completenessHouseNumberRadioButton = new JRadioButton(I18n.tr("Number"));
        this.completenessAnyRadioButton = new JRadioButton(I18n.tr("Any"));
        ButtonGroup completenessFieldGroup = new ButtonGroup();
        completenessFieldGroup.add(completenessPostcodeRadioButton);
        completenessFieldGroup.add(completenessStreetRadioButton);
        completenessFieldGroup.add(completenessHouseNumberRadioButton);
        completenessFieldGroup.add(completenessAnyRadioButton);
        applyCompletenessRadioSelection(streetModeController.getCompletenessMissingField());
        this.completenessPostcodeRadioButton.addActionListener(e -> onCompletenessMissingFieldChanged());
        this.completenessStreetRadioButton.addActionListener(e -> onCompletenessMissingFieldChanged());
        this.completenessHouseNumberRadioButton.addActionListener(e -> onCompletenessMissingFieldChanged());
        this.completenessAnyRadioButton.addActionListener(e -> onCompletenessMissingFieldChanged());

        this.createOverviewButton = new JButton(SHOW_OVERVIEW_BUTTON_TEXT);
        this.createOverviewButton.addActionListener(e -> onCreateOverviewRequested());
        this.createDuplicateOverviewButton = new JButton(SHOW_DUPLICATE_BUTTON_TEXT);
        this.createDuplicateOverviewButton.addActionListener(e -> onCreateDuplicateOverviewRequested());
        this.createPostcodeOverviewButton = new JButton(SHOW_POSTCODE_BUTTON_TEXT);
        this.createPostcodeOverviewButton.addActionListener(e -> onCreatePostcodeOverviewRequested());
        this.streetModeController.setModeStateListener(this::refreshModeStateUi);
        this.modeStateLabel.setFont(this.modeStateLabel.getFont().deriveFont(Font.BOLD));

        this.showHouseNumberLayerCheckbox = new JCheckBox(I18n.tr("Show house number labels"));
        this.showConnectionLinesCheckbox = new JCheckBox(I18n.tr("Show connections"));
        this.showSeparateEvenOddConnectionLinesCheckbox = new JCheckBox(I18n.tr("Separate even / odd"));
        this.showHouseNumberOverviewCheckbox = new JCheckBox(I18n.tr("Show overview panel (selected street)"));
        this.showStreetHouseNumberCountsCheckbox = new JCheckBox(I18n.tr("Show all street counts"));
        this.zoomToSelectedStreetCheckbox = new JCheckBox(I18n.tr("Auto-zoom to selected street"));
        this.showHouseNumberLayerCheckbox.addActionListener(e -> onOverlayLayerSelectionChanged());
        this.showConnectionLinesCheckbox.addActionListener(e -> onConnectionLinesSelectionChanged());
        this.showSeparateEvenOddConnectionLinesCheckbox.addActionListener(e -> onSeparateEvenOddConnectionLinesSelectionChanged());
        this.showHouseNumberOverviewCheckbox.addActionListener(e -> onHouseNumberOverviewSelectionChanged());
        this.showStreetHouseNumberCountsCheckbox.addActionListener(e -> onStreetHouseNumberCountsSelectionChanged());
        this.zoomToSelectedStreetCheckbox.addActionListener(e -> onZoomToSelectedStreetSelectionChanged());
        updateOverlayOptionsEnablement(false, false);

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

        JPanel modeStatePanel = new JPanel(new BorderLayout(8, 0));
        modeStatePanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Status")));
        modeStatePanel.add(modeStateLabel, BorderLayout.WEST);
        modeStatePanel.add(continueWorkingButton, BorderLayout.EAST);
        int statusPanelStableHeight = Math.max(modeStateLabel.getPreferredSize().height, continueWorkingButton.getPreferredSize().height) + 16;
        Dimension stableStatusSize = new Dimension(modeStatePanel.getPreferredSize().width, statusPanelStableHeight);
        modeStatePanel.setMinimumSize(stableStatusSize);
        modeStatePanel.setPreferredSize(stableStatusSize);

        this.splitMakeRectangularCheckbox = new JCheckBox(I18n.tr("Make rectangular"));
        this.splitMakeRectangularCheckbox.setSelected(rememberedSplitMakeRectangular);
        this.splitMakeRectangularCheckbox.addActionListener(e -> onSplitMakeRectangularSelectionChanged());
        this.streetModeController.setRectangularizeAfterLineSplit(this.splitMakeRectangularCheckbox.isSelected());
        this.rowHousePartsField = new JTextField("2", 4);
        this.rowHousePartsField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onRowHousePartsChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onRowHousePartsChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onRowHousePartsChanged();
            }
        });
        int rowHousePartsFieldHeight = rowHousePartsField.getPreferredSize().height;
        Dimension rowHousePartsFieldSize = new Dimension(40, rowHousePartsFieldHeight);
        this.rowHousePartsField.setPreferredSize(rowHousePartsFieldSize);
        this.rowHousePartsField.setMinimumSize(rowHousePartsFieldSize);
        this.rowHousePartsMinusButton = createRowHousePartsAdjustButton(-1);
        this.rowHousePartsPlusButton = createRowHousePartsAdjustButton(1);
        this.rowHousePartsMinusButton.setMargin(new Insets(1, 10, 1, 10));
        this.rowHousePartsPlusButton.setMargin(new Insets(1, 10, 1, 10));
        int partsButtonWidth = Math.max(
                40,
                Math.max(rowHousePartsMinusButton.getPreferredSize().width, rowHousePartsPlusButton.getPreferredSize().width)
        );
        int partsButtonHeight = Math.max(
                rowHousePartsFieldHeight + 4,
                Math.max(rowHousePartsMinusButton.getPreferredSize().height, rowHousePartsPlusButton.getPreferredSize().height)
        );
        Dimension squarePartsButtonSize = new Dimension(partsButtonWidth, partsButtonHeight);
        this.rowHousePartsMinusButton.setPreferredSize(squarePartsButtonSize);
        this.rowHousePartsMinusButton.setMinimumSize(squarePartsButtonSize);
        this.rowHousePartsMinusButton.setMaximumSize(squarePartsButtonSize);
        this.rowHousePartsPlusButton.setPreferredSize(squarePartsButtonSize);
        this.rowHousePartsPlusButton.setMinimumSize(squarePartsButtonSize);
        this.rowHousePartsPlusButton.setMaximumSize(squarePartsButtonSize);
        this.streetModeController.setTerracePartsUpdateListener(this::updateRowHousePartsFromMode);

        JPanel sectionsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints sectionGbc = new GridBagConstraints();
        sectionGbc.gridx = 0;
        sectionGbc.weightx = 1.0;
        sectionGbc.fill = GridBagConstraints.HORIZONTAL;
        sectionGbc.anchor = GridBagConstraints.NORTHWEST;

        sectionGbc.gridy = 0;
        sectionGbc.insets = new Insets(0, 0, 0, 0);
        sectionsPanel.add(createAddressSection(incrementButtonsPanel), sectionGbc);

        sectionGbc.gridy = 1;
        sectionGbc.insets = new Insets(6, 0, 0, 0);
        sectionsPanel.add(createStreetNavigationSection(), sectionGbc);

        sectionGbc.gridy = 2;
        sectionGbc.insets = new Insets(6, 0, 0, 0);
        sectionsPanel.add(createLineSplitSection(), sectionGbc);

        sectionGbc.gridy = 3;
        sectionGbc.insets = new Insets(6, 0, 0, 0);
        sectionsPanel.add(createRowHousesSection(), sectionGbc);

        sectionGbc.gridy = 4;
        sectionGbc.insets = new Insets(6, 0, 0, 0);
        sectionsPanel.add(createDisplaySection(), sectionGbc);

        sectionGbc.gridy = 5;
        sectionGbc.insets = new Insets(6, 0, 0, 0);
        sectionsPanel.add(createAnalysisSection(), sectionGbc);

        sectionGbc.gridy = 6;
        sectionGbc.insets = new Insets(6, 0, 0, 0);
        sectionsPanel.add(createHelpSection(), sectionGbc);

        sectionGbc.gridy = 7;
        sectionGbc.weighty = 1.0;
        sectionGbc.fill = GridBagConstraints.BOTH;
        sectionGbc.insets = new Insets(0, 0, 0, 0);
        sectionsPanel.add(new JPanel(), sectionGbc);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(modeStatePanel, BorderLayout.NORTH);
        content.add(sectionsPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.add(closeButton, BorderLayout.EAST);
        content.add(bottomPanel, BorderLayout.SOUTH);

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
    }

    static void showNoDataSetMessage() {
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                I18n.tr("No active dataset available."),
                I18n.tr("HouseNumberClick"),
                JOptionPane.WARNING_MESSAGE
        );
    }

    void showDialog(DataSet activeDataSet, List<StreetOption> streetOptions, List<String> detectedPostcodes) {
        if (streetOptions == null || streetOptions.isEmpty()) {
            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    I18n.tr("No street names found in the current view."),
                    I18n.tr("HouseNumberClick"),
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        if (isDataSetChanged(activeDataSet)) {
            resetRememberedValuesForDataSetChange();
        }
        rememberedDataSet = activeDataSet;

        String previousStreet = null;
        updatingInputs = true;
        currentStreetOptions = new ArrayList<>();

        streetCombo.removeAllItems();
        for (StreetOption option : streetOptions) {
            if (option == null || !option.isValid()) {
                continue;
            }
            currentStreetOptions.add(option);
            streetCombo.addItem(option.getDisplayStreetName());
        }

        if (previousStreet != null && !normalize(previousStreet).isEmpty()) {
            streetCombo.setSelectedItem(previousStreet);
        }
        streetCombo.setSelectedItem(null);

        populatePostcodeOptions(detectedPostcodes);
        setSelectedPostcode(rememberedPostcode);
        buildingTypeCombo.getEditor().setItem(firstNonEmpty(rememberedBuildingType, ""));
        houseNumberField.setText(firstNonEmpty(rememberedHouseNumber, INITIAL_HOUSE_NUMBER));
        applyIncrementStep(rememberedIncrementStep);
        applyOverlaySettings(
                rememberedHouseNumberLayerEnabled,
                rememberedConnectionLinesEnabled,
                rememberedSeparateEvenOddLinesEnabled
        );
        showHouseNumberOverviewCheckbox.setSelected(rememberedHouseNumberOverviewEnabled);
        showStreetHouseNumberCountsCheckbox.setSelected(rememberedStreetHouseNumberCountsEnabled);
        zoomToSelectedStreetCheckbox.setSelected(rememberedZoomToSelectedStreetEnabled);
        splitMakeRectangularCheckbox.setSelected(rememberedSplitMakeRectangular);
        streetModeController.setRectangularizeAfterLineSplit(rememberedSplitMakeRectangular);
        applyCompletenessRadioSelection(streetModeController.getCompletenessMissingField());
        rowHousePartsField.setText(Integer.toString(streetModeController.getConfiguredTerraceParts()));
        lastSelectedStreet = null;
        rememberedStreet = null;
        updatingInputs = false;
        updateStreetNavigationButtonState();

        notifyAddressChanged();
        notifyOverlaySettingsChanged();
        notifyHouseNumberOverviewChanged();
        notifyStreetHouseNumberCountsChanged();
        notifyZoomToSelectedStreetChanged();
        refreshModeStateUi(streetModeController.isActive());
        refreshOverviewButtonLabel();
        refreshDuplicateOverviewButtonLabel();
        refreshPostcodeOverviewButtonLabel();

        if (!dialog.isVisible()) {
            positionTopLeftInOwner(MainApplication.getMainFrame());
            dialog.setVisible(true);
            registerStreetNavigationDispatcher();
        } else {
            dialog.toFront();
            dialog.requestFocus();
        }

        // Ensure Street Mode is the effective active map mode after UI state updates.
        streetModeController.activate(buildCurrentSelection());
        refreshModeStateUi(streetModeController.isActive());
    }

    private void notifyAddressChanged() {
        if (updatingInputs) {
            return;
        }
        enforcePlusOneForLetterHouseNumbers();
        rememberCurrentValues();
        streetModeController.activate(buildCurrentSelection());
    }

    private void onStreetSelectionChanged() {
        if (updatingInputs) {
            return;
        }

        String selectedStreet = getSelectedStreet();
        boolean streetChanged = hasStreetSelectionChanged(lastSelectedStreet, selectedStreet);
        boolean changedByNavigation = consumeStreetSelectionChangedByNavigation();
        if (streetChanged) {
            boolean wasUpdatingInputs = updatingInputs;
            updatingInputs = true;
            if (changedByNavigation) {
                setSelectedPostcode("");
                houseNumberField.setText("");
            } else {
                houseNumberField.setText(DEFAULT_HOUSE_NUMBER);
            }
            updatingInputs = wasUpdatingInputs;
        }
        lastSelectedStreet = selectedStreet;
        rememberedStreet = normalize(selectedStreet);
        notifyAddressChanged();
        if (streetChanged && zoomToSelectedStreetCheckbox.isSelected()) {
            streetModeController.zoomToCurrentStreet();
        }
        updateStreetNavigationButtonState();
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
        int enforcedStep = dialogController.enforcePlusOneForLetterHouseNumbers(
                houseNumberField.getText(),
                houseNumberIncrementStep
        );
        if (enforcedStep == houseNumberIncrementStep) {
            return;
        }

        houseNumberIncrementStep = enforcedStep;
        if (plusOneIncrementButton != null) {
            boolean wasUpdatingInputs = updatingInputs;
            updatingInputs = true;
            plusOneIncrementButton.setSelected(true);
            updatingInputs = wasUpdatingInputs;
        }
        rememberedIncrementStep = houseNumberIncrementStep;
    }


    private void updateHouseNumberFromMode(String houseNumber) {
        updatingInputs = true;
        houseNumberField.setText(normalize(houseNumber));
        updatingInputs = false;
        notifyAddressChanged();
    }

    private void updateRowHousePartsFromMode(int parts) {
        if (rowHousePartsField == null) {
            return;
        }
        String partsText = Integer.toString(parts);
        if (partsText.equals(rowHousePartsField.getText())) {
            return;
        }
        // Listener callbacks may run while document notifications are active; defer to avoid nested mutations.
        javax.swing.SwingUtilities.invokeLater(() -> applyRowHousePartsText(partsText));
    }

    private void applyRowHousePartsText(String partsText) {
        if (rowHousePartsField == null || partsText == null || partsText.equals(rowHousePartsField.getText())) {
            return;
        }
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        rowHousePartsField.setText(partsText);
        updatingInputs = wasUpdatingInputs;
    }

    private void updateAddressValuesFromMode(String streetName, String postcode, String buildingType, String houseNumber) {
        String previousSelectedStreet = getSelectedStreet();
        updatingInputs = true;
        StreetOption resolvedReadbackStreet = streetModeController.resolveStreetOptionForReadback(streetName);
        setStreetSelection(resolvedReadbackStreet != null ? resolvedReadbackStreet.getDisplayStreetName() : streetName);
        String normalizedPostcode = normalize(postcode);
        if (!normalizedPostcode.isEmpty()) {
            setSelectedPostcode(normalizedPostcode);
        }
        String normalizedBuildingType = normalize(buildingType);
        if (!normalizedBuildingType.isEmpty()) {
            buildingTypeCombo.getEditor().setItem(normalizedBuildingType);
        }
        String normalizedHouseNumber = normalize(houseNumber);
        if (!normalizedHouseNumber.isEmpty()) {
            houseNumberField.setText(normalizedHouseNumber);
        }
        String readbackSelectedStreet = getSelectedStreet();
        boolean streetChangedByReadback = hasStreetSelectionChanged(previousSelectedStreet, readbackSelectedStreet);
        lastSelectedStreet = readbackSelectedStreet;
        updatingInputs = false;
        notifyAddressChanged();
        if (streetChangedByReadback && zoomToSelectedStreetCheckbox.isSelected()) {
            streetModeController.zoomToCurrentStreet();
        }
        updateStreetNavigationButtonState();
    }

    private void consumeBuildingTypeFromMode() {
        if (applyTypeToAllCheckbox != null && applyTypeToAllCheckbox.isSelected()) {
            return;
        }
        updatingInputs = true;
        buildingTypeCombo.getEditor().setItem("");
        updatingInputs = false;
        rememberCurrentValues();
        notifyAddressChanged();
    }

    private void onApplyTypeToAllSelectionChanged() {
        if (applyTypeToAllCheckbox == null || !applyTypeToAllCheckbox.isSelected()) {
            return;
        }

        int decision = JOptionPane.showConfirmDialog(
                dialog,
                I18n.tr("Building type will stay active for subsequent clicks until you turn it off. Do you want to continue?"),
                I18n.tr("HouseNumberClick"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (decision != JOptionPane.YES_OPTION) {
            applyTypeToAllCheckbox.setSelected(false);
        }
    }

    private void onOverlayLayerSelectionChanged() {
        if (updatingInputs) {
            return;
        }

        boolean wasOverlayEnabled = rememberedHouseNumberLayerEnabled;
        boolean overlayEnabled = showHouseNumberLayerCheckbox.isSelected();
        if (!wasOverlayEnabled && overlayEnabled) {
            new Notification(I18n.tr("Please wait, this takes a moment."))
                    .setDuration(Notification.TIME_SHORT)
                    .show();
        }
        if (!overlayEnabled) {
            rememberedConnectionLinesPreference = showConnectionLinesCheckbox.isSelected();
            rememberedSeparateEvenOddLinesPreference = showSeparateEvenOddConnectionLinesCheckbox.isSelected();
            applyConnectionLinesSelection(false);
            applySeparateEvenOddConnectionLinesSelection(false);
        } else {
            applyConnectionLinesSelection(rememberedConnectionLinesPreference);
            if (showConnectionLinesCheckbox.isSelected()) {
                applySeparateEvenOddConnectionLinesSelection(rememberedSeparateEvenOddLinesPreference);
            }
        }
        updateOverlayOptionsEnablement(overlayEnabled, showConnectionLinesCheckbox.isSelected());
        rememberCurrentValues();
        notifyOverlaySettingsChanged();
        focusMapViewIfStreetModeActive();
    }

    private void onConnectionLinesSelectionChanged() {
        if (updatingInputs) {
            return;
        }
        boolean connectionLinesEnabled = showConnectionLinesCheckbox.isSelected();
        rememberedConnectionLinesPreference = connectionLinesEnabled;
        if (!connectionLinesEnabled) {
            rememberedSeparateEvenOddLinesPreference = showSeparateEvenOddConnectionLinesCheckbox.isSelected();
            applySeparateEvenOddConnectionLinesSelection(false);
        } else {
            applySeparateEvenOddConnectionLinesSelection(rememberedSeparateEvenOddLinesPreference);
        }
        updateOverlayOptionsEnablement(showHouseNumberLayerCheckbox.isSelected(), connectionLinesEnabled);
        rememberCurrentValues();
        notifyOverlaySettingsChanged();
        focusMapViewIfStreetModeActive();
    }

    private void onSeparateEvenOddConnectionLinesSelectionChanged() {
        if (updatingInputs) {
            return;
        }
        rememberedSeparateEvenOddLinesPreference = showSeparateEvenOddConnectionLinesCheckbox.isSelected();
        rememberCurrentValues();
        notifyOverlaySettingsChanged();
        focusMapViewIfStreetModeActive();
    }

    private void onHouseNumberOverviewSelectionChanged() {
        if (updatingInputs) {
            return;
        }
        rememberCurrentValues();
        notifyHouseNumberOverviewChanged();
        focusMapViewIfStreetModeActive();
    }

    private void onZoomToSelectedStreetSelectionChanged() {
        if (updatingInputs) {
            return;
        }
        rememberCurrentValues();
        notifyZoomToSelectedStreetChanged();
        focusMapViewIfStreetModeActive();
    }

    private void onStreetHouseNumberCountsSelectionChanged() {
        if (updatingInputs) {
            return;
        }
        rememberCurrentValues();
        notifyStreetHouseNumberCountsChanged();
        focusMapViewIfStreetModeActive();
    }

    private void focusMapViewIfStreetModeActive() {
        if (!streetModeController.isActive()) {
            return;
        }
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.requestFocusInWindow();
        }
    }

    private void setStreetSelection(String streetName) {
        String normalizedStreet = streetName == null ? "" : streetName.trim();
        if (normalizedStreet.isEmpty()) {
            return;
        }

        StreetOption matchingOption = findStreetOptionByDisplayName(normalizedStreet);
        if (matchingOption == null) {
            matchingOption = findFirstStreetOptionByBaseName(normalizedStreet);
        }
        if (matchingOption != null) {
            streetCombo.setSelectedItem(matchingOption.getDisplayStreetName());
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

    private void applyStreetSelectionFromOverview(StreetOption selectedStreetOption) {
        if (selectedStreetOption == null || !selectedStreetOption.isValid()) {
            return;
        }
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        setStreetSelection(selectedStreetOption.getDisplayStreetName());
        lastSelectedStreet = getSelectedStreet();
        rememberedStreet = normalize(lastSelectedStreet);
        updatingInputs = wasUpdatingInputs;
        updateStreetNavigationButtonState();
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
        unregisterStreetNavigationDispatcher();
        streetModeController.onMainDialogClosed();
    }

    private void continueWorking() {
        streetModeController.activate(buildCurrentSelection());
        restoreLastDialogInputFocus();
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

    private JPanel createAddressSection(JPanel incrementButtonsPanel) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Address")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(I18n.tr("Street:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(streetCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(2, 0, 2, 8);
        panel.add(new JLabel(I18n.tr("Postcode:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(postcodeCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(2, 0, 2, 8);
        panel.add(new JLabel(I18n.tr("House number:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(houseNumberField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(2, 0, 2, 8);
        panel.add(new JLabel(I18n.tr("Building type:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(buildingTypeCombo, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(applyTypeToAllCheckbox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(2, 0, 2, 8);
        panel.add(new JLabel(I18n.tr("Increment:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(incrementButtonsPanel, gbc);

        return panel;
    }

    private JPanel createDisplaySection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Display")));

        JPanel houseNumberSubOptionsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints subGbc = new GridBagConstraints();
        subGbc.gridx = 0;
        subGbc.gridy = 0;
        subGbc.anchor = GridBagConstraints.WEST;
        subGbc.insets = new Insets(0, 0, 0, 12);
        houseNumberSubOptionsPanel.add(showConnectionLinesCheckbox, subGbc);

        subGbc.gridx = 1;
        subGbc.insets = new Insets(0, 0, 0, 0);
        houseNumberSubOptionsPanel.add(showSeparateEvenOddConnectionLinesCheckbox, subGbc);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridy = 0;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(zoomToSelectedStreetCheckbox, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(showHouseNumberLayerCheckbox, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(2, 16, 2, 0);
        panel.add(houseNumberSubOptionsPanel, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(showHouseNumberOverviewCheckbox, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(showStreetHouseNumberCountsCheckbox, gbc);

        return panel;
    }

    private JPanel createAnalysisSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Analysis")));

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        buttons.add(createOverviewButton);
        buttons.add(createDuplicateOverviewButton);
        panel.add(buttons, BorderLayout.NORTH);

        JPanel radios = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        radios.add(completenessPostcodeRadioButton);
        radios.add(completenessStreetRadioButton);
        radios.add(completenessHouseNumberRadioButton);
        radios.add(completenessAnyRadioButton);
        panel.add(radios, BorderLayout.CENTER);

        JPanel postcodeButtonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        postcodeButtonRow.add(createPostcodeOverviewButton);
        panel.add(postcodeButtonRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStreetNavigationSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Select street")));

        harmonizeNavigationButtonWidths();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 4);
        panel.add(previousStreetButton, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 4, 0);
        panel.add(nextStreetButton, gbc);


        return panel;
    }

    private JPanel createLineSplitSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Line Split")));
        panel.add(splitMakeRectangularCheckbox, BorderLayout.WEST);
        return panel;
    }

    private JPanel createRowHousesSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Row Houses")));

        JPanel partsControlsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints controlsGbc = new GridBagConstraints();
        controlsGbc.gridy = 0;
        controlsGbc.anchor = GridBagConstraints.WEST;

        controlsGbc.gridx = 0;
        controlsGbc.insets = new Insets(0, 0, 0, 4);
        partsControlsPanel.add(rowHousePartsMinusButton, controlsGbc);

        controlsGbc.gridx = 1;
        controlsGbc.insets = new Insets(0, 0, 0, 4);
        partsControlsPanel.add(rowHousePartsField, controlsGbc);

        controlsGbc.gridx = 2;
        controlsGbc.insets = new Insets(0, 0, 0, 0);
        partsControlsPanel.add(rowHousePartsPlusButton, controlsGbc);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 0);
        panel.add(new JLabel(I18n.tr("Parts")), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(partsControlsPanel, gbc);

        return panel;
    }

    private JPanel createHelpSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Help")));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 2, 12);

        GridBagConstraints actionGbc = new GridBagConstraints();
        actionGbc.gridx = 1;
        actionGbc.anchor = GridBagConstraints.WEST;
        actionGbc.weightx = 1.0;
        actionGbc.fill = GridBagConstraints.HORIZONTAL;
        actionGbc.insets = new Insets(0, 0, 2, 0);

        addHelpRow(panel, gbc, actionGbc, 0, I18n.tr("Left click"), I18n.tr("Apply address"));
        addHelpRow(panel, gbc, actionGbc, 1, I18n.tr("Ctrl+Left click"), I18n.tr("Read address"));
        addHelpRow(panel, gbc, actionGbc, 2, I18n.tr("Alt+Left click+Drag"), I18n.tr("Split building"));
        addHelpRow(panel, gbc, actionGbc, 3, I18n.tr("Alt+Right click"), I18n.tr("Split into row houses"));
        addHelpRow(panel, gbc, actionGbc, 4, I18n.tr("Alt+1..9"), I18n.tr("Set number of parts"));

        return panel;
    }

    private void addHelpRow(JPanel panel, GridBagConstraints keyGbc, GridBagConstraints actionGbc,
                            int row, String input, String action) {
        keyGbc.gridy = row;
        actionGbc.gridy = row;
        panel.add(new JLabel(input), keyGbc);
        panel.add(new JLabel(action), actionGbc);
    }

    private void onSplitMakeRectangularSelectionChanged() {
        if (updatingInputs) {
            return;
        }
        rememberCurrentValues();
        streetModeController.setRectangularizeAfterLineSplit(splitMakeRectangularCheckbox.isSelected());
        focusMapViewIfStreetModeActive();
    }

    private void onRowHousePartsChanged() {
        if (updatingInputs || rowHousePartsField == null) {
            return;
        }
        int parts = parseTerraceParts(rowHousePartsField.getText());
        if (parts >= 2) {
            streetModeController.setConfiguredTerraceParts(parts);
        }
    }

    static int parseTerraceParts(String rawParts) {
        String normalized = normalizeStatic(rawParts);
        if (normalized.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private JButton createRowHousePartsAdjustButton(int delta) {
        JButton button = new JButton(delta < 0 ? "-" : "+");
        button.addActionListener(e -> {
            int current = parseTerraceParts(rowHousePartsField.getText());
            if (current < 2) {
                current = 2;
            }
            int next = Math.max(2, current + delta);
            rowHousePartsField.setText(Integer.toString(next));
        });
        return button;
    }

    private void onCreateOverviewRequested() {
        streetModeController.toggleBuildingOverviewLayer();
        refreshOverviewButtonLabel();
        refreshDuplicateOverviewButtonLabel();
        refreshPostcodeOverviewButtonLabel();
        focusMapViewIfStreetModeActive();
    }

    private void onCreateDuplicateOverviewRequested() {
        streetModeController.toggleDuplicateAddressOverviewLayer();
        refreshOverviewButtonLabel();
        refreshDuplicateOverviewButtonLabel();
        refreshPostcodeOverviewButtonLabel();
        focusMapViewIfStreetModeActive();
    }

    private void onCreatePostcodeOverviewRequested() {
        streetModeController.togglePostcodeOverviewLayer();
        refreshOverviewButtonLabel();
        refreshDuplicateOverviewButtonLabel();
        refreshPostcodeOverviewButtonLabel();
        focusMapViewIfStreetModeActive();
    }

    private void onCompletenessMissingFieldChanged() {
        if (updatingInputs) {
            return;
        }
        streetModeController.setCompletenessMissingField(getSelectedCompletenessMissingField());
        if (streetModeController.isBuildingOverviewLayerVisible()) {
            streetModeController.createBuildingOverviewLayer();
        }
        refreshOverviewButtonLabel();
        refreshDuplicateOverviewButtonLabel();
        refreshPostcodeOverviewButtonLabel();
        focusMapViewIfStreetModeActive();
    }

    private void refreshOverviewButtonLabel() {
        if (createOverviewButton == null) {
            return;
        }
        createOverviewButton.setText(
                streetModeController.isBuildingOverviewLayerVisible()
                        ? HIDE_OVERVIEW_BUTTON_TEXT
                        : SHOW_OVERVIEW_BUTTON_TEXT
        );
    }

    private void refreshPostcodeOverviewButtonLabel() {
        if (createPostcodeOverviewButton == null) {
            return;
        }
        createPostcodeOverviewButton.setText(
                streetModeController.isPostcodeOverviewLayerVisible()
                        ? HIDE_POSTCODE_BUTTON_TEXT
                        : SHOW_POSTCODE_BUTTON_TEXT
        );
    }

    private void refreshDuplicateOverviewButtonLabel() {
        if (createDuplicateOverviewButton == null) {
            return;
        }
        createDuplicateOverviewButton.setText(
                streetModeController.isDuplicateAddressOverviewLayerVisible()
                        ? HIDE_DUPLICATE_BUTTON_TEXT
                        : SHOW_DUPLICATE_BUTTON_TEXT
        );
    }

    private BuildingOverviewLayer.MissingField getSelectedCompletenessMissingField() {
        if (completenessAnyRadioButton.isSelected()) {
            return BuildingOverviewLayer.MissingField.ANY;
        }
        if (completenessStreetRadioButton.isSelected()) {
            return BuildingOverviewLayer.MissingField.STREET;
        }
        if (completenessHouseNumberRadioButton.isSelected()) {
            return BuildingOverviewLayer.MissingField.HOUSE_NUMBER;
        }
        return BuildingOverviewLayer.MissingField.POSTCODE;
    }

    private void applyCompletenessRadioSelection(BuildingOverviewLayer.MissingField missingField) {
        BuildingOverviewLayer.MissingField normalized = missingField != null
                ? missingField
                : BuildingOverviewLayer.MissingField.POSTCODE;
        switch (normalized) {
            case ANY:
                completenessAnyRadioButton.setSelected(true);
                break;
            case STREET:
                completenessStreetRadioButton.setSelected(true);
                break;
            case HOUSE_NUMBER:
                completenessHouseNumberRadioButton.setSelected(true);
                break;
            case POSTCODE:
            default:
                completenessPostcodeRadioButton.setSelected(true);
                break;
        }
        streetModeController.setCompletenessMissingField(normalized);
    }

    private void harmonizeNavigationButtonWidths() {
        if (previousStreetButton == null || nextStreetButton == null) {
            return;
        }
        int width = Math.max(previousStreetButton.getPreferredSize().width, nextStreetButton.getPreferredSize().width);
        int previousHeight = previousStreetButton.getPreferredSize().height;
        int nextHeight = nextStreetButton.getPreferredSize().height;
        previousStreetButton.setPreferredSize(new Dimension(width, previousHeight));
        nextStreetButton.setPreferredSize(new Dimension(width, nextHeight));
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
        rememberedPostcode = getSelectedPostcode();
        rememberedBuildingType = normalize(getSelectedBuildingType());
        rememberedHouseNumber = normalize(houseNumberField.getText());
        rememberedIncrementStep = houseNumberIncrementStep;
        rememberedHouseNumberLayerEnabled = showHouseNumberLayerCheckbox != null && showHouseNumberLayerCheckbox.isSelected();
        rememberedConnectionLinesEnabled = showConnectionLinesCheckbox != null && showConnectionLinesCheckbox.isSelected();
        rememberedSeparateEvenOddLinesEnabled = showSeparateEvenOddConnectionLinesCheckbox != null
                && showSeparateEvenOddConnectionLinesCheckbox.isSelected();
        rememberedHouseNumberOverviewEnabled = showHouseNumberOverviewCheckbox != null
                && showHouseNumberOverviewCheckbox.isSelected();
        rememberedStreetHouseNumberCountsEnabled = showStreetHouseNumberCountsCheckbox != null
                && showStreetHouseNumberCountsCheckbox.isSelected();
        rememberedZoomToSelectedStreetEnabled = zoomToSelectedStreetCheckbox != null
                && zoomToSelectedStreetCheckbox.isSelected();
        rememberedSplitMakeRectangular = splitMakeRectangularCheckbox != null
                && splitMakeRectangularCheckbox.isSelected();
        if (rememberedHouseNumberLayerEnabled) {
            rememberedConnectionLinesPreference = rememberedConnectionLinesEnabled;
            if (rememberedConnectionLinesEnabled) {
                rememberedSeparateEvenOddLinesPreference = rememberedSeparateEvenOddLinesEnabled;
            }
        }
    }

    private void applyOverlaySettings(boolean overlayEnabled, boolean connectionLinesEnabled, boolean separateEvenOddLinesEnabled) {
        boolean normalizedConnections = overlayEnabled && connectionLinesEnabled;
        boolean normalizedSeparateEvenOddLines = normalizedConnections && separateEvenOddLinesEnabled;
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        showHouseNumberLayerCheckbox.setSelected(overlayEnabled);
        showConnectionLinesCheckbox.setSelected(normalizedConnections);
        showSeparateEvenOddConnectionLinesCheckbox.setSelected(normalizedSeparateEvenOddLines);
        updatingInputs = wasUpdatingInputs;
        updateOverlayOptionsEnablement(overlayEnabled, normalizedConnections);
        rememberedConnectionLinesPreference = overlayEnabled ? normalizedConnections : rememberedConnectionLinesPreference;
        if (normalizedConnections) {
            rememberedSeparateEvenOddLinesPreference = normalizedSeparateEvenOddLines;
        }
    }

    private void applyConnectionLinesSelection(boolean selected) {
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        showConnectionLinesCheckbox.setSelected(selected);
        updatingInputs = wasUpdatingInputs;
    }

    private void applySeparateEvenOddConnectionLinesSelection(boolean selected) {
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        showSeparateEvenOddConnectionLinesCheckbox.setSelected(selected);
        updatingInputs = wasUpdatingInputs;
    }

    private void updateOverlayOptionsEnablement(boolean overlayEnabled, boolean connectionLinesEnabled) {
        showConnectionLinesCheckbox.setEnabled(overlayEnabled);
        showSeparateEvenOddConnectionLinesCheckbox.setEnabled(overlayEnabled && connectionLinesEnabled);
    }

    private void notifyOverlaySettingsChanged() {
        boolean overlayEnabled = showHouseNumberLayerCheckbox.isSelected();
        boolean connectionLinesEnabled = overlayEnabled && showConnectionLinesCheckbox.isSelected();
        boolean separateEvenOddLinesEnabled = connectionLinesEnabled
                && showSeparateEvenOddConnectionLinesCheckbox.isSelected();
        streetModeController.updateOverlaySettings(overlayEnabled, connectionLinesEnabled, separateEvenOddLinesEnabled);
    }

    private void notifyHouseNumberOverviewChanged() {
        streetModeController.setHouseNumberOverviewEnabled(showHouseNumberOverviewCheckbox.isSelected());
    }

    private void notifyStreetHouseNumberCountsChanged() {
        streetModeController.setStreetHouseNumberCountsEnabled(showStreetHouseNumberCountsCheckbox.isSelected());
    }

    private void updateHouseNumberOverviewCheckboxFromController(boolean enabled) {
        if (showHouseNumberOverviewCheckbox == null) {
            return;
        }
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        showHouseNumberOverviewCheckbox.setSelected(enabled);
        updatingInputs = wasUpdatingInputs;
        rememberCurrentValues();
    }

    private void updateStreetHouseNumberCountsCheckboxFromController(boolean enabled) {
        if (showStreetHouseNumberCountsCheckbox == null) {
            return;
        }
        boolean wasUpdatingInputs = updatingInputs;
        updatingInputs = true;
        showStreetHouseNumberCountsCheckbox.setSelected(enabled);
        updatingInputs = wasUpdatingInputs;
        rememberCurrentValues();
    }

    private void notifyZoomToSelectedStreetChanged() {
        streetModeController.setZoomToSelectedStreetEnabled(zoomToSelectedStreetCheckbox.isSelected());
    }

    private int normalizeIncrementStep(int step) {
        return dialogController.normalizeIncrementStep(step);
    }

    private StreetModeController.AddressSelection buildCurrentSelection() {
        StreetOption selectedOption = getSelectedStreetOption();
        String baseStreetName = selectedOption != null ? selectedOption.getBaseStreetName() : "";
        String displayStreetName = selectedOption != null ? selectedOption.getDisplayStreetName() : "";
        String clusterId = selectedOption != null ? selectedOption.getClusterId() : "";
        return new StreetModeController.AddressSelection(
                baseStreetName,
                displayStreetName,
                clusterId,
                getSelectedPostcode(),
                getSelectedBuildingType(),
                houseNumberField.getText(),
                houseNumberIncrementStep
        );
    }

    private void populatePostcodeOptions(List<String> postcodes) {
        postcodeCombo.removeAllItems();
        postcodeCombo.addItem("");
        if (postcodes == null) {
            return;
        }
        for (String postcode : postcodes) {
            String normalized = normalize(postcode);
            if (!normalized.isEmpty()) {
                postcodeCombo.addItem(normalized);
            }
        }
    }

    private void setSelectedPostcode(String postcode) {
        String normalized = normalize(postcode);
        if (normalized.isEmpty()) {
            postcodeCombo.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < postcodeCombo.getItemCount(); i++) {
            String item = postcodeCombo.getItemAt(i);
            if (normalized.equals(item)) {
                postcodeCombo.setSelectedIndex(i);
                return;
            }
        }
        // Keep manually entered postcodes even when they are not in the detected list.
        postcodeCombo.setSelectedItem(normalized);
    }

    private String getSelectedPostcode() {
        Object selected = postcodeCombo.isEditable()
                ? postcodeCombo.getEditor().getItem()
                : postcodeCombo.getSelectedItem();
        return selected instanceof String ? normalize((String) selected) : "";
    }

    private StreetOption getSelectedStreetOption() {
        String selectedDisplayStreet = getSelectedStreet();
        return findStreetOptionByDisplayName(selectedDisplayStreet);
    }

    private StreetOption findStreetOptionByDisplayName(String displayStreetName) {
        String normalizedDisplayStreet = normalize(displayStreetName);
        if (normalizedDisplayStreet.isEmpty()) {
            return null;
        }
        for (StreetOption option : currentStreetOptions) {
            if (option != null && normalizedDisplayStreet.equals(option.getDisplayStreetName())) {
                return option;
            }
        }
        return null;
    }

    private StreetOption findFirstStreetOptionByBaseName(String baseStreetName) {
        String normalizedBaseStreet = normalize(baseStreetName);
        if (normalizedBaseStreet.isEmpty()) {
            return null;
        }
        for (StreetOption option : currentStreetOptions) {
            if (option != null && normalizedBaseStreet.equals(option.getBaseStreetName())) {
                return option;
            }
        }
        return null;
    }

    static boolean isDataSetChanged(DataSet rememberedDataSet, DataSet activeDataSet) {
        return rememberedDataSet != activeDataSet;
    }

    private boolean isDataSetChanged(DataSet activeDataSet) {
        return isDataSetChanged(rememberedDataSet, activeDataSet);
    }

    private void resetRememberedValuesForDataSetChange() {
        rememberedStreet = null;
        rememberedPostcode = null;
        rememberedBuildingType = null;
        rememberedHouseNumber = INITIAL_HOUSE_NUMBER;
        rememberedIncrementStep = 1;
        rememberedHouseNumberLayerEnabled = true;
        rememberedConnectionLinesEnabled = true;
        rememberedConnectionLinesPreference = true;
        rememberedSeparateEvenOddLinesEnabled = true;
        rememberedSeparateEvenOddLinesPreference = true;
        rememberedHouseNumberOverviewEnabled = false;
        rememberedStreetHouseNumberCountsEnabled = false;
        rememberedZoomToSelectedStreetEnabled = true;
        rememberedSplitMakeRectangular = false;
        lastSelectedStreet = null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStatic(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonEmpty(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        if (!normalizedPrimary.isEmpty()) {
            return normalizedPrimary;
        }
        return normalize(fallback);
    }

    private void navigateStreetByOffset(int offset) {
        StreetOption selectedStreetOption = getSelectedStreetOption();
        if (!canNavigateStreet(offset)) {
            return;
        }
        if (selectedStreetOption == null) {
            if (offset > 0 && streetCombo != null && streetCombo.getItemCount() > 0) {
                // With empty initial selection, Next should start at the first street.
                streetSelectionChangedByNavigation = true;
                streetCombo.setSelectedIndex(0);
            }
            return;
        }

        StreetOption nextStreetOption = resolveNextStreetByNavigationOrder(selectedStreetOption, offset);
        if (nextStreetOption != null) {
            streetSelectionChangedByNavigation = true;
            setStreetSelection(nextStreetOption.getDisplayStreetName());
            return;
        }

        int nextIndex = streetCombo.getSelectedIndex() + offset;
        streetSelectionChangedByNavigation = true;
        streetCombo.setSelectedIndex(nextIndex);
    }

    private boolean consumeStreetSelectionChangedByNavigation() {
        boolean changedByNavigation = streetSelectionChangedByNavigation;
        streetSelectionChangedByNavigation = false;
        return changedByNavigation;
    }

    private boolean canNavigateStreet(int offset) {
        if (streetCombo == null || streetCombo.getItemCount() == 0 || offset == 0) {
            return false;
        }

        StreetOption selectedStreetOption = getSelectedStreetOption();
        StreetOption nextByOrder = resolveNextStreetByNavigationOrder(selectedStreetOption, offset);
        if (nextByOrder != null) {
            return true;
        }

        int nextIndex = streetCombo.getSelectedIndex() + offset;
        return nextIndex >= 0 && nextIndex < streetCombo.getItemCount();
    }

    private StreetOption resolveNextStreetByNavigationOrder(StreetOption selectedStreet, int offset) {
        if (selectedStreet == null || offset == 0) {
            return null;
        }

        List<StreetOption> orderedStreets = streetModeController.getStreetNavigationOrder();
        if (orderedStreets == null || orderedStreets.isEmpty()) {
            return null;
        }

        int currentIndex = orderedStreets.indexOf(selectedStreet);
        if (currentIndex < 0) {
            return null;
        }

        int nextIndex = currentIndex + offset;
        if (nextIndex < 0 || nextIndex >= orderedStreets.size()) {
            return null;
        }

        return orderedStreets.get(nextIndex);
    }

    private void updateStreetNavigationButtonState() {
        if (previousStreetButton == null || nextStreetButton == null) {
            return;
        }
        previousStreetButton.setEnabled(canNavigateStreet(-1));
        nextStreetButton.setEnabled(canNavigateStreet(1));
    }

    private boolean hasStreetSelectionChanged(String previousStreet, String selectedStreet) {
        String previous = normalize(previousStreet);
        String selected = normalize(selectedStreet);
        return !selected.isEmpty() && !selected.equals(previous);
    }

    private boolean handleGlobalStreetNavigationKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }
        if (!dialog.isVisible() || !streetModeController.isActive()) {
            return false;
        }

        if (event.getKeyCode() == KeyEvent.VK_ESCAPE && event.getID() == KeyEvent.KEY_PRESSED) {
            if (!isDialogFocused()) {
                return false;
            }
            rememberLastDialogInputFocus();
            streetModeController.deactivate();
            // Keep the dialog visible, but return keyboard focus to JOSM so standard tools/shortcuts work immediately.
            focusMapViewAfterPause();
            event.consume();
            return true;
        }
        return false;
    }

    private void focusMapViewAfterPause() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.requestFocusInWindow();
            return;
        }
        Frame mainFrame = MainApplication.getMainFrame();
        if (mainFrame != null) {
            mainFrame.requestFocus();
        }
    }

    private void rememberLastDialogInputFocus() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!isComponentInsideDialog(focusOwner) || !isDialogInputComponent(focusOwner)) {
            return;
        }
        lastFocusedDialogInput = focusOwner;
    }

    private void restoreLastDialogInputFocus() {
        Component target = lastFocusedDialogInput;
        if (target == null) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (isComponentInsideDialog(target) && target.isShowing() && target.isFocusable()) {
                target.requestFocusInWindow();
            }
        });
    }

    private boolean isDialogInputComponent(Component component) {
        if (component == null) {
            return false;
        }
        return component instanceof JTextComponent
                || component == houseNumberField
                || component == rowHousePartsField
                || component == postcodeCombo
                || component == streetCombo
                || component == buildingTypeCombo
                || component == postcodeCombo.getEditor().getEditorComponent()
                || component == streetCombo.getEditor().getEditorComponent()
                || component == buildingTypeCombo.getEditor().getEditorComponent();
    }

    private boolean isComponentInsideDialog(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (current == dialog) {
                return true;
            }
        }
        return false;
    }

    private boolean isDialogFocused() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        for (Component component = focusOwner; component != null; component = component.getParent()) {
            if (component == dialog) {
                return true;
            }
        }
        return false;
    }

    private void registerStreetNavigationDispatcher() {
        if (streetNavigationDispatcherRegistered) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(streetNavigationKeyDispatcher);
        streetNavigationDispatcherRegistered = true;
    }

    private void unregisterStreetNavigationDispatcher() {
        if (!streetNavigationDispatcherRegistered) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(streetNavigationKeyDispatcher);
        streetNavigationDispatcherRegistered = false;
    }
}
