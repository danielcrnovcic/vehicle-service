package org.unibl.etf.mdp.vservice.client.controller;

import org.unibl.etf.mdp.vservice.client.model.*;
import org.unibl.etf.mdp.vservice.client.util.AppLogger;
import org.unibl.etf.mdp.vservice.client.util.LocalDateTimeAdapter;
import org.unibl.etf.mdp.vservice.client.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.client.util.ChatClient; 

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TabPane;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ClientDashboardViewController implements Initializable {

    public static String loggedInUsername;
    private static final String BASE_URL = PropertiesUtil.getProperty("BASE_URL");

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    @FXML
    private TabPane mainTabPane;

    // Zakazivanje termina tab
    @FXML
    private DatePicker appointmentDatePicker;
    @FXML
    private ComboBox<String> appointmentTimePicker;
    @FXML
    private ComboBox<String> serviceTypeComboBox;

    // Pregled istorije servisa tab
    @FXML
    private TableView<Appointment> historyTable;
    @FXML
    private TableColumn<Appointment, LocalDateTime> historyDateTimeColumn;
    @FXML
    private TableColumn<Appointment, String> historyServiceTypeColumn;
    @FXML
    private TableColumn<Appointment, String> historyStatusColumn;
    @FXML
    private TableColumn<Appointment, String> historyDescriptionColumn;
    @FXML
    private TableColumn<Appointment, String> historyCommentColumn;

    // Chat tab FXML elementi
    @FXML
    private Tab chatTab;
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField chatMessageField;
    @FXML
    private ComboBox<String> messageTypeComboBox;
    @FXML
    private Label recipientLabel;
    @FXML
    private TextField recipientField;

    // NOVI FXML element za grupne komande
    @FXML
    private TextField groupNameField; // Polje za unos imena grupe

    // Polja za chat komunikaciju
    private ChatClient chatClient; 
    private String chatServerAddress;
    private int chatServerPort;
    private String servicerUsername; // Dodato: korisničko ime servisera
    
    @FXML
    private Label welcomeLabel; // pozdravna labela za chat tab

    @Override
    public void initialize(URL location, ResourceBundle resources) {
	
	// Postaviti tekst dobrodošlice OVDJE jer je loggedInUsername već dostupan
        if (loggedInUsername != null && !loggedInUsername.isEmpty()) {
            welcomeLabel.setText("Dobrodošli, " + loggedInUsername + "!");
        } else {
            welcomeLabel.setText("Dobrodošli, Klijenate!"); // Fallback ako ime nije dostupno
        }
        
        ObservableList<String> times = FXCollections.observableArrayList();
        for (int i = 8; i <= 18; i++) {
            times.add(String.format("%02d:00", i));
        }
        appointmentTimePicker.setItems(times);

        serviceTypeComboBox.setItems(FXCollections.observableArrayList("Redovni servis", "Popravka"));

        historyDateTimeColumn.setCellValueFactory(new PropertyValueFactory<>("dateTime"));
        historyServiceTypeColumn.setCellValueFactory(new PropertyValueFactory<>("serviceType"));
        historyStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        historyDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        historyCommentColumn.setCellValueFactory(new PropertyValueFactory<>("comment"));

        mainTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null && newTab.getText().equals("Pregled istorije servisa")) {
                loadServiceHistory();
            }
        });

        initializeChatTab();
        
        // Ovo je ranije bilo u setLoggedInUser() metodi
        chatArea.appendText("Dobrodošli, " + loggedInUsername + "!\n");
        loadServiceHistory(); // Pozovite ovo
        connectToChatServer(); // <-- KLJUČNA IZMENA: Pozivam povezivanje na chat ovde!
    }

