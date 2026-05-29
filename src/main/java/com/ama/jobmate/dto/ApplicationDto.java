package com.ama.jobmate.dto;

import com.ama.jobmate.entity.Application;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ApplicationDto {

    private Long id;
    private String companyName;
    private String jobTitle;
    private LocalDate appliedDate;
    private LocalDate deadline;   // ✅ 추가
    private String status;
    private String memo;

    public ApplicationDto(Application app) {
        this.id          = app.getId();
        this.companyName = app.getCompanyName();
        this.jobTitle    = app.getJobTitle();
        this.appliedDate = app.getAppliedDate();
        this.deadline    = app.getDeadline();  // ✅ 추가
        this.status      = app.getStatus().name();
        this.memo        = app.getMemo();
    }
}
