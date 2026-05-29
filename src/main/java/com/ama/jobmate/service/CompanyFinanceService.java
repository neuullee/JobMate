package com.ama.jobmate.service;

import com.ama.jobmate.entity.CompanyFinance;
import com.ama.jobmate.entity.DartCorpCode;
import com.ama.jobmate.repository.CompanyFinanceRepository;
import com.ama.jobmate.repository.DartCorpCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CompanyFinanceService {

    private final CompanyFinanceRepository companyFinanceRepository;
    private final DartCorpCodeRepository dartCorpCodeRepository;
    private final DartApiService dartApiService;

    /**
     * company_finance 전체 레코드에 사업자등록번호(business_number) 채우기
     * dart_corp_code 테이블의 bizr_no 활용
     */
    @Transactional
    public Map<String, Object> fillBusinessNumbers() {
        List<CompanyFinance> all = companyFinanceRepository.findAll();
        int success = 0, skipped = 0, failed = 0;

        for (CompanyFinance cf : all) {
            // 이미 있으면 skip
            if (cf.getBusinessNumber() != null && !cf.getBusinessNumber().isBlank()) {
                skipped++;
                continue;
            }

            String companyName = cf.getCompanyName();
            if (companyName == null || companyName.isBlank()) {
                failed++;
                continue;
            }

            // 1단계: corp_code로 dart_corp_code 조회
            String corpCode = cf.getCorpCode();
            if (corpCode != null && !corpCode.isBlank()) {
                Optional<DartCorpCode> byCorpCode = dartCorpCodeRepository.findByCorpCode(corpCode);
                if (byCorpCode.isPresent() && byCorpCode.get().getBizrNo() != null) {
                    cf.setBusinessNumber(byCorpCode.get().getBizrNo());
                    cf.setLastUpdatedAt(LocalDateTime.now());
                    companyFinanceRepository.save(cf);
                    System.out.println("[BIZ] corp_code 매칭: " + companyName + " → " + byCorpCode.get().getBizrNo());
                    success++;
                    continue;
                }
            }

            // 2단계: 회사명으로 dart_corp_code 조회
            Optional<DartCorpCode> byName = dartCorpCodeRepository.findByCorpName(companyName);
            if (byName.isPresent() && byName.get().getBizrNo() != null) {
                cf.setBusinessNumber(byName.get().getBizrNo());
                if (cf.getCorpCode() == null) cf.setCorpCode(byName.get().getCorpCode());
                cf.setLastUpdatedAt(LocalDateTime.now());
                companyFinanceRepository.save(cf);
                System.out.println("[BIZ] 회사명 매칭: " + companyName + " → " + byName.get().getBizrNo());
                success++;
                continue;
            }

            // 3단계: DART API로 corp_code 새로 조회 후 bizr_no 가져오기
            String newCorpCode = dartApiService.getCorpCode(companyName);
            if (newCorpCode != null) {
                Map corpInfo = dartApiService.getCorpInfo(newCorpCode);
                if (corpInfo != null) {
                    String bizrNo = (String) corpInfo.get("bizr_no");
                    if (bizrNo != null && !bizrNo.isBlank()) {
                        cf.setBusinessNumber(bizrNo);
                        cf.setCorpCode(newCorpCode);
                        cf.setLastUpdatedAt(LocalDateTime.now());
                        companyFinanceRepository.save(cf);
                        System.out.println("[BIZ] DART API 매칭: " + companyName + " → " + bizrNo);
                        success++;
                        continue;
                    }
                }
            }

            System.out.println("[BIZ] 매칭 실패: " + companyName);
            failed++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }

    /**
     * company_finance에 3개년 재무 JSON 채우기
     */
    @Transactional
    public Map<String, Object> fillYearlyFinanceData() {
        List<CompanyFinance> all = companyFinanceRepository.findAll();
        int success = 0, skipped = 0, failed = 0;

        for (CompanyFinance cf : all) {
            if (cf.getCorpCode() == null || cf.getCorpCode().isBlank()) {
                failed++;
                continue;
            }

            if (cf.getYearlyFinanceData() != null && !cf.getYearlyFinanceData().isBlank()) {
                skipped++;
                continue;
            }

            try {
                List<Map<String, Object>> yearlyList = new ArrayList<>();
                int currentYear = java.time.LocalDate.now().getYear();

                for (int i = 1; i <= 3; i++) {
                    String year = String.valueOf(currentYear - i);
                    Map financial = dartApiService.getFinancialInfoByYear(cf.getCorpCode(), year);
                    if (financial == null) continue;

                    List items = (List) financial.get("list");
                    if (items == null) continue;

                    Long revenue = null, operatingProfit = null;
                    for (Object obj : items) {
                        Map item = (Map) obj;
                        String account = (String) item.get("account_nm");
                        if (account == null) continue;
                        String amount = (String) item.get("thstrm_amount");
                        if (amount == null || amount.isBlank() || "-".equals(amount.trim())) continue;
                        try {
                            long value = Long.parseLong(amount.replace(",", "").trim());
                            String acLower = account.toLowerCase();
                            if (revenue == null && (acLower.contains("매출액") || acLower.contains("영업수익") || acLower.contains("매출총액")))
                                revenue = value;
                            if (operatingProfit == null && (acLower.contains("영업이익") || acLower.contains("영업손익")))
                                operatingProfit = value;
                        } catch (Exception ignored) {}
                    }

                    Map<String, Object> yearData = new LinkedHashMap<>();
                    yearData.put("year", year);
                    yearData.put("revenue", revenue);
                    yearData.put("operatingProfit", operatingProfit);
                    yearlyList.add(yearData);
                }

                if (!yearlyList.isEmpty()) {
                    // 간단한 JSON 직렬화
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < yearlyList.size(); i++) {
                        Map<String, Object> yd = yearlyList.get(i);
                        sb.append("{\"year\":\"").append(yd.get("year")).append("\"")
                                .append(",\"revenue\":").append(yd.get("revenue"))
                                .append(",\"operatingProfit\":").append(yd.get("operatingProfit")).append("}");
                        if (i < yearlyList.size() - 1) sb.append(",");
                    }
                    sb.append("]");
                    cf.setYearlyFinanceData(sb.toString());
                    cf.setLastUpdatedAt(LocalDateTime.now());
                    companyFinanceRepository.save(cf);
                    success++;
                } else {
                    failed++;
                }

            } catch (Exception e) {
                System.out.println("[FINANCE] 오류: " + cf.getCompanyName() + " - " + e.getMessage());
                failed++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }
}