package org.unibl.etf.mdp.vservice.supplier.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;

import org.unibl.etf.mdp.vservice.supplier.model.Part;
import org.unibl.etf.mdp.vservice.supplier.rmi.AccountingInterface;
import org.unibl.etf.mdp.vservice.supplier.model.Order;
import org.unibl.etf.mdp.vservice.supplier.model.Bill;
import org.unibl.etf.mdp.vservice.supplier.util.ConnectionFactoryUtil;
import org.unibl.etf.mdp.vservice.supplier.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.supplier.util.AppLogger;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class SupplierServerThread extends Thread {

//    private final Socket socket;
    private SSLSocket socket;
    private final List<Part> availableParts; // Proslijeđena lista dijelova
    private final AccountingInterface accountingService;
    private static String QUEUE_NAME = PropertiesUtil.getProperty("QUEUE_NAME");

    private static final Logger LOGGER = AppLogger.getLogger();

    public SupplierServerThread(SSLSocket s, List<Part> availableParts, AccountingInterface accountingService) {
        this.socket = s;
        this.availableParts = availableParts;
        this.accountingService = accountingService;
    }
    
    public static void finishOrder(Order order) { // Uklonjen throws IOException, TimeoutException
        if (order == null) {
            LOGGER.warning("Narudžba ne smije biti prazna.");
            return;
        }
        
        Gson gson = new Gson();
        String jsonString = gson.toJson(order);

        Connection conn = null;
        Channel channel = null;
        try {
            conn = ConnectionFactoryUtil.createConnection();
            channel = conn.createChannel();

            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            channel.basicPublish("", QUEUE_NAME, null, jsonString.getBytes(StandardCharsets.UTF_8));

            LOGGER.info("Narudžba uspješno poslana!");

        } catch (IOException | TimeoutException e) {
            LOGGER.log(Level.SEVERE, "Greška pri slanju narudžbe preko RabbitMQ-a: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (TimeoutException | IOException e) {
                    LOGGER.log(Level.SEVERE, "Greška prilikom zatvaranja RabbitMQ kanala: " + e.getMessage(), e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Greška prilikom zatvaranja RabbitMQ konekcije: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void run() {
        try (
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String inputLine;
            Gson gson = new Gson();

            while ((inputLine = in.readLine()) != null) {
                LOGGER.log(Level.INFO, "Primljen zahtjev od klijenta {0}: {1}", new Object[]{socket.getInetAddress(), inputLine});

                if (inputLine.equals("GET_PARTS")) {
                    String jsonString = gson.toJson(availableParts);
                    out.println(jsonString);
                    LOGGER.log(Level.INFO, "Poslata lista dijelova klijentu {0} ({1} dijelova).", new Object[]{socket.getInetAddress(), availableParts.size()});
                } else if (inputLine.equals("ADD_PART")) {
                    String partJson = in.readLine(); 
                    try {
                        Part newPart = gson.fromJson(partJson, Part.class);
                        if (newPart != null && newPart.getCode() != null) {
                            synchronized (availableParts) { // Sinhronizovan pristup listi
                                if (availableParts.stream().noneMatch(p -> p.getCode().equals(newPart.getCode()))) {
                                    availableParts.add(newPart);
                                    out.println("SUCCESS: Part added: " + newPart.getName());
                                    LOGGER.log(Level.INFO, "Dodat novi dio: {0} (Code: {1})", new Object[]{newPart.getName(), newPart.getCode()});
                                } else {
                                    out.println("ERROR: Part with code " + newPart.getCode() + " already exists.");
                                    LOGGER.log(Level.WARNING, "Pokušaj dodavanja postojećeg dijela: {0}", newPart.getCode());
                                }
                            }
                        } else {
                            out.println("ERROR: Invalid part data provided.");
                            LOGGER.severe("Nevalidni podaci o dijelu za ADD_PART komandu.");
                        }
                    } catch (JsonSyntaxException e) {
                        out.println("ERROR: Invalid JSON format for part.");
                        LOGGER.log(Level.SEVERE, "Greška u JSON formatu za dodavanje dijela: {0}", e.getMessage());
                    }
                } else if (inputLine.equals("GENERATE_BILL")) {
                    String jsonBill = in.readLine();
                    try {
                        Bill bill = gson.fromJson(jsonBill, Bill.class);

                        double vatAmount = accountingService.processBill(bill);

                        out.println("SUCCESS: PDV za račun (" + String.format("%.2f", bill.getTotalAmount()) + "): " + String.format("%.2f", vatAmount) + " KM");
                        LOGGER.log(Level.INFO, "PDV za račun: {0} KM", String.format("%.2f", vatAmount));

                    } catch (JsonSyntaxException e) {
                        out.println("ERROR: Invalid JSON format for bill.");
                        LOGGER.log(Level.SEVERE, "Greška u JSON formatu za račun: {0}", e.getMessage());
                    } catch (RemoteException e) {
                        out.println("ERROR: Error processing bill with RMI: " + e.getMessage());
                        LOGGER.log(Level.SEVERE, "Greška pri obradi računa sa RMI serverom: {0}", e.getMessage());
                    } catch (Exception e) {
                        out.println("ERROR: An unexpected error occurred: " + e.getMessage());
                        LOGGER.log(Level.SEVERE, "Neočekivana greška pri obradi računa: {0}", e.getMessage());
                    }
                } else if(inputLine.equals("TAKE_ORDER")) {
                    String json = in.readLine();
                    Order order = gson.fromJson(json, Order.class);
                    
                    finishOrder(order);
                    
                } else if (inputLine.startsWith("ORDER_INFO:")) {
                    String orderId = inputLine.substring("ORDER_INFO:".length());
                    String status = SupplierServer.orderStatuses.getOrDefault(orderId, "NEPOZNATO");
                    out.println(status);
                    LOGGER.log(Level.INFO, "Poslat status ''{0}'' za ID narudžbe: {1}", new Object[]{status, orderId});
                } else if (inputLine.startsWith("ORDER_STATUS:")) {
                    String[] parts = inputLine.substring("ORDER_STATUS:".length()).split(":", 2);
                    if (parts.length == 2) {
                        String orderId = parts[0].trim();
                        String newStatus = parts[1].trim().toUpperCase();

                        SupplierServer.orderStatuses.put(orderId, newStatus);
                        out.println("STATUS_UPDATED");
                        LOGGER.log(Level.INFO, "Status narudžbe ID: {0} ažuriran na: {1}", new Object[]{orderId, newStatus});
                    } else {
                        out.println("INVALID_STATUS_FORMAT");
                        LOGGER.log(Level.WARNING, "Nevalidan ORDER_STATUS format: {0}", inputLine);
                    }
                } else if (inputLine.equals("END")) {
                    break;
                } else {
                    out.println("Nepoznata komanda.");
                    LOGGER.log(Level.WARNING, "Primljena nepoznata komanda od klijenta {0}: {1}", new Object[]{socket.getInetAddress(), inputLine});
                }
            }
            LOGGER.log(Level.INFO, "Klijent se odjavio: {0}:{1}", new Object[]{socket.getInetAddress().getHostAddress(), socket.getPort()});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška u komunikaciji sa klijentom {0}: {1}", new Object[]{socket.getInetAddress(), e.getMessage(), e});
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Greška prilikom zatvaranja klijentskog soketa {0}: {1}", new Object[]{socket.getInetAddress(), e.getMessage(), e});
            }
        }
    }
}
