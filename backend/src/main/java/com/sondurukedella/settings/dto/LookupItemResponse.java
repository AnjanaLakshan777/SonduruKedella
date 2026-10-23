package com.sondurukedella.settings.dto;

/** An entry of an editable Settings list (unit of measurement or payment method). */
public record LookupItemResponse(long id, String name, boolean active, int sortOrder) {
}
