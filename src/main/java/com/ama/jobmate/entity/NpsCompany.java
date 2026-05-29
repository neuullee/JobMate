package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "nps_company")
public class NpsCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_no")
    private String bizNo;                  // 사업자등록번호

    @Column(name = "company_name")
    private String companyName;            // 사업장명

    @Column(name = "industry_code")
    private String industryCode;           // 업종코드

    @Column(name = "industry_name")
    private String industryName;           // 업종명

    @Column(name = "recent_employees")
    private Integer recentEmployees;       // 최근 직원수

    @Column(name = "annual_joiners")
    private Integer annualJoiners;         // 연간 입사자

    @Column(name = "annual_leavers")
    private Integer annualLeavers;         // 연간 퇴사자

    @Column(name = "annual_resignation_rate")
    private Double annualResignationRate;  // 퇴사율

    @Column(name = "monthly_details", columnDefinition = "TEXT")
    private String monthlyDetails;         // 월별 JSON

    @Column(name = "address")
    private String address;                // 주소

    @Column(name = "estimated_avg_salary")
    private Integer estimatedAvgSalary;    // 추정 평균연봉

    @Column(name = "operating_profit")
    private Long operatingProfit;          // 영업이익

    @Column(name = "sales_revenue")
    private Long salesRevenue;             // 매출액

    @Column(name = "finance_last_updated")
    private String financeLastUpdated;     // 재무 마지막 업데이트
}