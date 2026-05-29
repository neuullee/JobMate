package com.ama.jobmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "company_stat")
public class CompanyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공통
    private String companyName;       // 사업장명

    // NPS 국민연금 데이터
    private String seq;               // 식별번호
    private String bzowrRgstsNo;      // 사업자등록번호
    private String dataCrtYm;         // 데이터생성년월
    private String wkplStylDvcd;      // 사업장형태구분코드
    private String wkplJngStcd;       // 사업장가입상태코드
    private String wkplRoadNmDtlAddr; // 도로명주소
    private String ldongAddrMgplDgCd;      // 시도코드
    private String ldongAddrMgplSgguCd;    // 시군구코드
    private String ldongAddrMgplSgguEmdCd; // 읍면동코드
    private Double leaveRate;         // 퇴사율

    // DART 기업 기본정보
    private String dartCorpCode;      // DART 고유번호
    private String corpName;          // 정식 회사명
    private String stockCode;         // 주식코드 (상장여부 판단)
    private String ceoName;           // 대표자명
    private String corpClass;         // 법인구분 (Y:유가, K:코스닥, N:비상장 등)
    private String establishDate;     // 설립일
    private String closingMonth;      // 결산월
    private String phoneNumber;       // 전화번호
    private String address;           // 주소
    private String homepageUrl;       // 홈페이지

    // DART 재무정보 (최근 사업연도)
    private Long revenue;             // 매출액
    private Long operatingProfit;     // 영업이익
    private Long netIncome;           // 당기순이익
    private Integer employeeCount;    // 사원수

    // DART 배당정보
    private String dividendInfo;      // 배당 정보 요약

    // DART 평균 연봉 (단위: 만원)
    private Integer avgSalary;        // 직원 1인 평균 급여액

    // 3개년 재무 JSON (예: [{"year":"2023","revenue":100,"operatingProfit":10}, ...])
    @Column(name = "yearly_finance_data", columnDefinition = "TEXT")
    private String yearlyFinanceData;
}