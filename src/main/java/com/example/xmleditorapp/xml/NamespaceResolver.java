package com.example.xmleditorapp.xml;

import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom implementation of NamespaceContext to allow XPath to resolve 'xs'
 * prefixes used in the XSD document.
 */
public class NamespaceResolver implements NamespaceContext {

    private final Map<String, String> prefixMap;

    /**
     * Constructs a resolver for the given namespace URI and its common prefix.
     * * @param namespaceUri The full URI of the namespace (e.g., "http://www.w3.org/2001/XMLSchema").
     * @param prefix The prefix to use in XPath (e.g., "xs").
     */
    public NamespaceResolver(String namespaceUri, String prefix) {
        prefixMap = new HashMap<>();
        prefixMap.put(prefix, namespaceUri);
    }

    @Override
    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null.");
        }
        return prefixMap.get(prefix);
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