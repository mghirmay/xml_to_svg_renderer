package com.example.xmleditorapp.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class XmlSchemaReader {

    // --- Singleton Instance ---
    private static XmlSchemaReader instance;

    // --- XSD Data ---
    // The Schema object for validation (compiled from all XSDs)
    private final Schema compiledSchema;
    // The Document object for XPath queries (loaded from the primary XSD)
    private final Document schemaDocument;
    private final XPath xpath;
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    // --- Private Constructor to enforce Singleton ---
    private XmlSchemaReader() throws Exception {
        // 1. Load the list of XSD files
        List<File> xsdFiles = loadAllXsdFilesFromRessourceDirectory("esign/xsd");

        if (xsdFiles == null || xsdFiles.isEmpty()) {
            throw new IllegalArgumentException("XSD file list cannot be empty.");
        }

        // 1. Convert File objects to JAXP Source objects for compilation
        List<Source> sources = xsdFiles.stream()
                .map(StreamSource::new)
                .collect(Collectors.toList());

        // 2. Combine and Compile the Schemas for validation
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            this.compiledSchema = factory.newSchema(sources.toArray(new Source[0]));
        } catch (SAXException e) {
            throw new SAXException("Failed to compile schemas: " + e.getMessage(), e);
        }

        // 3. For XPath Queries: Load the primary XSD into a DOM Document
        File primaryXsd = xsdFiles.get(0);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true); // Must be true for XPath to work correctly
        this.schemaDocument = dbf.newDocumentBuilder().parse(primaryXsd);
        this.schemaDocument.normalize();

        // 4. Initialize XPath
        this.xpath = XPathFactory.newInstance().newXPath();
        this.xpath.setNamespaceContext(new NamespaceResolver(XSD_NAMESPACE, "xs"));
    }

    // --- Public Access Method (The Singleton Getter) ---

    /**
     * Gets the singleton instance of XmlSchemaReader, initializing it if necessary.
     * This version requires the list of XSD files for the first call.
      * @return The single XmlSchemaReader instance.
     */
    public static XmlSchemaReader getInstance() throws Exception {
        if (instance == null) {
            synchronized (XmlSchemaReader.class) {
                if (instance == null) {
                    instance = new XmlSchemaReader();
                }
            }
        }
        return instance;
    }

    /**
     * Helper to load all XSD files from a given local file system directory.
     * Use this method in your Controller to prepare the list for getInstance().
     * @param directoryPath The path to the directory containing XSD files.
     * @return A list of File objects for all .xsd files found.
     */
    public static List<File> loadAllXsdFilesFromSystemDirectory(String directoryPath) throws FileNotFoundException {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new FileNotFoundException("XSD directory not found: " + directoryPath);
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xsd"));
        if (files == null || files.length == 0) {
            throw new FileNotFoundException("No XSD files found in directory: " + directoryPath);
        }
        return Arrays.asList(files);
    }

    // Inside XmlSchemaReader.java

    /**
     * Helper to load all XSD files from a given directory on the application's CLASSPATH.
     * NOTE: This method relies on the underlying resource URL being file-system-based,
     * which is unreliable when running from a packaged JAR file.
     * * @param resourceDirectory The path to the directory relative to the classpath root (e.g., "/xsd").
     * @return A list of File objects for all .xsd files found.
     * @throws FileNotFoundException if the directory is not found or is empty.
     */
    public static List<File> loadAllXsdFilesFromRessourceDirectory(String resourceDirectory) throws FileNotFoundException {

        // Use the class loader to get the URL for the resource directory
        java.net.URL resourceUrl = XmlSchemaReader.class.getResource(resourceDirectory);

        if (resourceUrl == null) {
            throw new FileNotFoundException("XSD resource directory not found on classpath: " + resourceDirectory);
        }

        // Attempt to convert the URL to a File object
        // WARNING: This conversion breaks when the application is packaged as a JAR.
        File dir;
        try {
            dir = new File(resourceUrl.toURI());
        } catch (java.net.URISyntaxException e) {
            throw new FileNotFoundException("Error converting resource URL to URI: " + e.getMessage());
        }

        if (!dir.exists() || !dir.isDirectory()) {
            // If running from a JAR, the URL scheme might be "jar:file:/..." instead of "file:/..."
            // In this case, `dir` will point to the JAR itself, or the path will be invalid.
            // We throw an exception, as we cannot list contents of JAR directories using standard File APIs.
            throw new FileNotFoundException("Cannot list contents of directory " + resourceDirectory + ". If running from a JAR, you must list XSD file names manually.");
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xsd"));

        if (files == null || files.length == 0) {
            throw new FileNotFoundException("No XSD files found in resource directory: " + resourceDirectory);
        }

        return Arrays.asList(files);
    }
    // --- Core Functionality Methods ---

    /**
     * Finds metadata about the attributes of a given element type in the XSD.
     * @param elementType e.g., "Button"
     * @return Map of attribute name to constraint (e.g., "required" or "optional").
     */
    public Map<String, String> getAttributeConstraints(String elementType) {
        // Implementation remains largely the same, using the stored xpath and schemaDocument
        Map<String, String> constraints = new HashMap<>();
        String expression = String.format("//xs:element[@name='%s']//xs:attribute", elementType);

        try {
            NodeList attributeNodes = (NodeList) xpath.evaluate(expression, schemaDocument, XPathConstants.NODESET);

            for (int i = 0; i < attributeNodes.getLength(); i++) {
                org.w3c.dom.Element attr = (org.w3c.dom.Element) attributeNodes.item(i);
                String name = attr.getAttribute("name");
                String use = attr.getAttribute("use");

                String constraint = use.isEmpty() ? "optional" : use;
                constraints.put(name, constraint);
            }
        } catch (Exception e) {
            System.err.println("Error querying XSD for " + elementType + ": " + e.getMessage());
        }

        if (!constraints.containsKey("name")) constraints.put("name", "required");

        return constraints;
    }

    /**
     * Identifies XML elements that are allowed to contain child elements (containers)
     * by checking for complex types that define a 'sequence' or 'choice'.
     * @return A Set of element tag names (e.g., "ComboBox", "RadioButtonGroup").
     */
    public Set<String> getContainerNodeTypes() {
        Set<String> containerTypes = new HashSet<>();
        try {
            // CORRECTED XPath: Finds xs:element nodes that contain a complexType
            // which, in turn, contains EITHER an xs:sequence OR an xs:choice descendant.
            String expression = "//xs:element[xs:complexType[.//xs:sequence | .//xs:choice]]";

            // OR, if you are certain they are direct children:
            // String expression = "//xs:element[xs:complexType[xs:sequence | xs:choice]]";
            // (The first option is safer as XSDs can wrap sequences/choices.)

            XPathExpression xpathExpr = xpath.compile(expression);
            NodeList elements = (NodeList) xpathExpr.evaluate(schemaDocument, XPathConstants.NODESET);

            // ... (rest of your logic to populate containerTypes remains the same) ...

        } catch (XPathExpressionException e) {
            System.err.println("Error reading container types from XSD: " + e.getMessage());
        }
        return containerTypes;
    }
    // Getter for the Schema object (useful for validation outside this class)
    public Schema getCompiledSchema() {
        return compiledSchema;
    }

    /**
     * Finds the allowed child element tag names for a given parent element type
     * by checking the XSD definition (xs:sequence or xs:choice content).
     * @param parentElementType The tag name of the parent element (e.g., "ComboBox").
     * @return A Set of allowed child tag names (e.g., "Item", "RadioButton").
     */
    public Set<String> getAllowedChildNodeTypes(String parentElementType) {
        Set<String> allowedChildren = new HashSet<>();
        try {
            // XPath to find all xs:element definitions that are descendants
            // of the parent element's complex type definition's content model (sequence/choice)
            String expression = String.format(
                    "//xs:element[@name='%s']//xs:complexType//xs:element",
                    parentElementType
            );

            XPathExpression xpathExpr = xpath.compile(expression);
            NodeList elements = (NodeList) xpathExpr.evaluate(schemaDocument, XPathConstants.NODESET);

            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                String xmlTagName = element.getAttribute("name");

                // Check for ref attribute (common in modular XSDs)
                String refName = element.getAttribute("ref");

                if (!xmlTagName.isEmpty()) {
                    allowedChildren.add(xmlTagName);
                } else if (!refName.isEmpty()) {
                    // If the element uses a ref, add the reference name
                    allowedChildren.add(refName);
                }
            }
        } catch (XPathExpressionException e) {
            System.err.println("Error reading allowed child types for " + parentElementType + ": " + e.getMessage());
        }
        return allowedChildren;
    }
}