package com.ama.jobmate.controller;

import com.ama.jobmate.entity.CompanyStat;
import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.CompanyStatRepository;
import com.ama.jobmate.repository.JobPostingRepository;
import com.ama.jobmate.service.NpsApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * CompanyStatController (수정본)
 *
 * 수정 내용:
 * GET /api/company/{companyName}
 *   → 기존: CompanyStat 엔티티 그대로 반환 (monthlyNpsData 없음)
 *   → 수정: job_posting 테이블에서 해당 회사의 monthlyNpsData / estimatedAvgSalary 를
 *           함께 조회해서 응답에 포함
 *
 * job-detail.html 이 기대하는 필드:
 *   wkplJngStcd, bzowrRgstsNo, wkplStylDvcd, ldongAddrMgplDgCd,
 *   wkplRoadNmDtlAddr, dataCrtYm,
 *   ceoName, establishDate, corpClass, closingMonth, homepageUrl, employeeCount,
 *   revenue, operatingProfit, netIncome, dividendInfo, leaveRate,
 *   dartCorpCode, companyName,
 *   monthlyNpsData (★ 신규), estimatedAvgSalary (★ 신규)
 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyStatController {

    private final NpsApiService npsApiService;
    private final CompanyStatRepository companyStatRepository;
    private final JobPostingRepository jobPostingRepository;

    /**
     * 회사명으로 통계 조회.
     * CompanyStat 필드 + job_posting 의 monthlyNpsData / estimatedAvgSalary 를 합쳐서 반환.
     */
    @GetMapping("/{companyName}")
    public ResponseEntity<?> getCompanyStat(@PathVariable String companyName) {
        CompanyStat stat = npsApiService.getCompanyStat(companyName);
        if (stat == null) return ResponseEntity.notFound().build();

        // ── job_posting 에서 monthly_nps_data / estimated_avg_salary 조회
        // 같은 회사 공고 중 monthlyNpsData 있는 것 우선
        String monthlyNpsData = null;
        Long estimatedAvgSalary = null;

        List<JobPosting> companyJobs = jobPostingRepository.findByCompanyAndIsClosedFalse(companyName);
        for (JobPosting job : companyJobs) {
            if (monthlyNpsData == null && job.getMonthlyNpsData() != null
                    && !job.getMonthlyNpsData().isBlank()) {
                monthlyNpsData = job.getMonthlyNpsData();
            }
            if (estimatedAvgSalary == null && job.getEstimatedAvgSalary() != null
                    && job.getEstimatedAvgSalary() > 0) {
                estimatedAvgSalary = job.getEstimatedAvgSalary();
            }
            if (monthlyNpsData != null && estimatedAvgSalary != null) break;
        }

        // ── 응답 Map 조립 (job-detail.html 이 기대하는 모든 필드 포함)
        Map<String, Object> response = new LinkedHashMap<>();

        // NPS 기본 정보
        response.put("companyName",           stat.getCompanyName());
        response.put("bzowrRgstsNo",          stat.getBzowrRgstsNo());
        response.put("dataCrtYm",             stat.getDataCrtYm());
        response.put("wkplStylDvcd",          stat.getWkplStylDvcd());
        response.put("wkplJngStcd",           stat.getWkplJngStcd());
        response.put("wkplRoadNmDtlAddr",     stat.getWkplRoadNmDtlAddr());
        response.put("ldongAddrMgplDgCd",     stat.getLdongAddrMgplDgCd());
        response.put("ldongAddrMgplSgguCd",   stat.getLdongAddrMgplSgguCd());
        response.put("ldongAddrMgplSgguEmdCd",stat.getLdongAddrMgplSgguEmdCd());
        response.put("leaveRate",             stat.getLeaveRate());

        // DART 기업 정보
        response.put("dartCorpCode",   stat.getDartCorpCode());
        response.put("corpName",       stat.getCorpName());
        response.put("stockCode",      stat.getStockCode());
        response.put("ceoName",        stat.getCeoName());
        response.put("corpClass",      stat.getCorpClass());
        response.put("establishDate",  stat.getEstablishDate());
        response.put("closingMonth",   stat.getClosingMonth());
        response.put("phoneNumber",    stat.getPhoneNumber());
        response.put("homepageUrl",    stat.getHomepageUrl());
        response.put("employeeCount",  stat.getEmployeeCount());

        // DART 재무 정보
        response.put("revenue",          stat.getRevenue());
        response.put("operatingProfit",  stat.getOperatingProfit());
        response.put("netIncome",        stat.getNetIncome());
        response.put("dividendInfo",     stat.getDividendInfo());
        response.put("yearlyFinanceData",stat.getYearlyFinanceData());

        // ★ 신규: NPS 월별 트렌드 + 예상 연봉
        // job_posting 에서 가져옴 (배치가 nps_company → job_posting 으로 복사한 값)
        response.put("monthlyNpsData",     monthlyNpsData);
        response.put("estimatedAvgSalary", estimatedAvgSalary);

        return ResponseEntity.ok(response);
    }

    /** 키워드로 DB 내 검색 */
    @GetMapping("/search")
    public ResponseEntity<List<CompanyStat>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(companyStatRepository.findByCompanyNameContaining(keyword));
    }
}
