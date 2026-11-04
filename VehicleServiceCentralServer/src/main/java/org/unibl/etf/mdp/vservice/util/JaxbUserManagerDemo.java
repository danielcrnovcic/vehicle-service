package org.unibl.etf.mdp.vservice.util;


import org.unibl.etf.mdp.vservice.model.User;
import org.unibl.etf.mdp.vservice.model.Vehicle;

import java.util.ArrayList;
import java.util.List;

public class JaxbUserManagerDemo {

    public static void main(String[] args) {
        // Define the path to clients.xml file
        String usersFilePath = "clients.xml";
        JaxbUserManager userManager = new JaxbUserManager(usersFilePath);

        // --- Create some User objects with the new structure ---

        // User 1
        Vehicle vehicle1 = new Vehicle("Toyota", "Camry", 2020, "BL123AB");
        User user1 = new User("john.doe", "hashedpass123", "John", "Doe",
                              "123 Main St", "111-222-3333", "john.doe@example.com",
                              vehicle1, "active");

        // User 2
        Vehicle vehicle2 = new Vehicle("Honda", "Civic", 2018, "SA456CD");
        User user2 = new User("jane.smith", "securepass456", "Jane", "Smith",
                              "456 Oak Ave", "444-555-6666", "jane.smith@example.com",
                              vehicle2, "pending");

        // User 3 (without a vehicle and different status)
        User user3 = new User("peter.jones", "anotherpass789", "Peter", "Jones",
                              "789 Pine Ln", "777-888-9999", "peter.jones@example.com",
                              null, "blocked"); // No vehicle for this user

        // --- Add users to a list and save them ---
        List<User> initialUsers = new ArrayList<>();
        initialUsers.add(user1);
        initialUsers.add(user2);
        initialUsers.add(user3);

        System.out.println("Saving initial users to " + usersFilePath + "...");
        userManager.saveUsers(initialUsers);
        System.out.println("Initial users saved.");

        // --- Demonstrate adding a new user ---
        User newUser = new User("new.user", "newpass", "New", "Guy",
                                "101 Elm St", "999-000-1111", "new.guy@example.com",
                                null, "pending");
        System.out.println("\nAttempting to add new user: " + newUser.getUsername() + "...");
        boolean added = userManager.addUser(newUser);
        if (added) {
            System.out.println("New user added successfully.");
        } else {
            System.out.println("Failed to add new user (username might already exist).");
        }

        // --- Demonstrate loading users ---
        System.out.println("\nLoading users from " + usersFilePath + "...");
        List<User> loadedUsers = userManager.loadUsers();
        if (loadedUsers.isEmpty()) {
            System.out.println("No users loaded or file is empty.");
        } else {
            System.out.println("Loaded users:");
            for (User user : loadedUsers) {
                System.out.println(user); // Calls the User's toString() method
            }
        }
    }
}
