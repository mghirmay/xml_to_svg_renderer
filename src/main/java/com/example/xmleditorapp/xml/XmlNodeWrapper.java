package com.example.xmleditorapp.xml;

// Save this as XmlNodeWrapper.java (or define it inside XmlEditorController)

public class XmlNodeWrapper {
    private final String displayName;
    private final org.w3c.dom.Node xmlNode;

    public XmlNodeWrapper(String displayName, org.w3c.dom.Node xmlNode) {
        this.displayName = displayName;
        this.xmlNode = xmlNode;
    }

    public org.w3c.dom.Node getXmlNode() {
        return xmlNode;
    }

    // This is crucial: TreeView calls toString() on the object to display text
    @Override
    public String toString() {
        return displayName;
    }
}