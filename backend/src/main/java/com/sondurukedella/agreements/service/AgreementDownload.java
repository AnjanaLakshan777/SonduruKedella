package com.sondurukedella.agreements.service;

import java.io.InputStream;

// The caller must close content (Spring does it when it writes the response body).
public record AgreementDownload(String fileName, long size, InputStream content) {
}
