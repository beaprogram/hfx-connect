package com.hfxconnect.resource;

import java.time.Instant;
import java.util.UUID;

/**
 * Closed interface projection for {@link ResourceRepository#findNearby} — a
 * native SQL query, so this is plain-value column-to-getter mapping (Spring
 * Data's relaxed name matching against the query's column aliases), not an
 * entity. See ADR-012 for why nearby search never touches a Hibernate-
 * mapped geometry type: every coordinate here is already a plain
 * {@code Double}, computed by PostGIS in the query itself.
 */
interface NearbyResourceProjection {

	UUID getId();

	String getName();

	String getSlug();

	String getCity();

	String getProvince();

	String getCostType();

	String getVerificationStatus();

	boolean getActive();

	Long getCategoryId();

	String getCategoryName();

	String getCategorySlug();

	Instant getCreatedAt();

	Double getLatitude();

	Double getLongitude();

	Double getDistanceMeters();

	/** Milestone 10A — {@code null} for an unowned resource. See {@code ResourceService.loadOrganizationSummariesByOrganizationIds}. */
	UUID getOrganizationId();

}
