package org.unibl.etf.mdp.vservice.servicer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PropertiesUtil {
 private static final Logger LOGGER = Logger.getLogger(PropertiesUtil.class.getName());
 private static Properties properties = new Properties();
 private static final String PROPERTIES_FILE_NAME ="config.properties"; 
 

//Metoda za učitavanje properties fajla
static {
   try (InputStream input = new FileInputStream(PROPERTIES_FILE_NAME)) {
       if (input == null) {
           LOGGER.log(Level.SEVERE, "Sorry, unable to find " + PROPERTIES_FILE_NAME + ". Make sure it's in the classpath (e.g., src/main/resources).");
       } else {
       properties.load(input);
        LOGGER.log(Level.INFO, PROPERTIES_FILE_NAME + " successfully loaded.");
       }
   } catch (Exception ex) {
       LOGGER.log(Level.SEVERE, "Error loading properties file: " + PROPERTIES_FILE_NAME, ex);
   }
}

 // Metoda za dobijanje String propertija
 public static String getProperty(String key) {
     return properties.getProperty(key);
 }

 // Metoda za dobijanje String propertija sa podrazumijevanom vrijednošću
 public static String getProperty(String key, String defaultValue) {
      return properties.getProperty(key, defaultValue);
 }
 
//Metoda za dobijanje Integer propertija bez podrazumijevane vrijednosti
public static int getIntProperty(String key) {
    return Integer.parseInt(getProperty(key));
}


 // Metoda za dobijanje Integer propertija
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

 // Dodati metode za boolean, double, itd. ako su potrebne
  public static boolean getBooleanProperty(String key, boolean defaultValue) {
     String value = getProperty(key);
     if (value != null) {
         return Boolean.parseBoolean(value);
     }
     return defaultValue;
 }
}


