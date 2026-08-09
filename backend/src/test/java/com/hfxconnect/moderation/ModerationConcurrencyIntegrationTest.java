package com.hfxconnect.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.ContributionAlreadyReviewedException;
import com.hfxconnect.common.text.SlugGenerator;
import com.hfxconnect.correctionreport.CorrectionReportCreateRequest;
import com.hfxconnect.correctionreport.CorrectionReportResponse;
import com.hfxconnect.correctionreport.CorrectionReportService;
import com.hfxconnect.correctionreport.IssueType;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.resourcesubmission.ResourceSubmissionCreateRequest;
import com.hfxconnect.resourcesubmission.ResourceSubmissionResponse;
import com.hfxconnect.resourcesubmission.ResourceSubmissionService;
import com.hfxconnect.security.CurrentUserPrincipal;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.List;
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
 * Real concurrent-review safety (Milestone 9A, ADR-016) — two moderators
 * racing to decide the same pending contribution. Deliberately NOT
 * {@code @Transactional} at the class level: each service call below must
 * commit in its own real transaction (using its own pooled connection) for
 * the {@code PESSIMISTIC_WRITE} row lock to genuinely serialize two
 * concurrent transactions, rather than two nested calls sharing one
 * never-committed test transaction.
 */
class ModerationConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ResourceSubmissionService submissionService;

	@Autowired
	private ResourceSubmissionReviewService submissionReviewService;

	@Autowired
	private CorrectionReportService reportService;

	@Autowired
	private CorrectionReportReviewService correctionReviewService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void onlyOneOfTwoConcurrentSubmissionApprovalsSucceedsAndExactlyOneResourceIsCreated() throws InterruptedException {
		User contributor = persistUser(Role.USER);
		User moderatorA = persistUser(Role.MODERATOR);
		User moderatorB = persistUser(Role.MODERATOR);
		Category category = activeCategory("Concurrency Submission Check");
		String name = "Concurrent Submission Item " + UUID.randomUUID();
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(),
				new ResourceSubmissionCreateRequest(category.getId(), name, "A short description.", null,
						"123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null));

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		Runnable attemptA = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				submissionReviewService.approve(principal(moderatorA), submission.id(),
						new ModerationDecisionRequest("Racing to approve first."));
				successCount.incrementAndGet();
			} catch (ContributionAlreadyReviewedException ex) {
				conflictCount.incrementAndGet();
			}
		};
		Runnable attemptB = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				submissionReviewService.approve(principal(moderatorB), submission.id(),
						new ModerationDecisionRequest("Racing to approve second."));
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

		List<CommunityResource> matching = resourceRepository.findAll().stream()
				.filter(r -> r.getName().equals(name)).toList();
		assertThat(matching).hasSize(1);
	}

	@Test
	void onlyOneOfTwoConcurrentCorrectionApprovalsSucceedsAndFieldIsAppliedExactlyOnce() throws InterruptedException {
		User reporter = persistUser(Role.USER);
		User moderatorA = persistUser(Role.MODERATOR);
		User moderatorB = persistUser(Role.MODERATOR);
		String marker = UUID.randomUUID().toString();
		Category category = categoryRepository.save(new Category("Concurrency Correction Cat " + marker,
				("concurrency correction cat " + marker).toLowerCase(Locale.ROOT), "mod-conc-cr-cat-" + marker, null));
		CommunityResource resource = resourceRepository.saveAndFlush(new CommunityResource(category,
				"Concurrency Correction Target " + marker, SlugGenerator.generate("Concurrency Correction Target " + marker).orElseThrow(),
				"An existing description.", "1 Original St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null));
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(),
				new CorrectionReportCreateRequest(IssueType.ADDRESS, "The address is stale.", null, null,
						"999 Concurrent Ave", null, null, null, null, null, null, null, null, null, null));

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		Runnable attemptA = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				correctionReviewService.approve(principal(moderatorA), report.id(),
						new CorrectionApprovalRequest("Racing to approve first.", true, false));
				successCount.incrementAndGet();
			} catch (ContributionAlreadyReviewedException ex) {
				conflictCount.incrementAndGet();
			}
		};
		Runnable attemptB = () -> {
			ready.countDown();
			awaitUninterruptibly(go);
			try {
				correctionReviewService.approve(principal(moderatorB), report.id(),
						new CorrectionApprovalRequest("Racing to approve second.", true, false));
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
		CommunityResource updated = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(updated.getAddressLine1()).isEqualTo("999 Concurrent Ave");
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private CurrentUserPrincipal principal(User user) {
		return new CurrentUserPrincipal(user.getId(), user.getEmail(), user.getRole());
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

	private Category activeCategory(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "mod-conc-rs-cat-" + marker, null));
	}

}
