package com.hfxconnect.resource;

/**
 * A resource's currently-calculated open status. {@code UNKNOWN} means the
 * resource has no operating-hours schedule at all (zero rows) — never that a
 * schedule exists but today's status couldn't be determined. See ADR-011's
 * "Status Model" section for why this is a single, whole-resource dividing
 * line rather than a per-day {@code UNKNOWN}.
 */
public enum HoursStatus {
	OPEN,
	CLOSED,
	UNKNOWN
}
