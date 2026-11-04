package org.unibl.etf.mdp.vservice.servicer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Appointment {
    private String id;
    private String userId;
    private LocalDateTime dateTime;
    private String serviceType; // redovni servis ili popravka
    private String status; // zakazano, završeno, otkazano, odbijeno
    private String description; // kratak opis usluge
    private String comment; // komentar servisera
    private List<Part> usedParts; // Ovo polje postoji, ali se ne popunjava u konstruktoru

    
    public Appointment() {
        this.usedParts = new ArrayList<>(); // Također inicijalizuj ovdje
    }

    public Appointment(String id, String userId, LocalDateTime dateTime, String serviceType, String status,
	    String description, String comment) {
	super();
	this.id = id;
	this.userId = userId;
	this.dateTime = dateTime;
	this.serviceType = serviceType;
	this.status = status;
	this.description = description;
	this.comment = comment;
        this.usedParts = new ArrayList<>(); // Preporučeno: prazna lista da se izbjegnu NullPointerException

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
    
    

    public List<Part> getUsedParts() {
	// Osiguraj da nikad ne vratiš null ako se koristi
        if (usedParts == null) {
            usedParts = new ArrayList<>();
        }
        return usedParts;
    }

    public void setUsedParts(List<Part> usedParts) {
        this.usedParts = usedParts;
    }

    @Override
    public String toString() {
	return "Appointment [id=" + id + ", userId=" + userId + ", dateTime=" + dateTime + ", serviceType="
		+ serviceType + ", status=" + status + ", description=" + description + ", comment=" + comment
		+ ", usedParts=" + usedParts + "]";
    }    
    
}

