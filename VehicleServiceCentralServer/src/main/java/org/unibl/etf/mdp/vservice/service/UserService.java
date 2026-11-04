package org.unibl.etf.mdp.vservice.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.GET; 
import javax.ws.rs.PUT; 
import javax.ws.rs.DELETE; 
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces; 
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.unibl.etf.mdp.vservice.model.User; 
import org.unibl.etf.mdp.vservice.repository.UserRepository;

@Path("/users")
public class UserService {
	
	UserRepository usersRepo = new UserRepository(); 
	
	@POST
	@Path("/register")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response register(User user) throws FileNotFoundException, IOException {
		if(usersRepo.register(user))
			return Response.status(200).entity(user).build();
		else
			return Response.status(500).entity("Error adding user. Username might already exist.").build(); // More specific error message
	}
	
	@POST
	@Path("/login/{user}/{pass}")
	public Response login(@PathParam("user") String username, @PathParam("pass") String password) throws FileNotFoundException, IOException {
		if(usersRepo.login(username, password))
			return Response.status(200).entity("Success.").build();
		else
			return Response.status(401).entity("Invalid credentials or inactive account.").build(); // Use 401 for unauthorized
	}

	//---
	//New Endpoints for User Management
	//This endpoint allows you to retrieve a list of all registered users.

	@GET
	@Produces(MediaType.APPLICATION_JSON) // Specify that this method produces JSON
	public Response getAllUsers() {
		List<User> users = UserRepository.getAllUsers(); // Call the static method from UserRepository
		if (users != null && !users.isEmpty()) {
			return Response.ok(users).build(); // HTTP 200 OK
		} else {
			return Response.status(204).entity("No users found.").build(); // HTTP 204 No Content if no users
		}
	}


	//This endpoint fetches a single user based on their username. This is useful for actions like `handleCreateInvoiceAction` where you need details about a specific client.
	@GET
	@Path("/{username}") // Path parameter for the username
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUserByUsername(@PathParam("username") String username) {
		
		User user = usersRepo.findUserByUsernameObject(username); 
		
		if (user != null) {
			return Response.ok(user).build(); // HTTP 200 OK with the User object
		} else {
			return Response.status(404).entity("User not found.").build(); // HTTP 404 Not Found
		}
	}


	//This endpoint is used to change a user's status to "active." The `UserRepository.approveRequest(int i)` method uses an index, but for a RESTful API, it's better to use the username directly
	@PUT
	@Path("/approve/{username}") // Path parameter for the username
	public Response approveUser(@PathParam("username") String username) {
		
		if (usersRepo.approveUserByUsername(username)) { 
			return Response.ok("User '" + username + "' approved successfully.").build(); // HTTP 200 OK
		} else {
			return Response.status(400).entity("Failed to approve user '" + username + "'. User might not be pending or does not exist.").build(); // HTTP 400 Bad Request
		}
	}


	//This endpoint sets a user's status to "blocked." Similar to `approveUser`, this assumes a `blockUserByUsername(String username)` method in `UserRepository`.
	@PUT
	@Path("/block/{username}") // Path parameter for the username
	public Response blockUser(@PathParam("username") String username) {
		
		if (usersRepo.blockUserByUsername(username)) { 
			return Response.ok("User '" + username + "' blocked successfully.").build(); // HTTP 200 OK
		} else {
			return Response.status(400).entity("Failed to block user '" + username + "'. User might not be active or does not exist.").build(); // HTTP 400 Bad Request
		}
	}


	//This endpoint removes a user from the system. It assumes a `deleteUserByUsername(String username)` method in `UserRepository`.
	@DELETE
	@Path("/{username}") // Path parameter for the username
	public Response deleteUser(@PathParam("username") String username) {
		
		if (usersRepo.deleteUserByUsername(username)) { 
			return Response.ok("User '" + username + "' deleted successfully.").build(); // HTTP 200 OK
		} else {
			return Response.status(404).entity("Failed to delete user '" + username + "'. User not found or cannot be deleted in current status.").build(); // HTTP 404 Not Found
		}
	}
}