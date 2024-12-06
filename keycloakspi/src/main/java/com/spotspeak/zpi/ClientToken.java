package com.spotspeak.zpi;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

public class ClientToken {

	public static String getClientAccessToken() {

		Keycloak keycloak = KeycloakBuilder.builder()
				.realm(Config.REALM_NAME)
				.serverUrl(Config.SERVER_URL)
				.clientId(Config.CLIENT_ID)
				.clientSecret(Config.CLIENT_SECRET)
				.grantType("client_credentials")
				.build();

		String token = keycloak.tokenManager().getAccessTokenString();
		return token;
	}
}
