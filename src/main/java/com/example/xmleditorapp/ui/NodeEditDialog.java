package com.example.xmleditorapp.ui;

import com.example.xmleditorapp.xml.XmlSchemaReader;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

// The return type is a Map<String, String> containing all updated attributes
public class NodeEditDialog extends Dialog<Map<String, String>> {

    // --- ENHANCEMENT: Static Counter for Logging ---
    private static int dialogCount = 0;

    private final Element targetElement;
    private final Map<String, TextField> attributeInputs = new HashMap<>();
    private final Set<String> containerNodeTypes;

    // Interface to call the controller's DOM update methods (Add/Delete/Update)
    public interface EditDialogListener {
        void fireUpdateRequest(Element element, Map<String, String> updatedAttrs);
        // Updated interface to handle the new dialogToClose argument
        void fireAddNodeRequest(Dialog<?> dialogToClose, String parentName, String newNodeType);
        void fireDeleteNodeRequest(String nodeName);
        void fireEditChildDialog(Element childElement);
        // NEW: Method to handle file processing
        String fireProcessBinaryFile(byte[] rawBytes, String nodeType) throws java.io.IOException;
    }

    private final EditDialogListener listener;

    /**
     * Constructor to initialize the dialog with the target node and necessary helpers.
     */
    public NodeEditDialog(Element element,  EditDialogListener listener, Set<String> containerNodeTypes) {

        // --- ENHANCEMENT: Logging ---
        dialogCount++;
        String nodeName = element.getAttribute("name").isEmpty() ? element.getNodeName() : element.getAttribute("name");
        System.out.printf("LOG: Opened NodeEditDialog #%d for element: <%s> (name='%s')\n",
                dialogCount, element.getNodeName(), nodeName);

        this.targetElement = element;
        this.listener = listener;
        this.containerNodeTypes = containerNodeTypes; // Store the set

        String nodeType = element.getNodeName();

        // 💡 Modality Change: Set dialog to be non-blocking (non-modal)
        this.initModality(javafx.stage.Modality.NONE);

        setTitle("Edit Node: " + nodeType);
        setHeaderText("Attributes for " + element.getAttribute("name"));

        // 1. Get Schema Constraints
        Map<String, String> constraints = null;
        try {
            constraints = XmlSchemaReader.getInstance().getAttributeConstraints(nodeType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. Build the main layout (Attributes Grid + Child List)
        VBox mainLayout = new VBox(10);
        mainLayout.setPrefWidth(550);

        // A. Attribute Editor Grid
        GridPane attributeGrid = createAttributeGrid(element, nodeType, constraints);

        // B. Child Node List (if applicable)
        VBox childrenContainer = createChildNodeSection(element);

        mainLayout.getChildren().add(attributeGrid);
        if (childrenContainer != null) {
            mainLayout.getChildren().add(childrenContainer);
        }

        getDialogPane().setContent(mainLayout);

        // 3. Configure Buttons
        setupButtonsAndConverter(constraints);
    }

    // --- Core UI Creation Methods ---

    private GridPane createAttributeGrid(Element element, String nodeType, Map<String, String> constraints) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // ... (existing variable declarations and key iteration logic) ...
        // Note: The logic to gather currentAttributes and sortedKeys remains the same.

        Map<String, String> currentAttributes = getCurrentAttributes(element);
        java.util.Set<String> keysToShow = new java.util.HashSet<>(currentAttributes.keySet());
        keysToShow.addAll(constraints.keySet());
        java.util.List<String> sortedKeys = new java.util.ArrayList<>(keysToShow);
        java.util.Collections.sort(sortedKeys);

        int row = 0;
        // Check if the current element requires binary file handling
        boolean isBinaryNode = element.getNodeName().equalsIgnoreCase("pdf") ||
                element.getNodeName().equalsIgnoreCase("BackgroundImage");

        for (String key : sortedKeys) {
            if (key.startsWith("xmlns") || key.equals("id")) continue;

            String constraint = constraints.getOrDefault(key, "optional");
            String value = currentAttributes.getOrDefault(key, "");
            boolean isRequired = constraint.equalsIgnoreCase("required");

            Label label = new Label(key + (isRequired ? " (*)" : "") + ":");
            label.setTextFill(isRequired ? Color.RED : Color.BLACK);

            javafx.scene.Node inputControl;

            // 💡 NEW LOGIC: If it's a binary node and the key is "value" (for content)
            if (isBinaryNode && key.equalsIgnoreCase("value")) {
                inputControl = createBinaryFileChooserControl(element, label);
                // The value field is already handled inside the helper, we just need the reference
                // Use a temporary TextField to hold the Base64 value for the result conversion
                TextField base64Field = new TextField(value.length() > 50 ? value.substring(0, 50) + "..." : value);
                base64Field.setEditable(false); // Display encoded status, but don't allow manual edit
                base64Field.setId("base64_display_" + element.getAttribute("name")); // Unique ID for finding it later
                attributeInputs.put(key, base64Field); // Store the reference to this display field

            } else {
                // Standard attribute field
                TextField textField = new TextField(value);
                attributeInputs.put(key, textField);
                inputControl = textField;
            }

            grid.add(label, 0, row);
            grid.add(inputControl, 1, row);
            row++;
        }
        return grid;
    }

    /**
     * Creates the input control specialized for Base64 file upload.
     */
    private HBox createBinaryFileChooserControl(Element element, Label labelToUpdate) {
        // 1. Display field (hidden input for data, visible field for status)
        TextField displayField = new TextField();
        displayField.setEditable(false);
        displayField.setPromptText(element.getTextContent().length() > 0 ? "Content Loaded (" + element.getTextContent().length() + " chars)" : "No file selected");

        // 2. The Browse Button
        Button browseButton = new Button("Browse...");

        // 3. Set the action for the button
        browseButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select File for " + element.getNodeName());

            // Suggest filters based on node type
            if (element.getNodeName().equalsIgnoreCase("pdf")) {
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            } else if (element.getNodeName().equalsIgnoreCase("BackgroundImage")) {
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
            }

            // Show the dialog
            java.io.File file = fileChooser.showOpenDialog(getDialogPane().getScene().getWindow());

            if (file != null) {
                try {
                    // Read file bytes
                    byte[] rawBytes = java.nio.file.Files.readAllBytes(file.toPath());

                    // Call the controller's logic to handle compression and encoding
                    String base64EncodedString = listener.fireProcessBinaryFile(rawBytes, element.getNodeName());

                    // Update the hidden text field that will be read by the result converter
                    TextField base64TextField = (TextField) attributeInputs.get("value");
                    base64TextField.setText(base64EncodedString);

                    // Update the visible status
                    displayField.setText(file.getName() + " | Encoded (" + base64EncodedString.length() + " chars)");
                    labelToUpdate.setStyle("-fx-font-weight: bold;"); // Visual feedback

                } catch (Exception e) {
                    showAlert("File Error", "Failed to load and encode file: " + e.getMessage(), Alert.AlertType.ERROR);
                    e.printStackTrace();
                }
            }
        });

