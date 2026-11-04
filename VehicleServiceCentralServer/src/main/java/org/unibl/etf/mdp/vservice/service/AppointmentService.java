package org.unibl.etf.mdp.vservice.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level; 
import java.util.logging.Logger; 

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.unibl.etf.mdp.vservice.model.Appointment;
import org.unibl.etf.mdp.vservice.model.Part;
import org.unibl.etf.mdp.vservice.repository.AppointmentRepository;
import org.unibl.etf.mdp.vservice.util.AppLogger; 

@Path("/appointments")
public class AppointmentService {

    private static final Logger LOGGER = AppLogger.getLogger();

    AppointmentRepository appointmentRepo = new AppointmentRepository();

    // Zakazivanje termina: Odabir datuma, vremena i vrste usluge (redovni servis ili popravka).
    @POST
    @Path("/schedule")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response scheduleAppointment(Appointment appointment) {
        try {
            if (appointmentRepo.scheduleAppointment(appointment)) {
                return Response.status(201).entity(appointment).build(); // 201 Created
            } else {
                return Response.status(400).entity("Failed to schedule appointment. Invalid data.").build();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri zakazivanju termina: " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }

    // Upravljanje terminima: Pregled svih zakazanih termina
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAppointments() {
        try {
            List<Appointment> appointments = appointmentRepo.getAllAppointments();
            return Response.status(200).entity(appointments).build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri dohvatanju svih termina: " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }

    // Pregled istorije servisa: Tabelarni prikaz prethodnih termina sa statusom (završeno, otkazano) i kratkim opisom.
    @GET
    @Path("/history/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserAppointmentHistory(@PathParam("userId") String userId) {
        try {
            List<Appointment> userAppointments = appointmentRepo.getAppointmentsByUserId(userId);
            return Response.status(200).entity(userAppointments).build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri dohvatanju istorije termina za korisnika " + userId + ": " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }

    // Potvrda zahtjeva
    @PUT
    @Path("/confirm/{appointmentId}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response confirmAppointment(@PathParam("appointmentId") String appointmentId) {
        try {
            if (appointmentRepo.confirmAppointment(appointmentId)) {
                return Response.status(200).entity("Appointment " + appointmentId + " confirmed successfully.").build();
            } else {
                return Response.status(404).entity("Appointment not found or cannot be confirmed.").build();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri potvrdi termina " + appointmentId + ": " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }
    
    // Odbijanje zahtjeva
    @PUT
    @Path("/reject/{appointmentId}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response rejectAppointment(@PathParam("appointmentId") String appointmentId) {
        try {
            if (appointmentRepo.rejectAppointment(appointmentId)) {
                return Response.status(200).entity("Appointment " + appointmentId + " rejected successfully.").build();
            } else {
                return Response.status(404).entity("Appointment not found or cannot be rejected.").build();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri odbijanju termina " + appointmentId + ": " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }

    // Dodavanje komentara (npr. "Potrebna zamjena akumulatora")
    @PUT
    @Path("/comment/{appointmentId}")
    @Consumes(MediaType.TEXT_PLAIN) // Expecting plain text for the comment
    @Produces(MediaType.TEXT_PLAIN)
    public Response addComment(@PathParam("appointmentId") String appointmentId, String comment) {
        try {
            if (appointmentRepo.addCommentToAppointment(appointmentId, comment)) {
                return Response.status(200).entity("Comment added to appointment " + appointmentId + " successfully.").build();
            } else {
                return Response.status(404).entity("Appointment not found or comment could not be added.").build();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri dodavanju komentara terminu " + appointmentId + ": " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }
    
    //Dodavanje utrošenih dijelova terminu
    //Ovaj endpoint će omogućiti serviseru da doda listu utrošenih dijelova određenom terminu. Pretpostavlja se da se lista dijelova šalje u tijelu zahtjeva kao JSON.
    @PUT
    @Path("/{appointmentId}/parts") // URL: /appointments/{appointmentId}/parts
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response addUsedParts(@PathParam("appointmentId") String appointmentId, List<Part> usedParts) {
        try {
            // Prvo provjeriti postoji li termin
            Optional<Appointment> existingAppointment = appointmentRepo.findById(appointmentId);
            if (existingAppointment.isEmpty()) {
                return Response.status(404).entity("Appointment not found.").build();
            }

            // Provjeriti da li je termin u statusu u kojem se mogu dodavati dijelovi
            // Na primjer, možda samo za "potvrđeno" ili "u toku" termine
            // Ako je već "završeno", ne bi trebalo dozvoliti direktno dodavanje dijelova preko ovog endpointa,
            // jer je "završeno" obično konačan status.
            // addUsedPartsToAppointment metoda već postavlja status na "završeno",
            // tako da bi se ovdje trebalo samo provjeriti da li je termin spreman za to.
            // Npr: if (!"potvrđeno".equals(existingAppointment.get().getStatus())) { /* error */ }

            if (usedParts == null || usedParts.isEmpty()) {
                return Response.status(400).entity("List of parts cannot be empty.").build();
            }

            if (appointmentRepo.addUsedPartsToAppointment(appointmentId, usedParts)) {
                return Response.status(200).entity("Used parts added/updated for appointment " + appointmentId + " successfully.").build();
            } else {
                return Response.status(500).entity("Failed to add used parts to appointment " + appointmentId + ".").build();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri dodavanju utrošenih dijelova terminu " + appointmentId + ": " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }
    
    @PUT
    @Path("/complete/{appointmentId}") // URL: /appointments/complete/{appointmentId}
    @Produces(MediaType.TEXT_PLAIN)
    public Response completeAppointment(@PathParam("appointmentId") String appointmentId) {
        try {
            if (appointmentRepo.completeAppointment(appointmentId)) {
                return Response.status(200).entity("Appointment " + appointmentId + " completed successfully.").build();
            } else {
                return Response.status(404).entity("Appointment not found or cannot be completed (wrong status).").build();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri kompletiranju termina " + appointmentId + ": " + e.getMessage(), e);
            return Response.status(500).entity("Internal server error: " + e.getMessage()).build();
        }
    }
}
