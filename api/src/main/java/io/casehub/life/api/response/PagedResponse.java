package io.casehub.life.api.response;

import java.util.List;

public record PagedResponse<T>(List<T> items, int page, int size, long totalCount) {}
