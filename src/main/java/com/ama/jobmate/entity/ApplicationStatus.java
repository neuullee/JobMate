package com.ama.jobmate.entity;

public enum ApplicationStatus {
    APPLIED("서류 지원"),
    DOCUMENT_PASS("서류 합격"),
    INTERVIEW("면접"),
    FINAL_PASS("최종 합격"),
    FAIL("불합격");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}