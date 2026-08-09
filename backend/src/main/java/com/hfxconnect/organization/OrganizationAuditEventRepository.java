package com.hfxconnect.organization;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationAuditEventRepository extends JpaRepository<OrganizationAuditEvent, UUID> {

	@Query("SELECT e FROM OrganizationAuditEvent e WHERE e.organizationId = :organizationId ORDER BY e.createdAt ASC, e.id ASC")
	Page<OrganizationAuditEvent> findByOrganization(UUID organizationId, Pageable pageable);

}
