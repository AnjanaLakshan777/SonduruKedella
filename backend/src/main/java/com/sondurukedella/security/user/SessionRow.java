package com.sondurukedella.security.user;

// what SessionUserFilter needs about the signed-in user on every request
public record SessionRow(long authVersion, boolean mustChangePassword) {
}
