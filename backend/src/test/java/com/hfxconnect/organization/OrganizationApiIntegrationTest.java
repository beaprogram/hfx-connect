package com.hfxconnect.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.text.SlugGenerator;
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

/**
 * Full HTTP-layer tests for every organization/ownership-claim route
 * (Milestone 10A) — the role matrix, the public-vs-private field split, and
 * the end-to-end profile-creation-to-verification-to-claim-to-ownership
 * flow, following {@code ModerationResourceSubmissionApiIntegrationTest}'s
 * established pattern.
 */
@AutoConfigureTestRestTemplate
class OrganizationApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	@Test
	void createProfileWithNoTokenIsUnauthorized() {
		ResponseEntity<String> response = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.POST,
				new HttpEntity<>(profileBody("No Token Org"), jsonHeaders()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void createProfileAsUserIsForbidden() {
		assertCreateStatus(Role.USER, HttpStatus.FORBIDDEN);
	}

	@Test
	void createProfileAsModeratorIsForbidden() {
		assertCreateStatus(Role.MODERATOR, HttpStatus.FORBIDDEN);
	}

	@Test
	void createProfileAsAdminIsForbidden() {
		assertCreateStatus(Role.ADMIN, HttpStatus.FORBIDDEN);
	}

	@Test
	void createProfileAsOrganizationSucceeds() {
		assertCreateStatus(Role.ORGANIZATION, HttpStatus.CREATED);
	}

	private void assertCreateStatus(Role role, HttpStatus expected) {
		User user = persistUser(role);
		ResponseEntity<String> response = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.POST,
				new HttpEntity<>(profileBody("Role Matrix Org " + role), authedJsonHeaders(user)), String.class);
		assertThat(response.getStatusCode()).isEqualTo(expected);
	}

	@Test
	void adminQueueAsModeratorIsForbidden() {
		User moderator = persistUser(Role.MODERATOR);
		ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/organizations", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(moderator)), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void adminQueueAsAdminSucceeds() {
		User admin = persistUser(Role.ADMIN);
		ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/organizations", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(admin)), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void publicProfileForAPendingOrganizationIs404() {
		User owner = persistUser(Role.ORGANIZATION);
		ResponseEntity<String> createResponse = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.POST,
				new HttpEntity<>(profileBody("Pending Public Org"), authedJsonHeaders(owner)), String.class);
		String slug = extractField(createResponse.getBody(), "slug");

		ResponseEntity<String> publicResponse = restTemplate.getForEntity("/api/v1/organizations/" + slug, String.class);

		assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void selfVerificationReturnsForbiddenWithTheSharedErrorCode() {
		// Role authorization reads the account's *current* DB role on every
		// request (see JwtAuthenticationFilter) — so to have one account
		// author a profile and then attempt to verify it as an ADMIN, the
		// account's DB role must actually change between the two calls, not
		// just the token's claim.
		User owner = persistUser(Role.ORGANIZATION);
		ResponseEntity<String> createResponse = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.POST,
				new HttpEntity<>(profileBody("Self Verify API Org"), authedJsonHeaders(owner)), String.class);
		String id = extractField(createResponse.getBody(), "id");

		User ownerNowAdmin = userRepository.saveAndFlush(
				TestUserFactory.withChangedRoleAndStatus(owner, Role.ADMIN, com.hfxconnect.user.AccountStatus.ACTIVE));

		ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/organizations/" + id + "/verify", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Approving my own organization.\"}", authedJsonHeaders(ownerNowAdmin)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"SELF_REVIEW_NOT_ALLOWED\"");
	}

	@Test
	void fullFlowFromProfileCreationToVerificationToClaimToOwnership() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		Category category = category("Full Org Flow Check");
		CommunityResource resource = activeUnownedResource("Full Org Flow Target", category);

		ResponseEntity<String> createResponse = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.POST,
				new HttpEntity<>(profileBody("Full Flow Org"), authedJsonHeaders(owner)), String.class);
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(createResponse.getBody()).contains("\"verificationStatus\":\"PENDING_VERIFICATION\"");
		String organizationId = extractField(createResponse.getBody(), "id");

		ResponseEntity<String> verifyResponse = restTemplate.exchange(
				"/api/v1/admin/organizations/" + organizationId + "/verify", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed against public registry.\"}", authedJsonHeaders(admin)), String.class);
		assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(verifyResponse.getBody()).contains("\"verificationStatus\":\"VERIFIED\"");

		ResponseEntity<String> claimResponse = restTemplate.exchange(
				"/api/v1/organizations/me/resource-claims/" + resource.getId(), HttpMethod.POST,
				new HttpEntity<>(null, authedJsonHeaders(owner)), String.class);
		assertThat(claimResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String claimId = extractField(claimResponse.getBody(), "id");

		ResponseEntity<String> approveResponse = restTemplate.exchange(
				"/api/v1/admin/resource-ownership-claims/" + claimId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed ownership documentation.\"}", authedJsonHeaders(admin)), String.class);
		assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(approveResponse.getBody()).contains("\"status\":\"APPROVED\"");

		ResponseEntity<String> ownedResources = restTemplate.exchange("/api/v1/organizations/me/resources", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(owner)), String.class);
		assertThat(ownedResources.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ownedResources.getBody()).contains(resource.getId().toString());

		ResponseEntity<String> publicDetail = restTemplate.getForEntity("/api/v1/resources/slug/" + resource.getSlug(), String.class);
		assertThat(publicDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(publicDetail.getBody())
				.contains("\"organization\"")
				.contains("\"name\":\"Full Flow Org\"")
				.contains("\"verified\":true");

		ResponseEntity<String> publicOrgProfile = restTemplate.getForEntity(
				"/api/v1/organizations/" + extractField(createResponse.getBody(), "slug"), String.class);
		assertThat(publicOrgProfile.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void claimingAResourceAlreadyOwnedReturnsConflictWithTheSharedCode() {
		User admin = persistUser(Role.ADMIN);
		User ownerA = persistUser(Role.ORGANIZATION);
		User ownerB = persistUser(Role.ORGANIZATION);
		Category category = category("Claim Conflict API Check");
		CommunityResource resource = activeUnownedResource("Claim Conflict API Target", category);

		verifyOrganization(ownerA, "Claim Conflict Org A", admin);
		verifyOrganization(ownerB, "Claim Conflict Org B", admin);

		String claimAId = extractField(restTemplate.exchange(
				"/api/v1/organizations/me/resource-claims/" + resource.getId(), HttpMethod.POST,
				new HttpEntity<>(null, authedJsonHeaders(ownerA)), String.class).getBody(), "id");
		restTemplate.exchange("/api/v1/admin/resource-ownership-claims/" + claimAId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed.\"}", authedJsonHeaders(admin)), String.class);

		ResponseEntity<String> secondClaimAttempt = restTemplate.exchange(
				"/api/v1/organizations/me/resource-claims/" + resource.getId(), HttpMethod.POST,
				new HttpEntity<>(null, authedJsonHeaders(ownerB)), String.class);

		assertThat(secondClaimAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(secondClaimAttempt.getBody()).contains("\"code\":\"RESOURCE_ALREADY_OWNED\"");
	}

	@Test
	void suspendedOrganizationsAttributionIsOmittedButResourceStaysPublic() {
		User admin = persistUser(Role.ADMIN);
		User owner = persistUser(Role.ORGANIZATION);
		Category category = category("Suspended Attribution API Check");
		CommunityResource resource = activeUnownedResource("Suspended Attribution API Target", category);
		verifyOrganization(owner, "Suspended Attribution Org", admin);
		String claimId = extractField(restTemplate.exchange(
				"/api/v1/organizations/me/resource-claims/" + resource.getId(), HttpMethod.POST,
				new HttpEntity<>(null, authedJsonHeaders(owner)), String.class).getBody(), "id");
		restTemplate.exchange("/api/v1/admin/resource-ownership-claims/" + claimId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed.\"}", authedJsonHeaders(admin)), String.class);
		String organizationId = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(owner)), String.class).getBody();
		String orgId = extractField(organizationId, "id");
		restTemplate.exchange("/api/v1/admin/organizations/" + orgId + "/suspend", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Repeated misrepresentation complaints.\"}", authedJsonHeaders(admin)), String.class);

		ResponseEntity<String> publicDetail = restTemplate.getForEntity("/api/v1/resources/slug/" + resource.getSlug(), String.class);

		assertThat(publicDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(publicDetail.getBody()).contains("\"organization\":null");
	}

	private void verifyOrganization(User owner, String name, User admin) {
		ResponseEntity<String> createResponse = restTemplate.exchange("/api/v1/organizations/me", HttpMethod.POST,
				new HttpEntity<>(profileBody(name), authedJsonHeaders(owner)), String.class);
		String organizationId = extractField(createResponse.getBody(), "id");
		restTemplate.exchange("/api/v1/admin/organizations/" + organizationId + "/verify", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed.\"}", authedJsonHeaders(admin)), String.class);
	}

	private String profileBody(String name) {
		return String.format(Locale.ROOT,
				"{\"name\":\"%s\",\"description\":\"A description.\",\"websiteUrl\":\"https://example.org\","
						+ "\"publicEmail\":\"contact@example.org\",\"phone\":\"902-555-0123\",\"addressLine1\":\"123 Main St\","
						+ "\"city\":\"Halifax\",\"province\":\"NS\",\"postalCode\":\"B3H 4R2\"}",
				name);
	}

	private String extractField(String body, String field) {
		return body.split("\"" + field + "\":\"")[1].split("\"")[0];
	}

	private CommunityResource activeUnownedResource(String namePrefix, Category category) {
		String name = namePrefix + " " + UUID.randomUUID();
		return resourceRepository.saveAndFlush(new CommunityResource(category, name,
				SlugGenerator.generate(name).orElseThrow(), "A resource available to claim.",
				"1 Claimable St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null));
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "org-api-cat-" + marker, null));
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

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

}
