package org.unibl.etf.mdp.vservice.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.unibl.etf.mdp.vservice.model.Part; 
import org.unibl.etf.mdp.vservice.model.Order; 
import org.unibl.etf.mdp.vservice.util.AppLogger;
import org.unibl.etf.mdp.vservice.util.OrderStatusUtil;
import org.unibl.etf.mdp.vservice.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.repository.PartRepository;

import com.google.gson.Gson; // Koristiti Gson za JSON serijalizaciju

@Path("/orders")
public class OrderService {

    // Konfiguracija dobavljač servera
    private static final String SUPPLIER_SERVER_HOST = PropertiesUtil.getProperty("SUPPLIER_SERVER_HOST"); 
    private static final int SUPPLIER_SERVER_PORT = PropertiesUtil.getIntProperty("SUPPLIER_SERVER_PORT");
    
    private static final Logger LOGGER = AppLogger.getLogger();

    private OrderStatusUtil orderStatusService = OrderStatusUtil.getInstance();


    private final Gson gson = new Gson();

    @POST
    @Path("/submit")
    @Consumes(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8") // Može vratiti potvrdu ili grešku u JSON-u
    public Response submitOrder(Order order) {
	
	if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return Response.status(400).entity("Order object is null or contains no items.").build();
        }
	
	// Ovdje se vrši upis u Redis za svaki dio u narudžbi, PRIJE slanja dobavljaču
	for(Part part:order.getItems()) {
	    PartRepository.addOrUpdatePart(part);
	}
	
	System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(SUPPLIER_SERVER_HOST, SUPPLIER_SERVER_PORT);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            // 1. Pošalji komandu TAKE_ORDER dobavljač serveru
            out.println("TAKE_ORDER");
            LOGGER.info("Sent TAKE_ORDER command to supplier server.");

            // 2. Pošalji JSON reprezentaciju Order objekta
            String orderJson = gson.toJson(order);
            out.println(orderJson);
            LOGGER.info("Sent order JSON to supplier server: " + orderJson);

            return Response.status(200).entity("Order successfully submitted to supplier.").build();

        } catch (IOException e) {
            LOGGER.severe("Error communicating with supplier server: " + e.getMessage());
            return Response.status(500).entity("Failed to submit order to supplier: " + e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.severe("An unexpected error occurred: " + e.getMessage());
            return Response.status(500).entity("An unexpected error occurred: " + e.getMessage()).build();
        }
    }
    
    @GET
    @Path("/status/{orderId}") // Specifična putanja za provjeru statusa
    @Produces(MediaType.TEXT_PLAIN) // Označava da endpoint vraća plain text
    public Response getOrderStatus(@PathParam("orderId") String orderId) {
        LOGGER.info("Received request for order status: " + orderId);
        try {
            // Poziva servis koji će komunicirati sa Socket serverom
            String status = orderStatusService.getOrderStatusFromSocketServer(orderId);

            if (status != null && !status.startsWith("ERROR") && !status.startsWith("TIMEOUT")) {
                return Response.ok(status).build(); // Vraća status 200 OK
            } else if (status != null && status.startsWith("TIMEOUT")) {
                return Response.status(Response.Status.REQUEST_TIMEOUT)
                               .entity("Server timeout: " + status)
                               .build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity("Could not retrieve status: " + status)
                               .build();
            }
        } catch (Exception e) {
            LOGGER.severe("Error retrieving order status for " + orderId + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Failed to retrieve order status: " + e.getMessage())
                           .build();
        }
    }
}
