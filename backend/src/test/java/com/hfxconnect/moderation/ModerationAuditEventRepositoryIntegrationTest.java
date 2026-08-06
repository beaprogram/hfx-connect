package com.hfxconnect.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ModerationAuditEventRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ModerationAuditEventRepository auditEventRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesAndReloadsSnapshotsAsJsonb() {
		UUID contributionId = UUID.randomUUID();
		User actor = persistUser();
		ModerationAuditEvent event = new ModerationAuditEvent(ContributionType.RESOURCE_SUBMISSION, contributionId,
				ModerationAction.RESOURCE_CREATED, ModerationDecision.APPROVED, actor.getId(), actor.getEmail(),
				"Looks accurate.", null, Map.of("name", "Before Name"), Map.of("name", "After Name"));

		ModerationAuditEvent saved = auditEventRepository.saveAndFlush(event);
		auditEventRepository.flush();
		ModerationAuditEvent reloaded = auditEventRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getBeforeSnapshot()).containsEntry("name", "Before Name");
		assertThat(reloaded.getAfterSnapshot()).containsEntry("name", "After Name");
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}

	@Test
	void findByContributionReturnsOnlyThatContributionsEventsOldestFirst() {
		UUID contributionId = UUID.randomUUID();
		UUID otherContributionId = UUID.randomUUID();
		User actor = persistUser();
		auditEventRepository.save(new ModerationAuditEvent(ContributionType.CORRECTION_REPORT, contributionId,
				ModerationAction.REVIEW_DECISION, ModerationDecision.REJECTED, actor.getId(), actor.getEmail(), "First.",
				null, null, null));
		auditEventRepository.save(new ModerationAuditEvent(ContributionType.CORRECTION_REPORT, otherContributionId,
				ModerationAction.REVIEW_DECISION, ModerationDecision.REJECTED, actor.getId(), actor.getEmail(), "Unrelated.",
				null, null, null));

		Page<ModerationAuditEvent> page = auditEventRepository.findByContribution(
				ContributionType.CORRECTION_REPORT, contributionId, PageRequest.of(0, 20, Sort.by("createdAt")));

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).getContributionId()).isEqualTo(contributionId);
	}

	@Test
	void findForGlobalListFiltersByContributionTypeAndDecision() {
		User actor = persistUser();
		auditEventRepository.save(new ModerationAuditEvent(ContributionType.RESOURCE_SUBMISSION, UUID.randomUUID(),
				ModerationAction.RESOURCE_CREATED, ModerationDecision.APPROVED, actor.getId(), actor.getEmail(), "Approved one.",
				null, null, null));
		auditEventRepository.save(new ModerationAuditEvent(ContributionType.CORRECTION_REPORT, UUID.randomUUID(),
				ModerationAction.REVIEW_DECISION, ModerationDecision.REJECTED, actor.getId(), actor.getEmail(), "Rejected one.",
				null, null, null));

		Page<ModerationAuditEvent> submissionsOnly = auditEventRepository.findForGlobalList(
				ContributionType.RESOURCE_SUBMISSION, null, PageRequest.of(0, 20));
		Page<ModerationAuditEvent> rejectedOnly = auditEventRepository.findForGlobalList(
				null, ModerationDecision.REJECTED, PageRequest.of(0, 20));

		assertThat(submissionsOnly.getContent()).allMatch(e -> e.getContributionType() == ContributionType.RESOURCE_SUBMISSION);
		assertThat(rejectedOnly.getContent()).allMatch(e -> e.getDecision() == ModerationDecision.REJECTED);
	}

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(Role.MODERATOR));
	}

}
