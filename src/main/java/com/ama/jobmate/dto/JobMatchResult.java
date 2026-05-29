package com.ama.jobmate.dto;

import com.ama.jobmate.entity.JobPosting;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobMatchResult {

    private JobSummaryDto job;
    private int score;
    private String matchReason;
    private String aiSummary;
    private String warningMessage;

    // ── 공고 카드 표시용 기업 데이터 ──
    private Double leaveRate;      // 퇴사율 (%)
    private Double joinRate;       // 입사율 (%) - leaveRate 역수 추정
    private Integer employeeCount; // 사원수 (회사 규모 뱃지용)

    public JobMatchResult(JobPosting job, int score, String matchReason) {
        this.job = new JobSummaryDto(job);
        this.score = score;
        this.matchReason = matchReason;
        this.aiSummary = "";
        this.warningMessage = "";
    }
}
