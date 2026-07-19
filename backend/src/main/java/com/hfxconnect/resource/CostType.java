package com.hfxconnect.resource;

/** How much a resource costs to use. Matches the database check constraint {@code resources_cost_type_valid} exactly. */
public enum CostType {
	FREE,
	LOW_COST,
	PAID,
	UNKNOWN
}
