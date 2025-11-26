package com.example.xmleditorapp;

import com.example.xmleditorapp.ui.NodeEditDialog;
import com.example.xmleditorapp.xml.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javafx.scene.layout.GridPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar.ButtonData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class XmlEditorController implements NodeEditDialog.EditDialogListener {
    private final int view_mode_defined_for_testing = 1;
    public TreeView<XmlNodeWrapper> xmlTreeView;
    public Menu recentFilesMenu;
    @FXML private TabPane tabPane;
    // We keep xmlSourceArea for underlying editing/data storage, but for viewing
    // the source, we will use the WebView when the Source tab is active.
    @FXML private TextArea xmlSourceArea;
    @FXML private WebView svgWebView;

    private Document xmlDocument;
    private File currentFile; // Track the currently loaded file
    private RecentFilesManager recentFilesManager; // New manager instance
     // NEW: Instance variable to hold the dynamically generated container types
    private Set<String> containerNodeTypes;
    // NEW: Temporary storage for the currently open dialog's stage/window
    private javafx.stage.Stage currentDialogStage;

    @FXML
    public void initialize() {
        XSLTFileManager.getInstance();

        // NEW: Initialize Recent Files Manager
        recentFilesManager = new RecentFilesManager();
        updateRecentFilesMenu();

        // NEW: Initialize Schema Reader (Assuming XSD is available at /ui_schema.xsd)
        try {
            // Get the Singleton instance
            XmlSchemaReader schemaReader = XmlSchemaReader.getInstance();

            // Access initialized data directly from the Singleton
            this.containerNodeTypes = schemaReader.getContainerNodeTypes();
            System.out.println("XSD Schema loaded successfully.");
        } catch (Exception e) {
            System.err.println("FATAL: Failed to load or parse XSD schema.");
            e.printStackTrace();
        }

        // 1. Establish JavaScript to Java Bridge
        // --- FIX: ESTABLISH JAVASCRIPT TO JAVA BRIDGE ---
        svgWebView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == javafx.concurrent.Worker.State.SUCCEEDED) {
                // Get the root JavaScript object (window)
                netscape.javascript.JSObject window = (netscape.javascript.JSObject) svgWebView.getEngine().executeScript("window");

                // Inject the Java object (this controller instance) directly into the window scope
                // The JavaScript code will refer to this as 'xmlEditorBridge'
                window.setMember("xmlEditorBridge", this);

                System.out.println("JavaFX Bridge is ready: window.xmlEditorBridge exposed.");
            }
        });

        // 2. Set up the listener for node selection in the TreeView
        xmlTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                XmlNodeWrapper wrapper = newValue.getValue();
                if (wrapper != null) {
                    org.w3c.dom.Node selectedNode = wrapper.getXmlNode();
                    displayNodeInfo(selectedNode);
                }
            }
        });

        // 💡 NEW: Double-Click Listener for TreeView editing
        xmlTreeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {

                // The TreeItem holds the XmlNodeWrapper object
                TreeItem<XmlNodeWrapper> selectedItem = xmlTreeView.getSelectionModel().getSelectedItem();

                if (selectedItem != null) {
                    // Get the wrapped object
                    XmlNodeWrapper wrapper = selectedItem.getValue();
                    org.w3c.dom.Node node = wrapper.getXmlNode();

                    // Ensure it's an Element (the nodes you can edit)
                    if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        org.w3c.dom.Element element = (org.w3c.dom.Element) node;

                        String nodeType = element.getNodeName();
                        // Crucial: Get the unique identifier ('name' attribute) directly
                        String nodeName = element.getAttribute("name");

                        if (!nodeName.isEmpty()) {
                            // Call the existing method that opens the native JavaFX edit dialog
                            openEditDialogForNode(nodeName, nodeType);
                        } else {
                            // Handle case where node is a container without a unique 'name'
                            // (e.g., if you click the root <Window>)
                            System.err.println("Cannot edit node without a 'name' attribute: " + nodeType);
                        }
                    }
                }
            }
        });

        // 3. Listener to swap WebView content based on Tab selection (if needed, but not strictly required here)
        tabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            TreeItem<XmlNodeWrapper> selectedItem = xmlTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                displayNodeInfo(selectedItem.getValue().getXmlNode());
            }
        });
    }

    // We may need a getter if the dialog class needs to access it directly:
    public Set<String> getContainerNodeTypes() {
        return containerNodeTypes;
    }


    /**
     * Populates the Recent Files menu dynamically.
     */
    private void updateRecentFilesMenu() {
        recentFilesMenu.getItems().clear();
        List<String> recentFiles = recentFilesManager.getRecentFiles();

        if (recentFiles.isEmpty()) {
            MenuItem emptyItem = new MenuItem("No Recent Files");
            emptyItem.setDisable(true);
            recentFilesMenu.getItems().add(emptyItem);
            return;
        }

        for (String path : recentFiles) {
            MenuItem item = new MenuItem(path);
            item.setOnAction(e -> Platform.runLater(() -> loadXml(new File(path))));
            recentFilesMenu.getItems().add(item);
        }
    }

    // --- XML Manipulation Methods (Called by JavaScript) ---


    /**
     * Handles all structural and attribute update requests coming from the SVG's JavaScript.
     * This method is exposed to the WebView engine via the 'xmlEditorBridge'.
     * @param jsonData A JSON string containing 'action', 'name', and other data.
     */
    public void handleJsUpdateRequest(String jsonData) {
        if (xmlDocument == null) return;

        try {
            JSONObject json = new JSONObject(jsonData);
            String action = json.getString("action");
            String originalName = json.optString("originalName");
            String nodeName = json.optString("name"); // Used for DELETE and ADD

            switch (action) {
                case "UPDATE":
                    JSONObject attributes = json.getJSONObject("attributes");
                    updateNodeAttributes(originalName, attributes);
                    break;
                case "DELETE":
                    deleteNode(nodeName);
                    break;
                case "ADD":
                    String parentName = json.getString("parentName");
                    addNewNode(parentName, json.getString("newNodeType"));
                    break;
                default:
                    System.err.println("Unknown action: " + action);
            }
            refreshUi(); // Refresh the DOM, TreeView, and SVG after any change

        } catch (Exception e) {
            System.err.println("Error processing JS update request: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void updateNodeAttributes(String nodeName, JSONObject attributes) {
        Node targetNode = findNodeByName(xmlDocument.getDocumentElement(), nodeName);
        if (targetNode instanceof Element) {
            Element element = (Element) targetNode;

            Iterator<String> keys = attributes.keys();
            while (keys.hasNext()) {
                String key = keys.next();

                // FIX: Use optString() to safely convert Integer/Double to String for the XML attribute.
                // This ensures attributes like "x", "y", "width", etc., are set as strings.
                String value = attributes.optString(key);

                if (key.equalsIgnoreCase("name")) {
                    element.setAttribute(key, value);
                } else if (key.equalsIgnoreCase("value")) {
                    // Special handling for the 'value' attribute, assuming it maps to text content or a dedicated attribute
                    // For simplicity, we update the first Text node child if it exists
                    NodeList children = element.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        if (children.item(i).getNodeType() == Node.TEXT_NODE) {
                            children.item(i).setNodeValue(value);
                            break;
                        }
                    }
                    // If no text node, sometimes it's an attribute
                    if (element.hasAttribute("value")) {
                        element.setAttribute("value", value);
                    }
                } else {
                    // Update any other attribute (x, y, width, height, etc.)
                    element.setAttribute(key, value);
                }
            }
        }
    }


    private void deleteNode(String nodeName) {
        Node targetNode = findNodeByName(xmlDocument.getDocumentElement(), nodeName);
        if (targetNode != null && targetNode.getParentNode() != null) {
            targetNode.getParentNode().removeChild(targetNode);
        }
    }

    private void addNewNode(String parentName, String newNodeType) {
        Node parentNode = findNodeByName(xmlDocument.getDocumentElement(), parentName);
        if (parentNode instanceof Element) {
            Element newElement = xmlDocument.createElement(newNodeType);
            newElement.setAttribute("name", "NewNode-" + System.currentTimeMillis() % 1000); // Simple unique name
            newElement.setAttribute("x", "50");
            newElement.setAttribute("y", "50");

            // Add initial structure if necessary (e.g., text content for Items)
            if (newNodeType.equals("Item")) {
                newElement.appendChild(xmlDocument.createTextNode("New Item Value"));
            }

            parentNode.appendChild(newElement);
        }
    }

    // --- Utility: Find Node ---

    private Node findNodeByName(Node node, String name) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            if (element.hasAttribute("name") && element.getAttribute("name").equals(name)) {
                return element;
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node result = findNodeByName(children.item(i), name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }


    // --- UI Refresh and Display Logic ---

    /**
     * Converts the in-memory DOM Document back to a String.
     */
    private String convertDocumentToString() throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(xmlDocument), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    /**
     * Re-parses the XML and refreshes the entire UI (TreeView and SVG).
     */
    private void refreshUi() {
        try {
            // 1. Update the XML Source Area text
            xmlSourceArea.setText(convertDocumentToString());

            // 2. Re-parse the DOM from the updated text (safer than directly modifying the old DOM object)
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            xmlDocument = builder.parse(new java.io.ByteArrayInputStream(xmlSourceArea.getText().getBytes()));
            xmlDocument.normalize();

            // 3. Rebuild the TreeView
            Element rootElement = xmlDocument.getDocumentElement();
            XmlNodeWrapper rootWrapper = new XmlNodeWrapper(rootElement.getNodeName(), rootElement);
            TreeItem<XmlNodeWrapper> rootItem = new TreeItem<>(rootWrapper);
            rootItem.setExpanded(true);
            xmlTreeView.setRoot(rootItem);
            buildTree(rootElement, rootItem);

            // 4. Force SVG/Node Info update on the currently selected node
            TreeItem<XmlNodeWrapper> selectedItem = xmlTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                displayNodeInfo(selectedItem.getValue().getXmlNode());
            }

        } catch (Exception e) {
            showAlert("UI Refresh Error", "Failed to update UI after XML modification.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    // --- File Menu Handlers ---

    @FXML
    private void handleOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            loadXml(file);
        }
    }

    @FXML
    private void handleClose() {
        xmlTreeView.setRoot(null);
        xmlSourceArea.setText("");
        svgWebView.getEngine().loadContent("<h1>No XML loaded.</h1>");
        xmlDocument = null;
    }

    /**
     * Handles the menu action to select and load a custom XSLT file.
     * The loaded file is immediately set as the active style in the Singleton manager.
     */
    @FXML
    private void handleConfigureXslt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Custom XSLT Stylesheet");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"));

        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            String filePath = file.getAbsolutePath();

            // Get the Singleton instance and attempt to load the file
            boolean success = XSLTFileManager.getInstance().loadFromFile(filePath);

            if (success) {
                showAlert("Success", "Custom XSLT stylesheet loaded successfully from:\n" + filePath, Alert.AlertType.INFORMATION);
            } else {
                showAlert("Configuration Error", "Failed to load XSLT file. Reverting to default internal stylesheet.", Alert.AlertType.WARNING);
            }
        } else {
            // User cancelled the dialog
            showAlert("Configuration Cancelled", "XSLT file selection was cancelled.", Alert.AlertType.INFORMATION);
        }

        // After loading, trigger an update if an XML document is already loaded and a node is selected
        TreeItem<XmlNodeWrapper> selectedItem = xmlTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            generateAndDisplaySvg(selectedItem.getValue().getXmlNode());
        }
    }


    // --- XML Loading and TreeView Population ---
