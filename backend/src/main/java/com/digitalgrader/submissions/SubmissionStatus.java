package com.digitalgrader.submissions;

public enum SubmissionStatus {
    QUEUED, RUNNING, GRADED, FAILED, TIMEOUT;
    public boolean allows(SubmissionStatus next) {
        return (this==QUEUED && next==RUNNING) || (this==RUNNING && (next==GRADED || next==FAILED || next==TIMEOUT));
    }
}
