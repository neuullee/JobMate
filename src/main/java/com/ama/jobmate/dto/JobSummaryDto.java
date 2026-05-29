package com.ama.jobmate.dto;

import com.ama.jobmate.entity.JobPosting;

public class JobSummaryDto {

    private Long id;
    private String jobId;
    private String title;
    private String company;
    private String location;
    private String jobType;
    private String techStack;
    private String salary;
    private Integer salaryMin;  // 추가
    private Integer salaryMax;  // 추가
    private String url;

    public JobSummaryDto(JobPosting job) {
        this.id = job.getId();
        this.jobId = job.getJobId();
        this.title = job.getTitle();
        this.company = job.getCompany();
        this.location = job.getLocation();
        this.jobType = job.getJobType();
        this.techStack = job.getTechStack();
        this.salary = job.getSalary();
        this.salaryMin = job.getSalaryMin();  // 추가
        this.salaryMax = job.getSalaryMax();  // 추가
        this.url = job.getUrl();
    }

    public Long getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public String getJobType() {
        return jobType;
    }

    public String getTechStack() {
        return techStack;
    }

    public String getSalary() {
        return salary;
    }

    public Integer getSalaryMin() { return salaryMin; }

    public Integer getSalaryMax() { return salaryMax; }

    public String getUrl() {
        return url;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}