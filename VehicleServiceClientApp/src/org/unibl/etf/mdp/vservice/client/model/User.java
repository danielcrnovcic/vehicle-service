package org.unibl.etf.mdp.vservice.client.model;

public class User {
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String address;
    private String phone;
    private String email;
    private Vehicle vehicle; // Podaci o vozilu
    private String status; // Status naloga
    
    public User() {
	super();
	// TODO Auto-generated constructor stub
    }

    public User(String username, String password, String firstName, String lastName, String address, String phone,
	    String email, Vehicle vehicle, String status) {
	super();
	this.username = username;
	this.password = password;
	this.firstName = firstName;
	this.lastName = lastName;
	this.address = address;
	this.phone = phone;
	this.email = email;
	this.vehicle = vehicle;
	this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
	return "User [username=" + username + ", password=" + password + ", firstName=" + firstName + ", lastName="
		+ lastName + ", address=" + address + ", phone=" + phone + ", email=" + email + ", vehicle=" + vehicle
		+ ", status=" + status + "]";
    }
    
    
    
    
    
}
