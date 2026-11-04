package org.unibl.etf.mdp.vservice.client.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;

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
import org.unibl.etf.mdp.vservice.client.model.User;
import org.unibl.etf.mdp.vservice.client.model.Vehicle;
import org.unibl.etf.mdp.vservice.client.util.AppLogger;

public class RegisterViewController {

    private static final Logger LOGGER = AppLogger.getLogger();

    private Stage stage;
    private Scene scene;

    @FXML
    TextField tfFirstName, tfLastName, tfAddress, tfEmail, tfUsername, tfPhone;

    // New fields for Vehicle information
    @FXML
    TextField tfVehicleMake, tfVehicleModel, tfVehicleYear, tfVehicleRegNumber;

    @FXML
    PasswordField pfPassword, pfPassword2;

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

    // Registration by calling the REST endpoint if all fields are valid
    public void register(ActionEvent event) {
        String firstName = tfFirstName.getText();
        String lastName = tfLastName.getText();
        String address = tfAddress.getText();
        String email = tfEmail.getText();
        String username = tfUsername.getText();
        String phone = tfPhone.getText();
        String password = pfPassword.getText();
        String password2 = pfPassword2.getText();

        // Vehicle fields (can be empty if no vehicle is registered)
        String vehicleMake = tfVehicleMake.getText();
        String vehicleModel = tfVehicleModel.getText();
        String vehicleYearStr = tfVehicleYear.getText();
        String vehicleRegNumber = tfVehicleRegNumber.getText();

        LOGGER.log(Level.INFO, "Pokušaj registracije za korisnika: {0}", username);

        // Basic validation for required fields
        if(firstName.length() < 1 || lastName.length() < 1 || address.length() < 1 || email.length() < 1 || username.length() < 1
                || phone.length() < 1 || password.length() < 1 || password2.length() < 1) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje o registraciji", "Sva glavna polja (Ime, Prezime, Adresa, E-mail, Korisničko ime, Telefon, Lozinka) su obavezna!");
            LOGGER.warning("Neuspješna registracija: Nisu popunjena sva obavezna polja.");
            return; // Exit if validation fails
        }

        if(password.compareTo(password2) != 0) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje o lozinkama", "Lozinke se ne podudaraju!");
            LOGGER.warning("Neuspješna registracija: Lozinke se ne podudaraju za korisnika: " + username);
            return; // Exit if passwords don't match
        }

        // Handle Vehicle creation if data is provided
        Vehicle vehicle = null;
        if (!vehicleMake.isEmpty() || !vehicleModel.isEmpty() || !vehicleYearStr.isEmpty() || !vehicleRegNumber.isEmpty()) {
            try {
                int vehicleYear = Integer.parseInt(vehicleYearStr);
                vehicle = new Vehicle(vehicleMake, vehicleModel, vehicleYear, vehicleRegNumber);
                LOGGER.info("Kreiran Vehicle objekat za korisnika: " + username);
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Greška godine vozila", "Nevažeća godina vozila. Molimo unesite ispravan broj.");
                LOGGER.log(Level.WARNING, "Neispravan format godine vozila za korisnika {0}: {1}", new Object[]{username, e.getMessage()});
                return;
            }
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(Main.PATH + "/users/register");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Create a User object with the new constructor (assuming "pending" status by default for registration)
            User user = new User(username, password, firstName, lastName, address, phone, email, vehicle, "pending");

            Gson gson = new Gson();
            String jsonUser = gson.toJson(user);
            LOGGER.log(Level.FINE, "JSON korisnika za registraciju: {0}", jsonUser);

            try (OutputStream outputStream = conn.getOutputStream()) {
                outputStream.write(jsonUser.getBytes("UTF-8"));
            }

            int statusCode = conn.getResponseCode();

            if(statusCode == 200) {
                showAlert(Alert.AlertType.INFORMATION, "Uspješna registracija", "Registracija uspješna!");
                LOGGER.log(Level.INFO, "Registracija korisnika {0} uspješna. HTTP Status: {1}", new Object[]{username, statusCode});
                // Optionally clear fields or navigate to login view
            } else {
                showAlert(Alert.AlertType.ERROR, "Neuspješna registracija", "Registracija neuspješna! Kod greške: " + statusCode);
                LOGGER.log(Level.WARNING, "Registracija korisnika {0} neuspješna. HTTP Status: {1}", new Object[]{username, statusCode});
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Registracija neuspješna! Greška u komunikaciji: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Greška u komunikaciji prilikom registracije korisnika {0}: {1}", new Object[]{username, e.getMessage()});
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void toLoginView(ActionEvent event) {
        LOGGER.info("Prelazak na LoginView.");
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/client/view/LoginView.fxml"));
            stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri učitavanju LoginView.fxml prilikom prelaska: {0}", e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Neočekivana greška pri prelasku na LoginView: {0}", e.getMessage());
        }
    }
}
