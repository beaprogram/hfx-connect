package com.hfxconnect.correctionreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
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

@AutoConfigureTestRestTemplate
class CorrectionReportApiIntegrationTest extends AbstractPostgresIntegrationTest {

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

	@Test
	void createWithNoTokenIsRejected() {
		CommunityResource resource = resource("No Token Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), jsonHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void createAsUserSucceeds() {
		assertCreateSucceeds(Role.USER);
	}

	@Test
	void createAsOrganizationSucceeds() {
		assertCreateSucceeds(Role.ORGANIZATION);
	}

	@Test
	void createAsModeratorSucceeds() {
		assertCreateSucceeds(Role.MODERATOR);
	}

	@Test
	void createAsAdminSucceeds() {
		assertCreateSucceeds(Role.ADMIN);
	}

	private void assertCreateSucceeds(Role role) {
		User user = persistUser(role);
		CommunityResource resource = resource(role + " Create Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).contains("\"status\":\"PENDING_REVIEW\"");
	}

	@Test
	void missingResourceReturnsNotFound() {
		User user = persistUser(Role.USER);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + UUID.randomUUID() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void inactiveResourceReturnsNotFoundNotADifferentError() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Inactive API Check");
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void blankExplanationReturnsValidationError() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Blank Explanation API Check");
		String body = "{\"issueType\":\"OTHER\",\"explanation\":\"\"}";

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(body, authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void duplicatePendingReturnsConflict() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Duplicate API Check");
		restTemplate.exchange("/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("\"code\":\"CORRECTION_REPORT_CONFLICT\"");
	}

	@Test
	void listReturnsOnlyOwnReports() {
		User owner = persistUser(Role.USER);
		User other = persistUser(Role.USER);
		CommunityResource resource = resource("List Isolation API Check");
		restTemplate.exchange("/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(owner)), String.class);
		restTemplate.exchange("/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(String.format(Locale.ROOT, "{\"issueType\":\"COST\",\"explanation\":\"Different issue.\"}"), authedJsonHeaders(other)), String.class);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/correction-reports", HttpMethod.GET, new HttpEntity<>(null, authHeaders(owner)), String.class);

		assertThat(response.getBody()).doesNotContain("\"issueType\":\"COST\"");
	}

	@Test
	void anotherUsersReportDetailReturnsNotFound() {
		User owner = persistUser(Role.USER);
		User other = persistUser(Role.USER);
		CommunityResource resource = resource("Detail Isolation API Check");
		ResponseEntity<String> created = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(owner)), String.class);
		String id = created.getBody().split("\"id\":\"")[1].split("\"")[0];

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/correction-reports/" + id, HttpMethod.GET, new HttpEntity<>(null, authHeaders(other)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void withdrawOwnReportSucceeds() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Withdraw API Check");
		ResponseEntity<String> created = restTemplate.exchange(
				"/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);
		String id = created.getBody().split("\"id\":\"")[1].split("\"")[0];

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/correction-reports/" + id + "/withdraw", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"WITHDRAWN\"");
	}

	@Test
	void publicResourceReadNeverIncludesReports() {
		User user = persistUser(Role.USER);
		CommunityResource resource = resource("Not Public Check");
		restTemplate.exchange("/api/v1/resources/" + resource.getId() + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(createBody(), authedJsonHeaders(user)), String.class);

		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources/" + resource.getId(), String.class);

		assertThat(response.getBody()).doesNotContain("correctionReport").doesNotContain("PENDING_REVIEW");
	}

	private String createBody() {
		return "{\"issueType\":\"GENERAL_INFORMATION\",\"explanation\":\"This information looks outdated.\"}";
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

	private HttpHeaders authedJsonHeaders(User user) {
		HttpHeaders headers = authHeaders(user);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

	private CommunityResource resource(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		Category category = categoryRepository.save(new Category(
				namePrefix + " Cat " + marker, (namePrefix + " Cat " + marker).toLowerCase(Locale.ROOT), "cr-api-cat-" + marker, null));
		CommunityResource resource = new CommunityResource(category, namePrefix + " " + marker, "cr-api-res-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

}
