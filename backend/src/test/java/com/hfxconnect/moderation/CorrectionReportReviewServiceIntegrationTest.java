package com.hfxconnect.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.text.SlugGenerator;
import com.hfxconnect.correctionreport.CorrectionReportCreateRequest;
import com.hfxconnect.correctionreport.CorrectionReportResponse;
import com.hfxconnect.correctionreport.CorrectionReportService;
import com.hfxconnect.correctionreport.CorrectionReportStatus;
import com.hfxconnect.correctionreport.IssueType;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.resource.VerificationStatus;
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
class CorrectionReportReviewServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private CorrectionReportService reportService;

	@Autowired
	private CorrectionReportReviewService reviewService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void approveWithApplyProposedChangesUpdatesTheResourceAndMarksVerified() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Apply Changes Target");
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(),
				addressReport("456 New Ave"));

		CorrectionReportModerationDetailResponse result = reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Confirmed the new address.", true, false));

		assertThat(result.status()).isEqualTo(CorrectionReportStatus.APPROVED);
		assertThat(result.appliedToResourceAt()).isNotNull();
		CommunityResource updated = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(updated.getAddressLine1()).isEqualTo("456 New Ave");
		assertThat(updated.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(updated.getLastVerifiedAt()).isNotNull();
	}

	@Test
	void approveWithNeitherFlagLeavesTheResourceUnchangedButStillApproves() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("No-Op Approval Target");
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(),
				addressReport("789 Unused Ave"));

		CorrectionReportModerationDetailResponse result = reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Acknowledged but not applying automatically.", false, false));

		assertThat(result.status()).isEqualTo(CorrectionReportStatus.APPROVED);
		assertThat(result.appliedToResourceAt()).isNull();
		CommunityResource unchanged = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(unchanged.getAddressLine1()).isEqualTo("1 Original St");
	}

	@Test
	void deactivateResourceRequiresResourceClosedIssueType() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Invalid Deactivation Target");
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), addressReport("1 X St"));

		assertThatThrownBy(() -> reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Trying to close for the wrong issue type.", false, true)))
				.isInstanceOf(InvalidDeactivationRequestException.class);
	}

	@Test
	void resourceClosedApprovalWithDeactivateTrueDeactivatesTheResource() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Closed Resource Target");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.RESOURCE_CLOSED, "This organization has permanently closed.", null, null, null, null,
				null, null, null, null, null, null, null, null, null);
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), request);

		CorrectionReportModerationDetailResponse result = reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Confirmed closed.", false, true));

		assertThat(result.status()).isEqualTo(CorrectionReportStatus.APPROVED);
		CommunityResource deactivated = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(deactivated.isActive()).isFalse();
	}

	@Test
	void applyProposedChangesIsUnsupportedForOperatingHours() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Operating Hours Target");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.OPERATING_HOURS, "The listed hours are wrong.", null, null, null, null,
				null, null, null, null, null, null, null, null, null);
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), request);

		assertThatThrownBy(() -> reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Trying to auto-apply hours.", true, false)))
				.isInstanceOf(UnsupportedCorrectionApplicationException.class);
	}

	@Test
	void applyProposedChangesIsUnsupportedForDuplicateResource() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Duplicate Resource Target");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.DUPLICATE_RESOURCE, "This is the same place as another listing.", null, null, null, null,
				null, null, null, null, null, null, null, null, null);
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), request);

		assertThatThrownBy(() -> reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Trying to auto-merge.", true, false)))
				.isInstanceOf(UnsupportedCorrectionApplicationException.class);
	}

	@Test
	void selfReviewIsBlockedForApproveAndReject() {
		User reporterModerator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Self Review Correction Target");
		CorrectionReportResponse report = reportService.create(reporterModerator.getId(), resource.getId(), addressReport("2 Self St"));

		assertThatThrownBy(() -> reviewService.approve(principal(reporterModerator), report.id(),
				new CorrectionApprovalRequest("Approving my own report.", true, false)))
				.isInstanceOf(SelfReviewNotAllowedException.class);
		assertThatThrownBy(() -> reviewService.reject(principal(reporterModerator), report.id(),
				new ModerationDecisionRequest("Rejecting my own report.")))
				.isInstanceOf(SelfReviewNotAllowedException.class);
	}

	@Test
	void reviewingAnAlreadyDecidedReportThrowsConflict() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Already Decided Correction Target");
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), addressReport("3 Done St"));
		reviewService.reject(principal(moderator), report.id(), new ModerationDecisionRequest("Not a valid correction."));

		User secondModerator = persistUser(Role.MODERATOR);
		assertThatThrownBy(() -> reviewService.approve(principal(secondModerator), report.id(),
				new CorrectionApprovalRequest("Too late.", true, false)))
				.isInstanceOf(ContributionAlreadyReviewedException.class);
	}

	@Test
	void rejectNeverModifiesTheTargetResource() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Reject Preserves Target");
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), addressReport("999 Rejected Ave"));

		reviewService.reject(principal(moderator), report.id(), new ModerationDecisionRequest("Explanation does not match proposal."));

		CommunityResource unchanged = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(unchanged.getAddressLine1()).isEqualTo("1 Original St");
	}

	@Test
	void onlyProposedFieldsAreChangedOthersArePreserved() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("Partial Field Update Target");
		String originalPhone = resource.getPhone();
		CorrectionReportResponse report = reportService.create(reporter.getId(), resource.getId(), addressReport("55 Partial St"));

		reviewService.approve(principal(moderator), report.id(),
				new CorrectionApprovalRequest("Only the address changed.", true, false));

		CommunityResource updated = resourceRepository.findById(resource.getId()).orElseThrow();
		assertThat(updated.getAddressLine1()).isEqualTo("55 Partial St");
		assertThat(updated.getPhone()).isEqualTo(originalPhone);
		assertThat(updated.getName()).isEqualTo(resource.getName());
	}

	private CorrectionReportCreateRequest addressReport(String proposedAddressLine1) {
		return new CorrectionReportCreateRequest(IssueType.ADDRESS, "The address on file is out of date.",
				null, null, proposedAddressLine1, null, null, null, null, null, null, null, null, null, null);
	}

	private CommunityResource persistResource(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		Category category = categoryRepository.save(
				new Category(name + " Cat", (name + " cat").toLowerCase(Locale.ROOT), "mod-cr-cat-" + marker, null));
		CommunityResource resource = new CommunityResource(category, name,
				SlugGenerator.generate(name).orElseThrow(), "An existing description.", "1 Original St", null,
				"Halifax", "NS", "B3H 4R2", "9025551234", "contact@example.org", "https://example.org",
				CostType.FREE, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

	private CurrentUserPrincipal principal(User user) {
		return new CurrentUserPrincipal(user.getId(), user.getEmail(), user.getRole());
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

}
