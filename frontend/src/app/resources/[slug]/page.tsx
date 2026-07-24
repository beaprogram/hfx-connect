import { cache } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getResourceBySlug } from "@/lib/api/resources";
import { ApiRequestError } from "@/lib/api/errors";
import { ResourceDetail } from "@/components/resources/resource-detail";

interface PageProps {
  params: Promise<{ slug: string }>;
}

// React's cache() dedupes this within a single request: generateMetadata and
// the page body both need the same resource, and without this they'd issue
// two separate network calls for one page view.
const loadResource = cache(getResourceBySlug);

async function loadResourceOrNotFound(slug: string) {
  try {
    return await loadResource(slug);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) {
      notFound();
    }
    throw error;
  }
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { slug } = await params;
  try {
    const resource = await loadResource(slug);
    return {
      title: `${resource.name} | HFX Connect`,
      description: resource.description ?? `${resource.name}, a ${resource.category.name} resource in Halifax.`,
    };
  } catch {
    return { title: "Resource | HFX Connect" };
  }
}

export default async function ResourceDetailPage({ params }: PageProps) {
  const { slug } = await params;
  const resource = await loadResourceOrNotFound(slug);

  return <ResourceDetail resource={resource} />;
}
