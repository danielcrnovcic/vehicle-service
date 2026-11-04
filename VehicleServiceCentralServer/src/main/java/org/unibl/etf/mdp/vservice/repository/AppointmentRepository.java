package org.unibl.etf.mdp.vservice.repository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level; 
import java.util.stream.Collectors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.unibl.etf.mdp.vservice.model.Appointment;
import org.unibl.etf.mdp.vservice.model.Part;
import org.unibl.etf.mdp.vservice.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.util.AppLogger; 

public class AppointmentRepository {

    public static File appointmentsPath;
    private static JSONArray jsonArray = null;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Statički inicijalizacioni blok
    static {
        try {
            // Pozivamo statičku metodu initializePath
            initializePath();
        } catch (IOException e) {
            // Ovdje je kritično logovati grešku jer neuspjeh inicijalizacije putanje
            // znači da repozitorijum neće moći raditi.
            // Bacamo ExceptionInInitializerError jer je ovo fatalna greška pri učitavanju klase.
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri inicijalizaciji putanje za termine: " + e.getMessage(), e);
            throw new ExceptionInInitializerError("Nije moguće inicijalizovati putanju za termine. Aplikacija ne može nastaviti.");
        }
    }

    private static void initializePath() throws IOException { // Promijenjeno u private static
        appointmentsPath = new File(PropertiesUtil.getProperty("APPOINTMENTS_PATH"));

        // Provjeriti da li fajl postoji
        if (!appointmentsPath.exists()) {
            // Kreiraj fajl ako ne postoji
            boolean created = appointmentsPath.createNewFile();
            if (created) {
                try (FileWriter writer = new FileWriter(appointmentsPath)) {
                    writer.write("[]"); // Inicijalizuj sa praznim JSON nizom
                    AppLogger.getLogger().info("Kreiran novi fajl za termine i inicijalizovan sa [] na putanji: " + appointmentsPath.getAbsolutePath());
                }
            } else {
                AppLogger.getLogger().warning("Fajl za termine već postoji ili ga nije moguće kreirati: " + appointmentsPath.getAbsolutePath());
            }
        } else {
            AppLogger.getLogger().info("Fajl za termine pronađen na putanji: " + appointmentsPath.getAbsolutePath());
        }
    }

