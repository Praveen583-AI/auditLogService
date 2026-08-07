package com.praveen.auditlog.application;

public interface AuditRequestContextProvider {

    AuditRequestContext currentContext();
}
