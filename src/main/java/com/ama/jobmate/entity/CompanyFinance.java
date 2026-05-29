package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "company_finance")
public class CompanyFinance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name")
    private String companyName;            // 회사명

    @Column(name = "business_number")
    private String businessNumber;         // 사업자등록번호 (핵심!)

    @Column(name = "corp_code")
    private String corpCode;               // DART 고유번호

    @Column(name = "stock_code")
    private String stockCode;              // 주식코드

    @Column(name = "yearly_finance_data", columnDefinition = "TEXT")
    private String yearlyFinanceData;      // 3개년 재무 JSON

    @Column(name = "status")
    private String status;                 // 상태 (success/failed/skipped)

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;   // 마지막 업데이트
}