        return new HBox(5, displayField, browseButton);
    }

    // ... (You will need to implement a simple showAlert helper method in NodeEditDialog) ...
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    //* The section is shown if the parent node already has children OR if its type
    //* is identified as a container by the XSD.
    private VBox createChildNodeSection(Element parentElement) {
        java.util.List<Element> childElements = getChildElements(parentElement);
        String parentNodeType = parentElement.getNodeName();

        // 1. Determine if the section should be displayed
        // Show the section if it has children OR if it's a known container type from the XSD
        if (childElements.isEmpty() && !containerNodeTypes.contains(parentNodeType)) {
            return null;
        }

        // 2. Determine Expected Child Type for display and Add prompt
        String expectedChildType;
        if (parentNodeType.equalsIgnoreCase("ComboBox")) {
            expectedChildType = "Item";
        } else if (parentNodeType.equalsIgnoreCase("RadioButtonGroup")) {
            expectedChildType = "RadioButton";
        } else {
            expectedChildType = "ChildNode";
        }

        // 3. Create the ListView and populate it
        ListView<String> childListView = new ListView<>();
        childListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        childListView.setPrefHeight(120);

        Map<String, Element> childMap = new HashMap<>(); // To link display name back to Element
        childElements.forEach(child -> {
            // Use the assigned unique 'name' attribute for linking, and a clean display name for the user
            String uniqueName = child.getAttribute("name");
            String displayName = child.getNodeName();

            // Enhance display name with human-readable content/label
//            if (child.hasAttribute("value")) {
//                displayName += " (Val: " + child.getAttribute("value") + ")";
//            } else if (child.getTextContent() != null && !child.getTextContent().trim().isEmpty()) {
//                // For nodes like <Item>Maenlich</Item>
//                displayName += " (" + child.getTextContent().trim() + ")";
//            }

            // Use the unique name as the key in the map
            childListView.getItems().add(displayName);
            childMap.put(displayName, child);
        });

        // Placeholder message if empty
        if (childElements.isEmpty()) {
            childListView.setPlaceholder(new Label("No children found. Click 'Add Child' to create a new " + expectedChildType + "."));
        }

        // 4. Double-Click Listener for Editing Child Nodes (Recursive)
        childListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedDisplayName = childListView.getSelectionModel().getSelectedItem();
                if (selectedDisplayName != null) {
                    Element selectedChild = childMap.get(selectedDisplayName);
                    if (selectedChild != null) {
                        listener.fireEditChildDialog(selectedChild);
                    }
                }
            }
        });

        // 5. Buttons for Add/Remove Management

        // --- ADD Button ---
        Button addButton = new Button("Add Child");
        addButton.setOnAction(e -> {
            try {
                // 1. Get the list of allowed children from the schema reader
                Set<String> allowedTypes = XmlSchemaReader.getInstance().getAllowedChildNodeTypes(parentNodeType);

                // 2. Instantiate the new custom dialog
                AddChildNodeDialog customTypeDialog = new AddChildNodeDialog(
                        parentNodeType,
                        allowedTypes,
                        expectedChildType // Use your calculated default type
                );

                // 3. Show and handle the result
                Optional<String> result = customTypeDialog.showAndWait();

                // Pass the new type and the current dialog instance to the controller to handle creation and refresh
                result.ifPresent(type -> listener.fireAddNodeRequest(this, parentElement.getAttribute("name"), type));

            } catch (Exception ex) {
                showAlert("Schema Error", "Failed to retrieve allowed child types: " + ex.getMessage(), Alert.AlertType.ERROR);
                ex.printStackTrace();
            }
        });

        // --- REMOVE Button ---
        Button removeButton = new Button("Remove Selected");
        removeButton.setDisable(true);
        removeButton.setOnAction(e -> {
            String selectedDisplayName = childListView.getSelectionModel().getSelectedItem();
            if (selectedDisplayName != null) {
                Element selectedChild = childMap.get(selectedDisplayName);
                String nodeName = selectedChild.getAttribute("name"); // Use the unique name
                listener.fireDeleteNodeRequest(nodeName);

                // Since this is non-modal, you might want to force a refresh on the parent dialog here
                // if the controller doesn't handle the update/reopen automatically.
            }
        });

        // Disable Remove button if nothing is selected
        childListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            removeButton.setDisable(newV == null);
        });

        // 6. Assemble the Pane
        HBox buttonBox = new HBox(5, addButton, removeButton);
        VBox content = new VBox(5, childListView, buttonBox);

        TitledPane titledPane = new TitledPane(parentNodeType + " Children (" + expectedChildType + " List)", content);
        titledPane.setCollapsible(false);

        return new VBox(titledPane);
    }
    // --- Utility Methods ---

    // Inside NodeEditDialog.java

    /**
     * Helper to retrieve all attributes AND the node's text content (under the key "value").
     * This function is critical for supporting both attributes and CDATA content.
     */
    private Map<String, String> getCurrentAttributes(Element element) {
        Map<String, String> attributes = new HashMap<>();

        // 1. Get all formal attributes
        NamedNodeMap attributeMap = element.getAttributes();
        for (int i = 0; i < attributeMap.getLength(); i++) {
            Node attr = attributeMap.item(i);
            attributes.put(attr.getNodeName(), attr.getNodeValue());
        }

        // 2. Get the Element's Text Content
        // This reliably retrieves text content, even if it's CDATA or surrounded by whitespace.
        String textContent = element.getTextContent();

        // Check if the node is a binary container type
        boolean isBinaryNode = element.getNodeName().equalsIgnoreCase("pdf") ||
                element.getNodeName().equalsIgnoreCase("BackGroundImage");

        if (isBinaryNode) {
            // For binary nodes, even if empty, we MUST ensure the "value" key is present
            // so that createAttributeGrid can detect it and display the file chooser control.
            attributes.put("value", textContent != null ? textContent.trim() : "");
        } else if (textContent != null && !textContent.trim().isEmpty()) {
            // For regular text-holding nodes (like <Item>Maenlich</Item>)
            attributes.put("value", textContent.trim());
        }

        // 💡 The key change is ensuring the "value" key is present for binary nodes.

        return attributes;
    }

    private java.util.List<Element> getChildElements(Element parentElement) {
        NodeList children = parentElement.getChildNodes();
        java.util.List<Element> childElements = new java.util.ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) children.item(i));
            }
        }
        return childElements;
    }

    // --- Button Setup ---
