package com.spotspeak.zpi;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegisteredUserDTO(
		UUID id,
		String firstName,
		String lastName,
		String email,
		String username,
		LocalDateTime registeredAt) {
}
