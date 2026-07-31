package com.hfxconnect.resource;

import java.util.List;

/** Business-layer page of nearby-search results. See {@link CreateResourceCommand} for why this isn't an HTTP DTO. */
record NearbyResourcePage(List<NearbyResourceDetails> content, int page, int size, long totalElements, int totalPages) {

}
