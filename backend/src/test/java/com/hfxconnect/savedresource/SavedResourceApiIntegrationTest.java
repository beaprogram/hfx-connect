package com.hfxconnect.savedresource;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.user.AccountStatus;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Full HTTP-layer integration tests for {@link SavedResourceController}
 * against the real database — following exactly the pattern
 * {@code ResourceApiIntegrationTest}/{@code AuthorizationMatrixApiIntegrationTest}
 * established. Every role in the authorization matrix
 * ({@code USER}/{@code ORGANIZATION}/{@code MODERATOR}/{@code ADMIN}) is
 * exercised directly here since saving resources has no role restriction
 * beyond "authenticated and ACTIVE" — unlike every other protected write
 * endpoint in this codebase.
 */
@AutoConfigureTestRestTemplate
class SavedResourceApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	// ---- PUT save: authentication/authorization ----

	@Test
	void saveWithNoTokenIsRejected() {
		CommunityResource resource = resource("Save No Token Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.PUT,
				new HttpEntity<>(null, new HttpHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void saveAsUserSucceeds() {
		assertSaveSucceeds(Role.USER);
	}

	@Test
	void saveAsOrganizationSucceeds() {
		assertSaveSucceeds(Role.ORGANIZATION);
	}

	@Test
	void saveAsModeratorSucceeds() {
		assertSaveSucceeds(Role.MODERATOR);
	}

	@Test
	void saveAsAdminSucceeds() {
		assertSaveSucceeds(Role.ADMIN);
	}

	@Test
	void repeatedSaveSucceeds() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Repeated Save API Check");
		HttpHeaders headers = authHeaders(user);

		ResponseEntity<Void> first = putSave(resource.getId(), headers);
		ResponseEntity<Void> second = putSave(resource.getId(), headers);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void savingAMissingResourceReturns404() {
		HttpHeaders headers = authHeaders(persistUser(Role.USER));

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + UUID.randomUUID(), HttpMethod.PUT,
				new HttpEntity<>(null, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void savingAnInactiveResourceReturns404() {
		CommunityResource resource = resource("Save Inactive API Check");
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);
		HttpHeaders headers = authHeaders(persistUser(Role.USER));

		ResponseEntity<String> response = putSave(resource.getId(), headers, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void saveResponseLeaksNoUserOrSecurityData() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Save No Leak Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.PUT,
				new HttpEntity<>(null, authHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(response.getBody()).isNullOrEmpty();
	}

	@Test
	void suspendedAccountIsDeniedEvenWithAPreviouslyIssuedToken() {
		User user = persistUser(Role.USER);
		String token = accessTokenService.issue(user.getId(), Role.USER).token();
		CommunityResource resource = resource("Suspended Save Check");

		userRepository.saveAndFlush(TestUserFactory.withChangedRoleAndStatus(user, Role.USER, AccountStatus.SUSPENDED));

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.PUT,
				new HttpEntity<>(null, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	// ---- DELETE remove ----

	@Test
	void removeWithNoTokenIsRejected() {
		CommunityResource resource = resource("Remove No Token Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.DELETE,
				new HttpEntity<>(null, new HttpHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void removeSucceedsForAnExistingSavedResource() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Remove Existing API Check");
		HttpHeaders headers = authHeaders(user);
		putSave(resource.getId(), headers);

		ResponseEntity<Void> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.DELETE,
				new HttpEntity<>(null, headers), Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void repeatedRemoveSucceeds() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Repeated Remove API Check");
		HttpHeaders headers = authHeaders(user);
		putSave(resource.getId(), headers);

		ResponseEntity<Void> first = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.DELETE,
				new HttpEntity<>(null, headers), Void.class);
		ResponseEntity<Void> second = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.DELETE,
				new HttpEntity<>(null, headers), Void.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void oneUserCannotRemoveAnotherUsersSavedResource() {
		User userA = persistUser(Role.USER);
		User userB = persistUser(Role.USER);
		CommunityResource resource = resource("Remove Isolation API Check");
		putSave(resource.getId(), authHeaders(userA));

		restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resource.getId(), HttpMethod.DELETE,
				new HttpEntity<>(null, authHeaders(userB)), Void.class);

		ResponseEntity<String> listForA = getList(authHeaders(userA));
		assertThat(listForA.getBody()).contains(resource.getId().toString());
	}

	// ---- GET list ----

	@Test
	void listWithNoTokenIsRejected() {
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources", HttpMethod.GET,
				new HttpEntity<>(null, new HttpHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void listReturnsOnlyTheCurrentUsersSavedResources() {
		User userA = persistUser(Role.USER);
		User userB = persistUser(Role.USER);
		CommunityResource resourceA = resource("List API Isolation A");
		CommunityResource resourceB = resource("List API Isolation B");
		putSave(resourceA.getId(), authHeaders(userA));
		putSave(resourceB.getId(), authHeaders(userB));

		ResponseEntity<String> response = getList(authHeaders(userA));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains(resourceA.getId().toString());
		assertThat(response.getBody()).doesNotContain(resourceB.getId().toString());
	}

	@Test
	void listExcludesAnInactiveSavedResource() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("List API Inactive Exclusion");
		putSave(resource.getId(), authHeaders(user));
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		ResponseEntity<String> response = getList(authHeaders(user));

		assertThat(response.getBody()).doesNotContain(resource.getId().toString());
	}

	@Test
	void listResponseNeverIncludesAUserId() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("List API No User Id Check");
		putSave(resource.getId(), authHeaders(user));

		ResponseEntity<String> response = getList(authHeaders(user));

		assertThat(response.getBody()).doesNotContain(user.getId().toString());
	}

	@Test
	void listSupportsPagination() {
		User user = persistUser(Role.USER);
		String marker = UUID.randomUUID().toString();
		HttpHeaders headers = authHeaders(user);
		for (int i = 0; i < 3; i++) {
			CommunityResource resource = resourceNamed("List API Pagination " + marker + " " + i);
			putSave(resource.getId(), headers);
		}

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources?page=0&size=2", HttpMethod.GET,
				new HttpEntity<>(null, headers), String.class);

		assertThat(response.getBody()).contains("\"totalElements\":3", "\"size\":2");
	}

	@Test
	void listSortByNameIsAccepted() {
		User user = persistUser(Role.USER);
		putSave(resource("List API Sort Check").getId(), authHeaders(user));

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources?sort=name", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void listRejectsAnInvalidSortValue() {
		HttpHeaders headers = authHeaders(persistUser(Role.USER));

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/saved-resources?sort=totallyBogus", HttpMethod.GET,
				new HttpEntity<>(null, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_SORT\"");
	}

	@Test
	void listReturnsAnEmptyPageForAUserWithNoSavedResources() {
		HttpHeaders headers = authHeaders(persistUser(Role.USER));

		ResponseEntity<String> response = getList(headers);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"content\":[]", "\"totalElements\":0");
	}

	// ---- POST status ----

	@Test
	void statusWithNoTokenIsRejected() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/users/me/saved-resources/status",
				new HttpEntity<>("{\"resourceIds\":[]}", jsonHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void statusReturnsOnlyTheCurrentUsersSavedIds() {
		User userA = persistUser(Role.USER);
		User userB = persistUser(Role.USER);
		CommunityResource resourceA = resource("Status API A");
		CommunityResource resourceB = resource("Status API B");
		putSave(resourceA.getId(), authHeaders(userA));
		putSave(resourceB.getId(), authHeaders(userB));

		ResponseEntity<String> response = postStatus(
				List.of(resourceA.getId(), resourceB.getId()), authHeaders(userA));

		assertThat(response.getBody()).contains(resourceA.getId().toString());
		assertThat(response.getBody()).doesNotContain(resourceB.getId().toString());
	}

	@Test
	void statusNormalizesDuplicateIds() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Status API Duplicate Check");
		putSave(resource.getId(), authHeaders(user));

		ResponseEntity<String> response = postStatus(
				List.of(resource.getId(), resource.getId()), authHeaders(user));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void statusRejectsMoreThanTheMaximumIds() {
		HttpHeaders headers = authHeaders(persistUser(Role.USER));
		List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID)
				.limit(SavedResourceValidation.MAX_STATUS_IDS + 1)
				.toList();

		ResponseEntity<String> response = postStatus(tooMany, headers);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void statusRejectsAMalformedBody() {
		HttpHeaders headers = authHeaders(persistUser(Role.USER));
		HttpHeaders jsonAuthHeaders = new HttpHeaders();
		jsonAuthHeaders.addAll(headers);
		jsonAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/users/me/saved-resources/status",
				new HttpEntity<>("not valid json", jsonAuthHeaders), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
	}

	@Test
	void statusRejectsAMissingResourceIdsField() {
		HttpHeaders headers = authHeaders(persistUser(Role.USER));
		HttpHeaders jsonAuthHeaders = new HttpHeaders();
		jsonAuthHeaders.addAll(headers);
		jsonAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/users/me/saved-resources/status",
				new HttpEntity<>("{}", jsonAuthHeaders), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	// ---- Security regression: existing routes unaffected ----

	@Test
	void publicCategoryAndResourceGetRoutesRemainPublic() {
		assertThat(restTemplate.getForEntity("/api/v1/categories", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(restTemplate.getForEntity("/api/v1/resources", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
	}

	// ---- Helpers ----

	private void assertSaveSucceeds(Role role) {
		User user = persistUser(role);
		CommunityResource resource = resource("Save " + role + " API Check");

		ResponseEntity<Void> response = putSave(resource.getId(), authHeaders(user));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	private ResponseEntity<Void> putSave(UUID resourceId, HttpHeaders headers) {
		return putSave(resourceId, headers, Void.class);
	}

	private <T> ResponseEntity<T> putSave(UUID resourceId, HttpHeaders headers, Class<T> responseType) {
		return restTemplate.exchange(
				"/api/v1/users/me/saved-resources/" + resourceId, HttpMethod.PUT,
				new HttpEntity<>(null, headers), responseType);
	}

	private ResponseEntity<String> getList(HttpHeaders headers) {
		return restTemplate.exchange(
				"/api/v1/users/me/saved-resources", HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
	}

	private ResponseEntity<String> postStatus(List<UUID> resourceIds, HttpHeaders headers) {
		HttpHeaders jsonAuthHeaders = new HttpHeaders();
		jsonAuthHeaders.addAll(headers);
		jsonAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
		String idsJson = resourceIds.stream().map(id -> "\"" + id + "\"")
				.reduce((a, b) -> a + "," + b).orElse("");
		String body = "{\"resourceIds\":[" + idsJson + "]}";
		return restTemplate.postForEntity(
				"/api/v1/users/me/saved-resources/status", new HttpEntity<>(body, jsonAuthHeaders), String.class);
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders authHeaders(User user) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessTokenService.issue(user.getId(), user.getRole()).token());
		return headers;
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

	private CommunityResource resource(String namePrefix) {
		return resourceNamed(namePrefix);
	}

	private CommunityResource resourceNamed(String name) {
		String marker = UUID.randomUUID().toString();
		Category category = category(name);
		CommunityResource resource = new CommunityResource(category, name + " " + marker, "srapi-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " Category " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "srapi-cat-" + marker, null));
	}

}
