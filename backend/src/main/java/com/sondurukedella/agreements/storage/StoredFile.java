package com.sondurukedella.agreements.storage;

// What the store knows after writing a file: its server-side key, real size and SHA-256 (hex).
public record StoredFile(String key, long size, String sha256) {
}
