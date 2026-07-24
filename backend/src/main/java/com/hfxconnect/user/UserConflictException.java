package com.hfxconnect.user;

import com.hfxconnect.common.error.ConflictException;

public class UserConflictException extends ConflictException {

	private UserConflictException(String message) {
		super("USER_CONFLICT", message);
	}

	static UserConflictException duplicateEmail() {
		return new UserConflictException("An account with this email address already exists.");
	}

}
