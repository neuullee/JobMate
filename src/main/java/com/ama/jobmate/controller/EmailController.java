package com.ama.jobmate.controller;

import com.ama.jobmate.dto.JobMatchResult;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.repository.MemberRepository;
import com.ama.jobmate.service.EmailService;
import com.ama.jobmate.service.JobMatchService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final JobMatchService jobMatchService;
    private final MemberRepository memberRepository;

    /**
     * 맞춤 공고 TOP 5 메일 발송
     * POST /api/email/send-top-jobs
     */
    @PostMapping("/send-top-jobs")
    public ResponseEntity<?> sendTopJobs(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }

        try {
            Member member = memberRepository.findById(memberId).orElse(null);
            if (member == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "회원 정보를 찾을 수 없습니다."));
            }
            if (member.getEmail() == null || member.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "등록된 이메일이 없습니다."));
            }

            List<JobMatchResult> topJobs = jobMatchService.getMatchedJobs(memberId)
                    .stream()
                    .limit(5)
                    .toList();

            if (topJobs.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "매칭된 공고가 없습니다. 프로필을 먼저 완성해 주세요."));
            }

            emailService.sendTopJobsMail(member.getEmail(), member.getName(), topJobs);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", member.getEmail() + " 으로 TOP " + topJobs.size() + "개 공고를 발송했습니다."));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "메일 발송 실패: " + e.getMessage()));
        }
    }
}
