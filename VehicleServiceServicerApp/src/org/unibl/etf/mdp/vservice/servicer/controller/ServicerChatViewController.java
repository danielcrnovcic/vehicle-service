package org.unibl.etf.mdp.vservice.servicer.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import org.unibl.etf.mdp.vservice.servicer.util.AppLogger; 
import org.unibl.etf.mdp.vservice.servicer.util.ChatClient;
import org.unibl.etf.mdp.vservice.servicer.util.PropertiesUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ServicerChatViewController implements Initializable {

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

    private ChatClient chatClient;
    private String chatServerAddress;
    private int chatServerPort;
    private String mechanicUsername;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        mechanicUsername = PropertiesUtil.getProperty("SERVICER_USERNAME");
        chatServerAddress = PropertiesUtil.getProperty("CHAT_SERVER_HOST");
        chatServerPort = Integer.parseInt(PropertiesUtil.getProperty("CHAT_SERVER_PORT"));

        ObservableList<String> messageTypes = FXCollections.observableArrayList("Broadcast", "Odgovori klijentu");
        messageTypeComboBox.setItems(messageTypes);
        messageTypeComboBox.getSelectionModel().selectFirst(); // Default je Broadcast

        messageTypeComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            boolean isRecipientNeeded = "Odgovori klijentu".equals(newValue);
            recipientLabel.setVisible(isRecipientNeeded);
            recipientField.setVisible(isRecipientNeeded);
            recipientField.clear(); // Očisti polje prilikom promjene tipa
        });

        connectToChatServer();
    }

    private void connectToChatServer() {
        // Serviser se uvijek povezuje sa svojim fiksnim imenom
        Consumer<String> messageHandler = message -> {
            Platform.runLater(() -> chatArea.appendText(message + "\n"));
        };

        chatClient = new ChatClient(mechanicUsername, messageHandler);
        try {
            chatClient.connect(chatServerAddress, chatServerPort);
            chatArea.appendText("Povezani ste na chat server kao: " + mechanicUsername + "\n");
            AppLogger.getLogger().info("Serviser " + mechanicUsername + " povezan na chat server.");
        } catch (IOException e) {
            Platform.runLater(() -> chatArea.appendText("Greška pri povezivanju na chat server: " + e.getMessage() + "\n"));
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri povezivanju servisera na chat server", e);
            chatMessageField.setDisable(true);
            messageTypeComboBox.setDisable(true);
            recipientField.setDisable(true);
        }
    }

    @FXML
    protected void handleSendMessageAction(ActionEvent event) {
        String message = chatMessageField.getText();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String selectedType = messageTypeComboBox.getSelectionModel().getSelectedItem();
        String recipient = recipientField.getText();

        if (chatClient != null && chatClient.isConnected()) {
            try {
                switch (selectedType) {
                    case "Broadcast":
                        chatClient.sendBroadcastMessage(message);
                        chatArea.appendText("Ja (Broadcast svima): " + message + "\n");
                        break;
                    case "Odgovori klijentu":
                        if (recipient == null || recipient.trim().isEmpty()) {
                            chatArea.appendText("Molimo unesite ime klijenta kojem odgovarate.\n");
                            return;
                        }
                        chatClient.sendUnicastMessage(recipient.trim(), message);
                        chatArea.appendText("Ja (Odgovor klijentu " + recipient.trim() + "): " + message + "\n");
                        break;
                    default:
                        chatArea.appendText("Nepoznat tip poruke.\n");
                        break;
                }
                chatMessageField.clear();
                AppLogger.getLogger().info("Serviser " + mechanicUsername + " poslao poruku (" + selectedType + "): " + message);
            } catch (Exception e) {
                Platform.runLater(() -> chatArea.appendText("Greška pri slanju poruke: " + e.getMessage() + "\n"));
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri slanju chat poruke od servisera", e);
            }
        } else {
            chatArea.appendText("Niste povezani na chat server ili konekcija nije aktivna.\n");
            AppLogger.getLogger().warning("Serviser " + mechanicUsername + " pokušao slati poruku bez aktivne konekcije.");
        }
    }

    public void closeChatConnection() {
        if (chatClient != null) {
            chatClient.close();
            AppLogger.getLogger().info("Serviser chat konekcija zatvorena.");
            AppLogger.getLogger().info("Serviser chat konekcija zatvorena za korisnika: " + mechanicUsername);
        }
    }
}