    private Gson getGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
    }

    public boolean scheduleAppointment(Appointment appointment) throws IOException {
        if (appointment != null) {
            Gson gson = getGson();
            if (appointment.getId() == null || appointment.getId().isEmpty()) {
                appointment.setId(UUID.randomUUID().toString());
            }
            appointment.setStatus("zakazano"); // Default status for new appointments

            String jsonString = gson.toJson(appointment);
            JSONObject jsonObject = (JSONObject) JSONValue.parse(jsonString);

            jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
            if (jsonArray == null) {
                jsonArray = new JSONArray();
            }

            jsonArray.add(jsonObject);
            writeJsonArrayToFile(jsonArray, appointmentsPath.getAbsolutePath());
            return true;
        }
        return false;
    }

    public List<Appointment> getAllAppointments() throws IOException {
        List<Appointment> appointments = new ArrayList<>();
        jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
        Gson gson = getGson();

        if (jsonArray == null) {
            return appointments;
        }

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonAppointment = (JSONObject) jsonArray.get(i);
            appointments.add(gson.fromJson(jsonAppointment.toJSONString(), Appointment.class));
        }
        return appointments;
    }

    public Optional<Appointment> findById(String appointmentId) throws IOException {
        List<Appointment> allAppointments = getAllAppointments(); // Get all appointments
        return allAppointments.stream()
                .filter(a -> a.getId().equals(appointmentId))
                .findFirst(); // Returns an Optional
    }

    public List<Appointment> getAppointmentsByUserId(String userId) throws IOException {
        return getAllAppointments().stream()
                .filter(a -> a.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public boolean confirmAppointment(String appointmentId) throws IOException {
        jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
        if (jsonArray == null) return false;

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonAppointment = (JSONObject) jsonArray.get(i);
            if (jsonAppointment.get("id").toString().equals(appointmentId)) {
                if ("zakazano".equals(jsonAppointment.get("status"))) {
                    jsonAppointment.put("status", "potvrdjeno");
                    writeJsonArrayToFile(jsonArray, appointmentsPath.getAbsolutePath());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean rejectAppointment(String appointmentId) throws IOException {
        jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
        if (jsonArray == null) return false;

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonAppointment = (JSONObject) jsonArray.get(i);
            if (jsonAppointment.get("id").toString().equals(appointmentId)) {
                if ("zakazano".equals(jsonAppointment.get("status"))) {
                    jsonAppointment.put("status", "odbijeno");
                    writeJsonArrayToFile(jsonArray, appointmentsPath.getAbsolutePath());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean addCommentToAppointment(String appointmentId, String comment) throws IOException {
        jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
        if (jsonArray == null) return false;

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonAppointment = (JSONObject) jsonArray.get(i);
            if (jsonAppointment.get("id").toString().equals(appointmentId)) {
                jsonAppointment.put("comment", comment);
                writeJsonArrayToFile(jsonArray, appointmentsPath.getAbsolutePath());
                return true;
            }
        }
        return false;
    }

    public boolean addUsedPartsToAppointment(String appointmentId, List<Part> partsUsed) throws IOException {
        jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
        if (jsonArray == null) return false;

        Gson gson = getGson(); // Za serijalizaciju Part objekata

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonAppointment = (JSONObject) jsonArray.get(i);
            if (jsonAppointment.get("id").toString().equals(appointmentId)) {
                // Postojeća lista dijelova (ako je ima)
                List<Part> existingUsedParts = new ArrayList<>();
                if (jsonAppointment.containsKey("usedParts")) {
                    // Ponovo parsiramo postojeće dijelove
                    existingUsedParts = gson.fromJson(jsonAppointment.get("usedParts").toString(), new com.google.gson.reflect.TypeToken<List<Part>>(){}.getType());
                }

                // Dodati nove dijelove na postojeću listu
                for (Part newPart : partsUsed) {
                    boolean found = false;
                    for (Part existingPart : existingUsedParts) {
                        if (existingPart.getCode().equals(newPart.getCode())) {
                            existingPart.setQuantity(existingPart.getQuantity() + newPart.getQuantity()); // Povećaj količinu
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        existingUsedParts.add(newPart); // Dodaj novi dio
                    }
                }

                // Sačuvati ažuriranu listu nazad u JSON objekt
                jsonAppointment.put("usedParts", gson.toJsonTree(existingUsedParts).getAsJsonArray());

                writeJsonArrayToFile(jsonArray, appointmentsPath.getAbsolutePath());
                return true;
            }
        }
        return false;
    }

    public boolean completeAppointment(String appointmentId) throws IOException {
        jsonArray = readJsonArrayFromFile(appointmentsPath.getAbsolutePath());
        if (jsonArray == null) return false;

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonAppointment = (JSONObject) jsonArray.get(i);
            if (jsonAppointment.get("id").toString().equals(appointmentId)) {
                String currentStatus = (String) jsonAppointment.get("status");
                if ("potvrdjeno".equals(currentStatus) || "u toku".equals(currentStatus) || "zakazano".equals(currentStatus)) {
                    jsonAppointment.put("status", "zavrseno");
                    writeJsonArrayToFile(jsonArray, appointmentsPath.getAbsolutePath());
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    public static JSONArray readJsonArrayFromFile(String fileName) {
        try (FileReader fileReader = new FileReader(fileName)) {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(fileReader);
            return (JSONArray) obj;
        } catch (FileNotFoundException e) {
            // Ako fajl ne postoji, vraćamo prazan JSONArray
            // (ili null, ali prazan niz je često pogodniji za dalju obradu)
            AppLogger.getLogger().log(Level.INFO, "Fajl nije pronađen, vraćam prazan JSON niz: " + fileName);
            return new JSONArray();
        } catch (IOException | ParseException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri čitanju JSON fajla: " + fileName, e);
            return null; // Vraćamo null u slučaju greške parsiranja/IO
        }
    }

    private static void writeJsonArrayToFile(JSONArray jsonArray, String fileName) {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(jsonArray.toJSONString());
            fileWriter.flush();
        } catch (IOException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Greška pri pisanju JSON fajla: " + fileName, e);
        }
    }

    // Custom Gson adapter for LocalDateTime
    static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>, com.google.gson.JsonDeserializer<LocalDateTime> {
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
            return LocalDateTime.parse(json.getAsString(), FORMATTER);
        }
    }
}
