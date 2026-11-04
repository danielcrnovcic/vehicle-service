package org.unibl.etf.mdp.vservice.servicer.main;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.unibl.etf.mdp.vservice.servicer.util.AppLogger;
import org.unibl.etf.mdp.vservice.servicer.util.PropertiesUtil; 

public class SingleInstanceSocketApp {

    private static final Logger LOGGER = AppLogger.getLogger();

    private static final int SINGLE_INSTANCE_PORT = PropertiesUtil.getIntProperty("SINGLE_INSTANCE_PORT"); // Izaberi slobodan port
    private static ServerSocket socket;

    public static boolean acquireLock() {
        try {
            socket = new ServerSocket(SINGLE_INSTANCE_PORT);
            LOGGER.info("Pokrenuta nova instanca aplikacije (socket na portu: " + SINGLE_INSTANCE_PORT + ").");
            // Dodaj shutdown hook da zatvoris socket pri gašenju
            Runtime.getRuntime().addShutdownHook(new Thread(() -> releaseLock()));
            return true;
        } catch (IOException e) { 
            LOGGER.log(Level.WARNING, "Aplikacija je već pokrenuta (socket na portu: " + SINGLE_INSTANCE_PORT + "). " + e.getMessage());
            return false;
        } catch (Exception e) { 
            LOGGER.log(Level.SEVERE, "Neočekivana greška prilikom pokušaja akvizicije lock-a: " + e.getMessage(), e);
            return false;
        }
    }

    private static void releaseLock() {
        LOGGER.info("Pokušavam zatvoriti single instance socket.");
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                LOGGER.info("Single instance socket uspješno zatvoren.");
            }
        } catch (IOException e) { 
            LOGGER.log(Level.SEVERE, "Greška pri zatvaranju single instance socketa: " + e.getMessage(), e);
        } catch (Exception e) { 
            LOGGER.log(Level.SEVERE, "Neočekivana greška pri zatvaranju socketa: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        if (!acquireLock()) {
            LOGGER.info("Aplikacija je već pokrenuta. Izlazim.");
            System.exit(0);
        }
        LOGGER.info("JavaFX aplikacija se pokreće...");
    }
}
