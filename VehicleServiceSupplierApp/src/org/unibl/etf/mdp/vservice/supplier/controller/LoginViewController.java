package org.unibl.etf.mdp.vservice.supplier.controller;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert; 
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.application.Platform; 

import org.unibl.etf.mdp.vservice.supplier.util.AppLogger;

public class LoginViewController {

    private static final Logger LOGGER = AppLogger.getLogger();

    private Stage stage;
    private Scene scene;

    @FXML
    TextField nameField;

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> { 
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null); 
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void login(ActionEvent event) {
        String enteredName = nameField.getText();
        LOGGER.log(Level.INFO, "Pokušaj prijave sa korisničkim imenom: {0}", enteredName);

        if(enteredName.length() < 1) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje za prijavu", "Polje ne može biti prazno!");
            LOGGER.warning("Pokušaj prijave sa praznim poljem za korisničko ime.");
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader("suppliers.txt"))) {
                String line;
                boolean loggedIn = false;
                while ((line = reader.readLine()) != null) {
                    if(line.compareTo(enteredName) == 0) {
                        SupplierDashboardViewController.supplierName = line;
                        LOGGER.info("Prijavljen dobavljač: " + line);
                        Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/supplier/view/SupplierDashboardView.fxml"));
                        stage = (Stage)((Node)event.getSource()).getScene().getWindow();
                        scene = new Scene(root);
                        stage.setScene(scene);
                        stage.show();
                        loggedIn = true;
                        break;
                    }
                }
                if (!loggedIn) {
                    showAlert(Alert.AlertType.ERROR, "Greška pri prijavi", "Dobavljač ne postoji!");
                    LOGGER.warning("Neuspješna prijava. Dobavljač '" + enteredName + "' ne postoji u suppliers.txt.");
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Greška pri čitanju suppliers.txt fajla ili učitavanju SupplierDashboardView.fxml: " + e.getMessage(), e);
                showAlert(Alert.AlertType.ERROR, "Greška pri čitanju podataka", "Greška pri čitanju podataka dobavljača ili učitavanju kontrolne table.");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Neočekivana greška prilikom prijave: " + e.getMessage(), e);
                showAlert(Alert.AlertType.ERROR, "Neočekivana greška", "Dogodila se neočekivana greška tokom prijave.");
            }
        }
    }
}
