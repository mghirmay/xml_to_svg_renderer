package com.example.xmleditorapp.xml; // Assuming an 'xml' package for utilities

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class RecentFilesManager {
    private static final String PROPERTIES_FILE = "xmleditor.properties";
    private static final int MAX_RECENT_FILES = 5;
    private final Properties properties = new Properties();

    public RecentFilesManager() {
        load();
    }

    // --- Persistence ---

    private void load() {
        File propFile = new File(PROPERTIES_FILE);
        if (propFile.exists()) {
            try (FileInputStream input = new FileInputStream(propFile)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("Error loading properties: " + e.getMessage());
            }
        }
    }

    private void save() {
        try (FileOutputStream output = new FileOutputStream(PROPERTIES_FILE)) {
            properties.store(output, "XML Editor Recent Files List");
        } catch (IOException e) {
            System.err.println("Error saving properties: " + e.getMessage());
        }
    }

    // --- Recent Files Logic ---

    public List<String> getRecentFiles() {
        List<String> recentFiles = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String path = properties.getProperty("recent." + i);
            if (path != null && new File(path).exists()) {
                recentFiles.add(path);
            }
        }
        return recentFiles;
    }

    public void addFile(String filePath) {
        List<String> files = getRecentFiles().stream()
                .filter(p -> !p.equals(filePath)) // Remove duplicates
                .collect(Collectors.toList());

        files.addFirst(filePath); // Add new path to the front

        // Trim list to max size
        if (files.size() > MAX_RECENT_FILES) {
            files = files.subList(0, MAX_RECENT_FILES);
        }

        // Save back to properties
        properties.clear();
        for (int i = 0; i < files.size(); i++) {
            properties.setProperty("recent." + i, files.get(i));
        }
        save();
    }
}