package com.ama.jobmate.controller;

import com.ama.jobmate.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    // 예상 질문 생성
    @GetMapping("/questions")
    public ResponseEntity<Map<String, String>> generateQuestions(
            @RequestParam String jobCategory,
            @RequestParam(required = false, defaultValue = "[]") String exclude) {

        List<String> excludeList = new java.util.ArrayList<>();
        if (!exclude.equals("[]") && !exclude.isBlank()) {
            try {
                excludeList = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(exclude, new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});
            } catch (Exception ignored) {}
        }

        String questions = interviewService.generateQuestions(jobCategory, excludeList);
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    // 답변 피드백
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> getFeedback(
            @RequestBody Map<String, String> request) {
        String jobCategory = request.get("jobCategory");
        String question    = request.get("question");
        String answer      = request.get("answer");
        String feedback    = interviewService.getFeedback(jobCategory, question, answer);
        return ResponseEntity.ok(Map.of("feedback", feedback));
    }
}