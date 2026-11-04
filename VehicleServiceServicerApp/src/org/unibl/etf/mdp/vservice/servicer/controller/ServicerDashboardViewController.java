package org.unibl.etf.mdp.vservice.servicer.controller;

import org.unibl.etf.mdp.vservice.servicer.util.AppLogger;
import org.unibl.etf.mdp.vservice.servicer.model.User; 
import org.unibl.etf.mdp.vservice.servicer.model.Appointment; 
import org.unibl.etf.mdp.vservice.servicer.model.Part; 
import org.unibl.etf.mdp.vservice.servicer.util.LocalDateTimeAdapter; // Custom adapter for LocalDateTime
import org.unibl.etf.mdp.vservice.servicer.util.PropertiesUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map; // For parsing parts if they are Map<String,String>
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ServicerDashboardViewController {

    private static final String BASE_URL = PropertiesUtil.getProperty("BASE_URL");
    private static final String APPOINTMENTS_URL = BASE_URL + "/appointments";
    private static final String USERS_URL = BASE_URL + "/users";
    private static final String PARTS_URL = BASE_URL + "/parts";
    private static final String INVOICES_URL = BASE_URL + "/invoices"; // URL za fakture


    // Gson instance with LocalDateTime adapter for consistent date/time parsing
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    @FXML private TabPane mainTabPane;

    // Upravljanje klijentima tab
    @FXML private TableView<User> clientsTable;
    @FXML private TableColumn<User, String> clientUsernameColumn;
    @FXML private TableColumn<User, String> clientFirstNameColumn;
    @FXML private TableColumn<User, String> clientLastNameColumn;
    @FXML private TableColumn<User, String> clientStatusColumn; // Updated to String for status
    private Stage chatStage = null; // Referenca na prozor za ćaskanje


    // Upravljanje terminima tab
    @FXML private TableView<Appointment> appointmentsTable;
    @FXML private TableColumn<Appointment, String> apptUserIdColumn;
    @FXML private TableColumn<Appointment, LocalDateTime> apptDateTimeColumn;
    @FXML private TableColumn<Appointment, String> apptServiceTypeColumn;
    @FXML private TableColumn<Appointment, String> apptStatusColumn;
    @FXML private TableColumn<Appointment, String> apptCommentColumn;
    @FXML private TextField appointmentCommentField;
    
 // Lista utrošenih dijelova za trenutno odabrani termin (pomoćna lista)
    private ObservableList<Part> currentUsedParts = FXCollections.observableArrayList();
    // Mapa za privremeno držanje dodatih dijelova za trenutni termin prije spremanja
    // Ključ je šifra dijela, vrijednost je Part objekat sa ažuriranom količinom
    private Map<String, Part> partsToAddToAppointment = new LinkedHashMap<>();
    
    @FXML private ComboBox<Part> partComboBox;
    @FXML private TextField partQuantityUsedField;
    @FXML private TableView<Part> usedPartsTable;
    @FXML private TableColumn<Part, String> usedPartCodeColumn;
    @FXML private TableColumn<Part, String> usedPartNameColumn;
    @FXML private TableColumn<Part, Integer> usedPartQuantityColumn;
    @FXML private TableColumn<Part, Void> usedPartRemoveColumn; // Kolona za dugme "Ukloni"
    @FXML private TableColumn<Appointment, String> apptUsedPartsSummaryColumn; // Sumarni prikaz utrošenih dijelova

    // Upravljanje dijelovima tab
    @FXML private TableView<Part> partsTable;
    @FXML private TableColumn<Part, String> partCodeColumn;
    @FXML private TableColumn<Part, String> partNameColumn;
    @FXML private TableColumn<Part, String> partManufacturerColumn;
    @FXML private TableColumn<Part, Double> partPriceColumn;
    @FXML private TableColumn<Part, Integer> partQuantityColumn;
    @FXML private TableColumn<Part, String> partDescriptionColumn;
    @FXML private TextField partCodeField;
    @FXML private TextField partNameField;
    @FXML private TextField partManufacturerField;
    @FXML private TextField partPriceField;
    @FXML private TextField partQuantityField;
    @FXML private TextArea partDescriptionArea;
 
    private Stage stage;
    private Scene scene;

    @FXML
    public void initialize() {
        // Inicijalizacija tabele klijenata
        clientUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        clientFirstNameColumn.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        clientLastNameColumn.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        // Assuming your User model has a 'status' field (e.g., "active", "pending", "blocked")
        clientStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status")); 

        // Inicijalizacija tabele termina
        apptUserIdColumn.setCellValueFactory(new PropertyValueFactory<>("userId"));
        apptDateTimeColumn.setCellValueFactory(new PropertyValueFactory<>("dateTime"));
        apptServiceTypeColumn.setCellValueFactory(new PropertyValueFactory<>("serviceType"));
        apptStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        apptCommentColumn.setCellValueFactory(new PropertyValueFactory<>("comment"));
        
     // Inicijalizacija kolone za utrošene dijelove u tabeli termina
        apptUsedPartsSummaryColumn.setCellValueFactory(cellData -> {
            Appointment appointment = cellData.getValue();
            // Kreiraj string sume dijelova, npr. "Dio1 (2kom), Dio2 (1kom)"
            if (appointment.getUsedParts() != null && !appointment.getUsedParts().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Part part : appointment.getUsedParts()) {
                    sb.append(part.getName()).append(" (").append(part.getQuantity()).append(" kom), ");
                }
                // Ukloni zadnji zarez i razmak
                return new javafx.beans.property.SimpleStringProperty(sb.substring(0, sb.length() - 2));
            }
            return new javafx.beans.property.SimpleStringProperty("Nema utrošenih dijelova");
        });


        // Inicijalizacija tabele utrošenih dijelova
        usedPartCodeColumn.setCellValueFactory(new PropertyValueFactory<>("code"));
        usedPartNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        usedPartQuantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        usedPartsTable.setItems(currentUsedParts); // Poveži ObservableListu sa tabelom

        // Dodavanje dugmeta za uklanjanje u kolonu 'usedPartRemoveColumn'
        usedPartRemoveColumn.setCellFactory(param -> new TableCell<Part, Void>() {
            private final Button removeButton = new Button("Ukloni");

            {
                removeButton.setOnAction(event -> {
                    Part part = getTableView().getItems().get(getIndex());
                    if (part != null) {
                        partsToAddToAppointment.remove(part.getCode()); // Ukloni iz privremene mape
                        currentUsedParts.remove(part); // Ukloni iz ObservableListe (što ažurira tabelu)
                        AppLogger.getLogger().info("Uklonjen dio " + part.getName() + " iz privremene liste.");
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(removeButton);
                }
            }
        });
        
     

        // Inicijalizacija tabele dijelova
        partCodeColumn.setCellValueFactory(new PropertyValueFactory<>("code"));
        partNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        partManufacturerColumn.setCellValueFactory(new PropertyValueFactory<>("manufacturer"));
        partPriceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        partQuantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        partDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Učitaj početne podatke
        loadClients();
        loadAppointments();
        loadParts();
        
        // --- DODATO: Učitaj dijelove i za ComboBox pri pokretanju aplikacije ---
        loadPartsForComboBox();

        // Listener za promjenu taba
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null && newTab.getText().equals("Upravljanje klijentima")) {
                loadClients();
            } else if (newTab != null && newTab.getText().equals("Upravljanje terminima")) {
                loadAppointments();
                // --- DODATO: Osvježi dijelove za ComboBox kada se otvori ovaj tab ---
                loadPartsForComboBox();
            } else if (newTab != null && newTab.getText().equals("Upravljanje dijelovima")) {
                loadParts();
            }
        });

        // Listener za odabir termina u tabeli
        appointmentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                appointmentCommentField.setText(newSelection.getComment());
            } else {
                appointmentCommentField.clear();
            }
        });

        // Listener za odabir dijela u tabeli za uređivanje
        partsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                partCodeField.setText(newSelection.getCode());
                partNameField.setText(newSelection.getName());
                partManufacturerField.setText(newSelection.getManufacturer());
                partPriceField.setText(String.valueOf(newSelection.getPrice()));
                partQuantityField.setText(String.valueOf(newSelection.getQuantity()));
                partDescriptionArea.setText(newSelection.getDescription());
            } else {
                clearPartFields();
            }
        });
    }

    // --- Metode za upravljanje klijentima ---
    private void loadClients() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String jsonResponse = sendGetRequest(USERS_URL); // Assuming GET /users returns all users
                List<User> clients = parseUsers(jsonResponse);
                javafx.application.Platform.runLater(() -> {
                    if (clients != null) {
                        clientsTable.setItems(FXCollections.observableArrayList(clients));
                        AppLogger.getLogger().info("Serviser učitao listu klijenata.");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće učitati listu klijenata.");
                    }
                });
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri dohvatanju klijenata", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za klijente."));
            }
        });
    }

    @FXML
    protected void handleApproveClientAction(ActionEvent event) {
        User selectedClient = clientsTable.getSelectionModel().getSelectedItem();
        if (selectedClient != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // Assuming a PUT endpoint for user approval like /users/approve/{username}
                    int responseCode = sendPutRequest(USERS_URL + "/approve/" + selectedClient.getUsername(), null);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Nalog klijenta " + selectedClient.getUsername() + " je odobren.");
                            AppLogger.getLogger().info("Serviser odobrio nalog klijenta: " + selectedClient.getUsername());
                            loadClients(); // Osvježi tabelu
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće odobriti nalog. Status: " + responseCode));
                        AppLogger.getLogger().warning("Nije uspjelo odobrenje naloga " + selectedClient.getUsername() + ". Status: " + responseCode);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri odobravanju klijenta", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite klijenta za odobrenje.");
        }
    }

    @FXML
    protected void handleBlockClientAction(ActionEvent event) {
        User selectedClient = clientsTable.getSelectionModel().getSelectedItem();
        if (selectedClient != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    
                    int responseCode = sendPutRequest(USERS_URL + "/block/" + selectedClient.getUsername(), null);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Status blokiranja klijenta " + selectedClient.getUsername() + " je promijenjen.");
                            AppLogger.getLogger().info("Serviser promijenio status blokiranja klijenta: " + selectedClient.getUsername());
                            loadClients(); // Osvježi tabelu
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće promijeniti status blokiranja. Status: " + responseCode));
                        AppLogger.getLogger().warning("Nije uspjela promjena statusa blokiranja za " + selectedClient.getUsername() + ". Status: " + responseCode);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri blokiranju klijenta", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite klijenta za blokiranje/deblokiranje.");
        }
    }

    @FXML
    protected void handleDeleteClientAction(ActionEvent event) {
        User selectedClient = clientsTable.getSelectionModel().getSelectedItem();
        if (selectedClient != null) {
            Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationAlert.setTitle("Potvrda brisanja");
            confirmationAlert.setHeaderText("Brisanje klijenta");
            confirmationAlert.setContentText("Da li ste sigurni da želite obrisati klijenta " + selectedClient.getUsername() + "?");

            Optional<ButtonType> result = confirmationAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        // Assuming a DELETE endpoint for users like /users/{username}
                        int responseCode = sendDeleteRequest(USERS_URL + "/" + selectedClient.getUsername());
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            javafx.application.Platform.runLater(() -> {
                                showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Klijent " + selectedClient.getUsername() + " je obrisan.");
                                AppLogger.getLogger().info("Serviser obrisao klijenta: " + selectedClient.getUsername());
                                loadClients(); // Osvježi tabelu
                            });
                        } else {
                            javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće obrisati klijenta. Status: " + responseCode));
                            AppLogger.getLogger().warning("Nije uspjelo brisanje klijenta " + selectedClient.getUsername() + ". Status: " + responseCode);
                        }
                    } catch (IOException e) {
                        AppLogger.getLogger().log(Level.SEVERE, "Greška pri brisanju klijenta", e);
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                    }
                });
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite klijenta za brisanje.");
        }
    }
    
    public void openChatWindow(ActionEvent event) {
        if (chatStage == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/unibl/etf/mdp/vservice/servicer/view/ServicerChatView.fxml"));
                Parent root = loader.load();

                ServicerChatViewController chatController = loader.getController();

                chatStage = new Stage();
                chatStage.setTitle("Serviser Panel");
                chatStage.setScene(new Scene(root, 600, 400));

                // Osiguraj zatvaranje klijenta kada se zatvori prozor za ćaskanje
                chatStage.setOnCloseRequest(e -> {
                    chatController.closeChatConnection();
                    chatStage = null; // Postavi referencu na null kada se prozor zatvori
                });

                chatStage.show();

            } catch (IOException e) {
                e.printStackTrace();
                // Obradi grešku pri učitavanju FXML-a
            }
        } else {
            // Ako prozor za ćaskanje već postoji, samo ga dovedi u fokus
            chatStage.toFront();
        }
    }

    // --- Metode za upravljanje terminima ---
    private void loadAppointments() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String jsonResponse = sendGetRequest(APPOINTMENTS_URL); // GET /appointments
                List<Appointment> appointments = parseAppointments(jsonResponse);
                javafx.application.Platform.runLater(() -> {
                    if (appointments != null) {
                        appointmentsTable.setItems(FXCollections.observableArrayList(appointments));
                        AppLogger.getLogger().info("Serviser učitao listu termina.");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće učitati termine.");
                    }
                });
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri učitavanju termina", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za termine."));
            }
        });
    }
    
    // Za odobravanje termina (nema tijela zahtjeva)
    @FXML
    protected void handleApproveAppointmentAction(ActionEvent event) {
        Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
        if (selectedAppointment != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // PUT /appointments/confirm/{appointmentId}
                    // Drugi argument je null jer nema tijela
                    int responseCode = sendPutRequest(APPOINTMENTS_URL + "/confirm/" + selectedAppointment.getId(), null);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Termin je odobren.");
                            AppLogger.getLogger().info("Serviser odobrio termin: " + selectedAppointment.getId());
                            loadAppointments(); // Osvježi tabelu
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće odobriti termin. Status: " + responseCode));
                        AppLogger.getLogger().warning("Nije uspjelo odobrenje termina " + selectedAppointment.getId() + ". Status: " + responseCode);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri odobravanju termina", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite termin za odobrenje.");
        }
    }
    
    // Za odbijanje termina (nema tijela zahtjeva)
    @FXML
    protected void handleRejectAppointmentAction(ActionEvent event) {
        Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
        if (selectedAppointment != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // PUT /appointments/reject/{appointmentId}
                    // Drugi argument je null jer nema tijela
                    int responseCode = sendPutRequest(APPOINTMENTS_URL + "/reject/" + selectedAppointment.getId(), null );
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Termin je odbijen.");
                            AppLogger.getLogger().info("Serviser odbio termin: " + selectedAppointment.getId());
                            loadAppointments(); // Osvježi tabelu
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće odbiti termin. Status: " + responseCode));
                        AppLogger.getLogger().warning("Nije uspjelo odbijanje termina " + selectedAppointment.getId() + ". Status: " + responseCode);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri odbijanju termina", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite termin za odbijanje.");
        }
    }

    @FXML
    protected void handleAddCommentAction(ActionEvent event) {
        Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
        if (selectedAppointment != null) {
            String comment = appointmentCommentField.getText();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // PUT /appointments/comment/{appointmentId} with comment in body
                    int responseCode = sendPutRequest(APPOINTMENTS_URL + "/comment/" + selectedAppointment.getId(), comment);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Komentar je dodan terminu.");
                            AppLogger.getLogger().info("Dodan komentar terminu " + selectedAppointment.getId() + ": " + comment);
                            loadAppointments(); // Osvježi tabelu
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće dodati komentar. Status: " + responseCode));
                        AppLogger.getLogger().warning("Nije uspjelo dodavanje komentara terminu " + selectedAppointment.getId() + ". Status: " + responseCode);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri dodavanju komentara", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite termin za dodavanje komentara.");
        }
    }
    
    //nove metode
    
    private void loadPartsForComboBox() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String jsonResponse = sendGetRequest(PARTS_URL);
                List<Part> parts = parseParts(jsonResponse);
                javafx.application.Platform.runLater(() -> {
                    if (parts != null) {
                        partComboBox.setItems(FXCollections.observableArrayList(parts));
                        // Postavi prikaz dijelova u ComboBoxu
                        partComboBox.setConverter(new javafx.util.StringConverter<Part>() {
                            @Override
                            public String toString(Part part) {
                                return part != null ? part.getName() + " (" + part.getCode() + ")" : "";
                            }

                            @Override
                            public Part fromString(String string) {
                                return null; // Ne koristi se za ovaj scenario
                            }
                        });
                        AppLogger.getLogger().info("Serviser učitao dijelove za ComboBox.");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće učitati dijelove za odabir.");
                    }
                });
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri učitavanju dijelova za ComboBox", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za dijelove."));
            }
        });
    }

    private void clearUsedPartsTablesAndFields() {
        currentUsedParts.clear();
        partsToAddToAppointment.clear();
        partComboBox.getSelectionModel().clearSelection();
        partQuantityUsedField.clear();
    }


    @FXML
    protected void handleAddPartToAppointmentAction(ActionEvent event) {
        Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
        Part selectedPart = partComboBox.getSelectionModel().getSelectedItem();
        String quantityText = partQuantityUsedField.getText();

        if (selectedAppointment == null) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite termin kojem želite dodati dijelove.");
            return;
        }
        if (selectedPart == null) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite dio.");
            return;
        }
        if (quantityText.isEmpty() || !quantityText.matches("\\d+")) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo unesite validnu količinu (cijeli broj).");
            return;
        }

        int quantity = Integer.parseInt(quantityText);
        if (quantity <= 0) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Količina mora biti veća od nule.");
            return;
        }
        if (quantity > selectedPart.getQuantity()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Tražena količina (" + quantity + ") je veća od dostupne na stanju (" + selectedPart.getQuantity() + ").");
            return;
        }

        // Provjeri da li je dio već dodat za ovaj termin u privremenu listu
        Part existingPart = partsToAddToAppointment.get(selectedPart.getCode());
        if (existingPart != null) {
            // Ažuriraj količinu ako dio već postoji
            existingPart.setQuantity(existingPart.getQuantity() + quantity);
        } else {
            // Dodaj novi dio (kloniraj ga da ne mijenjaš originalni objekat iz ComboBoxa)
            Part partToAdd = new Part(selectedPart.getCode(), selectedPart.getName(), selectedPart.getManufacturer(), selectedPart.getPrice(), quantity, selectedPart.getDescription());
            partsToAddToAppointment.put(partToAdd.getCode(), partToAdd);
        }

        // Ažuriraj ObservableListu koja je povezana sa usedPartsTable
        currentUsedParts.setAll(partsToAddToAppointment.values());
        partQuantityUsedField.clear();
        AppLogger.getLogger().info("Privremeno dodan dio " + selectedPart.getName() + " (količina: " + quantity + ") terminu " + selectedAppointment.getId());
    }
    
    @FXML
    protected void handleCompleteServiceAction(ActionEvent event) {
        Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
        if (selectedAppointment == null) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite termin za završetak servisa.");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Potvrda završetka servisa");
        confirmAlert.setHeaderText("Završetak servisa za termin " + selectedAppointment.getId());
        confirmAlert.setContentText("Da li ste sigurni da želite završiti servis i spremiti utrošene dijelove?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // 1. Ažuriraj utrošene dijelove na serveru (PUT na /appointments/{id}/parts)
                    List<Part> partsToSubmit = new ArrayList<>(partsToAddToAppointment.values());
                    String jsonParts = gson.toJson(partsToSubmit);

                    URL partsUrl = new URL(APPOINTMENTS_URL + "/" + selectedAppointment.getId() + "/parts");
                    HttpURLConnection partsConn = (HttpURLConnection) partsUrl.openConnection();
                    partsConn.setRequestMethod("PUT");
                    partsConn.setDoOutput(true);
                    partsConn.setRequestProperty("Content-Type", "application/json");

                    try (OutputStream os = partsConn.getOutputStream()) {
                        byte[] input = jsonParts.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    int partsResponseCode = partsConn.getResponseCode();
                    String partsResponse = readResponse(partsConn); // Koristi ugrađenu readResponse

                    if (partsResponseCode == HttpURLConnection.HTTP_OK) {
                        // 2. Završi servis (PUT na /appointments/{id}/complete)
                        URL completeUrl = new URL(APPOINTMENTS_URL + "/complete/" + selectedAppointment.getId());
                        HttpURLConnection completeConn = (HttpURLConnection) completeUrl.openConnection();
                        completeConn.setRequestMethod("PUT");
                        completeConn.setDoOutput(true); // Može biti potrebno za prazno tijelo
                        completeConn.setRequestProperty("Content-Type", "application/json"); // Često se šalje i za prazno tijelo

                        // Nema tijela za slanje, ali neka os.write bude ovdje ako endpoint to traži (prazan JSON)
                        try (OutputStream os = completeConn.getOutputStream()) {
                             byte[] input = "{}".getBytes("utf-8"); // Možete poslati prazan JSON objekat
                             os.write(input, 0, input.length);
                        }

                        int completeResponseCode = completeConn.getResponseCode();
                        String completeResponse = readResponse(completeConn); // Koristimo ugrađenu readResponse

                        if (completeResponseCode == HttpURLConnection.HTTP_OK) {
                            javafx.application.Platform.runLater(() -> {
                                showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Servis je uspješno završen i dijelovi su spremljeni.");
                                AppLogger.getLogger().info("Servis završen i dijelovi spremljeni za termin: " + selectedAppointment.getId());
                                loadAppointments(); // Osvježi tabelu termina
                                clearUsedPartsTablesAndFields(); // Očisti UI
                            });
                        } else {
                            javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće završiti servis. Status: " + completeResponseCode + ", Greška: " + completeResponse));
                            AppLogger.getLogger().warning("Nije uspjelo završavanje servisa za termin " + selectedAppointment.getId() + ". Status: " + completeResponseCode + ", Greška: " + completeResponse);
                        }
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće spremiti utrošene dijelove. Status: " + partsResponseCode + ", Greška: " + partsResponse));
                        AppLogger.getLogger().warning("Nije uspjelo spremanje utrošenih dijelova za termin " + selectedAppointment.getId() + ". Status: " + partsResponseCode + ", Greška: " + partsResponse);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri završetku servisa i spremanju dijelova", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        }
    }
    
    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        } catch (IOException e) {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                return errorResponse.toString();
            } catch (Exception ex) {
                return "Nije moguće pročitati odgovor.";
            }
        }
    }


    @FXML
    protected void handleCreateInvoiceAction(ActionEvent event) {
        Appointment selectedAppointment = appointmentsTable.getSelectionModel().getSelectedItem();
        if (selectedAppointment == null) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite termin za kreiranje fakture.");
            return;
        }

        if (!"zavrseno".equalsIgnoreCase(selectedAppointment.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Faktura se može kreirati samo za završene termine.");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null; // Deklarirajte conn van try bloka da bude dostupan u finally
            try {
                URL url = new URL(INVOICES_URL + "/generate-and-send/" + selectedAppointment.getId());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                // Slanje praznog JSON tijela, kao što je bilo i prije
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = "{}".getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // SERVER VRAĆA TEKSTUALNU PORUKU, NE ZIP FAJL
                    // Čitamo odgovor servera kao tekst
                    String serverResponse;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder responseBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                        serverResponse = responseBuilder.toString();
                    }

                    javafx.application.Platform.runLater(() -> {
                        // Obavještavamo korisnika da je faktura kreirana/poslana na serveru
                        // i prikazujemo poruku koju je server vratio.
                        showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Faktura je uspješno kreirana i poslana (na serveru).\nPoruka servera: " + serverResponse);
                        AppLogger.getLogger().info("Serviser pokrenuo kreiranje i slanje fakture za termin: " + selectedAppointment.getId());
                    });

                } else {
                    // Obrada greške kao i prije
                    String errorResponse;
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                        StringBuilder errorBuilder = new StringBuilder();
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorBuilder.append(errorLine);
                        }
                        errorResponse = errorBuilder.toString();
                    } catch (Exception ex) {
                        errorResponse = "Nije moguće pročitati odgovor o grešci.";
                    }

                    String finalErrorResponse = errorResponse;

                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće kreirati fakturu. Status: " + responseCode + ", Greška: " + finalErrorResponse));
                    AppLogger.getLogger().warning("Nije uspjelo kreiranje fakture za termin " + selectedAppointment.getId() + ". Status: " + responseCode + ", Greška: " + errorResponse);
                }
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri komunikaciji sa serverom za fakture", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za fakture."));
            } finally {
                if (conn != null) {
                    conn.disconnect(); // Osiguraj da je konekcija zatvorena
                }
            }
        });
    }

    // --- Metode za upravljanje dijelovima (CRUD) ---
    private void loadParts() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String jsonResponse = sendGetRequest(PARTS_URL); // GET /parts
                List<Part> parts = parseParts(jsonResponse); 
                javafx.application.Platform.runLater(() -> {
                    if (parts != null) {
                        partsTable.setItems(FXCollections.observableArrayList(parts));
                        AppLogger.getLogger().info("Serviser učitao listu dijelova.");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće učitati listu dijelova.");
                    }
                });
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri učitavanju dijelova", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis za dijelove."));
            }
        });
    }

    @FXML
    protected void handleAddPartAction(ActionEvent event) {
        if (partCodeField.getText().isEmpty() || partNameField.getText().isEmpty() ||
            partPriceField.getText().isEmpty() || partQuantityField.getText().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo popunite sva obavezna polja za dio.");
            return;
        }

        Part newPart = new Part();
        newPart.setCode(partCodeField.getText());
        newPart.setName(partNameField.getText());
        newPart.setManufacturer(partManufacturerField.getText());
        try {
            newPart.setPrice(Double.parseDouble(partPriceField.getText()));
            newPart.setQuantity(Integer.parseInt(partQuantityField.getText()));
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Greška", "Cijena i količina moraju biti brojevi.");
            AppLogger.getLogger().log(Level.WARNING, "Neispravan format za cijenu ili količinu dijela.", e);
            return;
        }
        newPart.setDescription(partDescriptionArea.getText());

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // POST /parts
                int responseCode = sendPostRequest(PARTS_URL, gson.toJson(newPart));
                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) { // 201 Created or 200 OK
                    javafx.application.Platform.runLater(() -> {
                        showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Dio je uspješno dodan.");
                        AppLogger.getLogger().info("Dodan novi dio: " + newPart.getCode());
                        loadParts();
                        clearPartFields();
                    });
                } else {
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće dodati dio. Status: " + responseCode));
                    AppLogger.getLogger().warning("Nije uspjelo dodavanje dijela " + newPart.getCode() + ". Status: " + responseCode);
                }
            } catch (IOException e) {
                AppLogger.getLogger().log(Level.SEVERE, "Greška pri dodavanju dijela", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
            }
        });
    }

    @FXML
    protected void handleUpdatePartAction(ActionEvent event) {
        Part selectedPart = partsTable.getSelectionModel().getSelectedItem();
        if (selectedPart != null) {
            if (partCodeField.getText().isEmpty() || partNameField.getText().isEmpty() ||
                partPriceField.getText().isEmpty() || partQuantityField.getText().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo popunite sva obavezna polja za dio.");
                return;
            }

            // Update selectedPart with new values from fields
            selectedPart.setCode(partCodeField.getText());
            selectedPart.setName(partNameField.getText());
            selectedPart.setManufacturer(partManufacturerField.getText());
            try {
                selectedPart.setPrice(Double.parseDouble(partPriceField.getText()));
                selectedPart.setQuantity(Integer.parseInt(partQuantityField.getText()));
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Greška", "Cijena i količina moraju biti brojevi.");
                AppLogger.getLogger().log(Level.WARNING, "Neispravan format za cijenu ili količinu dijela prilikom ažuriranja.", e);
                return;
            }
            selectedPart.setDescription(partDescriptionArea.getText());

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // PUT /parts/{code}
                    int responseCode = sendPutRequest(PARTS_URL + "/" + selectedPart.getCode(), gson.toJson(selectedPart));
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Dio je uspješno ažuriran.");
                            AppLogger.getLogger().info("Ažuriran dio: " + selectedPart.getCode());
                            loadParts();
                            clearPartFields();
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće ažurirati dio. Status: " + responseCode));
                        AppLogger.getLogger().warning("Nije uspjelo ažuriranje dijela " + selectedPart.getCode() + ". Status: " + responseCode);
                    }
                } catch (IOException e) {
                    AppLogger.getLogger().log(Level.SEVERE, "Greška pri ažuriranju dijela", e);
                    javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                }
            });
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite dio za ažuriranje.");
        }
    }

    @FXML
    protected void handleDeletePartAction(ActionEvent event) {
        Part selectedPart = partsTable.getSelectionModel().getSelectedItem();
        if (selectedPart != null) {
            Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationAlert.setTitle("Potvrda brisanja");
            confirmationAlert.setHeaderText("Brisanje dijela");
            confirmationAlert.setContentText("Da li ste sigurni da želite obrisati dio " + selectedPart.getName() + " (" + selectedPart.getCode() + ")?");

            Optional<ButtonType> result = confirmationAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        // DELETE /parts/{code}
                        int responseCode = sendDeleteRequest(PARTS_URL + "/" + selectedPart.getCode());
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            javafx.application.Platform.runLater(() -> {
                                showAlert(Alert.AlertType.INFORMATION, "Uspjeh", "Dio je uspješno obrisan.");
                                AppLogger.getLogger().info("Obrisan dio: " + selectedPart.getCode());
                                loadParts();
                                clearPartFields();
                            });
                        } else {
                            javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće obrisati dio. Status: " + responseCode));
                            AppLogger.getLogger().warning("Nije uspjelo brisanje dijela " + selectedPart.getCode() + ". Status: " + responseCode);
                        }
                    } catch (IOException e) {
                        AppLogger.getLogger().log(Level.SEVERE, "Greška pri brisanju dijela", e);
                        javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Greška u komunikaciji", "Nije moguće kontaktirati servis."));
                    }
                });
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Molimo odaberite dio za brisanje.");
        }
    }

    private void clearPartFields() {
        partCodeField.clear();
        partNameField.clear();
        partManufacturerField.clear();
        partPriceField.clear();
        partQuantityField.clear();
        partDescriptionArea.clear();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Helper method to send HTTP POST requests.
     * @param urlString The URL to send the request to.
     * @param requestBody The JSON request body string.
     * @return The HTTP response code.
     * @throws IOException If an I/O error occurs.
     */
    private int sendPostRequest(String urlString, String requestBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        conn.disconnect();
        return responseCode;
    }

    /**
     * Helper method to send HTTP PUT requests.
     * @param urlString The URL to send the request to.
     * @param requestBody The JSON request body string (can be empty for simple PUTs).
     * @return The HTTP response code.
     * @throws IOException If an I/O error occurs.
     */
    private int sendPutRequest(String urlString, String requestBody) throws IOException {
	    URL url = new URL(urlString);
	    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	    conn.setRequestMethod("PUT");

	    // Standardno prihvatamo JSON, ali za /comment/ ćemo slati text/plain
	    // Uvijek postavite Accept zaglavlje za ono što očekujete kao odgovor
	    conn.setRequestProperty("Accept", "text/plain, application/json"); // Prihvati oba tipa odgovora

	    // Postavite setDoOutput na true samo ako postoji requestBody
	    // Ovo je ključno za zahtjeve bez tijela (kao što su confirm/reject)
	    boolean hasBody = (requestBody != null && !requestBody.isEmpty());
	    conn.setDoOutput(hasBody);

	    if (hasBody) {
	        // Logika za postavljanje Content-Type
	        if (urlString.contains("/comment/")) {
	            conn.setRequestProperty("Content-Type", "text/plain");
	        } else {
	            // Ako u budućnosti imate PUT zahtjeve s JSON tijelom, ovo je ispravno
	            conn.setRequestProperty("Content-Type", "application/json");
	        }

	        try (OutputStream os = conn.getOutputStream()) {
	            byte[] input = requestBody.getBytes("utf-8");
	            os.write(input, 0, input.length);
	        }
	    } else {
	        // Ako nema tijela, osiguraj da Content-Type nije postavljen
	        // ili da se Content-Length postavi na 0
	        // Neki serveri mogu biti striktni. Uklanjanje Content-Type je često bolje.
	        // Conn.setRequestProperty("Content-Length", "0"); // Ovo je opcionalno, ali može pomoći
	    }

	    int responseCode = conn.getResponseCode();
	    conn.disconnect();
	    return responseCode;
	}

    /**
     * Helper method to send HTTP DELETE requests.
     * @param urlString The URL to send the request to.
     * @return The HTTP response code.
     * @throws IOException If an I/O error occurs.
     */
    private int sendDeleteRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.connect(); // Connect to send the DELETE request

        int responseCode = conn.getResponseCode();
        conn.disconnect();
        return responseCode;
    }

    /**
     * Helper method to send HTTP GET requests and retrieve response body.
     * @param urlString The URL to send the request to.
     * @return The response body as a String, or null if an error occurs.
     * @throws IOException If an I/O error occurs.
     */
    private String sendGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json"); // Expecting JSON response

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                return content.toString();
            }
        } else {
            AppLogger.getLogger().warning("GET request failed for URL: " + urlString + ", Status: " + responseCode);
            // You might want to read error stream for more details
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String errorLine;
                StringBuilder errorContent = new StringBuilder();
                while ((errorLine = errorReader.readLine()) != null) {
                    errorContent.append(errorLine);
                }
                AppLogger.getLogger().warning("Error response: " + errorContent.toString());
            } catch (Exception e) {
                // Ignore if error stream cannot be read
            }
            return null;
        } 
    }

    /**
     * Helper method to parse a JSON string into a list of Appointment objects.
     * @param jsonString The JSON string to parse.
     * @return A list of Appointment objects.
     */
    private List<Appointment> parseAppointments(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            return gson.fromJson(jsonString, new TypeToken<List<Appointment>>(){}.getType());
        } catch (com.google.gson.JsonSyntaxException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri parsiranju JSON-a za termine: " + jsonString, e);
            return null;
        }
    }

    /**
     * Helper method to parse a JSON string into a list of User objects.
     * @param jsonString The JSON string to parse.
     * @return A list of User objects.
     */
    private List<User> parseUsers(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            return gson.fromJson(jsonString, new TypeToken<List<User>>(){}.getType());
        } catch (com.google.gson.JsonSyntaxException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri parsiranju JSON-a za klijente: " + jsonString, e);
            return null;
        }
    }

    /**
     * Helper method to parse a JSON string into a single User object.
     * @param jsonString The JSON string to parse.
     * @return A User object.
     */
    private User parseUser(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            return gson.fromJson(jsonString, User.class);
        } catch (com.google.gson.JsonSyntaxException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri parsiranju JSON-a za pojedinačnog klijenta: " + jsonString, e);
            return null;
        }
    }

    /**
     * Helper method to parse a JSON string into a list of Part objects.
     * Assumes Part objects are directly deserializable by Gson.
     * @param jsonString The JSON string to parse.
     * @return A list of Part objects.
     */
    private List<Part> parseParts(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            return gson.fromJson(jsonString, new TypeToken<List<Part>>(){}.getType());
        } catch (com.google.gson.JsonSyntaxException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri parsiranju JSON-a za dijelove: " + jsonString, e);
            return null;
        }
    }
    
    public void openOrderWindow(ActionEvent event) throws IOException {
	
	Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/servicer/view/OrderView.fxml"));
	stage = (Stage)((Node)event.getSource()).getScene().getWindow();
	scene = new Scene(root);
	stage.setScene(scene);
	stage.show();
}
}