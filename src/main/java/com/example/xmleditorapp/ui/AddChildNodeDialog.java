package com.example.xmleditorapp.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.util.Optional;
import java.util.Set;

public class AddChildNodeDialog extends Dialog<String> {

    private final ChoiceBox<String> typeChoiceBox;
    private final TextField typeTextField;

    public AddChildNodeDialog(String parentNodeType, Set<String> allowedTypes, String defaultType) {
        setTitle("Add New Child Node to " + parentNodeType);
        setHeaderText("Select the element tag for the new child node.");

        // --- Layout ---
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // 1. ChoiceBox for allowed types
        Label listLabel = new Label("1. Select an Allowed Type:");
        ObservableList<String> items = FXCollections.observableArrayList(allowedTypes);
        typeChoiceBox = new ChoiceBox<>(items);
        typeChoiceBox.setTooltip(new Tooltip("Select an element type defined in the XSD."));

        // Set default selection
        if (allowedTypes.contains(defaultType)) {
            typeChoiceBox.setValue(defaultType);
        } else if (!allowedTypes.isEmpty()) {
            typeChoiceBox.getSelectionModel().selectFirst();
        } else {
            // If no allowed types, force user to manually enter
            typeChoiceBox.setDisable(true);
            typeChoiceBox.setTooltip(new Tooltip("No allowed children found in XSD. Manual entry required."));
        }

        // 2. TextField for manual entry (or if the list is empty)
        Label manualLabel = new Label("2. OR Manually Enter Type:");
        typeTextField = new TextField();
        typeTextField.setPromptText("Enter tag name (e.g., CustomWidget)");

        // Add a listener to clear the other input when one is typed into
        typeChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                typeTextField.setText("");
            }
        });
        typeTextField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.trim().isEmpty()) {
                typeChoiceBox.getSelectionModel().clearSelection();
            }
        });

        content.getChildren().addAll(listLabel, typeChoiceBox, new Separator(), manualLabel, typeTextField);

        getDialogPane().setContent(content);

        // --- Buttons and Result Converter ---
        ButtonType addButtonType = new ButtonType("Add Node", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Set the result converter to return the selected/typed value
        setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String selected = typeChoiceBox.getSelectionModel().getSelectedItem();
                String typed = typeTextField.getText().trim();

                if (selected != null) return selected;
                if (!typed.isEmpty()) return typed;

                // If neither is selected/typed
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Validation Error");
                errorAlert.setHeaderText(null);
                errorAlert.setContentText("Please select a node type or manually enter one.");
                errorAlert.showAndWait();
                return null;
            }
            return null;
        });

        // Initially disable the Add button until a valid choice is made
        Button addButton = (Button) getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(typeChoiceBox.getSelectionModel().isEmpty() && typeTextField.getText().trim().isEmpty());

        // Enable/disable the button based on input state
        typeChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) ->
                addButton.setDisable(newV == null && typeTextField.getText().trim().isEmpty())
        );
        typeTextField.textProperty().addListener((obs, oldV, newV) ->
                addButton.setDisable(newV.trim().isEmpty() && typeChoiceBox.getSelectionModel().isEmpty())
        );
    }
}