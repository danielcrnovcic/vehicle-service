package org.unibl.etf.mdp.vservice.servicer.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.unibl.etf.mdp.vservice.servicer.model.Part;
import org.unibl.etf.mdp.vservice.servicer.model.Order;
import org.unibl.etf.mdp.vservice.servicer.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.servicer.util.AppLogger; 

public class OrderViewController implements Initializable {

    private static final Logger LOGGER = AppLogger.getLogger();

    private static String SERVICE_ID;
    private Stage stage;
    private Scene scene;

    @FXML
    private Label quantityLabel;

    @FXML
    private TextField quantityField;

    @FXML
    private ChoiceBox<String> partBox;

    @FXML
    private ListView<String> orderList;

    private String pickedPartName = "";
    public static Map<String, Part> allPartsByName = new HashMap<>();
    private Map<String, Integer> availableQuantities = new HashMap<>();

    private List<Part> currentOrderItems = new ArrayList<>();
    private List<String> currentOrderItemsStrings = new ArrayList<>();

    private final Gson gson;
    
    @FXML
    private Label orderStatusLabel;
    @FXML
    private Button refreshStatusButton;

    private String lastSentOrderId;
    
    private static final String BASE_URL = PropertiesUtil.getProperty("BASE_URL");
    private static final String ORDER_STATUS_API_URL = BASE_URL + "/orders/status/";
    private static final String PARTS_API_URL = BASE_URL + "/parts/supplier";
    private static final String ORDER_SUBMIT_API_URL = BASE_URL + "/orders/submit";

