package org.unibl.etf.mdp.vservice.client.controller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert; 
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.application.Platform; 

import org.unibl.etf.mdp.vservice.client.main.Main;
import org.unibl.etf.mdp.vservice.client.util.AppLogger;

public class LoginViewController {

    private static final Logger LOGGER = AppLogger.getLogger();

    private Stage stage;
    private Scene scene;

    @FXML
    TextField username;

    @FXML
    PasswordField password;

    // Helper method to display Alert dialogs
    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> { 
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null); 
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Login attempt by calling REST endpoint
    public void login(ActionEvent event) {

        String userNameText = username.getText();
        String passwordText = password.getText();

        LOGGER.log(Level.INFO, "Pokušaj prijave za korisnika: {0}", userNameText);

        if(userNameText.length() < 1 || passwordText.length() < 1) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje za prijavu", "Sva polja su obavezna!");
            LOGGER.warning("Pokušaj prijave sa praznim poljima.");
        } else {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Main.PATH + "/users/login/" + userNameText + "/" + passwordText);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");

                int statusCode = conn.getResponseCode();

                if(statusCode == HttpURLConnection.HTTP_OK) {
                    ClientDashboardViewController.loggedInUsername = userNameText;
                    LOGGER.log(Level.INFO, "Korisnik {0} uspješno prijavljen. HTTP Status: {1}", new Object[]{userNameText, statusCode});
                    // Navigate to ClientDashboardView on successful login
                    Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/client/view/ClientDashboardView.fxml"));
                    stage = (Stage)((Node)event.getSource()).getScene().getWindow();
                    scene = new Scene(root);
                    stage.setScene(scene);
                    stage.show();
                }
                else {
                    showAlert(Alert.AlertType.ERROR, "Greška pri prijavi", "Prijava neuspješna! Provjerite korisničko ime i lozinku.");
                    LOGGER.log(Level.WARNING, "Prijava za korisnika {0} neuspješna. HTTP Status: {1}", new Object[]{userNameText, statusCode});
                }
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Prijava neuspješna! Greška u komunikaciji: " + e.getMessage());
                String errorMessage = "Greška u komunikaciji prilikom prijave korisnika " + userNameText + ": " + e.getMessage();
                LOGGER.log(Level.SEVERE, errorMessage, e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    public void toRegisterView(ActionEvent event) {
        LOGGER.info("Prelazak na RegisterView.");
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/client/view/RegisterView.fxml"));
            stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri učitavanju RegisterView.fxml prilikom prelaska: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Neočekivana greška pri prelasku na RegisterView: " + e.getMessage(), e);
        }
    }
}
