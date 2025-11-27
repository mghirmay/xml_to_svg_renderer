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
import java.nio.file.Paths;
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

    // Define a set of XSD types that should be treated as binary content
    private static final Set<String> BINARY_XSD_TYPES = Set.of(
            "xs:base64Binary",
            "base64Binary", // Common XSD binary type
            // Add any custom type names your schema uses for binary data here
            "ImageDataType",
            "PDFDataType"
    );

    // --- Private Constructor to enforce Singleton ---
    private XmlSchemaReader() throws Exception {
        List<File> xsdFiles = loadAllXsdFilesFromRessourceDirectory("esign/xsd");
        if (xsdFiles.isEmpty()) {
            throw new IllegalArgumentException("XSD file list cannot be empty.");
        }

        System.out.println("--- XSD Schema Reader Initialization ---");

        // 1. Convert File objects to JAXP Source objects for compilation
        List<Source> sources = xsdFiles.stream()
                .map(file -> {
                    System.out.println("✅ Compiling schema source: " + file.getName()); // LOGGING
                    return new StreamSource(file);
                })
                .collect(Collectors.toList());

        // 2. Combine and Compile the Schemas for validation
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            this.compiledSchema = factory.newSchema(sources.toArray(new Source[0]));
            System.out.println("✅ All " + xsdFiles.size() + " schemas compiled successfully."); // LOGGING
        } catch (SAXException e) {
            System.err.println("❌ Failed to compile schemas!"); // LOGGING
            throw new SAXException("Failed to compile schemas: " + e.getMessage(), e);
        }

        // 3. For XPath Queries: Load the primary XSD into a DOM Document
        File primaryXsd = xsdFiles.get(0);
        System.out.println("ℹ️ Using primary XSD for XPath lookups: " + primaryXsd.getName()); // LOGGING

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        this.schemaDocument = dbf.newDocumentBuilder().parse(primaryXsd);
        this.schemaDocument.normalize();

        // 4. Initialize XPath
        this.xpath = XPathFactory.newInstance().newXPath();
        // Assuming 'xpath' is your javax.xml.xpath.XPath instance
        NamespaceResolver resolver = new NamespaceResolver(XSD_NAMESPACE, "xs");
        xpath.setNamespaceContext(resolver); // <--- This is the crucial line you might be missing

        this.xpath.setNamespaceContext(resolver);
    }

    // --- Public Access Method (The Singleton Getter) ---

    /**
     * Gets the singleton instance of XmlSchemaReader, initializing it if necessary.
     * This version requires the list of XSD files for the first call.
      * @return The single XmlSchemaReader instance.
     */
    public static XmlSchemaReader getInstance()  {
        if (instance == null) {
            synchronized (XmlSchemaReader.class) {
                if (instance == null) {
                    try {
                        instance = new XmlSchemaReader();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
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
            throw new FileNotFoundException("❌ XSD resource directory not found on classpath: " + resourceDirectory);
        }

        File dir;
        try {
            dir = Paths.get(resourceUrl.toURI()).toFile();
            System.out.println("ℹ️ Found resource directory path: " + dir.getAbsolutePath()); // LOGGING
        } catch (Exception e) {
            System.err.println("❌ Error converting resource URL to URI/File: " + e.getMessage()); // LOGGING
            dir = new File(resourceUrl.getPath());
        }

        if (!dir.exists() || !dir.isDirectory()) {
            throw new FileNotFoundException("❌ Resource path found but cannot be treated as a listable directory: " + dir.getAbsolutePath());
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xsd"));

        if (files == null || files.length == 0) {
            throw new FileNotFoundException("❌ No XSD files found in resource directory: " + dir.getAbsolutePath());
        }

        List<File> xsdFiles = Arrays.stream(files)
                .filter(File::isFile)
                .collect(Collectors.toList());

        System.out.println("✅ Found " + xsdFiles.size() + " XSD file(s):"); // LOGGING
        xsdFiles.forEach(file -> System.out.println("  - " + file.getName())); // LOGGING

        return xsdFiles;
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
     * Identifies elements that are true structural containers by checking if their
     * schema definition includes an <xs:sequence>, <xs:choice>, or <xs:all> block
     * that allows other elements inside.
     * * @return A Set of element names (tags) that are containers.
     */
    public Set<String> getContainerNodeTypes() {
        Set<String> containerTypes = new HashSet<>();

        // 1. Define the Robust XPath Expression
        // This expression finds the name of any global element (xs:element) that meets either of two criteria:
        // A. It references a global complex type that defines a sequence/choice/all (i.e., it holds children).
        // B. It defines a local complex type that contains a sequence/choice/all.
        // C. It uses a substitutionGroup, which means it likely acts as a parent type (though less common).

        final String CONTAINER_XPATH =
                "//xs:element[" +
                        // CRITERIA A & B: Find elements that have a type definition allowing child elements
                        "  @type = //xs:complexType[descendant::xs:sequence or descendant::xs:choice or descendant::xs:all]/@name " +
                        "  or " +
                        "  descendant::xs:complexType[descendant::xs:sequence or descendant::xs:choice or descendant::xs:all] " +
                        "]/@name";

        try {
            // 2. Execute the XPath query
            NodeList nodeNames = (NodeList) xpath.compile(CONTAINER_XPATH).evaluate(schemaDocument, XPathConstants.NODESET);

            // 3. Collect the resulting element names
            for (int i = 0; i < nodeNames.getLength(); i++) {
                containerTypes.add(nodeNames.item(i).getNodeValue());
            }

            // Manually add the root element if necessary (e.g., if "ESign" isn't caught by the XPath)
            // String rootName = schemaDocument.getDocumentElement().getNodeName();
            // containerTypes.add(rootName);

        } catch (XPathExpressionException e) {
            System.err.println("Error running XPath for container types: " + e.getMessage());
        }

        System.out.println("LOG: Identified Container Types: " + containerTypes);
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
            String expression;
            String typeName = null;

            // STEP 1: Find the type name for the parent element
            // Find the global element definition (e.g., <xs:element name="Dialogs" type="DialogsType"/>)
            String elementTypeExpr = String.format("//xs:element[@name='%s']", parentElementType);
            NodeList parentElements = (NodeList) xpath.compile(elementTypeExpr).evaluate(schemaDocument, XPathConstants.NODESET);

            if (parentElements.getLength() > 0) {
                Element parentElement = (Element) parentElements.item(0);
                typeName = parentElement.getAttribute("type");
            }

            // STEP 2: Construct the correct XPath based on whether a type was referenced
            if (typeName != null && !typeName.isEmpty()) {
                // Case A: Element uses a global type reference (e.g., type="DialogsType")
                // Find children inside the referenced GLOBAL complex type
                // expression = "//xs:complexType[@name='DialogsType']//xs:element"
                expression = String.format("//xs:complexType[@name='%s']//xs:element", typeName);
            } else {
                // Case B: Element uses an anonymous/local complex type
                // The children are nested inside the element definition
                // expression = "//xs:element[@name='MyElement']//xs:complexType//xs:element"
                expression = String.format("//xs:element[@name='%s']//xs:complexType//xs:element", parentElementType);
            }

            // --- Execute the combined XPath ---
            XPathExpression xpathExpr = xpath.compile(expression);
            NodeList elements = (NodeList) xpathExpr.evaluate(schemaDocument, XPathConstants.NODESET);

            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                String xmlTagName = element.getAttribute("name");
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

    /**
     * Finds all attributes for a given element type, prioritizing required attributes
     * and their default/fixed values from the XSD.
     * @param elementType e.g., "Button"
     * @return Map of attribute name to default value. Required attributes without a
     * default will map to an empty string ("").
     */
    public Map<String, String> getDefaultAttributes(String elementType) {
        Map<String, String> defaultAttrs = new HashMap<>();

        // This logic must handle the same two cases as getAllowedChildNodeTypes:
        // 1. Element uses a global type reference (most common).
        // 2. Element uses an anonymous/local type.

        try {
            String typeName = null;
            String elementPath = String.format("//xs:element[@name='%s']", elementType);
            NodeList parentElements = (NodeList) xpath.compile(elementPath).evaluate(schemaDocument, XPathConstants.NODESET);

            if (parentElements.getLength() > 0) {
                Element parentElement = (Element) parentElements.item(0);
                typeName = parentElement.getAttribute("type");
            }

            String expression;
            if (typeName != null && !typeName.isEmpty()) {
                // Case A: Global type reference: Look for attributes within the referenced complex type.
                expression = String.format("//xs:complexType[@name='%s']//xs:attribute", typeName);
            } else {
                // Case B: Local type: Look for attributes defined directly inside the element.
                expression = String.format("//xs:element[@name='%s']//xs:attribute", elementType);
            }

            // Execute XPath to find all attributes associated with this element's definition
            NodeList attributeNodes = (NodeList) xpath.evaluate(expression, schemaDocument, XPathConstants.NODESET);

            for (int i = 0; i < attributeNodes.getLength(); i++) {
                Element attr = (Element) attributeNodes.item(i);
                String name = attr.getAttribute("name");
                String use = attr.getAttribute("use");
                String defaultValue = attr.getAttribute("default");
                String fixedValue = attr.getAttribute("fixed");

                // Prioritize required attributes and any explicit values
                if (use.equalsIgnoreCase("required") || !defaultValue.isEmpty() || !fixedValue.isEmpty()) {
                    if (!fixedValue.isEmpty()) {
                        // Fixed values must be used
                        defaultAttrs.put(name, fixedValue);
                    } else if (!defaultValue.isEmpty()) {
                        // Default values are next
                        defaultAttrs.put(name, defaultValue);
                    } else if (use.equalsIgnoreCase("required")) {
                        // Required attribute without a default, use an empty string as a placeholder
                        defaultAttrs.put(name, "");
                    }
                }
            }

            // Ensure the 'name' attribute is always included, as it's typically required
            if (!defaultAttrs.containsKey("name")) {
                defaultAttrs.put("name", ""); // Placeholder for the unique name we generate
            }

        } catch (XPathExpressionException e) {
            System.err.println("Error querying XSD for default attributes of " + elementType + ": " + e.getMessage());
        }

        return defaultAttrs;
    }

    /**
     * Finds the XSD type (e.g., "xs:string", "xs:base64Binary", or a custom type name)
     * for an element's simple content (its text value).
     * @param elementType e.g., "PDF"
     * @return The XSD type name as a String (e.g., "xs:base64Binary"). Returns "xs:string" as a default.
     */
    public String getElementContentType(String elementType) {
        try {
            // 1. Find the element definition
            String elementPath = String.format("//xs:element[@name='%s']", elementType);
            NodeList elements = (NodeList) xpath.compile(elementPath).evaluate(schemaDocument, XPathConstants.NODESET);

            if (elements.getLength() > 0) {
                Element element = (Element) elements.item(0);

                // 2. Check for an explicit 'type' attribute (Case: <xs:element name="PDF" type="xs:base64Binary"/>)
                String typeName = element.getAttribute("type");
                if (!typeName.isEmpty()) {
                    return typeName;
                }

                // 3. Check for a local <xs:complexType> definition with simple content
                // (Case: <xs:element name="PDF"><xs:complexType><xs:simpleContent>...</xs:simpleContent>...</xs:complexType></xs:element>)
                String simpleContentPath = "descendant::xs:complexType[descendant::xs:simpleContent]";
                NodeList simpleContentNodes = (NodeList) xpath.compile(simpleContentPath).evaluate(element, XPathConstants.NODESET);

                if (simpleContentNodes.getLength() > 0) {
                    // If simpleContent is found, look for its base type (on <xs:extension> or <xs:restriction>)
                    String baseTypePath = "descendant::xs:extension/@base | descendant::xs:restriction/@base";
                    NodeList baseTypeNodes = (NodeList) xpath.compile(baseTypePath).evaluate(element, XPathConstants.NODESET);

                    if (baseTypeNodes.getLength() > 0) {
                        return baseTypeNodes.item(0).getNodeValue();
                    }
                }
            }
        } catch (XPathExpressionException e) {
            System.err.println("Error querying XSD for content type of " + elementType + ": " + e.getMessage());
        }
        // Default to string if type is not explicitly defined
        return "xs:string";
    }


    /**
     * Checks if an element's type is one of the designated types for binary content.
     */
    public boolean isBinaryContentElement(String elementType) {
        try {
            // Find the element definition
            String elementPath = String.format("//xs:element[@name='%s']", elementType);
            NodeList elements = (NodeList) xpath.compile(elementPath).evaluate(schemaDocument, XPathConstants.NODESET);

            if (elements.getLength() > 0) {
                Element element = (Element) elements.item(0);

                // 1. Check the explicit 'type' attribute
                String typeName = element.getAttribute("type");
                if (!typeName.isEmpty() && BINARY_XSD_TYPES.contains(typeName)) {
                    return true;
                }

                // 2. Check if the element contains an anonymous complex type that is simple content
                // (You could extend this logic if needed, but checking the named type is the primary generalization)
            }
        } catch (XPathExpressionException e) {
            System.err.println("Error querying XSD for binary type check: " + e.getMessage());
        }
        return false;
    }
}