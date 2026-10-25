package com.sondurukedella.common.audit;

// Implemented by the signed-in user's principal, so audit code can read the user id without
// depending on the security module.
public interface AuditUser {

    long userId();
}
