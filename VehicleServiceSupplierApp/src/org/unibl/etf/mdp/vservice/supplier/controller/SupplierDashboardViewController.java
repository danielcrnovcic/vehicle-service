package org.unibl.etf.mdp.vservice.supplier.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level; 
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import org.unibl.etf.mdp.vservice.supplier.util.ConnectionFactoryUtil;
import org.unibl.etf.mdp.vservice.supplier.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.supplier.util.AppLogger; 
import org.unibl.etf.mdp.vservice.supplier.model.Order;
import org.unibl.etf.mdp.vservice.supplier.model.Part;
import org.unibl.etf.mdp.vservice.supplier.model.Bill;

public class SupplierDashboardViewController {

    private static final Logger LOGGER = AppLogger.getLogger();

    public static String QUEUE_NAME;
    private static String SUPPLIER_SERVER_HOST;
    private static int SUPPLIER_SERVER_PORT;
    private static String SUPPLIER_ID; // Korišćenje umjesto supplierName
    public static String supplierName; // Zadržano za inicijalizaciju SUPPLIER_ID

    @FXML
    Label infoLabel;
    @FXML
    Label orderIdLabel;
    @FXML
    ListView<String> orderList;
    @FXML
    ListView<String> partsListView;
    
    // Polja za unos novog dijela
    @FXML
    private TextField codeField;
    @FXML
    private TextField nameField;
    @FXML
    private TextField manufacturerField;
    @FXML
    private TextField priceField;
    @FXML
    private TextField quantityField;
    @FXML
    private TextField descriptionField;

    private List<String> stringOrder = new ArrayList<>();
    private Order currentOrder;
    private List<Part> fetchedParts = new ArrayList<>();

    private final Gson gson = new Gson();

    public void initialize() {
        QUEUE_NAME = PropertiesUtil.getProperty("QUEUE_NAME");
        SUPPLIER_SERVER_HOST = PropertiesUtil.getProperty("SUPPLIER_SERVER_HOST");
        SUPPLIER_SERVER_PORT = PropertiesUtil.getIntProperty("SUPPLIER_SERVER_PORT");
        SUPPLIER_ID = supplierName; // Postavi SUPPLIER_ID iz statičke varijable
        LOGGER.log(Level.INFO, "SupplierDashboardViewController inicijalizovan. QUEUE_NAME: {0}, SERVER_HOST: {1}, SERVER_PORT: {2}, SUPPLIER_ID: {3}",
            new Object[]{QUEUE_NAME, SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT, SUPPLIER_ID});
    }
    
