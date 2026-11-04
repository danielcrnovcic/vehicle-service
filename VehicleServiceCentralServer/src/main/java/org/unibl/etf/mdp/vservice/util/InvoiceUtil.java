package org.unibl.etf.mdp.vservice.util;

import org.unibl.etf.mdp.vservice.model.Appointment;
import org.unibl.etf.mdp.vservice.model.Part;
import org.unibl.etf.mdp.vservice.model.User;
import org.unibl.etf.mdp.vservice.model.Invoice;
import org.unibl.etf.mdp.vservice.repository.AppointmentRepository;
import org.unibl.etf.mdp.vservice.repository.UserRepository;
import org.unibl.etf.mdp.vservice.repository.PartRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class InvoiceUtil {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PartRepository partRepository;

    // Define the directory for invoices
    private static final String INVOICES_DIRECTORY = PropertiesUtil.getProperty("INVOICES_DIRECTORY");
    public InvoiceUtil(AppointmentRepository appointmentRepository, UserRepository userRepository, PartRepository partRepository) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.partRepository = partRepository;
        // Ensure the invoices directory exists when InvoiceUtil is initialized
        createInvoicesDirectory();
    }

    private void createInvoicesDirectory() {
        File directory = new File(INVOICES_DIRECTORY);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                AppLogger.getLogger().info("Created invoices directory: " + directory.getAbsolutePath());
            } else {
                AppLogger.getLogger().log(Level.SEVERE, "Failed to create invoices directory: " + directory.getAbsolutePath());
            }
        }
    }

    public boolean createAndSendInvoice(String appointmentId) {
        // 1. Fetch appointment details
        Appointment appointment = null;
        try {
            appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));
        } catch (IOException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Error fetching appointment by ID: " + appointmentId, e);
            return false;
        }

        // 2. Fetch user details (client)
        User client = userRepository.findUserByUsernameObject(appointment.getUserId());
        if (client == null) {
            AppLogger.getLogger().log(Level.WARNING, "Client not found for username: " + appointment.getUserId() + " linked to appointment: " + appointmentId);
            throw new RuntimeException("Client not found for appointment.");
        }

        // 3. Fetch used parts
        List<Part> usedParts = appointment.getUsedParts();
        if (usedParts == null) {
            usedParts = new ArrayList<>();
            AppLogger.getLogger().info("No parts recorded for appointment " + appointmentId + ".");
        }

        // 4. Calculate total amount
        double partsTotal = usedParts.stream().mapToDouble(p -> p.getPrice() * p.getQuantity()).sum();
        double servicePrice = 50.0;
        double totalAmount = partsTotal + servicePrice;

        // 5. Generate Invoice object
        Invoice newInvoice = new Invoice();
        newInvoice.setInvoiceId("INV-" + System.currentTimeMillis());
        newInvoice.setUserId(client.getUsername());
        newInvoice.setServiceType(appointment.getServiceType());
        newInvoice.setUsedParts(usedParts);
        newInvoice.setTotalAmount(totalAmount);
        newInvoice.setInvoiceDate(LocalDateTime.now());
        newInvoice.setVehicleInfo(client.getVehicle() != null ? client.getVehicle().toString() : "N/A");

        // 6. Generate invoice content
        String invoiceContent = generateInvoiceText(newInvoice, client);
        String invoiceFileName = "faktura_" + appointment.getId() + ".txt";
        String zipFileName = invoiceFileName.replace(".txt", ".zip");

        // Construct full paths for saving the files
        File invoiceFile = new File(INVOICES_DIRECTORY, invoiceFileName);
        File zipFile = new File(INVOICES_DIRECTORY, zipFileName);

        try {
            try (FileOutputStream fos = new FileOutputStream(invoiceFile)) {
                fos.write(invoiceContent.getBytes());
            }
            AppLogger.getLogger().info("Invoice file created: " + invoiceFile.getAbsolutePath());

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zos.putNextEntry(new ZipEntry(invoiceFile.getName()));
                byte[] bytes = new byte[1024];
                int length;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(invoiceFile)) {
                    while ((length = fis.read(bytes)) >= 0) {
                        zos.write(bytes, 0, length);
                    }
                }
                zos.closeEntry();
            }
            AppLogger.getLogger().info("Invoice zip file created: " + zipFile.getAbsolutePath());

            boolean sent = MailSender.sendInvoiceEmail(
                client.getEmail(),
                "Vaša faktura za servis vozila",
                "Poštovani/a,\n\nU prilogu se nalazi vaša faktura za obavljeni servis vozila.\n\nS poštovanjem,\nServis vozila",
                zipFile.getAbsolutePath()
            );

            return sent;

        } catch (IOException e) {
            AppLogger.getLogger().log(Level.SEVERE, "Error creating or sending invoice for appointment " + appointmentId, e);
            return false;
        } finally {
            // It's generally good practice to clean up temporary files if they're not needed after sending.
            // Uncomment the following if you want to delete the files after they've been sent.
            // if (invoiceFile != null && invoiceFile.exists()) {
            //     if (!invoiceFile.delete()) {
            //         AppLogger.getLogger().log(Level.WARNING, "Failed to delete temporary invoice file: " + invoiceFile.getAbsolutePath());
            //     }
            // }
            // if (zipFile != null && zipFile.exists()) {
            //     if (!zipFile.delete()) {
            //         AppLogger.getLogger().log(Level.WARNING, "Failed to delete temporary zip file: " + zipFile.getAbsolutePath());
            //     }
            // }
        }
    }

    private String generateInvoiceText(Invoice invoice, User client) {
        StringBuilder content = new StringBuilder();
        content.append("FAKTURA\n");
        content.append("---------------------------------\n");
        content.append(String.format("Datum: %s\n", invoice.getInvoiceDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));
        content.append(String.format("Klijent: %s %s (%s)\n", client.getFirstName(), client.getLastName(), client.getUsername()));
        content.append(String.format("Vrsta usluge: %s\n", invoice.getServiceType()));
        content.append("---------------------------------\n");
        content.append("Utrošeni dijelovi:\n");

        if (invoice.getUsedParts() != null && !invoice.getUsedParts().isEmpty()) {
            for (Part part : invoice.getUsedParts()) {
                content.append(String.format("- %s (%s): %.2f BAM x %d = %.2f BAM\n",
                    part.getName(), part.getCode(), part.getPrice(), part.getQuantity(), part.getPrice() * part.getQuantity()));
            }
        } else {
            content.append("Nema utrošenih dijelova.\n");
        }

        double partsOnlyTotal = invoice.getUsedParts() != null ? invoice.getUsedParts().stream().mapToDouble(p -> p.getPrice() * p.getQuantity()).sum() : 0.0;
        double serviceOnlyPrice = invoice.getTotalAmount() - partsOnlyTotal;

        content.append(String.format("Ukupno dijelovi: %.2f BAM\n", partsOnlyTotal));
        content.append(String.format("Cijena usluge: %.2f BAM\n", serviceOnlyPrice));
        content.append(String.format("UKUPNO: %.2f BAM\n", invoice.getTotalAmount()));
        content.append("---------------------------------\n");
        return content.toString();
    }
}