package org.unibl.etf.mdp.vservice.servicer.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.unibl.etf.mdp.vservice.servicer.util.PropertiesUtil;

public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger(AppLogger.class.getName());

    static {
        try {
            FileHandler fh = new FileHandler(PropertiesUtil.getProperty("LOG_FILE"), true);
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
            LOGGER.setUseParentHandlers(false);
            LOGGER.setLevel(Level.INFO); // Postavi defaultni nivo
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri inicijalizaciji logera", e);
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
