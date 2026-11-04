package org.unibl.etf.mdp.vservice.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PropertiesUtil {
    private static final Logger LOGGER = Logger.getLogger(PropertiesUtil.class.getName());
    private static Properties properties = new Properties();
    private static final String PROPERTIES_FILE_NAME = "C:\\Users\\Hp\\eclipse-workspace\\VehicleServiceCentralServer\\config.properties"; 

    // Statički blok - izvršava se samo jednom, kada se klasa PropertiesUtil učita u JVM.
    static {
        try (InputStream input = new FileInputStream(PROPERTIES_FILE_NAME)) {
            if (input == null) {
                // Ovo se obično ne bi trebalo desiti za FileInputStream ako putanja nije null/prazna
                // ali ostavljam provjeru za svaki slučaj.
                LOGGER.log(Level.SEVERE, "Sorry, unable to find " + PROPERTIES_FILE_NAME + ".");
            } else {
                properties.load(input);
                LOGGER.log(Level.INFO, PROPERTIES_FILE_NAME + " successfully loaded via static initializer.");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error loading properties file via static initializer: " + PROPERTIES_FILE_NAME, ex);
        }
    }

    /**
     * Retrieves the String value of the specified property key.
     * @param key The key of the property.
     * @return The String value of the property, or null if the key is not found.
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Retrieves the String value of the specified property key, with a default value.
     * @param key The key of the property.
     * @param defaultValue The default value to return if the key is not found.
     * @return The String value of the property, or the defaultValue if the key is not found.
     */
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Retrieves the integer value of the specified property key.
     * Throws NumberFormatException if the property exists but is not a valid integer.
     * @param key The key of the property.
     * @return The integer value of the property.
     * @throws NumberFormatException If the property value cannot be parsed as an integer.
     * @throws NullPointerException If the property is not found.
     */
    public static int getIntProperty(String key) {
        // Poziva getProperty(key) koje vraća null ako ne postoji, pa će Integer.parseInt baciti NPE.
        // Ili NumberFormatException ako je format loš.
        return Integer.parseInt(getProperty(key));
    }

    /**
     * Retrieves the integer value of the specified property key, with a default value.
     * @param key The key of the property.
     * @param defaultValue The default value to return if the key is not found or cannot be parsed.
     * @return The integer value of the property, or the defaultValue if the key is not found or invalid.
     */
    public static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Invalid integer property value: " + key + "=" + value + ", using default value: " + defaultValue, e);
            }
        }
        return defaultValue;
    }

    /**
     * Retrieves the boolean value of the specified property key, with a default value.
     * @param key The key of the property.
     * @param defaultValue The default value to return if the key is not found.
     * @return The boolean value of the property, or the defaultValue if the key is not found.
     */
    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value); // Boolean.parseBoolean() vraća false za "null", "false", ili bilo šta što nije "true" (case-insensitive)
        }
        return defaultValue;
    }
}