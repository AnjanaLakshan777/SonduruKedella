package com.sondurukedella.settings.service;

/** Result of adding a list entry: {@code created} is false when a removed entry was reactivated. */
public record LookupAddResult<T>(T item, boolean created) {
}