// Inside XmlEditorController.java

    private void loadXml(File file) {
        try {
            // 1. Load the XML Document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            xmlDocument = builder.parse(file);
            xmlDocument.normalize();

            if (xmlDocument != null) {
                // Normalize the document to ensure every element has a 'name' attribute
                normalizeXmlNames(xmlDocument.getDocumentElement());
            }

            // 2. Display Raw XML Text
            String xmlContent = java.nio.file.Files.readString(file.toPath());
            xmlSourceArea.setText(xmlContent); // Assuming xmlSourceArea is a JFX control

            // 3. Populate TreeView
            Element rootElement = xmlDocument.getDocumentElement();
            String rootName = rootElement.getAttribute("name");
            String displayName = rootElement.getNodeName() + (rootName.isEmpty() ? "" : " [name=\"" + rootName + "\"]");

            XmlNodeWrapper rootWrapper = new XmlNodeWrapper(displayName, rootElement);
            TreeItem<XmlNodeWrapper> rootItem = new TreeItem<>(rootWrapper);

            rootItem.setExpanded(true);
            xmlTreeView.setRoot(rootItem); // Assuming xmlTreeView is of type TreeView<XmlNodeWrapper>

            buildTree(rootElement, rootItem); // Your existing recursive tree builder

            // 4. Update tracking variables and manager
            this.currentFile = file;
            recentFilesManager.addFile(file.getAbsolutePath());
            updateRecentFilesMenu();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error loading XML", e.getMessage(), Alert.AlertType.ERROR);
        }
    }


    /**
     * Recursively assigns a unique 'name' attribute to any element missing one.
     */
    private void normalizeXmlNames(org.w3c.dom.Node currentNode) {
        if (currentNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) currentNode;

            if (!element.hasAttribute("name") || element.getAttribute("name").trim().isEmpty()) {
                String syntheticName = "synth_" + element.getNodeName() + "_" + UUID.randomUUID().toString().substring(0, 5);
                element.setAttribute("name", syntheticName);
                System.out.println("Assigned synthetic name '" + syntheticName + "' to node: " + element.getNodeName());
            }
        }

        org.w3c.dom.NodeList children = currentNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            normalizeXmlNames(children.item(i));
        }
    }

    private void buildTree(Node node, TreeItem<XmlNodeWrapper> parentItem) {
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            org.w3c.dom.Node childNode = nodeList.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) childNode;
                String displayName = childElement.getNodeName();
                if (childElement.hasAttribute("name")) { // Add common attributes to display
                    displayName += " (" + childElement.getAttribute("name") + ")";
                }

                // 1. Create the wrapper object which holds the display name and the XML node.
                XmlNodeWrapper childWrapper = new XmlNodeWrapper(displayName, childNode);

                // 2. Pass the wrapper object to the TreeItem constructor.
                TreeItem<XmlNodeWrapper> newItem = new TreeItem<>(childWrapper);

                parentItem.getChildren().add(newItem);
                buildTree(childNode, newItem);
            }
        }
    }

    // --- Node Information and SVG Generation ---

    private void displayNodeInfo(Node selectedItem) {
        // Ensures the XML source tab is visible. Now we update the WebView via the Tab Listener.
        if (xmlSourceArea.getText().isEmpty()) {
            tabPane.getSelectionModel().select(0);
            svgWebView.getEngine().loadContent("<html><body><h1>Load an XML file first.</h1></body></html>");
            return;
        }

        if (view_mode_defined_for_testing == 0) { // XML Source Tab (Index 0)
            //==========displaying as highlited text
            // Display the full, highlighted source in the WebView
            String highlightedHtml = XmlHighlighter.highlight(xmlSourceArea.getText());
            svgWebView.getEngine().loadContent(highlightedHtml);
        } else if (view_mode_defined_for_testing == 1) { // SVG Visualization Tab (Index 1)
            //====== Displaying as graphic svg file format
            // Ensure SVG is displayed if a node is selected
            if (selectedItem != null) {
                generateAndDisplaySvg(selectedItem);
            } else {
                svgWebView.getEngine().loadContent("<html><body><h1>Select a node to see the visualization.</h1></body></html>");
            }
        }
    }

    private void generateAndDisplaySvg(Node selectedNode) {
        try {
            // 1. Prepare XSLT Transformer
            TransformerFactory factory = TransformerFactory.newInstance();

            // Get the active source directly from the Singleton manager
            StreamSource xslSource = XSLTFileManager.getInstance().getActiveStyle();

            Transformer transformer = factory.newTransformer(xslSource);

            // 2. Define the source as the selected XML node
            DOMSource source = new DOMSource(selectedNode);

            // 3. Perform Transformation and capture output
            StringWriter writer = new StringWriter();
            transformer.transform(source, new StreamResult(writer));
            String svgContent = writer.toString();

            // 4. Display SVG in WebView
            svgWebView.getEngine().loadContent(svgContent, "image/svg+xml");

        } catch (Exception e) {
            e.printStackTrace();
            svgWebView.getEngine().loadContent("<h1>Error generating SVG.</h1><p>" + e.getMessage() + "</p>");
        }
    }

    // --- Utility ---
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void handleExit(ActionEvent actionEvent) {
    }



    @FXML
    private void handleSave() {
        if (xmlDocument == null) return;

        if (currentFile != null) {
            saveXmlToFile(currentFile);
        } else {
            // If currentFile is null (new/unsaved document), prompt Save As
            handleSaveAs();
        }
    }

    @FXML
    private void handleSaveAs() {
        if (xmlDocument == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));

        // Suggest a default filename
        if (currentFile != null) {
            fileChooser.setInitialFileName(currentFile.getName());
        } else {
            fileChooser.setInitialFileName("new_document.xml");
        }

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            saveXmlToFile(file);
        }
    }

    /**
     * Saves the current in-memory DOM Document to the specified file.
     */
    private void saveXmlToFile(File file) {
        try {
            // Use the Transformer to write the DOM to the file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(xmlDocument);
            StreamResult result = new StreamResult(file);

            // Optional: Add pretty printing settings
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            transformer.transform(source, result);

            // Update controller state
            this.currentFile = file;
            recentFilesManager.addFile(file.getAbsolutePath());
            updateRecentFilesMenu();

            showAlert("Save Successful", "XML saved to:\n" + file.getAbsolutePath(), Alert.AlertType.INFORMATION);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Save Error", "Failed to save XML: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }


    /**
     * Called by JavaScript to open a native JavaFX dialog for editing a node.
     * This method is the entry point for dialog creation.
     */
    // Inside XmlEditorController.java

    public void openEditDialogForNode(String nodeName, String nodeType) {
        Platform.runLater(() -> {
            Node targetNode = findNodeByName(xmlDocument.getDocumentElement(), nodeName);
            if (!(targetNode instanceof Element)) {
                showAlert("Error", "Node not found or invalid type: " + nodeName, Alert.AlertType.ERROR);
                return;
            }

            Element targetElement = (Element) targetNode;

            NodeEditDialog dialog = new NodeEditDialog(targetElement, this, containerNodeTypes);

            // 💡 FIX: Set a listener to capture the stage reference only AFTER it is shown.
            // The dialog stage is initialized just before showing.
            dialog.showingProperty().addListener((observable, wasShowing, isShowing) -> {
                if (isShowing) {
                    // Get the stage when the dialog is actually visible
                    currentDialogStage = (javafx.stage.Stage) dialog.getDialogPane().getScene().getWindow();
                } else {
                    // Clear the reference when the dialog is closed
                    currentDialogStage = null;
                }
            });

            dialog.showAndWait();
            // The listener will have captured and cleared the stage reference during this call.
        });
    }


    // --- Interface Methods Implementation ---

    @Override
    public void fireUpdateRequest(Element element, Map<String, String> updatedAttrs) {
        // Convert map to JSON structure expected by handleJsUpdateRequest
        JSONObject attributesJson = new JSONObject(updatedAttrs);

        JSONObject updateRequest = new JSONObject();
        updateRequest.put("action", "UPDATE");
        updateRequest.put("originalName", element.getAttribute("name"));
        updateRequest.put("attributes", attributesJson);

        // Call the core handler that updates the DOM and refreshes the UI
        handleJsUpdateRequest(updateRequest.toString());
    }

    @Override
    public void fireAddNodeRequest(String parentName, String newNodeType) {
        // Close current dialog (will be done in openEditDialogForNode fix)
        // Delegate to existing logic
        addNewNode(parentName, newNodeType);

        // Refresh the parent dialog after DOM update
        openEditDialogForNode(parentName, findNodeByName(xmlDocument.getDocumentElement(), parentName).getNodeName());
    }

    @Override
    public void fireDeleteNodeRequest(String nodeName) {
        // Close current dialog (will be done in openEditDialogForNode fix)
        // Delegate to existing logic
        deleteNode(nodeName);

        // Note: Reopening the parent dialog happens implicitly in the recursive call path
    }

    @Override
    public void fireEditChildDialog(Element childElement) {
        // 1. Hide the current (parent) dialog using the stored reference
        if (currentDialogStage != null) {
            currentDialogStage.hide();
        }

        // 2. Clear the stored reference immediately so the recursive call can store its own stage
        currentDialogStage = null;

        // 3. Open the child dialog (recursive call)
        openEditDialogForNode(childElement.getAttribute("name"), childElement.getNodeName());

        // 4. After the recursive call returns (meaning the child dialog has closed),
        // we need to reopen the parent dialog with fresh data. This is done by
        // relying on the main control flow returning to where it called the parent dialog.
    }

    @Override
    public String fireProcessBinaryFile(byte[] rawBytes, String nodeType) throws IOException {
        // Determine if compression is needed. Assuming yes for large binary data.
        boolean shouldCompress = true; // Apply compression to both PDF and BackgroundImage

        if (shouldCompress) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(bos)) {

                gos.write(rawBytes);
                gos.finish(); // Finish compression

                byte[] zippedData = bos.toByteArray();
                return Base64.getEncoder().encodeToString(zippedData);

            }
        } else {
            // Just Base64 encode the raw data
            return Base64.getEncoder().encodeToString(rawBytes);
        }
    }
}