package com.example.xmleditorapp;

import com.example.xmleditorapp.xml.XmlHighlighter; // New Import
import com.example.xmleditorapp.xml.XmlNodeWrapper;
import com.example.xmleditorapp.xml.XSLTFileManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

public class XmlEditorController {
    private final int view_mode_defined_for_testing = 1;
    public TreeView<XmlNodeWrapper> xmlTreeView;
    @FXML private TabPane tabPane;
    // We keep xmlSourceArea for underlying editing/data storage, but for viewing
    // the source, we will use the WebView when the Source tab is active.
    @FXML private TextArea xmlSourceArea;
    @FXML private WebView svgWebView;

    private Document xmlDocument;

    @FXML
    public void initialize() {
        XSLTFileManager.getInstance();
        // Set up the listener for node selection in the TreeView
        xmlTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                XmlNodeWrapper wrapper = newValue.getValue();
                if (wrapper != null) {
                    org.w3c.dom.Node selectedNode = wrapper.getXmlNode();
                    displayNodeInfo(selectedNode);
                }
            }
        });

        // NEW: Listener to swap WebView content based on Tab selection
        tabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (xmlSourceArea.getText().isEmpty()) {
                svgWebView.getEngine().loadContent("<html><body><h1>Load an XML file first.</h1></body></html>");
                return;
            }

            TreeItem<XmlNodeWrapper> selectedItem = xmlTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                displayNodeInfo(selectedItem.getValue().getXmlNode());
            }
        });

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

    private void loadXml(File file) {
        try {
            // 1. Load the XML Document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            xmlDocument = builder.parse(file);
            xmlDocument.normalize();

            // 2. Display Raw XML Text (for hidden editing/storage)
            String xmlContent = Files.readString(file.toPath());
            xmlSourceArea.setText(xmlContent);

            // 3. Populate TreeView
            Element rootElement = xmlDocument.getDocumentElement();

            // Use the wrapper class for the value
            XmlNodeWrapper rootWrapper = new XmlNodeWrapper(rootElement.getNodeName(), rootElement);
            TreeItem<XmlNodeWrapper> rootItem = new TreeItem<>(rootWrapper);

            rootItem.setExpanded(true);
            xmlTreeView.setRoot(rootItem);

            buildTree(rootElement, rootItem);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error loading XML", e.getMessage(), Alert.AlertType.ERROR);
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
}