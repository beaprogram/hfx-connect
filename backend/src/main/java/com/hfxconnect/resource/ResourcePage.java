package com.hfxconnect.resource;

import java.util.List;

/** Business-layer page of resources. See {@link CreateResourceCommand} for why this isn't an HTTP DTO. */
public record ResourcePage(List<ResourceDetails> content, int page, int size, long totalElements, int totalPages) {

}
