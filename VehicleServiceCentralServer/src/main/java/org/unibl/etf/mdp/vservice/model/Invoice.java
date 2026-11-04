package org.unibl.etf.mdp.vservice.model;

import java.time.LocalDateTime;
import java.util.List;

public class Invoice {
    
    private String invoiceId;
    private String userId; // Korisnik za kojeg je izdata faktura
    private String vehicleInfo; // Podaci o vozilu
    private String serviceType; // Vrsta usluge
    private List<Part> usedParts; // Utrošeni dijelovi
    private double totalAmount;
    private LocalDateTime invoiceDate;
    
    public Invoice() {
	super();
    }

    public Invoice(String invoiceId, String userId, String vehicleInfo, String serviceType, List<Part> usedParts,
	    double totalAmount, LocalDateTime invoiceDate) {
	super();
	this.invoiceId = invoiceId;
	this.userId = userId;
	this.vehicleInfo = vehicleInfo;
	this.serviceType = serviceType;
	this.usedParts = usedParts;
	this.totalAmount = totalAmount;
	this.invoiceDate = invoiceDate;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getVehicleInfo() {
        return vehicleInfo;
    }

    public void setVehicleInfo(String vehicleInfo) {
        this.vehicleInfo = vehicleInfo;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public List<Part> getUsedParts() {
        return usedParts;
    }

    public void setUsedParts(List<Part> usedParts) {
        this.usedParts = usedParts;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDateTime invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    @Override
    public String toString() {
	return "Invoice [invoiceId=" + invoiceId + ", userId=" + userId + ", vehicleInfo=" + vehicleInfo
		+ ", serviceType=" + serviceType + ", usedParts=" + usedParts + ", totalAmount=" + totalAmount
		+ ", invoiceDate=" + invoiceDate + "]";
    }
}
