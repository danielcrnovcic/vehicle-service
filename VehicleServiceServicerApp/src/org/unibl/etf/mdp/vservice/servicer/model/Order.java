package org.unibl.etf.mdp.vservice.servicer.model;


import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Date;
import java.util.ArrayList;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    private String orderId;
    private String serviceId; 
    private List<Part> items;
    private String timestamp; 
    
    public Order() {
	super();
	// TODO Auto-generated constructor stub
    }

    // Constructor for simulating received orders
    public Order(String orderId, String libraryId, List<Part> items) {
        this.orderId = orderId;
        this.serviceId = libraryId;
        this.items = items;
        this.timestamp = new Date().toString(); 
    }
   
    // Getters
    public String getOrderId() { return orderId; }
    public String getServiceId() { return serviceId; }
    public List<Part> getItems() { return items; }
    public String getTimestamp() { return timestamp; }
    
    
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setItems(List<Part> items) {
        this.items = items;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Order ID: ").append(orderId).append("\n");
        sb.append("Library: ").append(serviceId).append("\n");
        sb.append("Timestamp: ").append(timestamp).append("\n");
        sb.append("Items:\n");
        for (Part item : items) {
            sb.append("  - ").append(item.toString()).append("\n");
        }
        return sb.toString();
    }
}

