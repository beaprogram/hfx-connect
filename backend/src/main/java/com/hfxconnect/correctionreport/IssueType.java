package com.hfxconnect.correctionreport;

/**
 * The category of problem a correction report describes. Matches the
 * database check constraint {@code correction_reports_issue_type_check}
 * exactly. {@link #RESOURCE_CLOSED} and {@link #OTHER} are the two issue
 * types a report is most likely to use with no proposed field values at
 * all — every proposed field on {@link CorrectionReport} is nullable for
 * exactly that reason.
 */
public enum IssueType {
	GENERAL_INFORMATION,
	ADDRESS,
	CONTACT_INFORMATION,
	OPERATING_HOURS,
	ELIGIBILITY,
	ACCESSIBILITY,
	COST,
	RESOURCE_CLOSED,
	DUPLICATE_RESOURCE,
	OTHER
}
