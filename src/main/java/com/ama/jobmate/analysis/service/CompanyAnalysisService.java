package com.ama.jobmate.analysis.service;

import com.ama.jobmate.entity.CompanyStat;
import com.ama.jobmate.repository.CompanyStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyAnalysisService {

    private final CompanyStatRepository companyStatRepository;

    // ── 전체 캐시: 서버 시작 시 한 번에 로딩 → N+1 쿼리 완전 차단 ──
    private final Map<String, CompanyStat> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCache() {
        try {
            List<CompanyStat> all = companyStatRepository.findAll();
            for (CompanyStat s : all) {
                if (s.getCompanyName() != null) {
                    cache.put(s.getCompanyName(), s);
                    // 정규화명도 캐싱
                    String normalized = normalize(s.getCompanyName());
                    if (!normalized.equals(s.getCompanyName())) {
                        cache.putIfAbsent(normalized, s);
                    }
                }
            }
            System.out.println("CompanyAnalysisService 캐시 로딩 완료: " + all.size() + "개 기업");
        } catch (Exception e) {
            System.out.println("CompanyAnalysisService 캐시 로딩 오류: " + e.getMessage());
        }
    }

    /** 외부에서 새 데이터 저장 후 캐시 갱신용 */
    public void refreshCache(CompanyStat stat) {
        if (stat != null && stat.getCompanyName() != null) {
            cache.put(stat.getCompanyName(), stat);
            String normalized = normalize(stat.getCompanyName());
            if (!normalized.equals(stat.getCompanyName())) {
                cache.put(normalized, stat);
            }
        }
    }

    public int calculateCompanyScore(String companyName) {
        Optional<CompanyStat> statOpt = findFromCache(companyName);
        if (statOpt.isEmpty()) return 0;

        CompanyStat stat = statOpt.get();
        double leaveRate = stat.getLeaveRate() != null ? stat.getLeaveRate() : 0;

        if (leaveRate < 10) return 20;
        if (leaveRate < 20) return 10;
        return 0;
    }

    /**
     * DART/NPS 정보 없는 기업 패널티
     * - NPS DB에 없음 → -10점
     * - NPS 있지만 DART 없음 (비상장) → -5점
     * - 정보 있음 → 0점
     */
    public int calculateInfoScore(String companyName) {
        Optional<CompanyStat> statOpt = findFromCache(companyName);
        if (statOpt.isEmpty()) return -10;

        CompanyStat stat = statOpt.get();
        if (stat.getDartCorpCode() == null || stat.getDartCorpCode().isBlank()) return -5;
        return 0;
    }

    public String getWarningMessage(String companyName) {
        Optional<CompanyStat> statOpt = findFromCache(companyName);
        if (statOpt.isEmpty()) return "";

        CompanyStat stat = statOpt.get();
        double leaveRate = stat.getLeaveRate() != null ? stat.getLeaveRate() : 0;

        if (leaveRate >= 20) return "퇴사율이 높은 기업으로 주의가 필요합니다.";
        if (leaveRate >= 10) return "퇴사율이 다소 높은 편입니다.";
        return "";
    }

    // ── 캐시에서 조회 (정확 → 정규화 순) ──
    private Optional<CompanyStat> findFromCache(String companyName) {
        if (companyName == null || companyName.isBlank()) return Optional.empty();

        CompanyStat s = cache.get(companyName);
        if (s != null) return Optional.of(s);

        String normalized = normalize(companyName);
        s = cache.get(normalized);
        if (s != null) return Optional.of(s);

        return Optional.empty();
    }

    private String normalize(String name) {
        if (name == null) return "";
        return name.replace("(주)", "").replace("주식회사", "").trim();
    }
}
