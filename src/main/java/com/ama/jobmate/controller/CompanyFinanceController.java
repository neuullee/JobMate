package com.ama.jobmate.controller;

import com.ama.jobmate.service.CompanyFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/company-finance")
@RequiredArgsConstructor
public class CompanyFinanceController {

    private final CompanyFinanceService companyFinanceService;

    // 사업자등록번호 채우기
    @PostMapping("/fill-biz-no")
    public Map<String, Object> fillBusinessNumbers() {
        return companyFinanceService.fillBusinessNumbers();
    }

    // 3개년 재무 JSON 채우기
    @PostMapping("/fill-yearly-finance")
    public Map<String, Object> fillYearlyFinanceData() {
        return companyFinanceService.fillYearlyFinanceData();
    }
}
