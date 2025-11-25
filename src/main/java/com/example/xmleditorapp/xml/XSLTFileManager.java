package com.example.xmleditorapp.xml;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the loading and retrieval of the XSLT stylesheet source.
 * Prioritizes: Custom file > Classpath resource > Hardcoded fallback.
 * Thread-safe Singleton implementation.
 */
public class XSLTFileManager {

    private static final String DEFAULT_RESOURCE_PATH ="esign.xsl";

    // Optional externally loaded file
    private File customXsltFile = null;

    // URL to classpath-based stylesheet, null if not found
    private URL defaultResourceUrl = null;

    // Fallback XSLT in case everything fails
    private static final String FALLBACK_XSLT =
            "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns=\"http://www.w3.org/2000/svg\">" +
                    "  <xsl:output method=\"xml\" indent=\"yes\"/>" +
                    "  <xsl:template match=\"/|@*|node()\">" +
                    "    <svg width=\"400\" height=\"300\" viewBox=\"0 0 400 300\">" +
                    "      <text x=\"20\" y=\"30\" fill=\"red\">ERROR: Visualization file missing.</text>" +
                    "    </svg>" +
                    "  </xsl:template>" +
                    "</xsl:stylesheet>";

    /**
     * Singleton instance holder (thread-safe, lazy-loaded).
     */
    private static class Holder {
        private static final XSLTFileManager INSTANCE = new XSLTFileManager();
    }

    /**
     * Returns the global instance.
     */
    public static XSLTFileManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Private constructor to prevent direct construction.
     */
    private XSLTFileManager() {
        loadDefaultResource();
    }

    // --------------------------------------------------------------
    // Resource loading
    // --------------------------------------------------------------

    /**
     * Attempts to locate the default XSLT from classpath resources.
     */
    private void loadDefaultResource() {
        System.out.println("class-path: " +
                XSLTFileManager.class.getResource("")
        );

        URL url =XSLTFileManager.class.getResource(DEFAULT_RESOURCE_PATH);

        if (url != null) {
            this.defaultResourceUrl = url;
            log("Loaded default XSLT resource: " + DEFAULT_RESOURCE_PATH);
        } else {
            warn("Default resource missing: " + DEFAULT_RESOURCE_PATH + ". Will use fallback.");
        }
    }

    /**
     * Load a user-specified XSLT file from disk (highest priority).
     */
    public boolean loadFromFile(String customXsltFilename) {
        Path xsltPath = Paths.get(customXsltFilename);

        if (!Files.exists(xsltPath)) {
            warn("Custom file not found: " + customXsltFilename + ". Reverting to default resource.");
            this.customXsltFile = null;
            return false;
        }

        try {
            File file = xsltPath.toFile();
            if (file.isDirectory()) {
                warn("Custom XSLT path is a directory, not a file: " + customXsltFilename);
                return false;
            }

            this.customXsltFile = file;
            log("Loaded custom XSLT from: " + customXsltFilename);
            return true;

        } catch (Exception e) {
            error("Could not load custom XSLT: " + e.getMessage());
            this.customXsltFile = null;
            return false;
        }
    }

    /**
     * Optional: Clears the custom file and returns to the default resource.
     */
    public void clearCustomFile() {
        this.customXsltFile = null;
        log("Custom XSLT cleared. Reverting to default resource.");
    }

    // --------------------------------------------------------------
    // StreamSource fetching
    // --------------------------------------------------------------

    /**
     * Provides a fresh StreamSource each time.
     * Priority order:
     * 1. Custom file
     * 2. Classpath resource
     * 3. Fallback hardcoded XSLT string
     */
    public StreamSource getActiveStyle() {

        // 1. Custom file
        if (this.customXsltFile != null) {
            log("Using custom XSLT file.");

            StreamSource source = new StreamSource(this.customXsltFile);
            source.setSystemId(this.customXsltFile.toURI().toString());
            return source;
        }

        // 2. Classpath resource
        if (this.defaultResourceUrl != null) {
            try {
                log("Using default classpath XSLT resource.");

                InputStream inputStream = this.defaultResourceUrl.openStream();
                StreamSource source = new StreamSource(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );
                source.setSystemId(defaultResourceUrl.toExternalForm());
                return source;

            } catch (Exception e) {
                error("Failed reading default resource: " + e.getMessage());
            }
        }

        // 3. Fallback
        warn("Using hardcoded fallback XSLT.");

        StreamSource fallback = new StreamSource(new StringReader(FALLBACK_XSLT));
        fallback.setSystemId("fallback-xslt");
        return fallback;
    }

    // --------------------------------------------------------------
    // Logging helpers
    // --------------------------------------------------------------

    private void log(String msg) {
        System.out.println("[XSLTFileManager] " + msg);
    }

    private void warn(String msg) {
        System.err.println("[XSLTFileManager WARNING] " + msg);
    }

    private void error(String msg) {
        System.err.println("[XSLTFileManager ERROR] " + msg);
    }
}
