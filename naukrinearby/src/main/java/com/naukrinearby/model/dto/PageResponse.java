package com.naukrinearby.model.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for paginated responses (security-and-api.md: content, totalElements, totalPages,
 * number). Controllers return this instead of Spring Data's {@code Page}, whose serialized form
 * (PageImpl internals such as {@code pageable}/{@code sort}) is not a stable contract. Serializing it
 * directly also triggers Spring Data's "Serializing PageImpl instances as-is" warning.
 */
public record PageResponse<T>(
		List<T> content,
		int number,
		int size,
		long totalElements,
		int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		return new PageResponse<>(page.getContent().stream().<T>map(mapper).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
