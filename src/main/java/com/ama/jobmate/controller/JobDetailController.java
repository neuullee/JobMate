package com.ama.jobmate.controller;

import com.ama.jobmate.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class JobDetailController {

    private final JobPostingRepository jobPostingRepository;

    @GetMapping("/job-detail/{jobId}")
    public String jobDetail(@PathVariable Long jobId) {
        return "job-detail";
    }

    /**
     * company_name이 NULL인 공고에 회사명 업데이트
     * jobs.html 카드 클릭 시 호출
     */
    @PostMapping("/api/jobs/{jobId}/update-company")
    @ResponseBody
    public ResponseEntity<?> updateCompanyName(
            @PathVariable Long jobId,
            @RequestBody Map<String, String> body) {
        String companyName = body.get("companyName");
        if (companyName == null || companyName.isBlank())
            return ResponseEntity.badRequest().build();

        jobPostingRepository.findById(jobId).ifPresent(job -> {
            if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
                job.setCompanyName(companyName.trim());
                jobPostingRepository.save(job);
            }
        });
        return ResponseEntity.ok(Map.of("success", true));
    }
}