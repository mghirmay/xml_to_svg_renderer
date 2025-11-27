package com.example.xmleditorapp.xml;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom implementation of NamespaceContext to allow XPath to resolve 'xs'
 * prefixes used in the XSD document, and comply with strict contract rules
 * to prevent null returns.
 */
public class NamespaceResolver implements NamespaceContext {

    private final Map<String, String> prefixMap;

    public NamespaceResolver(String namespaceUri, String prefix) {
        prefixMap = new HashMap<>();
        prefixMap.put(prefix, namespaceUri);
    }

    @Override
    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            // According to the contract, if prefix is null, an exception should be thrown.
            throw new IllegalArgumentException("Prefix cannot be null.");
        }

        // 1. Handle default namespace (empty string prefix)
        if (prefix.isEmpty()) {
            return XMLConstants.NULL_NS_URI;
        }

        // 2. Handle XML reserved prefixes (optional but good practice)
        if (prefix.equals(XMLConstants.XML_NS_PREFIX)) {
            return XMLConstants.XML_NS_URI;
        }
        if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        }

        // 3. Look up custom prefixes (like "xs")
        String uri = prefixMap.get(prefix);

        // 4. CRITICAL: If the prefix is not found, return XMLConstants.NULL_NS_URI, NOT null.
        return (uri != null) ? uri : XMLConstants.NULL_NS_URI;
    }

    // --- Not required for simple XSD querying, but necessary for the interface ---

    @Override
    public String getPrefix(String namespaceURI) {
        // Reverse lookup not needed for this application
        return null;
    }

    @Override
    public Iterator<String> getPrefixes(String namespaceURI) {
        // Reverse lookup not needed for this application
        return null;
    }
}