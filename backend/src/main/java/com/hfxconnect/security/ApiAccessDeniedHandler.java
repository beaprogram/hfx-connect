package com.hfxconnect.security;

import com.hfxconnect.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes this project's standard {@link ApiError} shape for an authenticated
 * caller whose role does not permit the requested action, instead of Spring
 * Security's own default HTML/plain-text response. Deliberately generic
 * ("You do not have permission...") — never echoes back the specific
 * authorization expression or role that was required, which is internal
 * policy detail, not something a rejected caller needs. See ADR-009.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws IOException {
		ApiError body = ApiError.of(
				HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", "You do not have permission to perform this action.");
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}

}
