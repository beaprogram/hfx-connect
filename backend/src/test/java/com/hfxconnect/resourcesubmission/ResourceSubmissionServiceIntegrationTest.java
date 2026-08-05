package com.hfxconnect.resourcesubmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidContributionStatusException;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.resource.CategoryNotFoundException;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.InactiveCategoryException;
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
class ResourceSubmissionServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ResourceSubmissionService submissionService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void createsAPendingSubmission() {
		User user = persistUser();
		Category category = activeCategory("Create Check");

		ResourceSubmissionResponse response = submissionService.create(user.getId(), request(category.getId(), "Halifax Central Library"));

		assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING_REVIEW);
		assertThat(response.name()).isEqualTo("Halifax Central Library");
		assertThat(response.category().id()).isEqualTo(category.getId());
		assertThat(response.submittedAt()).isNotNull();
		assertThat(response.withdrawnAt()).isNull();
	}

	@Test
	void missingCategoryThrows() {
		User user = persistUser();

		assertThatThrownBy(() -> submissionService.create(user.getId(), request(999999999L, "No Such Category")))
				.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void inactiveCategoryThrows() {
		User user = persistUser();
		Category category = activeCategory("Inactive Check");
		category.deactivate();
		categoryRepository.saveAndFlush(category);

		assertThatThrownBy(() -> submissionService.create(user.getId(), request(category.getId(), "Inactive Category Item")))
				.isInstanceOf(InactiveCategoryException.class);
	}

	@Test
	void blankNameThrowsValidationException() {
		User user = persistUser();
		Category category = activeCategory("Blank Name Check");

		assertThatThrownBy(() -> submissionService.create(user.getId(), request(category.getId(), "   ")))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void invalidPostalCodeThrowsValidationException() {
		User user = persistUser();
		Category category = activeCategory("Bad Postal Check");
		ResourceSubmissionCreateRequest request = new ResourceSubmissionCreateRequest(
				category.getId(), "Bad Postal Item", "A short description.", null, "123 Main St", null, "Halifax",
				"NS", "NOTREAL", null, null, null, CostType.UNKNOWN, null, null);

		assertThatThrownBy(() -> submissionService.create(user.getId(), request))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void unsafeWebsiteUrlSchemeIsRejected() {
		User user = persistUser();
		Category category = activeCategory("Unsafe URL Check");
		ResourceSubmissionCreateRequest request = new ResourceSubmissionCreateRequest(
				category.getId(), "Unsafe URL Item", "A short description.", null, "123 Main St", null, "Halifax",
				"NS", "B3H 4R2", null, null, "javascript:alert(1)", CostType.UNKNOWN, null, null);

		assertThatThrownBy(() -> submissionService.create(user.getId(), request))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void missingCostTypeDefaultsToUnknown() {
		User user = persistUser();
		Category category = activeCategory("Default Cost Check");
		ResourceSubmissionCreateRequest request = new ResourceSubmissionCreateRequest(
				category.getId(), "No Cost Type Item", "A short description.", null, "123 Main St", null, "Halifax",
				"NS", "B3H 4R2", null, null, null, null, null, null);

		ResourceSubmissionResponse response = submissionService.create(user.getId(), request);

		assertThat(response.costType()).isEqualTo(CostType.UNKNOWN);
	}

	@Test
	void duplicatePendingSubmissionThrowsConflict() {
		User user = persistUser();
		Category category = activeCategory("Duplicate Service Check");
		submissionService.create(user.getId(), request(category.getId(), "Duplicate Item"));

		assertThatThrownBy(() -> submissionService.create(user.getId(), request(category.getId(), "Duplicate Item")))
				.isInstanceOf(ResourceSubmissionConflictException.class);
	}

	@Test
	void duplicateCheckIsCaseInsensitive() {
		User user = persistUser();
		Category category = activeCategory("Case Insensitive Check");
		submissionService.create(user.getId(), request(category.getId(), "Mixed Case Item"));

		assertThatThrownBy(() -> submissionService.create(user.getId(), request(category.getId(), "MIXED CASE ITEM")))
				.isInstanceOf(ResourceSubmissionConflictException.class);
	}

	@Test
	void listReturnsOnlyOwnersSubmissions() {
		User owner = persistUser();
		User other = persistUser();
		Category category = activeCategory("List Ownership Check");
		String marker = UUID.randomUUID().toString();
		submissionService.create(owner.getId(), request(category.getId(), "Owned " + marker));
		submissionService.create(other.getId(), request(category.getId(), "Not Owned " + marker));

		ResourceSubmissionPageResponse page = submissionService.list(owner.getId(), 0, 20, null);

		assertThat(page.content()).extracting(ResourceSubmissionResponse::name).containsExactly("Owned " + marker);
	}

	@Test
	void negativePageThrows() {
		User user = persistUser();
		assertThatThrownBy(() -> submissionService.list(user.getId(), -1, 20, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void oversizedPageThrows() {
		User user = persistUser();
		assertThatThrownBy(() -> submissionService.list(user.getId(), 0, 500, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void invalidSortThrows() {
		User user = persistUser();
		assertThatThrownBy(() -> submissionService.list(user.getId(), 0, 20, "notARealSortField"))
				.isInstanceOf(InvalidSortException.class);
	}

	@Test
	void getReturnsOwnedSubmission() {
		User user = persistUser();
		Category category = activeCategory("Get Check");
		ResourceSubmissionResponse created = submissionService.create(user.getId(), request(category.getId(), "Get Me"));

		ResourceSubmissionResponse fetched = submissionService.get(user.getId(), created.id());

		assertThat(fetched.id()).isEqualTo(created.id());
	}

	@Test
	void getAnotherUsersSubmissionThrowsNotFound() {
		User owner = persistUser();
		User other = persistUser();
		Category category = activeCategory("Get Isolation Check");
		ResourceSubmissionResponse created = submissionService.create(owner.getId(), request(category.getId(), "Not Yours"));

		assertThatThrownBy(() -> submissionService.get(other.getId(), created.id()))
				.isInstanceOf(ResourceSubmissionNotFoundException.class);
	}

	@Test
	void getNonexistentSubmissionThrowsNotFound() {
		User user = persistUser();
		assertThatThrownBy(() -> submissionService.get(user.getId(), UUID.randomUUID()))
				.isInstanceOf(ResourceSubmissionNotFoundException.class);
	}

	@Test
	void withdrawSetsStatusAndTimestamp() {
		User user = persistUser();
		Category category = activeCategory("Withdraw Check");
		ResourceSubmissionResponse created = submissionService.create(user.getId(), request(category.getId(), "Withdraw Me"));

		ResourceSubmissionResponse withdrawn = submissionService.withdraw(user.getId(), created.id());

		assertThat(withdrawn.status()).isEqualTo(SubmissionStatus.WITHDRAWN);
		assertThat(withdrawn.withdrawnAt()).isNotNull();
	}

	@Test
	void withdrawingTwiceThrows() {
		User user = persistUser();
		Category category = activeCategory("Double Withdraw Check");
		ResourceSubmissionResponse created = submissionService.create(user.getId(), request(category.getId(), "Double Withdraw"));
		submissionService.withdraw(user.getId(), created.id());

		assertThatThrownBy(() -> submissionService.withdraw(user.getId(), created.id()))
				.isInstanceOf(InvalidContributionStatusException.class);
	}

	@Test
	void withdrawingAnotherUsersSubmissionThrowsNotFound() {
		User owner = persistUser();
		User other = persistUser();
		Category category = activeCategory("Withdraw Isolation Check");
		ResourceSubmissionResponse created = submissionService.create(owner.getId(), request(category.getId(), "Not Yours To Withdraw"));

		assertThatThrownBy(() -> submissionService.withdraw(other.getId(), created.id()))
				.isInstanceOf(ResourceSubmissionNotFoundException.class);
	}

	@Test
	void withdrawingAllowsResubmittingTheSameNameAndCategory() {
		User user = persistUser();
		Category category = activeCategory("Resubmit Check");
		ResourceSubmissionResponse created = submissionService.create(user.getId(), request(category.getId(), "Resubmit Item"));
		submissionService.withdraw(user.getId(), created.id());

		ResourceSubmissionResponse resubmitted = submissionService.create(user.getId(), request(category.getId(), "Resubmit Item"));

		assertThat(resubmitted.id()).isNotEqualTo(created.id());
	}

	private ResourceSubmissionCreateRequest request(Long categoryId, String name) {
		return new ResourceSubmissionCreateRequest(categoryId, name, "A short description.", null, "123 Main St",
				null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null);
	}

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(Role.USER));
	}

	private Category activeCategory(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "rs-svc-cat-" + marker, null));
	}

}
