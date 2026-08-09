package com.hfxconnect.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.ContributionAlreadyReviewedException;
import com.hfxconnect.common.text.SlugGenerator;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real concurrent-decision safety for the organization domain (Milestone
 * 10A, ADR-017) — deliberately NOT {@code @Transactional} at the class
 * level, the same reasoning {@code
 * com.hfxconnect.moderation.ModerationConcurrencyIntegrationTest} already
 * established: each service call must commit in its own real transaction
 * for the {@code PESSIMISTIC_WRITE} row locks to genuinely serialize two
 * concurrent transactions.
 */
class OrganizationConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private AdminOrganizationService adminOrganizationService;

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
	void onlyOneOfTwoConcurrentOrganizationVerificationsSucceeds() throws InterruptedException {
		User owner = persistUser(Role.ORGANIZATION);
		User adminA = persistUser(Role.ADMIN);
		User adminB = persistUser(Role.ADMIN);
		OrganizationResponse created = organizationService.create(principal(owner), request("Concurrent Verify Org"));

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		Runnable attemptA = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				adminOrganizationService.verify(principal(adminA), created.id(), new OrganizationDecisionRequest("Racing to verify first."));
				successCount.incrementAndGet();
			} catch (ContributionAlreadyReviewedException ex) {
				conflictCount.incrementAndGet();
			}
		};
		Runnable attemptB = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				adminOrganizationService.verify(principal(adminB), created.id(), new OrganizationDecisionRequest("Racing to verify second."));
				successCount.incrementAndGet();
			} catch (ContributionAlreadyReviewedException ex) {
				conflictCount.incrementAndGet();
			}
		};

		executor.submit(attemptA);
		executor.submit(attemptB);
		ready.await(10, TimeUnit.SECONDS);
		go.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(conflictCount.get()).isEqualTo(1);
		Organization organization = organizationRepository.findById(created.id()).orElseThrow();
		assertThat(organization.getVerificationStatus()).isEqualTo(OrganizationVerificationStatus.VERIFIED);
	}

	/**
	 * Two different organizations, two different claims (two different
	 * claim rows), racing to become the owner of the exact same resource.
	 * Locking only the claim row would never serialize these two
	 * transactions against each other — this is the scenario {@link
	 * AdminResourceOwnershipClaimService#approve}'s resource-row lock
	 * exists to make safe. See ADR-017's "Concurrency Strategy" section.
	 */
	@Test
	void onlyOneOfTwoClaimsRacingForTheSameResourceCanBeApproved() throws InterruptedException {
		User admin = persistUser(Role.ADMIN);
		User ownerA = verifiedOrganizationOwner("Race Verify Org A");
		User ownerB = verifiedOrganizationOwner("Race Verify Org B");
		CommunityResource resource = activeUnownedResource("Race Verify Target");
		OwnershipClaimResponse claimA = claimService.create(principal(ownerA), resource.getId());
		OwnershipClaimResponse claimB = claimService.create(principal(ownerB), resource.getId());

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		Runnable attemptA = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				adminClaimService.approve(principal(admin), claimA.id(), new OrganizationDecisionRequest("Racing to approve claim A."));
				successCount.incrementAndGet();
			} catch (ResourceAlreadyOwnedException ex) {
				conflictCount.incrementAndGet();
			}
		};
		Runnable attemptB = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				adminClaimService.approve(principal(admin), claimB.id(), new OrganizationDecisionRequest("Racing to approve claim B."));
				successCount.incrementAndGet();
			} catch (ResourceAlreadyOwnedException ex) {
				conflictCount.incrementAndGet();
			}
		};

		executor.submit(attemptA);
		executor.submit(attemptB);
		ready.await(10, TimeUnit.SECONDS);
		go.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(conflictCount.get()).isEqualTo(1);

		CommunityResource reloaded = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(reloaded.getOrganizationId()).isNotNull();
		Organization organizationA = organizationRepository.findByOwnerUserId(ownerA.getId()).orElseThrow();
		Organization organizationB = organizationRepository.findByOwnerUserId(ownerB.getId()).orElseThrow();
		assertThat(reloaded.getOrganizationId()).isIn(organizationA.getId(), organizationB.getId());
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
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
		Category category = categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "conc-claim-cat-" + marker, null));
		return resourceRepository.saveAndFlush(new CommunityResource(category, name,
				SlugGenerator.generate(name).orElseThrow(), "A resource available to claim.",
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
