package org.unibl.etf.mdp.vservice.supplier.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class Bill implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String orderId; // Link to the originating order
        private String serviceId; // To whom the invoice is addressed
        private List<Part> items; // Items with unit prices
        private Date date;
        private double totalAmount;
        // VAT will be calculated by the accounting service
        
        public Bill() {
	    super();
	    // TODO Auto-generated constructor stub
	}

        public Bill(String orderId, String serviceId, List<Part> items, double totalAmount) {
	    super();
	    this.orderId = orderId;
	    this.serviceId = serviceId;
	    this.items = items;
	    this.totalAmount = totalAmount;
	}

	public String getId() {
	    return id;
	}

	public void setId(String id) {
	    this.id = id;
	}

	public String getOrderId() {
	    return orderId;
	}

	public void setOrderId(String orderId) {
	    this.orderId = orderId;
	}

	public String getServiceId() {
	    return serviceId;
	}

	public void setServiceId(String serviceId) {
	    this.serviceId = serviceId;
	}

	public List<Part> getItems() {
	    return items;
	}

	public void setItems(List<Part> items) {
	    this.items = items;
	}

	public Date getDate() {
	    return date;
	}

	public void setDate(Date date) {
	    this.date = date;
	}

	public double getTotalAmount() {
	    return totalAmount;
	}

	public void setTotalAmount(double totalAmount) {
	    this.totalAmount = totalAmount;
	}

	public static long getSerialversionuid() {
	    return serialVersionUID;
	}

	@Override
	public String toString() {
	    return "Bill [id=" + id + ", orderId=" + orderId + ", serviceId=" + serviceId + ", items=" + items
		    + ", date=" + date + ", totalAmount=" + totalAmount + "]";
	}

	
	
       

    }
