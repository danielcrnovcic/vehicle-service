package org.unibl.etf.mdp.vservice.supplier.model;

import java.io.Serializable;

public class Part implements Serializable {
    private String code; // Šifra
    private String name; // Naziv
    private String manufacturer; // Proizvođač
    private double price; // Cijena
    private int quantity; // Dostupna količina
    private String description; // Opis
    
    public Part() {
	super();
	// TODO Auto-generated constructor stub
    }
    
    

    public Part(int quantity) {
	super();
	this.quantity = quantity;
	this.price = 0.0;
    }



    public Part(String code, String name, String manufacturer, double price, int quantity, String description) {
	super();
	this.code = code;
	this.name = name;
	this.manufacturer = manufacturer;
	this.price = price;
	this.quantity = quantity;
	this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
	return "Part [code=" + code + ", name=" + name + ", manufacturer=" + manufacturer + ", price=" + price
		+ ", quantity=" + quantity + ", description=" + description + "]";
    }
    
    
    
    

}