    public OrderViewController() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting() 
            .create();
    }

    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
        SERVICE_ID = PropertiesUtil.getProperty("LIBRARY_ID");
        if (SERVICE_ID == null || SERVICE_ID.trim().isEmpty()) {
            SERVICE_ID = "DefaultService001";
            LOGGER.log(Level.WARNING, "SERVICE_ID nije konfigurisan u config.properties. Korišćen default: {0}", SERVICE_ID);
        }
        
        new Thread(() -> {
            initializePartsFromRest();
        }).start();
        
        refresh();
        LOGGER.info("OrderViewController inicijalizovan.");
    }
    
    private void initializePartsFromRest() {
        allPartsByName.clear();
        availableQuantities.clear();
        LOGGER.info("Pokušaj dohvatanja dijelova sa REST API-ja: " + PARTS_API_URL);

        try {
            URL url = new URL(PARTS_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    
                    Part[] partsArray = gson.fromJson(response.toString(), Part[].class);
                    List<Part> parts = Arrays.asList(partsArray);

                    if (parts != null && !parts.isEmpty()) {
                        for (Part part : parts) {
                            allPartsByName.put(part.getName(), part);
                            availableQuantities.put(part.getName(), part.getQuantity());
                        }
                        Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Dijelovi uspješno učitani sa dobavljač servera.");
                            refresh();
                        });
                        LOGGER.log(Level.INFO, "Uspješno učitano {0} dijelova sa REST API-ja.", parts.size());
                    } else {
                        Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Informacija", "Nema dostupnih dijelova na dobavljač serveru.");
                            refresh();
                        });
                        LOGGER.info("Nema dostupnih dijelova na dobavljač serveru.");
                    }
                }
            } else {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri dohvatanju dijelova sa dobavljač servera. HTTP Status: " + responseCode);
                });
                LOGGER.log(Level.SEVERE, "Failed to fetch parts from supplier. HTTP Status: {0}", responseCode);
            }
        } catch (IOException | JsonSyntaxException e) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri komunikaciji sa dobavljač serverom ili parsiranju podataka: " + e.getMessage());
            });
            LOGGER.log(Level.SEVERE, "Error fetching or parsing parts from supplier: {0}", e.getMessage());
        }
    }

    public void refresh() {
        partBox.getItems().clear();
        orderList.getItems().clear();

        if (!currentOrderItemsStrings.isEmpty()) {
            orderList.getItems().addAll(currentOrderItemsStrings);
        }

        List<String> partNamesForChoiceBox = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : availableQuantities.entrySet()) {
            if (entry.getValue() > 0) {
                partNamesForChoiceBox.add(entry.getKey());
            }
        }
        partBox.getItems().addAll(partNamesForChoiceBox);
        partBox.setOnAction(this::getPickedPart);
        LOGGER.info("Prikaz dijelova i liste narudžbi osvježen.");
    }

    public void addPart(ActionEvent event) {
        if (pickedPartName == null || pickedPartName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Dio mora biti odabran!");
            LOGGER.warning("Pokušaj dodavanja dijela bez odabranog dijela.");
            return;
        }
        if (quantityField.getText().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Količina mora biti unesena!");
            LOGGER.warning("Pokušaj dodavanja dijela bez unesene količine.");
            return;
        }

        try {
            int newQuantity = Integer.parseInt(quantityField.getText());
            if (newQuantity < 1) {
                showAlert(Alert.AlertType.WARNING, "Upozorenje", "Količina mora biti najmanje 1!");
                LOGGER.warning("Pokušaj dodavanja dijela sa količinom manjom od 1: " + newQuantity);
                return;
            }

            Integer currentAvailableQuantity = availableQuantities.get(pickedPartName);
            if (currentAvailableQuantity == null || currentAvailableQuantity <= 0) {
                showAlert(Alert.AlertType.WARNING, "Upozorenje", "Dio '" + pickedPartName + "' trenutno nije dostupan ili nema zaliha.");
                LOGGER.warning("Pokušaj dodavanja dijela '" + pickedPartName + "' koji nije dostupan ili nema zaliha.");
                return;
            }

            if (newQuantity > currentAvailableQuantity) {
                showAlert(Alert.AlertType.WARNING, "Upozorenje", "Greška u unesenoj količini! Dostupno: " + currentAvailableQuantity);
                LOGGER.warning("Prevelika količina za dio '" + pickedPartName + "'. Uneseno: " + newQuantity + ", Dostupno: " + currentAvailableQuantity);
                return;
            }

            Part pickedPart = allPartsByName.get(pickedPartName);
            if (pickedPart == null) {
                showAlert(Alert.AlertType.ERROR, "Greška", "Greška: Podaci o odabranom dijelu nisu pronađeni.");
                LOGGER.severe("Greška: Podaci o odabranom dijelu '" + pickedPartName + "' nisu pronađeni u mapi allPartsByName.");
                return;
            }

            Part orderItem = new Part(pickedPart.getCode(), pickedPart.getName(), pickedPart.getManufacturer(), pickedPart.getPrice(), newQuantity, pickedPart.getDescription());
            currentOrderItems.add(orderItem);
            currentOrderItemsStrings.add(orderItem.getName() + " (Količina: " + newQuantity + ")");

            availableQuantities.put(pickedPartName, currentAvailableQuantity - newQuantity);

            refresh();
            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Dio dodan u narudžbu.");
            quantityField.clear();
            LOGGER.info("Dio '" + pickedPartName + "' (količina: " + newQuantity + ") dodan u narudžbu.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Greška", "Molimo unesite validnu količinu (broj).");
            LOGGER.log(Level.SEVERE, "Greška u formatu količine: {0}", e.getMessage());
        }
    }

    public void getPickedPart(ActionEvent event) {
        pickedPartName = partBox.getValue();
        if (pickedPartName != null && !pickedPartName.isEmpty()) {
            Integer availableQty = availableQuantities.get(pickedPartName);
            if (availableQty != null) {
                quantityLabel.setText(String.valueOf(availableQty));
                LOGGER.log(Level.INFO, "Odabran dio: {0}, Dostupno: {1}", new Object[]{pickedPartName, availableQty});
            } else {
                quantityLabel.setText("N/A");
                LOGGER.log(Level.WARNING, "Dostupna količina za odabrani dio '{0}' nije pronađena.", pickedPartName);
            }
        } else {
            quantityLabel.setText("");
            LOGGER.info("Nijedan dio nije odabran u ChoiceBox-u.");
        }
    }
    
    public void sendOrder(ActionEvent event) {
        if (currentOrderItems.isEmpty()) {
            Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Upozorenje", "Narudžba ne može biti prazna!"));
            LOGGER.warning("Pokušaj slanja prazne narudžbe.");
            return;
        }

        String orderId = UUID.randomUUID().toString();
        
        LOGGER.info("Glavna aplikacija šalje narudžbu sa ID: " + orderId);
        
        Order order = new Order(orderId, SERVICE_ID, currentOrderItems);

        try {
            URL url = new URL(ORDER_SUBMIT_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            
            String jsonString = gson.toJson(order);
            LOGGER.log(Level.FINE, "JSON narudžbe poslan serveru: {0}", jsonString);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Narudžba uspješno poslana!");
                    orderStatusLabel.setText("POSLATO");
                });
                lastSentOrderId = orderId;
                LOGGER.log(Level.INFO, "Narudžba ID: {0} uspješno poslana. HTTP Status: {1}", new Object[]{orderId, responseCode});

                currentOrderItems.clear();
                currentOrderItemsStrings.clear();
                Platform.runLater(this::refresh); 
            } else {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri slanju narudžbe. HTTP Status: " + responseCode);
                    orderStatusLabel.setText("GREŠKA PRI SLANJU");
                });
                LOGGER.log(Level.SEVERE, "Neuspješno slanje narudžbe. HTTP Status: {0}", responseCode);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = br.readLine()) != null) {
                        errorResponse.append(errorLine.trim());
                    }
                    LOGGER.log(Level.SEVERE, "Error response from server: {0}", errorResponse.toString());
                }
                lastSentOrderId = null;
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri komunikaciji sa serverom: " + e.getMessage());
                orderStatusLabel.setText("KOMUNIKACIJSKA GREŠKA");
            });
            LOGGER.log(Level.SEVERE, "Greška pri slanju narudžbe putem REST-a: {0}", e.getMessage());
            lastSentOrderId = null;
        }
    }
    
    @FXML
    public void refreshOrderStatus(ActionEvent event) {
        if (lastSentOrderId == null || lastSentOrderId.isEmpty()) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.INFORMATION, "Informacija", "Nema poslane narudžbe za provjeru statusa.");
                orderStatusLabel.setText("N/A");
            });
            LOGGER.info("Nema poslane narudžbe za provjeru statusa.");
            return;
        }

        Platform.runLater(() -> {
            orderStatusLabel.setText("Provjera statusa...");
        });

        LOGGER.log(Level.INFO, "Pokušaj provjere statusa za narudžbu ID: {0}", lastSentOrderId);

        new Thread(() -> {
            try {
                URL url = new URL(ORDER_STATUS_API_URL + lastSentOrderId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/plain");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String status = in.readLine();
                        Platform.runLater(() -> {
                            orderStatusLabel.setText(status != null ? status.toUpperCase() : "NEPOZNATO");
                            showAlert(Alert.AlertType.INFORMATION, "Status Narudžbe", "Status narudžbe '" + lastSentOrderId + "': " + (status != null ? status.toUpperCase() : "NEPOZNATO"));
                        });
                        LOGGER.log(Level.INFO, "Status narudžbe ID: {0} je: {1}", new Object[]{lastSentOrderId, status});
                    }
                } else {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri dohvatanju statusa. HTTP Status: " + responseCode);
                        orderStatusLabel.setText("GREŠKA (" + responseCode + ")");
                    });
                    LOGGER.log(Level.SEVERE, "Failed to fetch order status for ID: {0}. HTTP Status: {1}", new Object[]{lastSentOrderId, responseCode});
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri komunikaciji sa serverom za status: " + e.getMessage());
                    orderStatusLabel.setText("KOMUNIKACIJSKA GREŠKA");
                });
                LOGGER.log(Level.SEVERE, "Greška pri dohvatanju statusa narudžbe (ID: {0}): {1}", new Object[]{lastSentOrderId, e.getMessage()});
            }
        }).start();
    }
    
    public void toFirstView(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/servicer/view/ServicerDashboardView.fxml"));
            stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
            LOGGER.info("Prebačen prikaz na ServicerDashboardView.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Greška", "Greška pri učitavanju prethodnog prikaza: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Greška pri učitavanju ServicerDashboardView.fxml: {0}", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Greška", "Neočekivana greška pri prelasku na prethodni prikaz: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Neočekivana greška pri prelasku na ServicerDashboardView: {0}", e.getMessage());
        }
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
