
package com.praveen.auditlog.retention;

public final class ArchiveProofException extends RuntimeException {
    private final RetentionService.ArchiveFailureReason reason;
    private final long sequence;

    public ArchiveProofException(
            RetentionService.ArchiveFailureReason reason,
            long sequence
    ) {
        super("Archived audit evidence could not be verified");
        this.reason = reason;
        this.sequence = sequence;
    }

    public RetentionService.ArchiveFailureReason reason() {
        return reason;
    }

    public long sequence() {
        return sequence;
    }
}

