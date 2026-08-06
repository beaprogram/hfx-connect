"use client";

import { useId, useState } from "react";
import { ResourceSubmissionQueue } from "@/components/moderation/resource-submission-queue";
import { CorrectionReportQueue } from "@/components/moderation/correction-report-queue";

type Tab = "submissions" | "corrections";

/** The two moderation queues, switched via an accessible ARIA tabs pattern (Milestone 9A). */
export function ModerationTabs() {
  const [tab, setTab] = useState<Tab>("submissions");
  const submissionsTabId = useId();
  const correctionsTabId = useId();
  const submissionsPanelId = useId();
  const correctionsPanelId = useId();

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold text-slate-900">Moderation</h1>

      <div role="tablist" aria-label="Moderation queues" className="flex gap-2 border-b border-slate-200">
        <button
          type="button"
          role="tab"
          id={submissionsTabId}
          aria-selected={tab === "submissions"}
          aria-controls={submissionsPanelId}
          tabIndex={tab === "submissions" ? 0 : -1}
          onClick={() => setTab("submissions")}
          className={`rounded-t-md px-4 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
            tab === "submissions" ? "border-b-2 border-blue-600 text-blue-700" : "text-slate-600 hover:text-slate-900"
          }`}
        >
          Resource Submissions
        </button>
        <button
          type="button"
          role="tab"
          id={correctionsTabId}
          aria-selected={tab === "corrections"}
          aria-controls={correctionsPanelId}
          tabIndex={tab === "corrections" ? 0 : -1}
          onClick={() => setTab("corrections")}
          className={`rounded-t-md px-4 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
            tab === "corrections" ? "border-b-2 border-blue-600 text-blue-700" : "text-slate-600 hover:text-slate-900"
          }`}
        >
          Correction Reports
        </button>
      </div>

      <div id={submissionsPanelId} role="tabpanel" aria-labelledby={submissionsTabId} hidden={tab !== "submissions"}>
        {tab === "submissions" && <ResourceSubmissionQueue />}
      </div>
      <div id={correctionsPanelId} role="tabpanel" aria-labelledby={correctionsTabId} hidden={tab !== "corrections"}>
        {tab === "corrections" && <CorrectionReportQueue />}
      </div>
    </div>
  );
}
