package org.unibl.etf.mdp.vservice.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "users") // Defines the root element name in XML
@XmlAccessorType(XmlAccessType.FIELD) // Specifies that JAXB should access fields directly
public class Users {

    @XmlElement(name = "user") // Defines the name for each User element within the list
    private List<User> userList;

    public Users() {
        // Default constructor for JAXB
        this.userList = new ArrayList<>();
    }

    public Users(List<User> userList) {
        this.userList = userList;
    }

    public List<User> getUserList() {
        return userList;
    }

    public void setUserList(List<User> userList) {
        this.userList = userList;
    }
}
