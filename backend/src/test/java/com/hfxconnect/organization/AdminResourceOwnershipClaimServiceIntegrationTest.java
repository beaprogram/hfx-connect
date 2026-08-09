package com.hfxconnect.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.ContributionAlreadyReviewedException;
import com.hfxconnect.common.error.SelfReviewNotAllowedException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.security.CurrentUserPrincipal;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminResourceOwnershipClaimServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private ResourceOwnershipClaimService claimService;

	@Autowired
	private AdminResourceOwnershipClaimService adminClaimService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void approveAssignsOwnershipAndMarksTheClaimApproved() {
		User admin = persistUser(Role.ADMIN);
		User owner = verifiedOrganizationOwner("Approve Owner Org");
		CommunityResource resource = activeUnownedResource("Approve Target");
		OwnershipClaimResponse claim = claimService.create(principal(owner), resource.getId());

		AdminOwnershipClaimDetailResponse result = adminClaimService.approve(
				principal(admin), claim.id(), new OrganizationDecisionRequest("Confirmed ownership documentation."));

		assertThat(result.status()).isEqualTo(ResourceOwnershipClaimStatus.APPROVED);
		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		CommunityResource reloaded = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(reloaded.getOrganizationId()).isEqualTo(organization.getId());
	}

	@Test
	void rejectLeavesTheResourceUnowned() {
		User admin = persistUser(Role.ADMIN);
		User owner = verifiedOrganizationOwner("Reject Owner Org");
		CommunityResource resource = activeUnownedResource("Reject Target");
		OwnershipClaimResponse claim = claimService.create(principal(owner), resource.getId());

		AdminOwnershipClaimDetailResponse result = adminClaimService.reject(
				principal(admin), claim.id(), new OrganizationDecisionRequest("Could not confirm ownership."));

		assertThat(result.status()).isEqualTo(ResourceOwnershipClaimStatus.REJECTED);
		CommunityResource reloaded = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(reloaded.getOrganizationId()).isNull();
	}

	@Test
	void approvalIsBlockedIfTheOrganizationIsNoLongerVerified() {
		User admin = persistUser(Role.ADMIN);
		User owner = verifiedOrganizationOwner("Suspended Between Org");
		CommunityResource resource = activeUnownedResource("Suspended Between Target");
		OwnershipClaimResponse claim = claimService.create(principal(owner), resource.getId());
		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		organization.suspend(admin.getId(), "Suspended before the claim could be decided.");
		organizationRepository.saveAndFlush(organization);

		assertThatThrownBy(() -> adminClaimService.approve(
				principal(admin), claim.id(), new OrganizationDecisionRequest("Attempting approval anyway.")))
				.isInstanceOf(OrganizationNotVerifiedException.class);
	}

	@Test
	void approvalIsBlockedIfTheResourceBecameOwnedInTheMeantime() {
		User admin = persistUser(Role.ADMIN);
		User owner = verifiedOrganizationOwner("Race Owner Org");
		User otherOwner = verifiedOrganizationOwner("Race Other Org");
		CommunityResource resource = activeUnownedResource("Race Target");
		OwnershipClaimResponse claim = claimService.create(principal(owner), resource.getId());
		Organization otherOrganization = organizationRepository.findByOwnerUserId(otherOwner.getId()).orElseThrow();
		CommunityResource reloaded = resourceRepository.findById(resource.getId()).orElseThrow();
		reloaded.assignOrganization(otherOrganization.getId());
		resourceRepository.saveAndFlush(reloaded);

		assertThatThrownBy(() -> adminClaimService.approve(
				principal(admin), claim.id(), new OrganizationDecisionRequest("Trying to approve a stale claim.")))
				.isInstanceOf(ResourceAlreadyOwnedException.class);
	}

	@Test
	void selfReviewIsBlockedForApproveAndReject() {
		User ownerAdmin = persistUser(Role.ADMIN);
		organizationService.create(new CurrentUserPrincipal(ownerAdmin.getId(), ownerAdmin.getEmail(), Role.ORGANIZATION),
				request("Self Review Claim Org"));
		Organization organization = organizationRepository.findByOwnerUserId(ownerAdmin.getId()).orElseThrow();
		organization.verify(persistUser(Role.ADMIN).getId(), "Confirmed for test setup.");
		organizationRepository.saveAndFlush(organization);
		CommunityResource resource = activeUnownedResource("Self Review Claim Target");
		OwnershipClaimResponse claim = claimService.create(
				new CurrentUserPrincipal(ownerAdmin.getId(), ownerAdmin.getEmail(), Role.ORGANIZATION), resource.getId());

		assertThatThrownBy(() -> adminClaimService.approve(
				principal(ownerAdmin), claim.id(), new OrganizationDecisionRequest("Approving my own organization's claim.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
		assertThatThrownBy(() -> adminClaimService.reject(
				principal(ownerAdmin), claim.id(), new OrganizationDecisionRequest("Rejecting my own organization's claim.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
	}

	@Test
	void decidingAnAlreadyDecidedClaimThrowsConflict() {
		User admin = persistUser(Role.ADMIN);
		User owner = verifiedOrganizationOwner("Already Decided Claim Org");
		CommunityResource resource = activeUnownedResource("Already Decided Claim Target");
		OwnershipClaimResponse claim = claimService.create(principal(owner), resource.getId());
		adminClaimService.approve(principal(admin), claim.id(), new OrganizationDecisionRequest("First decision."));

		assertThatThrownBy(() -> adminClaimService.reject(
				principal(admin), claim.id(), new OrganizationDecisionRequest("Second decision.")))
				.isInstanceOf(ContributionAlreadyReviewedException.class);
	}

	@Test
	void queueDefaultsToPendingReviewOldestFirst() {
		User admin = persistUser(Role.ADMIN);
		User ownerA = verifiedOrganizationOwner("Queue Claim Org A");
		User ownerB = verifiedOrganizationOwner("Queue Claim Org B");
		CommunityResource resourceA = activeUnownedResource("Queue Claim Target A");
		CommunityResource resourceB = activeUnownedResource("Queue Claim Target B");
		OwnershipClaimResponse first = claimService.create(principal(ownerA), resourceA.getId());
		OwnershipClaimResponse second = claimService.create(principal(ownerB), resourceB.getId());
		adminClaimService.approve(principal(admin), first.id(), new OrganizationDecisionRequest("Approved, leaves the default queue."));

		AdminOwnershipClaimQueuePageResponse queue = adminClaimService.queue(null, null, 0, 20);

		assertThat(queue.content()).extracting(AdminOwnershipClaimQueueItemResponse::id).contains(second.id())
				.doesNotContain(first.id());
	}

	@Test
	void detailShowsTheClaimingOrganizationAndCurrentResourceOwnership() {
		User admin = persistUser(Role.ADMIN);
		User owner = verifiedOrganizationOwner("Detail Claim Org");
		CommunityResource resource = activeUnownedResource("Detail Claim Target");
		OwnershipClaimResponse claim = claimService.create(principal(owner), resource.getId());
		adminClaimService.approve(principal(admin), claim.id(), new OrganizationDecisionRequest("Confirmed."));

		AdminOwnershipClaimDetailResponse detail = adminClaimService.detail(claim.id());

		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		assertThat(detail.currentResourceOrganizationId()).isEqualTo(organization.getId());
		assertThat(detail.organizationId()).isEqualTo(organization.getId());
	}

	private User verifiedOrganizationOwner(String orgName) {
		User owner = persistUser(Role.ORGANIZATION);
		User admin = persistUser(Role.ADMIN);
		organizationService.create(principal(owner), request(orgName));
		Organization organization = organizationRepository.findByOwnerUserId(owner.getId()).orElseThrow();
		organization.verify(admin.getId(), "Confirmed for test setup.");
		organizationRepository.saveAndFlush(organization);
		return owner;
	}

	private CommunityResource activeUnownedResource(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		Category category = categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "admin-claim-cat-" + marker, null));
		return resourceRepository.saveAndFlush(new CommunityResource(category, name,
				com.hfxconnect.common.text.SlugGenerator.generate(name).orElseThrow(), "A resource available to claim.",
				"1 Claimable St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null));
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
