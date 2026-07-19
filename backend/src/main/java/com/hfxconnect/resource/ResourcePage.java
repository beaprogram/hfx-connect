package com.hfxconnect.resource;

import java.util.List;
import org.springframework.data.domain.Page;

/** Business-layer page of resources. See {@link CreateResourceCommand} for why this isn't an HTTP DTO. */
public record ResourcePage(List<ResourceDetails> content, int page, int size, long totalElements, int totalPages) {

	static ResourcePage from(Page<CommunityResource> page) {
		return new ResourcePage(
				page.getContent().stream().map(ResourceDetails::from).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
