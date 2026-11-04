package org.unibl.etf.mdp.vservice.util;

import org.unibl.etf.mdp.vservice.model.User; 
import org.unibl.etf.mdp.vservice.model.Users;
import java.io.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class JaxbUserManager {

    private static final Logger LOGGER = AppLogger.getLogger();
    private String usersFilePath;

    public JaxbUserManager(String usersFilePath) {
        this.usersFilePath = usersFilePath;
        File file = new File(usersFilePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        if (!file.exists()) {
            LOGGER.log(Level.INFO, "File " + usersFilePath + " does not exist, creating it with an empty list of users.");
            saveUsers(new ArrayList<>());
        }
    }

    public JaxbUserManager() {
        // Empty constructor
    }

    public void setUsersFilePath(String usersFilePath) {
        this.usersFilePath = usersFilePath;
        File file = new File(usersFilePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        if (!file.exists()) {
            LOGGER.log(Level.INFO, "File " + usersFilePath + " does not exist, creating it with an empty list of users.");
            saveUsers(new ArrayList<>());
        }
    }

    public synchronized void saveUsers(List<User> userList) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Users.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            Users usersWrapper = new Users(userList);
            File file = new File(usersFilePath);

            jaxbMarshaller.marshal(usersWrapper, file);

            LOGGER.log(Level.INFO, "User list successfully serialized to XML file: " + usersFilePath);

        } catch (JAXBException e) {
            LOGGER.log(Level.SEVERE, "Error during JAXB serialization of user list", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error during list serialization", e);
        }
    }

    public synchronized List<User> loadUsers() {
        try {
            File file = new File(usersFilePath);

            if (!file.exists() || file.length() == 0) {
                LOGGER.log(Level.WARNING, "File " + usersFilePath + " does not exist or is empty. Returning an empty user list.");
                return new ArrayList<>();
            }

            JAXBContext jaxbContext = JAXBContext.newInstance(Users.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

            Users usersWrapper = (Users) jaxbUnmarshaller.unmarshal(file);

            LOGGER.log(Level.INFO, "User list successfully deserialized from XML file: " + usersFilePath);
            return usersWrapper.getUserList() != null ? new ArrayList<>(usersWrapper.getUserList()) : new ArrayList<>();

        } catch (JAXBException e) {
            LOGGER.log(Level.SEVERE, "Error during JAXB deserialization of user list", e);
            return new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "General error during list deserialization", e);
            return new ArrayList<>();
        }
    }

    // Add user - check for unique username
    public synchronized boolean addUser(User newUser) {
        List<User> users = loadUsers();

        if (newUser == null || newUser.getUsername() == null || newUser.getUsername().trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Attempt to add null user or user with empty username.");
            return false;
        }

        // Check if user with the same username already exists
        boolean exists = users.stream()
                               .anyMatch(user -> newUser.getUsername().equals(user.getUsername()));
        if (exists) {
            LOGGER.log(Level.WARNING, "User with username '" + newUser.getUsername() + "' already exists. Cannot add.");
            return false;
        }

        users.add(newUser);
        saveUsers(users);
        LOGGER.log(Level.INFO, "Added new user: " + newUser.getUsername());
        return true;
    }

    // Find user by username
    public User findUserByUsername(String username) {
        List<User> users = loadUsers();
        if (users == null || users.isEmpty()) {
            LOGGER.log(Level.WARNING, "User list is empty or not loaded. Cannot find user: " + username);
            return null;
        }
        if (username == null || username.trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Attempt to find user with empty or null username.");
            return null;
        }

        return users.stream()
                     .filter(user -> username.equals(user.getUsername()))
                     .findFirst()
                     .orElse(null);
    }

    // Update user - finds by username and updates fields
    public synchronized boolean updateUser(User updatedUser) {
        List<User> users = loadUsers();
        boolean found = false;
        if (updatedUser == null || updatedUser.getUsername() == null || updatedUser.getUsername().trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Attempt to update user with null object or empty username.");
            return false;
        }

        Iterator<User> iterator = users.iterator();
        while (iterator.hasNext()) {
            User user = iterator.next();
            if (updatedUser.getUsername().equals(user.getUsername())) {
                // Update fields of the found object
                user.setFirstName(updatedUser.getFirstName());
                user.setLastName(updatedUser.getLastName());
                user.setAddress(updatedUser.getAddress());
                user.setEmail(updatedUser.getEmail());
                user.setPhone(updatedUser.getPhone()); 
                user.setVehicle(updatedUser.getVehicle()); 
                user.setPassword(updatedUser.getPassword()); 
                user.setStatus(updatedUser.getStatus());
                found = true;
                LOGGER.log(Level.INFO, "Updated user with username: " + updatedUser.getUsername());
                break;
            }
        }

        if (found) {
            saveUsers(users); // Save the modified list back to the file
        } else {
            LOGGER.log(Level.WARNING, "Attempt to update non-existent user with username: " + updatedUser.getUsername());
        }

        return found;
    }

    // Delete user by username
    public synchronized boolean deleteUserByUsername(String username) {
        List<User> users = loadUsers();

        if (users == null || users.isEmpty()) {
            LOGGER.log(Level.WARNING, "User list is empty or not loaded. Cannot delete user with username: " + username);
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Attempt to delete user with empty or null username.");
            return false;
        }

        boolean deleted = false;
        Iterator<User> iterator = users.iterator();
        while (iterator.hasNext()) {
            User user = iterator.next();
            if (username.equals(user.getUsername())) {
                iterator.remove(); // Remove the user from the list
                deleted = true;
                LOGGER.log(Level.INFO, "User with username '" + username + "' found and removed from the list.");
                break; // Assuming usernames are unique
            }
        }

        if (deleted) {
            saveUsers(users); // Save the modified list back to the file
            LOGGER.log(Level.INFO, "User with username '" + username + "' successfully deleted from file.");
        } else {
            LOGGER.log(Level.WARNING, "User with username '" + username + "' not found in the list for deletion.");
        }

        return deleted;
    }
}
