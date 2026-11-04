package org.unibl.etf.mdp.vservice.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class OrderStatusUtil {

    private static final Logger LOGGER = AppLogger.getLogger();
    private static final String SUPPLIER_SERVER_HOST = PropertiesUtil.getProperty("SUPPLIER_SERVER_HOST"); // IP adresa Socket servera
    private static final int SUPPLIER_SERVER_PORT = PropertiesUtil.getIntProperty("SUPPLIER_SERVER_PORT"); // Port Socket servera

    private static OrderStatusUtil instance;

    private OrderStatusUtil() {
        // Prazan konstruktor
    }

    public static synchronized OrderStatusUtil getInstance() {
        if (instance == null) {
            instance = new OrderStatusUtil();
        }
        return instance;
    }

    /**
     * Uspostavlja socket konekciju sa Socket Serverom i šalje zahtjev za status narudžbe.
     * Očekuje direktan odgovor sa istog socketa.
     * @param orderId ID narudžbe čiji status se traži.
     * @return Status narudžbe kao string, ili poruku o grešci.
     */
    public String getOrderStatusFromSocketServer(String orderId) {
	System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Pošalji poruku Socket serveru: "ORDER_INFO:orderId"
            String messageToSend = "ORDER_INFO:" + orderId;
            out.println(messageToSend);
            LOGGER.info("Sent to Socket Server: " + messageToSend);

            // VAŽNO: Sada direktno čitamo odgovor sa socketa
            String status = in.readLine(); // Čekaj dok ne dobiješ odgovor
            
            if (status == null) {
                // Ako je stream zatvoren ili nema odgovora prije kraja streama
                LOGGER.warning("Received null response from Socket Server for order " + orderId);
                return "ERROR: No response from server.";
            }

            LOGGER.info("Received status for order " + orderId + ": " + status);
            return status;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error connecting to Socket Server or during communication: " + e.getMessage(), e);
            // pendingRequests.remove(orderId); // Ukloni zahtjev ako dođe do greške konekcije (ako bi se CompletableFuture koristio)
            return "ERROR: " + e.getMessage();
        } catch (Exception e) { // Hvatanje opšte greške
            LOGGER.log(Level.SEVERE, "An unexpected error occurred in getOrderStatusFromSocketServer: " + e.getMessage(), e);
            return "ERROR: An unexpected error occurred.";
        }
    }
}