// Inside NodeEditDialog.java

    private void setupButtonsAndConverter(Map<String, String> constraints) {
        ButtonType saveButtonType = new ButtonType("Save", ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Result Converter: Collects all attribute text field values
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Map<String, String> updatedAttrs = new HashMap<>();
                for (Map.Entry<String, TextField> entry : attributeInputs.entrySet()) {
                    // Only include non-empty fields in the update request
                    if (!entry.getValue().getText().trim().isEmpty()) {
                        updatedAttrs.put(entry.getKey(), entry.getValue().getText().trim());
                    }
                }
                return updatedAttrs;
            }
            return null;
        });

        // CRITICAL: Since we are using show() (non-blocking), we must manually
        // fire the update request when the Save button is clicked and the dialog closes.
        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);

        // The previous check was incorrect. We attach the filter directly to the saveButton,
        // so we can safely assume the action comes from it.
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            // Only run the update logic if the action is the click event
            if (event.getEventType() == ActionEvent.ACTION) {
                // Manually call the result converter to gather the attribute changes
                Map<String, String> updatedAttrs = getResultConverter().call(saveButtonType);

                if (updatedAttrs != null) {
                    // Fire the update request to the controller
                    listener.fireUpdateRequest(targetElement, updatedAttrs);
                }
            }
        });

        // Final call to show the dialog
        show(); // Non-blocking call
    }
}