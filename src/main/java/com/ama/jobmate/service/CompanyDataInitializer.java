package com.ama.jobmate.service;

import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.CompanyStatRepository;
import com.ama.jobmate.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompanyDataInitializer {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyStatRepository companyStatRepository;
    private final NpsApiService npsApiService;
    private final com.ama.jobmate.analysis.service.CompanyAnalysisService companyAnalysisService;

    /**
     * 서버 완전 기동 후 백그라운드에서 실행
     * - 공고 목록의 모든 회사 중 company_stat에 없거나 wkplJngStcd가 null인 것만 수집
     * - API 과부하 방지: 회사당 1.5초 딜레이
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initCompanyData() {
        try {
            Thread.sleep(3000); // 서버 완전 기동 후 3초 대기

            // 유효한 IT 공고에서 회사명 목록 추출 (중복 제거)
            List<JobPosting> jobs = jobPostingRepository
                    .findByIsValidTrueAndIsDuplicateFalseAndIsITJobTrue();

            Set<String> allCompanies = jobs.stream()
                    .map(JobPosting::getCompany)
                    .filter(c -> c != null && !c.isBlank())
                    .collect(Collectors.toSet());

            // 이미 정상 수집된 회사 제외 (wkplJngStcd 또는 dartCorpCode 있으면 OK)
            Set<String> alreadyCollected = companyStatRepository.findAll().stream()
                    .filter(s -> s.getWkplJngStcd() != null || s.getDartCorpCode() != null)
                    .map(s -> s.getCompanyName())
                    .collect(Collectors.toSet());

            List<String> targets = allCompanies.stream()
                    .filter(c -> !alreadyCollected.contains(c))
                    .collect(Collectors.toList());

            System.out.println("=== CompanyDataInitializer 시작: "
                    + targets.size() + "개 회사 수집 예정 (전체 " + allCompanies.size() + "개 중) ===");

            int success = 0, fail = 0;
            for (String company : targets) {
                try {
                    npsApiService.fetchAndSave(company);
                    // CompanyAnalysisService 캐시 갱신
                    companyStatRepository.findByCompanyName(company)
                            .ifPresent(companyAnalysisService::refreshCache);
                    success++;
                    System.out.println("[" + success + "/" + targets.size() + "] 수집 완료: " + company);
                    Thread.sleep(1500); // API 과부하 방지
                } catch (Exception e) {
                    fail++;
                    System.out.println("수집 실패: " + company + " - " + e.getMessage());
                }
            }

            System.out.println("=== CompanyDataInitializer 완료: 성공 " + success + "개, 실패 " + fail + "개 ===");

        } catch (Exception e) {
            System.out.println("CompanyDataInitializer 오류: " + e.getMessage());
        }
    }
}
