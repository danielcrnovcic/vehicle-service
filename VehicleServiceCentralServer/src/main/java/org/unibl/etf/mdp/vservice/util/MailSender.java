package org.unibl.etf.mdp.vservice.util;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;

public class MailSender {
    private static final Properties props = new Properties();
    private static String MAIL_USERNAME;
    private static String MAIL_PASSWORD;

    static {
        try {
            // Postaviti SMTP svojstva za Gmail
            props.put("mail.smtp.auth", PropertiesUtil.getProperty("MAIL_SMTP_AUTH"));
            props.put("mail.smtp.starttls.enable", PropertiesUtil.getProperty("MAIL_SMTP_STARTTLS_ENABLE"));
            props.put("mail.smtp.host", PropertiesUtil.getProperty("MAIL_SMTP_HOST"));
            props.put("mail.smtp.port", PropertiesUtil.getProperty("MAIL_SMTP_PORT"));

            // Dodatna svojstva za pouzdanost (preporučeno)
            props.put("mail.smtp.connectiontimeout", "5000"); // 5 sekundi
            props.put("mail.smtp.timeout", "5000");           // 5 sekundi

            // Učitavanje korisničkog imena i lozinke
            MAIL_USERNAME = PropertiesUtil.getProperty("MAIL_USERNAME");
            MAIL_PASSWORD = PropertiesUtil.getProperty("MAIL_PASSWORD");

            // Provjera da li su korisničko ime i lozinka uspješno učitani
            if (MAIL_USERNAME == null || MAIL_USERNAME.isEmpty() || MAIL_PASSWORD == null || MAIL_PASSWORD.isEmpty()) {
                // Baciti izuzetak ili logiraj kritičnu grešku ako su kredencijali prazni
                String msg = "SMTP korisničko ime ili lozinka nisu ispravno konfigurisani u config.properties.";
                if (AppLogger.getLogger() != null) {
                    AppLogger.getLogger().log(Level.SEVERE, msg);
                } else {
                    System.err.println("Kritična greška: " + msg);
                }
                // Opcionalno: throw new RuntimeException(msg); da se zaustavi inicijalizacija
            }

        } catch (Exception e) { // Hvatanje izuzetka ako PropertiesUtil ne radi kako se očekuje
            // Logovanje greške koja se dogodi tokom inicijalizacije MailSendera
            String msg = "Greška pri inicijalizaciji MailSendera iz config.properties.";
            if (AppLogger.getLogger() != null) {
                AppLogger.getLogger().log(Level.SEVERE, msg, e);
            } else {
                System.err.println("Kritična greška: " + msg);
                e.printStackTrace();
            }
            // Opcionalno: throw new ExceptionInInitializerError(e); da aplikacija padne
        }
    }

    /**
     * Šalje email sa fakturom, uključujući prilog.
     *
     * @param recipientEmail E-mail adresa primaoca.
     * @param subject Predmet emaila.
     * @param body Sadržaj emaila (tekst).
     * @param attachmentPath Putanja do fajla koji se prilaže. Može biti null ili prazan ako nema priloga.
     * @return true ako je email uspješno poslat, false inače.
     */
    public static boolean sendInvoiceEmail(String recipientEmail, String subject, String body, String attachmentPath) {
        // Kreiranje Session objekta
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(MAIL_USERNAME, MAIL_PASSWORD);
            }
        });

        try {
            // Kreiranje poruke
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(MAIL_USERNAME)); // Adresa pošiljaoca
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail)); // Adresa primaoca
            message.setSubject(subject); // Predmet poruke

            // Kreiranje višedijelnog sadržaja
            MimeMultipart multipart = new MimeMultipart();

            // Dodavanje teksta poruke
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(body);
            multipart.addBodyPart(messageBodyPart);

            // Dodavanje priloga
            if (attachmentPath != null && !attachmentPath.isEmpty()) {
                File attachmentFile = new File(attachmentPath);
                // VAŽNO: Provjera da li prilog postoji i da li je validan fajl
                if (attachmentFile.exists() && attachmentFile.isFile()) {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(attachmentFile);
                    attachmentPart.setDataHandler(new DataHandler(source));
                    attachmentPart.setFileName(attachmentFile.getName());
                    multipart.addBodyPart(attachmentPart);
                } else {
                    // Logovanje upozorenja ako prilog nije pronađen
                    String warningMsg = "Prilog nije pronađen ili nije validan fajl: " + attachmentPath;
                    if (AppLogger.getLogger() != null) {
                        AppLogger.getLogger().warning(warningMsg);
                    } else {
                        System.err.println("Upozorenje: " + warningMsg);
                    }
                }
            }

            message.setContent(multipart); // Postavljanje sadržaja poruke

            Transport.send(message); // Slanje emaila

            // Logovanje uspješnog slanja
            if (AppLogger.getLogger() != null) {
                AppLogger.getLogger().info("Email sa fakturom poslat na: " + recipientEmail);
            } else {
                System.out.println("Email sa fakturom poslat na: " + recipientEmail);
            }
            return true;
        } catch (MessagingException e) {
            // Logovanje greške prilikom slanja
            String errorMsg = "Greška pri slanju emaila sa fakturom na " + recipientEmail;
            if (AppLogger.getLogger() != null) {
                AppLogger.getLogger().log(Level.SEVERE, errorMsg, e);
            } else {
                System.err.println(errorMsg + ": " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }
}