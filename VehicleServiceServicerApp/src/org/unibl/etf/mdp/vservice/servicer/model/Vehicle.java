package org.unibl.etf.mdp.vservice.servicer.model;

public class Vehicle {
    private String make;
    private String model;
    private int year;
    private String registrationNumber;
    
    public Vehicle() {
	super();
	// TODO Auto-generated constructor stub
    }

    public Vehicle(String make, String model, int year, String registrationNumber) {
	super();
	this.make = make;
	this.model = model;
	this.year = year;
	this.registrationNumber = registrationNumber;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @Override
    public String toString() {
	return "Vehicle [make=" + make + ", model=" + model + ", year=" + year + ", registrationNumber="
		+ registrationNumber + "]";
    }
    
    
    
    
    
}
