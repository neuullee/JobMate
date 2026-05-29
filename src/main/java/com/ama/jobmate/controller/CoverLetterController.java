package com.ama.jobmate.controller;

import com.ama.jobmate.dto.CoverLetterDto;
import com.ama.jobmate.service.CoverLetterService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    /* ─── 페이지 렌더 ─── */
    @GetMapping("/cover-letter")
    public String coverLetterPage() {
        return "cover-letter";
    }

    /* ─── 목록 조회 ─── */
    @GetMapping("/api/cover-letters")
    @ResponseBody
    public ResponseEntity<List<CoverLetterDto.Response>> getAll(HttpSession session) {
        Long memberId = getMemberId(session);
        return ResponseEntity.ok(coverLetterService.getAll(memberId));
    }

    /* ─── 단건 조회 ─── */
    @GetMapping("/api/cover-letters/{id}")
    @ResponseBody
    public ResponseEntity<CoverLetterDto.Response> getById(
            @PathVariable Long id, HttpSession session) {
        Long memberId = getMemberId(session);
        return ResponseEntity.ok(coverLetterService.getById(id, memberId));
    }

    /* ─── 저장 ─── */
    @PostMapping("/api/cover-letters")
    @ResponseBody
    public ResponseEntity<CoverLetterDto.Response> save(
            @RequestBody CoverLetterDto.Request dto, HttpSession session) {
        Long memberId = getMemberId(session);
        return ResponseEntity.ok(coverLetterService.save(memberId, dto));
    }

    /* ─── 수정 ─── */
    @PutMapping("/api/cover-letters/{id}")
    @ResponseBody
    public ResponseEntity<CoverLetterDto.Response> update(
            @PathVariable Long id,
            @RequestBody CoverLetterDto.Request dto,
            HttpSession session) {
        Long memberId = getMemberId(session);
        return ResponseEntity.ok(coverLetterService.update(id, memberId, dto));
    }

    /* ─── 삭제 ─── */
    @DeleteMapping("/api/cover-letters/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(
            @PathVariable Long id, HttpSession session) {
        Long memberId = getMemberId(session);
        coverLetterService.delete(id, memberId);
        return ResponseEntity.ok().build();
    }

    /* ─── AI 피드백 ─── */
    @PostMapping("/api/cover-letters/{id}/feedback")
    @ResponseBody
    public ResponseEntity<CoverLetterDto.FeedbackResponse> feedback(
            @PathVariable Long id, HttpSession session) {
        Long memberId = getMemberId(session);
        String feedback = coverLetterService.generateFeedback(id, memberId);
        return ResponseEntity.ok(CoverLetterDto.FeedbackResponse.builder()
                .feedback(feedback).build());
    }

    /* ─── 세션에서 memberId 추출 (기존 프로젝트 방식) ─── */
    private Long getMemberId(HttpSession session) {
        Object id = session.getAttribute("memberId");
        if (id == null) {
            // 세션 없을 때 기본값 1 (개발/테스트용)
            return 1L;
        }
        if (id instanceof Integer) return ((Integer) id).longValue();
        if (id instanceof Long)    return (Long) id;
        return Long.parseLong(id.toString());
    }
}
