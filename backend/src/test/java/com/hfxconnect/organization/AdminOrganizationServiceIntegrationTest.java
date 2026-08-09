package com.hfxconnect.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.common.error.ContributionAlreadyReviewedException;
import com.hfxconnect.common.error.SelfReviewNotAllowedException;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.security.CurrentUserPrincipal;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminOrganizationServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private AdminOrganizationService adminOrganizationService;

	@Autowired
	private OrganizationAuditQueryService auditQueryService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void verifyMarksTheOrganizationVerifiedAndRecordsWhoAndWhy() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Verify Check Org"));

		AdminOrganizationDetailResponse result = adminOrganizationService.verify(
				principal(admin), created.id(), new OrganizationDecisionRequest("Confirmed against public registry."));

		assertThat(result.verificationStatus()).isEqualTo(OrganizationVerificationStatus.VERIFIED);
		assertThat(result.verifiedByUserId()).isEqualTo(admin.getId());
		assertThat(result.verifiedAt()).isNotNull();
	}

	@Test
	void rejectMarksTheOrganizationRejectedAndKeepsTheProfile() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Reject Check Org"));

		AdminOrganizationDetailResponse result = adminOrganizationService.reject(
				principal(admin), created.id(), new OrganizationDecisionRequest("Could not confirm this organization exists."));

		assertThat(result.verificationStatus()).isEqualTo(OrganizationVerificationStatus.REJECTED);
		assertThat(organizationService.getCurrent(owner.getId()).verificationReason())
				.isEqualTo("Could not confirm this organization exists.");
	}

	@Test
	void suspendIsOnlyLegalFromVerified() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Suspend Precondition Org"));

		assertThatThrownBy(() -> adminOrganizationService.suspend(
				principal(admin), created.id(), new OrganizationDecisionRequest("Attempting to suspend before verification.")))
				.isInstanceOf(com.hfxconnect.common.error.InvalidContributionStatusException.class);
	}

	@Test
	void suspendMarksAVerifiedOrganizationSuspended() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Suspend Check Org"));
		adminOrganizationService.verify(principal(admin), created.id(), new OrganizationDecisionRequest("Confirmed."));

		AdminOrganizationDetailResponse result = adminOrganizationService.suspend(
				principal(admin), created.id(), new OrganizationDecisionRequest("Repeated complaints of misrepresentation."));

		assertThat(result.verificationStatus()).isEqualTo(OrganizationVerificationStatus.SUSPENDED);
	}

	@Test
	void selfVerificationIsBlockedEvenForAnAdminOwner() {
		User ownerAdmin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(
				new CurrentUserPrincipal(ownerAdmin.getId(), ownerAdmin.getEmail(), Role.ORGANIZATION), request("Self Verify Org"));

		assertThatThrownBy(() -> adminOrganizationService.verify(
				principal(ownerAdmin), created.id(), new OrganizationDecisionRequest("Approving my own organization.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
	}

	@Test
	void verifyingAnAlreadyDecidedOrganizationThrowsConflict() {
		User owner = persistUser(Role.ORGANIZATION);
		User adminA = persistUser(Role.ADMIN);
		User adminB = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Already Decided Org"));
		adminOrganizationService.verify(principal(adminA), created.id(), new OrganizationDecisionRequest("First decision."));

		assertThatThrownBy(() -> adminOrganizationService.verify(
				principal(adminB), created.id(), new OrganizationDecisionRequest("Second decision.")))
				.isInstanceOf(ContributionAlreadyReviewedException.class);
	}

	@Test
	void blankReasonIsRejected() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Blank Reason Org"));

		assertThatThrownBy(() -> adminOrganizationService.verify(principal(admin), created.id(), new OrganizationDecisionRequest("  ")))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void queueDefaultsToPendingVerificationOldestFirst() {
		User ownerA = persistUser(Role.ORGANIZATION);
		User ownerB = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse first = organizationService.create(principal(ownerA), request("Queue First Org"));
		OrganizationResponse second = organizationService.create(principal(ownerB), request("Queue Second Org"));
		adminOrganizationService.verify(principal(admin), first.id(), new OrganizationDecisionRequest("Approved, leaves the default queue."));

		AdminOrganizationQueuePageResponse queue = adminOrganizationService.queue(null, 0, 20, null);

		assertThat(queue.content()).extracting(AdminOrganizationQueueItemResponse::id).contains(second.id())
				.doesNotContain(first.id());
	}

	@Test
	void detailIsNotOwnerScoped() {
		User owner = persistUser(Role.ORGANIZATION);
		OrganizationResponse created = organizationService.create(principal(owner), request("Detail Not Owner Scoped Org"));

		AdminOrganizationDetailResponse detail = adminOrganizationService.detail(created.id());

		assertThat(detail.id()).isEqualTo(created.id());
		assertThat(detail.ownerUserId()).isEqualTo(owner.getId());
	}

	@Test
	void auditHistoryRecordsSubmissionAndVerification() {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Audit History Org"));
		adminOrganizationService.verify(principal(admin), created.id(), new OrganizationDecisionRequest("Confirmed."));

		OrganizationAuditEventPageResponse audit = auditQueryService.forOrganization(created.id(), 0, 20);

		assertThat(audit.content()).extracting(OrganizationAuditEventResponse::eventType)
				.containsExactly(OrganizationAuditEventType.ORGANIZATION_SUBMITTED, OrganizationAuditEventType.ORGANIZATION_VERIFIED);
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
