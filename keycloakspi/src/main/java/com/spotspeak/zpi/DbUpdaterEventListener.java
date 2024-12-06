package com.spotspeak.zpi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessToken.Access;
import org.keycloak.services.Urls;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class DbUpdaterEventListener implements EventListenerProvider {

	private KeycloakSession session;

	public DbUpdaterEventListener(KeycloakSession session) {
		this.session = session;
	}

	@Override
	public void close() {
	}

	@Override
	public void onEvent(Event event) {
		switch (event.getType()) {
			case REGISTER:
				insertUserOnRegister(event);
				break;

			default:
				return;
		}
	}

	@Override
	public void onEvent(AdminEvent arg0, boolean arg1) {
	}

	private void insertUserOnRegister(Event registerEvent) {
		assert registerEvent.getType() == EventType.REGISTER;

		UserModel user = session.users().getUserById(
				session.realms().getRealm(registerEvent.getRealmId()),
				registerEvent.getUserId());

		RegisteredUserDTO registeredUser = createRegistrationDTO(user);

		String accessToken = getAccessToken(user, session);
		System.out.println("TOKEN");
		System.out.println(accessToken);
		notifyBackendAboutRegistration(registeredUser, accessToken);
	}

	private void notifyBackendAboutRegistration(RegisteredUserDTO registeredUser, String accessToken) {
		System.out.println("Notifying backend about registration...");
		HttpClient client = HttpClient.newHttpClient();
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());

		try {
			System.out.println("id: " + registeredUser.id());
			System.out.println("firstName: " + registeredUser.firstName());
			System.out.println("lastName: " + registeredUser.lastName());
			System.out.println("email: " + registeredUser.email());
			System.out.println("username: " + registeredUser.username());
			System.out.println("registrationDate: " + LocalDateTime.now());

			String requestBody = objectMapper.writeValueAsString(registeredUser);
			System.out.println("Serialized JSON Payload: " + requestBody); // Log the payload

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(Config.INSERT_USER_ENDPOINT))
					.headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println("Response status code: " + response.statusCode());
		} catch (IOException | InterruptedException e) {
			System.out.println("Failed to send request " + e.getMessage());
		}
	}

	private RegisteredUserDTO createRegistrationDTO(UserModel user) {

		RegisteredUserDTO registeredUser = new RegisteredUserDTO(
				UUID.fromString(user.getId()),
				user.getFirstName(),
				user.getLastName(),
				user.getEmail(),
				user.getUsername(),
				LocalDateTime.now());

		return registeredUser;
	}

	public String getAccessToken(UserModel userModel, KeycloakSession keycloakSession) {
		KeycloakContext keycloakContext = keycloakSession.getContext();

		AccessToken token = new AccessToken();
		token.subject(userModel.getId());
		token.issuer(Urls.realmIssuer(keycloakContext.getUri().getBaseUri(), keycloakContext.getRealm().getName()));
		token.issuedNow();
		token.exp((long) (token.getIat() + 60L)); // Lifetime of 60 seconds

		Access access = new Access();
		access.addRole("INITIALIZE_ACCOUNT");
		token.setRealmAccess(access);

		KeyWrapper key = keycloakSession.keys().getActiveKey(keycloakContext.getRealm(), KeyUse.SIG,
				"RS256");

		return new JWSBuilder().kid(key.getKid()).type("JWT").jsonContent(token)
				.sign(new AsymmetricSignatureSignerContext(key));
	}
}
