package com.sondurukedella.common.audit;

// lets common.audit get the user id without depending on the security package
public interface AuditUser {

    long userId();
}
