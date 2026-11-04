package org.unibl.etf.mdp.vservice.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.unibl.etf.mdp.vservice.model.Part;
import org.unibl.etf.mdp.vservice.repository.PartRepository;
import org.unibl.etf.mdp.vservice.util.AppLogger;
import org.unibl.etf.mdp.vservice.util.PropertiesUtil;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Path("/parts")
public class PartService {
    
    private static final String SUPPLIER_SERVER_HOST = PropertiesUtil.getProperty("SUPPLIER_SERVER_HOST");
    private static final int SUPPLIER_SERVER_PORT = PropertiesUtil.getIntProperty("SUPPLIER_SERVER_PORT"); 
    
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response getParts() {
        List<Map<String, String>> allPartsAsMaps = PartRepository.getAllParts();

        List<Part> parts = new ArrayList<>();

        if (allPartsAsMaps != null) {
            for (Map<String, String> partMap : allPartsAsMaps) {
                try {
                    //Važna izmjena: Provjeriti da li ključna polja postoje i nisu prazna
                    String code = partMap.get("code");
                    String name = partMap.get("name");
                    String manufacturer = partMap.get("manufacturer");
                    String description = partMap.get("description"); // Opcionalno, ali je dobro provjeriti

                    // Ako nedostaje bilo koji od ključnih identifikatora, preskočiti ovaj dio
                    if (code == null || code.trim().isEmpty() ||
                        name == null || name.trim().isEmpty() ||
                        manufacturer == null || manufacturer.trim().isEmpty()) {
                        AppLogger.getLogger().log(Level.WARNING, "Preskačem nepotpuni unos dijela (nedostaju 'code', 'name' ili 'manufacturer'): " + partMap);
                        continue; // Ide se na sljedeću mapu u listi
                    }

                    Part part = new Part();
                    part.setCode(code);
                    part.setName(name);
                    part.setManufacturer(manufacturer);

                    // Parsiranje Cijene: sigurno rukovanje NumberFormatException
                    if (partMap.containsKey("price")) {
                        try {
                            part.setPrice(Double.parseDouble(partMap.get("price"))); // Pretpostavljamo '.' kao decimalni separator
                        } catch (NumberFormatException e) {
                            AppLogger.getLogger().log(Level.WARNING, "Greška pri parsiranju cijene za dio '" + name + "': " + partMap.get("price") + ". Postavljam na 0.0.", e);
                            part.setPrice(0.0); // Postavi default vrijednost ako parsiranje ne uspije
                        }
                    } else {
                        part.setPrice(0.0); // Postavi default ako polje "price" nedostaje
                    }

                    // Parsiranje Količine: sigurno rukovanje NumberFormatException
                    if (partMap.containsKey("quantity")) {
                        try {
                            part.setQuantity(Integer.parseInt(partMap.get("quantity")));
                        } catch (NumberFormatException e) {
                            AppLogger.getLogger().log(Level.WARNING, "Greška pri parsiranju količine za dio '" + name + "': " + partMap.get("quantity") + ". Postavljam na 0.", e);
                            part.setQuantity(0); // Postavi default vrijednost ako parsiranje ne uspije
                        }
                    } else {
                        part.setQuantity(0); // Postavi default ako polje "quantity" nedostaje
                    }

                    // Postavljanje opisa (ako je null, postavi prazan string)
                    part.setDescription(description != null ? description : "");

                    parts.add(part);
                } catch (Exception e) {
                    // Loguje se greška koja se može desiti tokom konverzije
                    AppLogger.getLogger().log(Level.SEVERE, "Neočekivana greška pri konverziji Map u Part objekt: " + partMap, e);
                }
            }
        }

        if (!parts.isEmpty()) { // Provjeriti da li lista sadrži dijelove
            return Response.status(200).entity(parts).build();
        } else {
            // Vraća 204 No Content ako nema pronađenih dijelova nakon filtriranja
            return Response.status(204).entity("Nema pronađenih dijelova ili su svi nekompletni.").build();
        }
    }
    @DELETE
    @Path("/{code}")
    public Response deletePart(@PathParam("code") String partCode) {
	// Prvo brišem iz Redis baze, to je primarni izvor podataka
	    PartRepository.deletePartDb(partCode);

	    // Zatim ažuriram in-memory keš. Najsigurnije je ponovo učitati sve iz Redisa.
	    // Ovo će takođe osigurati da PartRepository.parts više nije null (ako je ikada bio).
	    PartRepository.getAllParts();

	    // Sada možemo ukloniti iz in-memory keša ako i dalje želimo,
	    // mada getAllParts() već ponovo puni keš sa svježim podacima.
	    // Ako želimo da budemo sigurni da je element uklonjen iz lokalne liste,
	    // iako bi getAllParts() to trebao da uradi:
	    if (PartRepository.parts != null) { // Iako sada ne bi trebalo biti null
	        PartRepository.parts.removeIf(part -> part.get("code").equals(partCode));//ako se ovo pozove prije metode getAllParts dobijam NPE zato sto prije nisam inicijalizovao parts prije poziva getAllParts metode
	    }

	    return Response.status(200).entity(PartRepository.parts).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addPart(Part part) { 
        if (part == null || part.getCode() == null || part.getCode().trim().isEmpty()) {
            return Response.status(400).entity("Part data or code is missing.").build();
        }

        // Provjeriti da li je quantity u dolaznom Part objektu validan za narudžbu
        if (part.getQuantity() <= 0) {
            return Response.status(400).entity("Quantity for ordered part must be greater than zero.").build();
        }

        PartRepository.addOrUpdatePart(part); 

        return Response.status(201).entity(part).build(); 
    }
    @PUT
    @Path("/{code}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePart(@PathParam("code") String partCode, Part part) {

        if (part == null || part.getCode() == null || !part.getCode().equals(partCode)) {
            return Response.status(400).entity("Part data is invalid or code mismatch.").build();
        }

        PartRepository.updatePart(part);

        PartRepository.getAllParts();

        return Response.status(200).entity(part).build();
    }
    
    @GET
    @Path("/supplier")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response getPartsFromSupplier() {
	System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            // Pošalji komandu GET_PARTS dobavljač serveru
            out.println("GET_PARTS");
            AppLogger.getLogger().info("Sent GET_PARTS request to supplier server.");

            // Pročitaj odgovor
            String jsonResponse = in.readLine();
            AppLogger.getLogger().info("Received response from supplier server: " + jsonResponse);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return Response.status(500).entity("Empty response from supplier server.").build();
            }

            // Parsiraj JSON u listu Part objekata
            Gson gson = new Gson();
            Part[] partsArray = gson.fromJson(jsonResponse, Part[].class);
            List<Part> supplierParts = Arrays.asList(partsArray);

            return Response.status(200).entity(supplierParts).build();

        } catch (IOException e) {
            AppLogger.getLogger().severe("Error communicating with supplier server: " + e.getMessage());
            return Response.status(500).entity("Failed to connect to supplier server or receive parts: " + e.getMessage()).build();
        } catch (JsonSyntaxException e) {
            AppLogger.getLogger().severe("Error parsing JSON response from supplier server: " + e.getMessage());
            return Response.status(500).entity("Invalid JSON response from supplier server: " + e.getMessage()).build();
        }
    }

}
