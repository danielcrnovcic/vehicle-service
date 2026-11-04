package org.unibl.etf.mdp.vservice.service;

import org.unibl.etf.mdp.vservice.util.InvoiceUtil;
import org.unibl.etf.mdp.vservice.util.AppLogger;

import org.unibl.etf.mdp.vservice.repository.AppointmentRepository; 
import org.unibl.etf.mdp.vservice.repository.UserRepository;     
import org.unibl.etf.mdp.vservice.repository.PartRepository;     


import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Level;

@Path("/invoices") 
public class InvoiceService {

    private final InvoiceUtil invoiceUtil;

    public InvoiceService() {
        
        AppointmentRepository appointmentRepository = new AppointmentRepository(); 
        UserRepository userRepository = new UserRepository();                   
        PartRepository partRepository = new PartRepository();                   

        this.invoiceUtil = new InvoiceUtil(appointmentRepository, userRepository, partRepository);
    }

    
    @POST 
    @Path("/generate-and-send/{appointmentId}") 
    @Produces(MediaType.TEXT_PLAIN) 
    public Response generateAndSendInvoice(@PathParam("appointmentId") String appointmentId) {
        try {
            boolean success = invoiceUtil.createAndSendInvoice(appointmentId);
            if (success) {
                AppLogger.getLogger().info("Successfully created and sent invoice for appointment ID: " + appointmentId);
                return Response.ok("Faktura uspješno kreirana i poslana.").build();
            } else {
                AppLogger.getLogger().warning("Failed to create and send invoice for appointment ID: " + appointmentId);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity("Greška pri kreiranju ili slanju fakture.")
                               .build();
            }
        } catch (RuntimeException e) {
            AppLogger.getLogger().log(Level.WARNING, "Error generating invoice for appointment ID " + appointmentId + ": " + e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Greška: " + e.getMessage())
                           .build();
        } catch (Exception e) {
            AppLogger.getLogger().log(Level.SEVERE, "Unexpected error in invoice generation endpoint for appointment ID " + appointmentId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Došlo je do neočekivane greške na serveru.")
                           .build();
        }
    }
}
