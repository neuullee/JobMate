package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "job_posting")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobId;
    private String title;
    private String company;
    private String location;
    private String jobType;
    private String techStack;
    private String salary;
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String fullDescription;

    private String normalizedJob;
    private String normalizedLocation;
    private String normalizedJobType;
    private String normalizedTechStack;

    private Integer salaryMin;
    private Integer salaryMax;

    private Boolean isITJob;
    private Boolean isClosed;
    private Boolean isDuplicate;
    private Boolean isValid;

    @Column(columnDefinition = "TEXT")
    private String invalidReason;

    private String duplicateGroupKey;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    private Double lat;
    private Double lng;

    @Column(name = "culture_keywords", length = 500)
    private String cultureKeywords;

    @Column(name = "core_values", length = 500)
    private String coreValues;

    @Column(name = "monthly_nps_data", columnDefinition = "TEXT")
    private String monthlyNpsData;

    @Column(name = "estimated_avg_salary")
    private Long estimatedAvgSalary;

    @Column(name = "deadline", length = 20)
    private String deadline;

    @Column(name = "req_experience_years")
    private Integer reqExperienceYears;

    public String getCompanyName() {
        return this.company;
    }

    public void setCompanyName(String companyName) {
        this.company = companyName;
    }
}