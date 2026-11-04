package org.unibl.etf.mdp.vservice.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.unibl.etf.mdp.vservice.util.AppLogger;
import org.unibl.etf.mdp.vservice.util.JaxbUserManager;
import org.unibl.etf.mdp.vservice.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.model.User;

public class UserRepository {

    private static final Logger LOGGER = AppLogger.getLogger();

    // Using Singleton pattern for the manager
    private static JaxbUserManager userManager = null;

    // Statički inicijalizacioni blok
    static {
        // Pozivamo metodu za inicijalizaciju menadžera kada se klasa prvi put učita
        initializeUserManager();
    }

    // Metoda za inicijalizaciju menadžera (nitno-bezbjedan Singleton)
    // Promijenjeno u private, jer se poziva samo unutar statičkog bloka
    private static void initializeUserManager() {
        // Double-checked locking za sigurnu singleton inicijalizaciju
        if (userManager == null) {
            synchronized (UserRepository.class) {
                if (userManager == null) {
                    String usersXmlPath = PropertiesUtil.getProperty("USERS_PATH");
                    if (usersXmlPath == null || usersXmlPath.trim().isEmpty()) {
                        LOGGER.log(Level.SEVERE, "Path to users.xml file (USERS_PATH) is not configured in the properties file!");
                        throw new ExceptionInInitializerError("File path configuration missing for UserRepository."); // Fatalna greška
                    }
                    userManager = new JaxbUserManager(usersXmlPath);
                    LOGGER.log(Level.INFO, "JaxbUserManager initialized with path: " + usersXmlPath);
                }
            }
        }
    }

    // Register method: Checks for existence and adds a new user
    public boolean register(User user) {
        if (user == null) {
            LOGGER.log(Level.WARNING, "Attempt to register null user.");
            return false;
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty() ||
            user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Attempt to register user without username or password.");
            return false;
        }
        if (user.getStatus() == null || user.getStatus().trim().isEmpty()) {
            user.setStatus("pending");
        }

        boolean success = userManager.addUser(user);

        if(success) {
            LOGGER.log(Level.INFO, "User '" + user.getUsername() + "' successfully registered (status: " + user.getStatus() + ").");
        } else {
            LOGGER.log(Level.WARNING, "Registration failed for user '" + user.getUsername() + "'. Username probably already exists.");
        }
        return success;
    }