    public void takeOrder(ActionEvent event) { 
        Platform.runLater(() -> {
            infoLabel.setText("");
            orderIdLabel.setText("");
            orderList.getItems().clear();
        });
        stringOrder.clear();
        currentOrder = null; // Resetuj trenutnu narudžbu

        Connection conn = null;
        Channel channel = null;

        try {
            conn = ConnectionFactoryUtil.createConnection();
            channel = conn.createChannel();

            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            LOGGER.info("Povezan sa RabbitMQ i deklarisan red: " + QUEUE_NAME);

            GetResponse response = channel.basicGet(QUEUE_NAME, false);
            if (response != null) {
                String jsonContent = new String(response.getBody(), StandardCharsets.UTF_8);
                LOGGER.log(Level.INFO, "Preuzeta sirova narudžba iz MQ: {0}", jsonContent);

                try {
                    Order order = gson.fromJson(jsonContent, Order.class);
                    
                    LOGGER.log(Level.INFO, "SupplierDashboard preuzeo narudžbu iz MQ sa ID: {0}", order.getOrderId());

                    currentOrder = order;
                    
                    Platform.runLater(() -> {
                        infoLabel.setText(order.getServiceId());
                        orderIdLabel.setText(order.getOrderId());

                        for (Part item : order.getItems()) {
                            stringOrder.add("Ime: " + item.getName() + " | Količina: " + item.getQuantity() + " | Proizvodjac: " + item.getManufacturer());
                        }
                        orderList.getItems().addAll(stringOrder);
                    });

                    channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                    LOGGER.info("Narudžba sa ID: " + order.getOrderId() + " uspješno obrađena i ACK poslan.");
                } catch (JsonSyntaxException e) {
                    LOGGER.log(Level.SEVERE, "Greška prilikom parsiranja JSON-a iz MQ-a: " + e.getMessage(), e);
                    channel.basicReject(response.getEnvelope().getDeliveryTag(), true); // Requeue na grešku parsiranja
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri obradi narudžbe (JSON format)."));
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Opšta greška pri obradi narudžbe iz MQ-a: " + e.getMessage(), e);
                    channel.basicReject(response.getEnvelope().getDeliveryTag(), true); // Requeue na opštu grešku
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Opšta greška pri obradi narudžbe."));
                }
            } else {
                LOGGER.info("Nema novih narudžbi u RabbitMQ redu.");
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Informacija", "Nema novih narudžbi u redu.");
                    infoLabel.setText("");
                    orderIdLabel.setText("");
                });
            }
        } catch (IOException | TimeoutException e) {
            LOGGER.log(Level.SEVERE, "Greška pri povezivanju s RabbitMQ: " + e.getMessage(), e);
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri povezivanju s MQ serverom."));
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

    public void approveOrder(ActionEvent event) {
        if (currentOrder != null && !currentOrder.getItems().isEmpty()) {
            LOGGER.log(Level.INFO, "Pokušaj odobravanja narudžbe sa ID: {0}", currentOrder.getOrderId());
            new Thread(() -> {
                try {
                    orderInfo(true); // Šalje "ODOBRENO" status
                    generateAndSendInvoice(); // Generiše i šalje račun
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Narudžba odobrena i status poslan dobavljač serveru. Račun poslan knjigovodstvu.");
                    });
                    LOGGER.log(Level.INFO, "Narudžba ID: {0} uspješno odobrena, status ažuriran i račun poslan.", currentOrder.getOrderId());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Greška pri komunikaciji sa serverom dobavljača prilikom odobravanja narudžbe: " + e.getMessage(), e);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri slanju odobrenja serveru dobavljača: " + e.getMessage()));
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Greška pri obradi računa sa RMI serverom ili neočekivana greška prilikom odobravanja narudžbe: " + e.getMessage(), e);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri slanju računa knjigovodstvu: " + e.getMessage()));
                } finally {
                    Platform.runLater(() -> {
                        orderIdLabel.setText("");
                        orderList.getItems().clear();
                        stringOrder.clear();
                        currentOrder = null;
                        infoLabel.setText(""); // Očisti infoLabel nakon obrade
                    });
                }
            }).start();
        } else {
            LOGGER.warning("Pokušaj odobravanja narudžbe bez aktivne narudžbe.");
            Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Upozorenje", "Nema narudžbe za odobravanje."));
        }
    }

    public void denyOrder(ActionEvent event) {
        if (currentOrder != null && !currentOrder.getItems().isEmpty()) {
            LOGGER.log(Level.INFO, "Pokušaj odbijanja narudžbe sa ID: {0}", currentOrder.getOrderId());
            new Thread(() -> {
                try {
                    orderInfo(false); // Šalje "ODBIJENO" status
                    Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Narudžba odbijena."));
                    LOGGER.log(Level.INFO, "Narudžba ID: {0} uspješno odbijena i status ažuriran.", currentOrder.getOrderId());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Greška pri komunikaciji sa serverom dobavljača prilikom odbijanja narudžbe: " + e.getMessage(), e);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri slanju odbijanja serveru dobavljača: " + e.getMessage()));
                } finally {
                    Platform.runLater(() -> {
                        orderIdLabel.setText("");
                        orderList.getItems().clear();
                        stringOrder.clear();
                        currentOrder = null;
                        infoLabel.setText(""); // Očisti infoLabel nakon obrade
                    });
                }
            }).start();
        } else {
            LOGGER.warning("Pokušaj odbijanja narudžbe bez aktivne narudžbe.");
            Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Upozorenje", "Nema narudžbe za odbijanje."));
        }
    }

    private void orderInfo(boolean approved) throws UnknownHostException, IOException {
        String status = approved ? "ODOBRENO" : "ODBIJENO";
        LOGGER.log(Level.INFO, "Slanje statusa narudžbe {0} za ID: {1} dobavljač serveru.", new Object[]{status, currentOrder.getOrderId()});

        System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket s = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)), true)) {

            String command = "ORDER_STATUS:" + currentOrder.getOrderId() + ":" + status;
            out.println(command);
            LOGGER.log(Level.INFO, "Poslata komanda dobavljač serveru: {0}", command);
            
            String response = in.readLine();
            LOGGER.log(Level.INFO, "Odgovor od SupplierServera za status narudžbe: {0}", response);

            if (!"STATUS_UPDATED".equals(response)) {
                LOGGER.log(Level.WARNING, "Očekivan odgovor 'STATUS_UPDATED', primljeno: {0}", response);
            }

        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "Nepoznat host pri slanju statusa narudžbe: " + SUPPLIER_SERVER_HOST + " - " + e.getMessage(), e);
            throw e; // Ponovo baci kako bi se uhvatila u approve/denyOrder
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri IO komunikaciji sa SupplierServerom prilikom slanja statusa: " + e.getMessage(), e);
            throw e; // Ponovo baci
        }
    }

    private void generateAndSendInvoice() throws Exception {
        LOGGER.info("Ulazak u generateAndSendInvoice metodu za narudžbu ID: " + currentOrder.getOrderId());
        List<Part> itemsForInvoice = new ArrayList<>();
        double totalAmount = 0.0;
        
        for (Part originalItem : currentOrder.getItems()) {
            Part part = new Part(originalItem.getCode(),originalItem.getName(),originalItem.getManufacturer(),originalItem.getPrice(),originalItem.getQuantity(),originalItem.getDescription());
            itemsForInvoice.add(part);
            totalAmount += originalItem.getPrice() * originalItem.getQuantity();
        }
        
        LOGGER.info("Kreiranje objekta računa za narudžbu ID: " + currentOrder.getOrderId() + ", ukupni iznos: " + totalAmount);

        Bill bill = new Bill(
                currentOrder.getOrderId(),
                currentOrder.getServiceId(),
                itemsForInvoice,
                totalAmount
        );
        bill.setDate(new Date());
        
        LOGGER.log(Level.INFO, "Generisani račun: {0}", bill.toString());
        System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

        try (
                SSLSocket s = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
        ) {
            Gson gson = new Gson();
            out.println("GENERATE_BILL");
            String jsonBill = gson.toJson(bill);
            out.println(jsonBill);
            LOGGER.info("Poslat GENERATE_BILL zahtjev i JSON računa dobavljač serveru.");

            String response = in.readLine();
            LOGGER.log(Level.INFO, "Odgovor od SupplierServera za račun: {0}", response);

            Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Račun", response));
            LOGGER.info("Račun uspješno poslan i odgovor prikazan korisniku.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri komunikaciji sa SupplierServerom prilikom slanja računa: " + e.getMessage(), e);
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri slanju računa na SupplierServer: " + e.getMessage()));
            throw e; // Ponovo baci kako bi se uhvatila u approveOrder
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Neočekivana greška prilikom generisanja i slanja računa: " + e.getMessage(), e);
            throw e; // Ponovo baci
        }
    }

    public void getPartsFromSupplier(ActionEvent event) {
        partsListView.getItems().clear();
        fetchedParts.clear();
        LOGGER.info("Zahtjev za preuzimanje dijelova sa SupplierServera.");
        System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

        try (
                SSLSocket socket = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            out.println("GET_PARTS");
            LOGGER.info("Poslat GET_PARTS zahtjev SupplierServeru.");
            String jsonResponse = in.readLine();
            LOGGER.log(Level.INFO, "Primljen JSON sa SupplierServera za dijelove: {0}", jsonResponse);

            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                Type partListType = new TypeToken<ArrayList<Part>>(){}.getType();
                fetchedParts = gson.fromJson(jsonResponse, partListType);

                if (fetchedParts != null && !fetchedParts.isEmpty()) {
                    Platform.runLater(() -> {
                        for (Part part : fetchedParts) {
                            partsListView.getItems().add(part.getName() + " by " + part.getManufacturer() + " (Price: " + part.getPrice() + ")");
                        }
                        LOGGER.info("Učitano " + fetchedParts.size() + " dijelova u ListView.");
                    });
                } else {
                    LOGGER.info("Nema dostupnih dijelova na SupplierServeru.");
                    Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Informacija", "Nema dostupnih dijelova na SupplierServeru."));
                }
            } else {
                LOGGER.warning("Nema odgovora ili prazan odgovor sa SupplierServera prilikom preuzimanja dijelova.");
                Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "Informacija", "Nema odgovora ili prazan odgovor sa SupplierServera."));
            }
            out.println("END"); // Poslati END komandu nakon završetka komunikacije
            LOGGER.info("Poslata END komanda SupplierServeru.");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri komunikaciji sa SupplierServerom prilikom preuzimanja dijelova: " + e.getMessage(), e);
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri povezivanju sa SupplierServerom: " + e.getMessage()));
        } catch (JsonSyntaxException e) {
            LOGGER.log(Level.SEVERE, "Greška pri parsiranju JSON-a sa SupplierServera (dijelovi): " + e.getMessage(), e);
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri parsiranju podataka dijelova."));
        }
    }

    public void addPartToSupplier(ActionEvent event) {
        String code = codeField.getText();
        String name = nameField.getText();
        String manufacturer = manufacturerField.getText();
        String description = descriptionField.getText();
        double price;
        int quantity;

        LOGGER.info("Pokušaj dodavanja novog dijela. Code: " + code + ", Name: " + name);

        if (code.isEmpty() || name.isEmpty() || manufacturer.isEmpty() || priceField.getText().isEmpty() || quantityField.getText().isEmpty()) {
            LOGGER.warning("Pokušaj dodavanja dijela sa praznim obaveznim poljima.");
            Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Upozorenje", "Sva polja (osim opisa) moraju biti popunjena!"));
            return;
        }

        try {
            price = Double.parseDouble(priceField.getText());
            quantity = Integer.parseInt(quantityField.getText());
            if (price < 0 || quantity < 0) {
                LOGGER.warning("Pokušaj dodavanja dijela sa negativnom cijenom ili količinom. Price: " + price + ", Quantity: " + quantity);
                Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Upozorenje", "Cijena i količina ne mogu biti negativni!"));
                return;
            }
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Greška pri parsiranju cijene ili količine za novi dio: " + e.getMessage(), e);
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Cijena i količina moraju biti brojevi!"));
            return;
        }

        Part newPart = new Part(code, name, manufacturer, price, quantity, description);
        LOGGER.info("Kreiran novi objekat dijela: " + newPart.toString());

        new Thread(() -> {
            System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
            System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

            SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (
                SSLSocket s = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
                PrintWriter newOut = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader newIn = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            ) {
                newOut.println("ADD_PART");
                String jsonPart = gson.toJson(newPart);
                newOut.println(jsonPart);
                LOGGER.info("Poslat ADD_PART zahtjev i JSON novog dijela SupplierServeru.");

                String response = newIn.readLine();
                LOGGER.log(Level.INFO, "Odgovor od SupplierServera za dodavanje dijela: {0}", response);

                Platform.runLater(() -> {
                    if (response.startsWith("SUCCESS")) {
                        showAlert(Alert.AlertType.INFORMATION, "Uspjeh", response);
                        codeField.clear();
                        nameField.clear();
                        manufacturerField.clear();
                        priceField.clear();
                        quantityField.clear();
                        descriptionField.clear();
                        getPartsFromSupplier(null); // Osvježi listu dijelova
                        LOGGER.info("Dio uspješno dodan i polja su očišćena.");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Greška", response);
                        LOGGER.warning("Neuspješno dodavanje dijela: " + response);
                    }
                });
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Greška pri komunikaciji sa SupplierServerom prilikom dodavanja dijela: " + e.getMessage(), e);
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri slanju dijela na SupplierServer: " + e.getMessage()));
            }
        }).start();
    }

    // showAlert metoda je sada dio klase
    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
