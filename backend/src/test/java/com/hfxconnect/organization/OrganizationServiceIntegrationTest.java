package com.hfxconnect.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.security.CurrentUserPrincipal;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrganizationServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void createStartsPendingVerificationAndGeneratesASlug() {
		User owner = persistUser(Role.ORGANIZATION);

		OrganizationResponse response = organizationService.create(principal(owner), request("Halifax Newcomer Services"));

		assertThat(response.verificationStatus()).isEqualTo(OrganizationVerificationStatus.PENDING_VERIFICATION);
		assertThat(response.slug()).isEqualTo("halifax-newcomer-services");
		assertThat(response.verifiedAt()).isNull();
	}

	@Test
	void aSecondProfileForTheSameAccountIsAConflict() {
		User owner = persistUser(Role.ORGANIZATION);
		organizationService.create(principal(owner), request("First Profile Org"));

		assertThatThrownBy(() -> organizationService.create(principal(owner), request("Second Profile Attempt")))
				.isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void aDuplicateGeneratedSlugIsAConflict() {
		User firstOwner = persistUser(Role.ORGANIZATION);
		User secondOwner = persistUser(Role.ORGANIZATION);
		organizationService.create(principal(firstOwner), request("Duplicate Slug Org"));

		assertThatThrownBy(() -> organizationService.create(principal(secondOwner), request("Duplicate Slug Org")))
				.isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void blankNameIsRejected() {
		User owner = persistUser(Role.ORGANIZATION);

		assertThatThrownBy(() -> organizationService.create(principal(owner), request("   ")))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void getCurrentReturnsTheOwnersOwnProfile() {
		User owner = persistUser(Role.ORGANIZATION);
		OrganizationResponse created = organizationService.create(principal(owner), request("Get Current Org"));

		OrganizationResponse fetched = organizationService.getCurrent(owner.getId());

		assertThat(fetched.id()).isEqualTo(created.id());
	}

	@Test
	void getCurrentWithNoProfileThrowsNotFound() {
		User owner = persistUser(Role.ORGANIZATION);

		assertThatThrownBy(() -> organizationService.getCurrent(owner.getId()))
				.isInstanceOf(OrganizationNotFoundException.class);
	}

	@Test
	void contactOnlyUpdateDoesNotResetAnAlreadyVerifiedOrganization() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		organizationService.create(principal(owner), request("Contact Update Org"));
		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		organization.verify(admin.getId(), "Confirmed against public registry.");
		organizationRepository.saveAndFlush(organization);

		OrganizationResponse updated = organizationService.update(owner.getId(),
				new OrganizationProfileRequest("Contact Update Org", "An updated description.", "https://example.org",
						"new-contact@example.org", "902-555-0199", "123 Main St", "Halifax", "NS", "B3H 4R2"));

		assertThat(updated.verificationStatus()).isEqualTo(OrganizationVerificationStatus.VERIFIED);
	}

	@Test
	void changingNameOnAVerifiedOrganizationResetsToPendingVerification() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		organizationService.create(principal(owner), request("Name Reset Org"));
		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		organization.verify(admin.getId(), "Confirmed against public registry.");
		organizationRepository.saveAndFlush(organization);

		OrganizationResponse updated = organizationService.update(owner.getId(), request("Name Reset Org Renamed"));

		assertThat(updated.verificationStatus()).isEqualTo(OrganizationVerificationStatus.PENDING_VERIFICATION);
		assertThat(updated.verifiedAt()).isNull();
		assertThat(updated.verificationReason()).isNull();
	}

	@Test
	void changingWebsiteOnAVerifiedOrganizationResetsToPendingVerification() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		organizationService.create(principal(owner),
				new OrganizationProfileRequest("Website Reset Org", null, "https://old.example.org", null, null, null, null, null, null));
		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		organization.verify(admin.getId(), "Confirmed.");
		organizationRepository.saveAndFlush(organization);

		OrganizationResponse updated = organizationService.update(owner.getId(),
				new OrganizationProfileRequest("Website Reset Org", null, "https://new.example.org", null, null, null, null, null, null));

		assertThat(updated.verificationStatus()).isEqualTo(OrganizationVerificationStatus.PENDING_VERIFICATION);
	}

	@Test
	void updateNeverChangesTheSlug() {
		User owner = persistUser(Role.ORGANIZATION);
		OrganizationResponse created = organizationService.create(principal(owner), request("Slug Stability Org"));

		OrganizationResponse updated = organizationService.update(owner.getId(), request("Totally Different Name"));

		assertThat(updated.slug()).isEqualTo(created.slug());
	}

	@Test
	void getPublicBySlugReturnsOnlyVerifiedOrganizations() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Public Verified Org"));

		assertThatThrownBy(() -> organizationService.getPublicBySlug(created.slug()))
				.isInstanceOf(OrganizationNotFoundException.class);

		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		organization.verify(admin.getId(), "Confirmed.");
		organizationRepository.saveAndFlush(organization);

		PublicOrganizationResponse publicResponse = organizationService.getPublicBySlug(created.slug());
		assertThat(publicResponse.name()).isEqualTo("Public Verified Org");
	}

	@Test
	void getPublicBySlugForAnUnknownSlugThrowsNotFound() {
		assertThatThrownBy(() -> organizationService.getPublicBySlug("no-such-slug-" + UUID.randomUUID()))
				.isInstanceOf(OrganizationNotFoundException.class);
	}

	private OrganizationProfileRequest request(String name) {
		return new OrganizationProfileRequest(name, "A description.", "https://example.org", "contact@example.org",
				"902-555-0123", "123 Main St", "Halifax", "NS", "B3H 4R2");
	}

	private CurrentUserPrincipal principal(User user) {
		return new CurrentUserPrincipal(user.getId(), user.getEmail(), user.getRole());
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

}
