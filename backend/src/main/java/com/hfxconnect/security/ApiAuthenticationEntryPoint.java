package com.hfxconnect.security;

import com.hfxconnect.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes this project's standard {@link ApiError} shape for a request to a
 * protected route with no (or an invalid) access token, instead of Spring
 * Security's own default HTML/plain-text response. Runs outside
 * {@code DispatcherServlet}, so {@code GlobalExceptionHandler} is never in
 * the call path for this case — see ADR-009.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ApiError body = ApiError.of(
				HttpStatus.UNAUTHORIZED.value(), "AUTHENTICATION_REQUIRED", "Authentication is required.");
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}

}