    // Login method: Finds user and verifies password and status
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Attempt to log in with empty username or password.");
            return false;
        }

        User user = userManager.findUserByUsername(username);

        if (user != null) {
            if (password.equals(user.getPassword())) {
                if ("active".equalsIgnoreCase(user.getStatus())) {
                    LOGGER.log(Level.INFO, "Successful login for user: " + username);
                    return true;
                } else {
                    LOGGER.log(Level.WARNING, "Attempt to log in for inactive user: " + username + ", status: " + user.getStatus());
                    return false;
                }
            } else {
                LOGGER.log(Level.WARNING, "Login failed for user: " + username + ". Incorrect password.");
            }
        } else {
            LOGGER.log(Level.WARNING, "Attempt to log in for non-existent user: " + username);
        }
        return false;
    }

    // Reading all users and displaying them in GUI
    public static List<User> getAllUsers() {
        List<User> users = userManager.loadUsers();

        if (users == null || users.isEmpty()) {
            LOGGER.log(Level.INFO, "No users in XML file. Returning empty list of Strings.");
            return new ArrayList<>();
        }
        return users;
    }

    // Method to get user by list index
    private static User getUserByListIndex(int index) {
        List<User> users = userManager.loadUsers();
        if (users != null && index >= 0 && index < users.size()) {
            return users.get(index);
        }
        LOGGER.log(Level.WARNING, "Attempt to access user at invalid index: " + index);
        return null;
    }

    // Changing the user status field to "active" based on index
    public static boolean approveRequest(int i) {
        User userToApprove = getUserByListIndex(i);

        if (userToApprove != null) {
            if ("pending".equalsIgnoreCase(userToApprove.getStatus())) {
                userToApprove.setStatus("active");
                boolean success = userManager.updateUser(userToApprove);
                LOGGER.log(Level.INFO, "Request approved for user at index " + i + ", username: " + userToApprove.getUsername());
                return success;
            } else {
                LOGGER.log(Level.WARNING, "Attempt to approve request for user not in 'pending' status (index " + i + ", username: " + userToApprove.getUsername() + "). Current status: " + userToApprove.getStatus());
            }
        }
        return false;
    }

    // Deleting the user from the XML file based on index (if pending)
    public static boolean denyRequest(int i) {
        User userToDeny = getUserByListIndex(i);

        if (userToDeny != null) {
            if ("pending".equalsIgnoreCase(userToDeny.getStatus())) {
                boolean success = userManager.deleteUserByUsername(userToDeny.getUsername());
                if(success) {
                    LOGGER.log(Level.INFO, "Request denied (deleted) for user at index " + i + ", username: " + userToDeny.getUsername());
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to delete user '" + userToDeny.getUsername() + "' after denying request.");
                }
                return success;
            } else {
                LOGGER.log(Level.WARNING, "Attempt to deny request for user not in 'pending' status (index " + i + ", username: " + userToDeny.getUsername() + "). Current status: " + userToDeny.getStatus());
            }
        }
        return false;
    }

    // Deleting the user from the XML file based on index (if active or blocked)
    public static boolean removeUser(int i) {
        User userToRemove = getUserByListIndex(i);

        if (userToRemove != null) {
            String status = userToRemove.getStatus();
            if ("active".equalsIgnoreCase(status) || "blocked".equalsIgnoreCase(status)) {
                boolean success = userManager.deleteUserByUsername(userToRemove.getUsername());
                if(success) {
                    LOGGER.log(Level.INFO, "User at index " + i + ", username: " + userToRemove.getUsername() + ", status: " + status + " deleted.");
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to delete user '" + userToRemove.getUsername() + "' with status '" + status + "'.");
                }
                return success;
            } else {
                LOGGER.log(Level.WARNING, "Attempt to delete user who is not 'active' or 'blocked' (index " + i + ", username: " + userToRemove.getUsername() + "). Current status: " + status);
            }
        }
        return false;
    }

    // Changing the user status field to "blocked" based on index
    public static boolean blockUser(int i) {
        User userToBlock = getUserByListIndex(i);

        if (userToBlock != null) {
            if ("active".equalsIgnoreCase(userToBlock.getStatus())) {
                userToBlock.setStatus("blocked");
                boolean success = userManager.updateUser(userToBlock);
                if(success) {
                    LOGGER.log(Level.INFO, "User at index " + i + ", username: " + userToBlock.getUsername() + " blocked.");
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to block user '" + userToBlock.getUsername() + "'.");
                }
                return success;
            } else {
                LOGGER.log(Level.WARNING, "Attempt to block user not in 'active' status (index " + i + ", username: " + userToBlock.getUsername() + "). Current status: " + userToBlock.getStatus());
            }
        }
        return false;
    }

    public User findUserByUsernameObject(String username) {
        return userManager.findUserByUsername(username);
    }

    public boolean approveUserByUsername(String username) {
        User userToApprove = userManager.findUserByUsername(username);

        if (userToApprove != null) {
            if ("pending".equalsIgnoreCase(userToApprove.getStatus())) {
                userToApprove.setStatus("active");
                boolean success = userManager.updateUser(userToApprove);
                if (success) {
                    LOGGER.log(Level.INFO, "User '" + username + "' approved successfully. Status set to 'active'.");
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to update user '" + username + "' status to 'active'.");
                }
                return success;
            } else {
                LOGGER.log(Level.WARNING, "Attempt to approve user '" + username + "' not in 'pending' status. Current status: " + userToApprove.getStatus());
            }
        } else {
            LOGGER.log(Level.WARNING, "Attempt to approve non-existent user: " + username);
        }
        return false;
    }

    public boolean blockUserByUsername(String username) {
        User userToBlock = userManager.findUserByUsername(username);

        if (userToBlock != null) {
            if ("active".equalsIgnoreCase(userToBlock.getStatus())) {
                userToBlock.setStatus("blocked");
                boolean success = userManager.updateUser(userToBlock);
                if (success) {
                    LOGGER.log(Level.INFO, "User '" + username + "' blocked successfully. Status set to 'blocked'.");
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to update user '" + username + "' status to 'blocked'.");
                }
                return success;
            } else {
                LOGGER.log(Level.WARNING, "Attempt to block user '" + username + "' not in 'active' status. Current status: " + userToBlock.getStatus());
            }
        } else {
            LOGGER.log(Level.WARNING, "Attempt to block non-existent user: " + username);
        }
        return false;
    }

    public boolean deleteUserByUsername(String username) {
        boolean success = userManager.deleteUserByUsername(username);
        if (success) {
            LOGGER.log(Level.INFO, "User '" + username + "' deleted successfully.");
        } else {
            LOGGER.log(Level.WARNING, "Failed to delete user '" + username + "'. User might not exist.");
        }
        return success;
    }
}
