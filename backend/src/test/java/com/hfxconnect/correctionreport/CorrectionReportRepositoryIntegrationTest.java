package com.hfxconnect.correctionreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CorrectionReportRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private CorrectionReportRepository reportRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationCreatedTheCorrectionReportsTable() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_name = 'correction_reports'", Integer.class);
		assertThat(tableCount).isEqualTo(1);
	}

	@Test
	void userForeignKeyIsEnforced() {
		CommunityResource resource = resource("FK Check");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO correction_reports (id, reported_by_user_id, resource_id, resource_name_snapshot, "
						+ "resource_slug_snapshot, issue_type, explanation) VALUES (?, ?, ?, 'X', 'x', 'OTHER', 'issue')",
				UUID.randomUUID(), UUID.randomUUID(), resource.getId()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void resourceDeletionSetsResourceIdToNullAndPreservesTheReport() {
		User user = persistUser();
		CommunityResource resource = resource("Delete Check");
		CorrectionReport report = reportRepository.saveAndFlush(report(user.getId(), resource, IssueType.OTHER));

		// Raw JDBC delete, not resourceRepository.delete(): the same
		// TransientPropertyValueException-avoidance reasoning
		// SavedResourceRepositoryIntegrationTest's own cascade test already
		// documents (Milestone 8A) for deleting a resource still referenced
		// by a tracked entity in the same persistence context.
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.update("DELETE FROM resources WHERE id = ?", resource.getId());

		// Verified via raw JDBC, not reportRepository.findById: the entity is
		// still cached in this test's own persistence context (from the
		// saveAndFlush above), and a JPA find-by-id returns the cached
		// managed instance without re-querying — it would never observe a
		// change made by a raw JDBC statement that bypassed Hibernate
		// entirely. Querying the actual column values directly is what
		// proves the database's own ON DELETE SET NULL behavior, which is
		// what this test exists to verify.
		Object[] row = jdbcTemplate.queryForObject(
				"SELECT resource_id, resource_name_snapshot, resource_slug_snapshot FROM correction_reports WHERE id = ?",
				(rs, rowNum) -> new Object[] { rs.getObject("resource_id"), rs.getString("resource_name_snapshot"),
						rs.getString("resource_slug_snapshot") },
				report.getId());

		assertThat(row[0]).isNull();
		assertThat(row[1]).isEqualTo(resource.getName());
		assertThat(row[2]).isEqualTo(resource.getSlug());
	}

	@Test
	void statusCheckConstraintIsEnforced() {
		User user = persistUser();
		CommunityResource resource = resource("Status Check");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO correction_reports (id, reported_by_user_id, resource_id, resource_name_snapshot, "
						+ "resource_slug_snapshot, issue_type, explanation, status) "
						+ "VALUES (?, ?, ?, 'X', 'x', 'OTHER', 'issue', 'NOT_A_REAL_STATUS')",
				UUID.randomUUID(), user.getId(), resource.getId()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void issueTypeCheckConstraintIsEnforced() {
		User user = persistUser();
		CommunityResource resource = resource("Issue Type Check");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO correction_reports (id, reported_by_user_id, resource_id, resource_name_snapshot, "
						+ "resource_slug_snapshot, issue_type, explanation) "
						+ "VALUES (?, ?, ?, 'X', 'x', 'NOT_A_REAL_ISSUE_TYPE', 'issue')",
				UUID.randomUUID(), user.getId(), resource.getId()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void duplicatePendingReportForSameUserResourceAndIssueTypeIsRejected() {
		User user = persistUser();
		CommunityResource resource = resource("Duplicate Check");

		reportRepository.saveAndFlush(report(user.getId(), resource, IssueType.ADDRESS));

		assertThatThrownBy(() -> reportRepository.saveAndFlush(report(user.getId(), resource, IssueType.ADDRESS)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void differentIssueTypesOnTheSameResourceAreAllowed() {
		User user = persistUser();
		CommunityResource resource = resource("Different Issue Check");

		reportRepository.saveAndFlush(report(user.getId(), resource, IssueType.ADDRESS));
		CorrectionReport second = reportRepository.saveAndFlush(report(user.getId(), resource, IssueType.CONTACT_INFORMATION));

		assertThat(second.getId()).isNotNull();
	}

	@Test
	void findByOwnerOrderBySubmittedAtDescExcludesOtherUsers() {
		User owner = persistUser();
		User other = persistUser();
		CommunityResource resource = resource("Owner Scope Check");
		reportRepository.saveAndFlush(report(owner.getId(), resource, IssueType.OTHER));
		reportRepository.saveAndFlush(report(other.getId(), resource, IssueType.DUPLICATE_RESOURCE));

		Page<CorrectionReport> page = reportRepository.findByOwnerOrderBySubmittedAtDesc(owner.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).getReportedByUserId()).isEqualTo(owner.getId());
	}

	@Test
	void findByIdAndOwnerReturnsEmptyForAnotherUsersReport() {
		User owner = persistUser();
		User other = persistUser();
		CommunityResource resource = resource("Ownership Detail Check");
		CorrectionReport report = reportRepository.saveAndFlush(report(owner.getId(), resource, IssueType.OTHER));

		assertThat(reportRepository.findByIdAndOwner(report.getId(), other.getId())).isEmpty();
		assertThat(reportRepository.findByIdAndOwner(report.getId(), owner.getId())).isPresent();
	}

	private CorrectionReport report(UUID userId, CommunityResource resource, IssueType issueType) {
		return new CorrectionReport(userId, resource, issueType, "Something looks wrong.", null, null, null, null,
				null, null, null, null, null, null, null, null, null);
	}

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(Role.USER));
	}

	private CommunityResource resource(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		Category category = categoryRepository.save(new Category(
				namePrefix + " Cat " + marker, (namePrefix + " Cat " + marker).toLowerCase(Locale.ROOT), "cr-cat-" + marker, null));
		CommunityResource resource = new CommunityResource(category, namePrefix + " " + marker, "cr-res-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

}
