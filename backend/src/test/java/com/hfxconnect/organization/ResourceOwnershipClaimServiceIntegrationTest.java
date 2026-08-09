package com.hfxconnect.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidContributionStatusException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceNotFoundException;
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
class ResourceOwnershipClaimServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private ResourceOwnershipClaimService claimService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void aNonVerifiedOrganizationCannotClaim() {
		User owner = persistUser(Role.ORGANIZATION);
		organizationService.create(principal(owner), request("Not Verified Org"));
		CommunityResource resource = activeUnownedResource("Not Verified Target");

		assertThatThrownBy(() -> claimService.create(principal(owner), resource.getId()))
				.isInstanceOf(OrganizationNotVerifiedException.class);
	}

	@Test
	void aVerifiedOrganizationCanClaimAnActiveUnownedResource() {
		User owner = verifiedOrganizationOwner("Verified Claimant Org");
		CommunityResource resource = activeUnownedResource("Claimable Target");

		OwnershipClaimResponse response = claimService.create(principal(owner), resource.getId());

		assertThat(response.status()).isEqualTo(ResourceOwnershipClaimStatus.PENDING_REVIEW);
		assertThat(response.resource().id()).isEqualTo(resource.getId());
	}

	@Test
	void claimingANonexistentResourceThrowsNotFound() {
		User owner = verifiedOrganizationOwner("Nonexistent Target Org");

		assertThatThrownBy(() -> claimService.create(principal(owner), UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void claimingAnInactiveResourceIsRejected() {
		User owner = verifiedOrganizationOwner("Inactive Target Org");
		CommunityResource resource = activeUnownedResource("Inactive Target");
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		assertThatThrownBy(() -> claimService.create(principal(owner), resource.getId()))
				.isInstanceOf(InactiveResourceException.class);
	}

	@Test
	void claimingAnAlreadyOwnedResourceIsAConflict() {
		User firstOwner = verifiedOrganizationOwner("First Owner Org");
		User secondOwner = verifiedOrganizationOwner("Second Owner Org");
		CommunityResource resource = activeUnownedResource("Already Owned Target");
		claimService.create(principal(firstOwner), resource.getId());
		Organization firstOrganization = organizationRepository.findByOwnerUserId(firstOwner.getId()).orElseThrow();
		resource.assignOrganization(firstOrganization.getId());
		resourceRepository.saveAndFlush(resource);

		assertThatThrownBy(() -> claimService.create(principal(secondOwner), resource.getId()))
				.isInstanceOf(ResourceAlreadyOwnedException.class);
	}

	@Test
	void aDuplicatePendingClaimByTheSameOrganizationIsRejected() {
		User owner = verifiedOrganizationOwner("Duplicate Pending Org");
		CommunityResource resource = activeUnownedResource("Duplicate Pending Target");
		claimService.create(principal(owner), resource.getId());

		assertThatThrownBy(() -> claimService.create(principal(owner), resource.getId()))
				.isInstanceOf(ResourceOwnershipClaimConflictException.class);
	}

	@Test
	void listOnlyReturnsTheCallingOrganizationsOwnClaims() {
		User ownerA = verifiedOrganizationOwner("List Scope Org A");
		User ownerB = verifiedOrganizationOwner("List Scope Org B");
		CommunityResource resourceA = activeUnownedResource("List Scope Target A");
		CommunityResource resourceB = activeUnownedResource("List Scope Target B");
		claimService.create(principal(ownerA), resourceA.getId());
		claimService.create(principal(ownerB), resourceB.getId());

		OwnershipClaimPageResponse listA = claimService.list(ownerA.getId(), null, 0, 20);

		assertThat(listA.content()).hasSize(1);
		assertThat(listA.content().getFirst().resource().id()).isEqualTo(resourceA.getId());
	}

	@Test
	void withdrawOnlyWorksForThePendingOwnClaim() {
		User owner = verifiedOrganizationOwner("Withdraw Org");
		CommunityResource resource = activeUnownedResource("Withdraw Target");
		OwnershipClaimResponse created = claimService.create(principal(owner), resource.getId());

		OwnershipClaimResponse withdrawn = claimService.withdraw(principal(owner), created.id());

		assertThat(withdrawn.status()).isEqualTo(ResourceOwnershipClaimStatus.WITHDRAWN);
	}

	@Test
	void withdrawingAnotherOrganizationsClaimThrowsNotFound() {
		User ownerA = verifiedOrganizationOwner("Withdraw Foreign Org A");
		User ownerB = verifiedOrganizationOwner("Withdraw Foreign Org B");
		CommunityResource resource = activeUnownedResource("Withdraw Foreign Target");
		OwnershipClaimResponse created = claimService.create(principal(ownerA), resource.getId());

		assertThatThrownBy(() -> claimService.withdraw(principal(ownerB), created.id()))
				.isInstanceOf(ResourceOwnershipClaimNotFoundException.class);
	}

	@Test
	void withdrawingAnAlreadyDecidedClaimIsRejected() {
		User owner = verifiedOrganizationOwner("Withdraw Decided Org");
		CommunityResource resource = activeUnownedResource("Withdraw Decided Target");
		OwnershipClaimResponse created = claimService.create(principal(owner), resource.getId());
		claimService.withdraw(principal(owner), created.id());

		assertThatThrownBy(() -> claimService.withdraw(principal(owner), created.id()))
				.isInstanceOf(InvalidContributionStatusException.class);
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
		Category category = categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "claim-cat-" + marker, null));
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
