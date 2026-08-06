package com.hfxconnect.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.resource.VerificationStatus;
import com.hfxconnect.resourcesubmission.ResourceSubmissionCreateRequest;
import com.hfxconnect.resourcesubmission.ResourceSubmissionResponse;
import com.hfxconnect.resourcesubmission.ResourceSubmissionService;
import com.hfxconnect.resourcesubmission.SubmissionStatus;
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
class ResourceSubmissionReviewServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ResourceSubmissionService submissionService;

	@Autowired
	private ResourceSubmissionReviewService reviewService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void approvePublishesAVerifiedResourceAndMarksSubmissionApproved() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Approve Publish Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Approve Publish Item"));

		ResourceSubmissionModerationDetailResponse result = reviewService.approve(
				principal(moderator), submission.id(), new ModerationDecisionRequest("Verified against the source."));

		assertThat(result.status()).isEqualTo(SubmissionStatus.APPROVED);
		assertThat(result.reviewedByUserId()).isEqualTo(moderator.getId());
		assertThat(result.resultingResource()).isNotNull();
		assertThat(result.resultingResource().name()).isEqualTo("Approve Publish Item");

		var resource = resourceRepository.findById(result.resultingResource().id()).orElseThrow();
		assertThat(resource.isActive()).isTrue();
		assertThat(resource.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(resource.getLastVerifiedAt()).isNotNull();
	}

	@Test
	void approveUsesFullDescriptionWhenPresentOtherwiseShortDescription() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Description Mapping Check");
		ResourceSubmissionCreateRequest request = new ResourceSubmissionCreateRequest(
				category.getId(), "Description Mapping Item", "Short version.", "The much fuller description.",
				"123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null);
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request);

		ResourceSubmissionModerationDetailResponse result = reviewService.approve(
				principal(moderator), submission.id(), new ModerationDecisionRequest("Looks accurate."));

		var resource = resourceRepository.findById(result.resultingResource().id()).orElseThrow();
		assertThat(resource.getDescription()).isEqualTo("The much fuller description.");
	}

	@Test
	void rejectNeverCreatesAResource() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Reject Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Reject Item"));

		ResourceSubmissionModerationDetailResponse result = reviewService.reject(
				principal(moderator), submission.id(), new ModerationDecisionRequest("Duplicate of an existing listing."));

		assertThat(result.status()).isEqualTo(SubmissionStatus.REJECTED);
		assertThat(result.resultingResource()).isNull();
		assertThat(resourceRepository.findAll()).noneMatch(r -> "Reject Item".equals(r.getName()));
	}

	@Test
	void selfReviewIsBlockedForApproveAndReject() {
		User contributorModerator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Self Review Check");
		ResourceSubmissionResponse submission = submissionService.create(
				contributorModerator.getId(), request(category.getId(), "Self Review Item"));

		assertThatThrownBy(() -> reviewService.approve(
				principal(contributorModerator), submission.id(), new ModerationDecisionRequest("Approving my own.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
		assertThatThrownBy(() -> reviewService.reject(
				principal(contributorModerator), submission.id(), new ModerationDecisionRequest("Rejecting my own.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
	}

	@Test
	void adminCannotBypassSelfReview() {
		User contributorAdmin = persistUser(Role.ADMIN);
		Category category = activeCategory("Admin Self Review Check");
		ResourceSubmissionResponse submission = submissionService.create(
				contributorAdmin.getId(), request(category.getId(), "Admin Self Review Item"));

		assertThatThrownBy(() -> reviewService.approve(
				principal(contributorAdmin), submission.id(), new ModerationDecisionRequest("Approving my own as admin.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
	}

	@Test
	void approvingAnAlreadyApprovedSubmissionThrowsConflict() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Already Reviewed Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Already Reviewed Item"));
		reviewService.approve(principal(moderator), submission.id(), new ModerationDecisionRequest("First decision."));

		User secondModerator = persistUser(Role.MODERATOR);
		assertThatThrownBy(() -> reviewService.approve(
				principal(secondModerator), submission.id(), new ModerationDecisionRequest("Second decision.")))
				.isInstanceOf(ContributionAlreadyReviewedException.class);
	}

	@Test
	void reviewingAWithdrawnSubmissionThrowsConflict() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Withdrawn Review Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Withdrawn Review Item"));
		submissionService.withdraw(contributor.getId(), submission.id());

		assertThatThrownBy(() -> reviewService.approve(
				principal(moderator), submission.id(), new ModerationDecisionRequest("Too late.")))
				.isInstanceOf(ContributionAlreadyReviewedException.class);
	}

	@Test
	void approvalConflictingWithAnExistingResourceLeavesSubmissionPending() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Publication Conflict Check");
		String name = "Publication Conflict Item " + UUID.randomUUID();
		resourceRepository.saveAndFlush(new com.hfxconnect.resource.CommunityResource(category, name,
				com.hfxconnect.common.text.SlugGenerator.generate(name).orElseThrow(), "An existing resource.",
				"1 Existing St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null));
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), name));

		assertThatThrownBy(() -> reviewService.approve(
				principal(moderator), submission.id(), new ModerationDecisionRequest("Trying to publish a duplicate.")))
				.isInstanceOf(ResourcePublicationConflictException.class);

		ResourceSubmissionResponse stillPending = submissionService.get(contributor.getId(), submission.id());
		assertThat(stillPending.status()).isEqualTo(SubmissionStatus.PENDING_REVIEW);
	}

	@Test
	void blankReasonIsRejected() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Blank Reason Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Blank Reason Item"));

		assertThatThrownBy(() -> reviewService.approve(principal(moderator), submission.id(), new ModerationDecisionRequest("  ")))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void tooShortReasonIsRejected() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Short Reason Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Short Reason Item"));

		assertThatThrownBy(() -> reviewService.approve(principal(moderator), submission.id(), new ModerationDecisionRequest("Hi")))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void queueDefaultsToPendingReviewOldestFirst() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = activeCategory("Queue Default Check");
		String marker = UUID.randomUUID().toString();
		ResourceSubmissionResponse first = submissionService.create(contributor.getId(), request(category.getId(), "Queue First " + marker));
		ResourceSubmissionResponse second = submissionService.create(contributor.getId(), request(category.getId(), "Queue Second " + marker));
		reviewService.approve(principal(moderator), first.id(), new ModerationDecisionRequest("Approved so it should leave the default queue view."));

		ResourceSubmissionQueuePageResponse queue = reviewService.queue(null, category.getId(), 0, 20, null);

		assertThat(queue.content()).extracting(ResourceSubmissionQueueItemResponse::id).containsExactly(second.id());
	}

	@Test
	void detailIsNotOwnerScoped() {
		User contributor = persistUser(Role.USER);
		Category category = activeCategory("Detail Not Owner Scoped Check");
		ResourceSubmissionResponse submission = submissionService.create(contributor.getId(), request(category.getId(), "Any Moderator Can View"));

		ResourceSubmissionModerationDetailResponse detail = reviewService.detail(submission.id());

		assertThat(detail.id()).isEqualTo(submission.id());
	}

	private ResourceSubmissionCreateRequest request(Long categoryId, String name) {
		return new ResourceSubmissionCreateRequest(categoryId, name, "A short description.", null, "123 Main St",
				null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null);
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
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "mod-rs-cat-" + marker, null));
	}

}