//    public void setLoggedInUser(String username) {
////        this.loggedInUsername = username;
//        chatArea.appendText("Dobrodošli, " + loggedInUsername + "!\n");
//        loadServiceHistory();
//        connectToChatServer();
//    }

    private void initializeChatTab() {
	// Preuzmi ime servisera iz propertija
        servicerUsername = PropertiesUtil.getProperty("SERVICER_USERNAME"); // Default na "Serviser"
        
        ObservableList<String> messageTypes = FXCollections.observableArrayList("Broadcast", "Unicast", "Multicast","Serviser");
        messageTypeComboBox.setItems(messageTypes);
        messageTypeComboBox.getSelectionModel().selectFirst();//default je broadcast

        messageTypeComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            boolean isRecipientNeeded = "Unicast".equals(newValue) || "Multicast".equals(newValue) || "Serviser".equals(newValue);;
            recipientLabel.setVisible(isRecipientNeeded);
            recipientField.setVisible(isRecipientNeeded);
            
            if ("Serviser".equals(newValue)) {
                recipientField.setText(servicerUsername); // Automatski popuni ime servisera
                recipientField.setEditable(false); // Onemogući uređivanje
            } else {
                recipientField.clear(); // Očisti polje
                recipientField.setEditable(true); // Omogući uređivanje
            }
        });

        chatServerAddress = PropertiesUtil.getProperty("CHAT_SERVER_HOST");
        chatServerPort = Integer.parseInt(PropertiesUtil.getProperty("CHAT_SERVER_PORT"));

        if (chatTab != null) {
            chatTab.setOnClosed(event -> closeChatConnection());
        }
    }

    private void connectToChatServer() {
        if (loggedInUsername == null || loggedInUsername.isEmpty()) {
            AppLogger.getLogger().warning("Pokušaj povezivanja na chat bez postavljenog korisničkog imena.");
            chatArea.appendText("Greška: Korisničko ime nije postavljeno za chat.\n");
            return;
        }

        Consumer<String> messageHandler = message -> {
            Platform.runLater(() -> chatArea.appendText(message + "\n"));
        };

        chatClient = new ChatClient(loggedInUsername, messageHandler); // Ime klase je ChatClient
        try {
            chatClient.connect(chatServerAddress, chatServerPort);
            chatArea.appendText("Povezani ste na chat server kao: " + loggedInUsername + "\n");
            AppLogger.getLogger().info("Klijent " + loggedInUsername + " povezan na chat server.");
        } catch (IOException e) {
            Platform.runLater(() -> chatArea.appendText("Greška pri povezivanju na chat server: " + e.getMessage() + "\n"));
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri povezivanju klijenta na chat server", e);
            chatMessageField.setDisable(true);
            messageTypeComboBox.setDisable(true);
            recipientField.setDisable(true);
            groupNameField.setDisable(true); // Onemogući i group field

        }
    }

    @FXML
    protected void handleScheduleAppointmentAction(ActionEvent event) {
        LocalDate date = appointmentDatePicker.getValue();
        String time = appointmentTimePicker.getSelectionModel().getSelectedItem();
        String serviceType = serviceTypeComboBox.getSelectionModel().getSelectedItem();

        if (date == null || time == null || serviceType == null) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite datum, vrijeme i vrstu usluge.");
            return;
        }

        LocalDateTime appointmentDateTime = LocalDateTime.of(date, LocalTime.parse(time));

        Appointment newAppointment = new Appointment();
        newAppointment.setUserId(loggedInUsername);
        newAppointment.setDateTime(appointmentDateTime);
        newAppointment.setServiceType(serviceType);
        newAppointment.setStatus("zakazano");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int responseCode = sendHttpRequest(BASE_URL + "/appointments/schedule", "POST", gson.toJson(newAppointment));

                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Termin je uspješno zakazan!");
                        AppLogger.getLogger().info("Klijent " + loggedInUsername + " zakazao termin za " + appointmentDateTime);
                        loadServiceHistory();
                    });
                } else {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Termin nije mogao biti zakazan. Status: " + responseCode));
                    AppLogger.getLogger().warning("Klijent " + loggedInUsername + " neuspješno zakazao termin za " + appointmentDateTime + ". Status: " + responseCode);
                }
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri zakazivanju termina", e);
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za zakazivanje."));
            }
        });
    }

    private void loadServiceHistory() {
        if (loggedInUsername == null || loggedInUsername.isEmpty()) {
            AppLogger.getLogger().warning("Pokušaj učitavanja historije servisa bez korisničkog imena.");
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String urlString = BASE_URL + "/appointments/history/" + loggedInUsername;
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                    conn.disconnect();

                    List<Appointment> history = parseAppointments(content.toString());

                    Platform.runLater(() -> {
                        if (history != null) {
                            historyTable.setItems(FXCollections.observableArrayList(history));
                            AppLogger.getLogger().info("Učitana historija servisa za korisnika: " + loggedInUsername);
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće učitati historiju servisa.");
                            AppLogger.getLogger().warning("Nije moguće učitati historiju servisa za korisnika: " + loggedInUsername);
                        }
                    });
                } else {
                    AppLogger.getLogger().warning("Greška pri dohvatanju historije servisa. Status: " + responseCode);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće učitati historiju servisa. Status: " + responseCode));
                }
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri dohvatanju historije servisa", e);
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za historiju."));
            }
        });
    }

    @FXML
    protected void handleSendMessageAction(ActionEvent event) {
        String message = chatMessageField.getText();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String selectedType = messageTypeComboBox.getSelectionModel().getSelectedItem();
        String recipient = recipientField.getText(); // Primalac može biti korisnik, grupa ili serviser

        if (chatClient != null && chatClient.isConnected()) {
            try {
                switch (selectedType) {
                    case "Broadcast":
                        chatClient.sendBroadcastMessage(message);
                        chatArea.appendText("Ja (Broadcast): " + message + "\n");
                        break;
                    case "Unicast":
                        if (recipient == null || recipient.trim().isEmpty()) {
                            chatArea.appendText("Molimo unesite ime primaoca za Unicast poruku.\n");
                            return;
                        }
                        chatClient.sendUnicastMessage(recipient.trim(), message);
                        chatArea.appendText("Ja (Unicast ka " + recipient.trim() + "): " + message + "\n");
                        break;
                    case "Multicast":
                        if (recipient == null || recipient.trim().isEmpty()) {
                            chatArea.appendText("Molimo unesite ime grupe za Multicast poruku.\n");
                            return;
                        }
                        chatClient.sendMulticastMessage(recipient.trim(), message);
                        chatArea.appendText("Ja (Multicast ka grupi " + recipient.trim() + "): " + message + "\n");
                        break;
                    case "Serviser": // NOVI CASE ZA KOMUNIKACIJU SA SERVISEROM
                        // Primalac je automatski postavljen na servicerUsername
                        chatClient.sendUnicastMessage(servicerUsername, message);
                        chatArea.appendText("Ja (ka serviseru " + servicerUsername + "): " + message + "\n");
                        break;
                    default:
                        chatArea.appendText("Nepoznat tip poruke.\n");
                        break;
                }
                chatMessageField.clear();
                AppLogger.getLogger().info("Poslata chat poruka od " + loggedInUsername + " (" + selectedType + "): " + message);
            } catch (Exception e) {
                Platform.runLater(() -> chatArea.appendText("Greška pri slanju poruke: " + e.getMessage() + "\n"));
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri slanju chat poruke", e);
            }
        } else {
            chatArea.appendText("Niste povezani na chat server ili konekcija nije aktivna.\n");
            AppLogger.getLogger().warning("Pokušaj slanja chat poruke bez aktivne konekcije.");
        }
    }

    // NOVE METODE ZA OBRADU GRUPNIH KOMANDI
    @FXML
    protected void handleJoinGroupAction(ActionEvent event) {
        String groupName = groupNameField.getText();
        if (groupName == null || groupName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo unesite ime grupe kojoj se želite pridružiti.");
            return;
        }

        if (chatClient != null && chatClient.isConnected()) {
            try {
                chatClient.joinGroup(groupName.trim());
                chatArea.appendText("Poslato serveru: Pridruživanje grupi '" + groupName.trim() + "'.\n");
                AppLogger.getLogger().info(loggedInUsername + " pokušava da se pridruži grupi: " + groupName.trim());
            } catch (Exception e) { // Hvata IOException iz sendMessage
                Platform.runLater(() -> chatArea.appendText("Greška pri slanju JOIN komande: " + e.getMessage() + "\n"));
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri slanju JOIN komande", e);
            }
        } else {
            chatArea.appendText("Niste povezani na chat server.\n");
            AppLogger.getLogger().warning(loggedInUsername + " pokušao da se pridruži grupi bez aktivne chat konekcije.");
        }
        groupNameField.clear(); // Očisti polje nakon slanja komande
    }

    @FXML
    protected void handleLeaveGroupAction(ActionEvent event) {
        String groupName = groupNameField.getText();
        if (groupName == null || groupName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo unesite ime grupe koju želite napustiti.");
            return;
        }

        if (chatClient != null && chatClient.isConnected()) {
            try {
                chatClient.leaveGroup(groupName.trim());
                chatArea.appendText("Poslato serveru: Napuštanje grupe '" + groupName.trim() + "'.\n");
                AppLogger.getLogger().info(loggedInUsername + " pokušava da napusti grupu: " + groupName.trim());
            } catch (Exception e) { // Hvata IOException iz sendMessage
                Platform.runLater(() -> chatArea.appendText("Greška pri slanju LEAVE komande: " + e.getMessage() + "\n"));
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri slanju LEAVE komande", e);
            }
        } else {
            chatArea.appendText("Niste povezani na chat server.\n");
            AppLogger.getLogger().warning(loggedInUsername + " pokušao da napusti grupu bez aktivne chat konekcije.");
        }
        groupNameField.clear(); // Očisti polje nakon slanja komande
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private int sendHttpRequest(String urlString, String method, String requestBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        if (requestBody != null && !requestBody.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
        }

        int responseCode = conn.getResponseCode();
        conn.disconnect();
        return responseCode;
    }

    private List<Appointment> parseAppointments(String jsonString) {
        try {
            AppLogger.getLogger().info(jsonString);
            return gson.fromJson(jsonString, new TypeToken<List<Appointment>>() {}.getType());
        } catch (com.google.gson.JsonSyntaxException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri parsiranju JSON-a za termine: " + jsonString, e);
            return null;
        }
    }

    public void closeChatConnection() {
        if (chatClient != null) {
            chatClient.close();
            AppLogger.getLogger().info("Chat konekcija zatvorena.");
            AppLogger.getLogger().info("Chat konekcija zatvorena za korisnika: " + loggedInUsername);
        }
    }
}