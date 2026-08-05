package com.hfxconnect.correctionreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidContributionStatusException;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceNotFoundException;
import com.hfxconnect.resource.ResourceRepository;
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
class CorrectionReportServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private CorrectionReportService reportService;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void createsAPendingReport() {
		User user = persistUser();
		CommunityResource resource = activeResource("Create Check");

		CorrectionReportResponse response = reportService.create(user.getId(), resource.getId(), request(IssueType.ADDRESS));

		assertThat(response.status()).isEqualTo(CorrectionReportStatus.PENDING_REVIEW);
		assertThat(response.issueType()).isEqualTo(IssueType.ADDRESS);
		assertThat(response.resource().resourceId()).isEqualTo(resource.getId());
		assertThat(response.resource().name()).isEqualTo(resource.getName());
	}

	@Test
	void missingResourceThrows() {
		User user = persistUser();
		assertThatThrownBy(() -> reportService.create(user.getId(), UUID.randomUUID(), request(IssueType.OTHER)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void inactiveResourceThrows() {
		User user = persistUser();
		CommunityResource resource = activeResource("Inactive Check");
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		assertThatThrownBy(() -> reportService.create(user.getId(), resource.getId(), request(IssueType.OTHER)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void blankExplanationThrowsValidationException() {
		User user = persistUser();
		CommunityResource resource = activeResource("Blank Explanation Check");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.OTHER, "   ", null, null, null, null, null, null, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> reportService.create(user.getId(), resource.getId(), request))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void explanationOnlyReportRequiresNoProposedFields() {
		User user = persistUser();
		CommunityResource resource = activeResource("Explanation Only Check");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.RESOURCE_CLOSED, "This location has permanently closed.", null, null, null, null, null,
				null, null, null, null, null, null, null, null);

		CorrectionReportResponse response = reportService.create(user.getId(), resource.getId(), request);

		assertThat(response.issueType()).isEqualTo(IssueType.RESOURCE_CLOSED);
		assertThat(response.proposedName()).isNull();
	}

	@Test
	void invalidProposedPostalCodeThrows() {
		User user = persistUser();
		CommunityResource resource = activeResource("Bad Proposed Postal Check");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.ADDRESS, "The postal code is wrong.", null, null, null, null, null, "NOTREAL", null, null,
				null, null, null, null, null);

		assertThatThrownBy(() -> reportService.create(user.getId(), resource.getId(), request))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void invalidProposedEmailThrows() {
		User user = persistUser();
		CommunityResource resource = activeResource("Bad Proposed Email Check");
		CorrectionReportCreateRequest request = new CorrectionReportCreateRequest(
				IssueType.CONTACT_INFORMATION, "The email is wrong.", null, null, null, null, null, null, null,
				"not-an-email", null, null, null, null, null);

		assertThatThrownBy(() -> reportService.create(user.getId(), resource.getId(), request))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void duplicatePendingReportThrowsConflict() {
		User user = persistUser();
		CommunityResource resource = activeResource("Duplicate Service Check");
		reportService.create(user.getId(), resource.getId(), request(IssueType.COST));

		assertThatThrownBy(() -> reportService.create(user.getId(), resource.getId(), request(IssueType.COST)))
				.isInstanceOf(CorrectionReportConflictException.class);
	}

	@Test
	void listReturnsOnlyOwnersReports() {
		User owner = persistUser();
		User other = persistUser();
		CommunityResource resource = activeResource("List Ownership Check");
		reportService.create(owner.getId(), resource.getId(), request(IssueType.ADDRESS));
		reportService.create(other.getId(), resource.getId(), request(IssueType.CONTACT_INFORMATION));

		CorrectionReportPageResponse page = reportService.list(owner.getId(), 0, 20, null);

		assertThat(page.content()).hasSize(1);
	}

	@Test
	void negativePageThrows() {
		User user = persistUser();
		assertThatThrownBy(() -> reportService.list(user.getId(), -1, 20, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void invalidSortThrows() {
		User user = persistUser();
		assertThatThrownBy(() -> reportService.list(user.getId(), 0, 20, "notReal"))
				.isInstanceOf(InvalidSortException.class);
	}

	@Test
	void getAnotherUsersReportThrowsNotFound() {
		User owner = persistUser();
		User other = persistUser();
		CommunityResource resource = activeResource("Get Isolation Check");
		CorrectionReportResponse created = reportService.create(owner.getId(), resource.getId(), request(IssueType.OTHER));

		assertThatThrownBy(() -> reportService.get(other.getId(), created.id()))
				.isInstanceOf(CorrectionReportNotFoundException.class);
	}

	@Test
	void withdrawSetsStatusAndTimestamp() {
		User user = persistUser();
		CommunityResource resource = activeResource("Withdraw Check");
		CorrectionReportResponse created = reportService.create(user.getId(), resource.getId(), request(IssueType.OTHER));

		CorrectionReportResponse withdrawn = reportService.withdraw(user.getId(), created.id());

		assertThat(withdrawn.status()).isEqualTo(CorrectionReportStatus.WITHDRAWN);
		assertThat(withdrawn.withdrawnAt()).isNotNull();
	}

	@Test
	void withdrawingTwiceThrows() {
		User user = persistUser();
		CommunityResource resource = activeResource("Double Withdraw Check");
		CorrectionReportResponse created = reportService.create(user.getId(), resource.getId(), request(IssueType.OTHER));
		reportService.withdraw(user.getId(), created.id());

		assertThatThrownBy(() -> reportService.withdraw(user.getId(), created.id()))
				.isInstanceOf(InvalidContributionStatusException.class);
	}

	@Test
	void withdrawingAllowsReReportingTheSameIssueType() {
		User user = persistUser();
		CommunityResource resource = activeResource("Re-Report Check");
		CorrectionReportResponse created = reportService.create(user.getId(), resource.getId(), request(IssueType.OTHER));
		reportService.withdraw(user.getId(), created.id());

		CorrectionReportResponse reReported = reportService.create(user.getId(), resource.getId(), request(IssueType.OTHER));

		assertThat(reReported.id()).isNotEqualTo(created.id());
	}

	private CorrectionReportCreateRequest request(IssueType issueType) {
		return new CorrectionReportCreateRequest(issueType, "Something looks wrong with this listing.", null, null,
				null, null, null, null, null, null, null, null, null, null, null);
	}

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(Role.USER));
	}

	private CommunityResource activeResource(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		Category category = categoryRepository.save(new Category(
				namePrefix + " Cat " + marker, (namePrefix + " Cat " + marker).toLowerCase(Locale.ROOT), "cr-svc-cat-" + marker, null));
		CommunityResource resource = new CommunityResource(category, namePrefix + " " + marker, "cr-svc-res-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

}
