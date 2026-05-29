package com.ama.jobmate.service;

import com.ama.jobmate.dto.CoverLetterDto;
import com.ama.jobmate.entity.CoverLetter;
import com.ama.jobmate.repository.CoverLetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private final CoverLetterRepository coverLetterRepository;
    private final RestTemplate restTemplate;

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    /* ─── 목록 조회 (memberId 기준) ─── */
    public List<CoverLetterDto.Response> getAll(Long memberId) {
        return coverLetterRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(CoverLetterDto.Response::from)
                .collect(Collectors.toList());
    }

    /* ─── 단건 조회 ─── */
    public CoverLetterDto.Response getById(Long id, Long memberId) {
        CoverLetter cl = coverLetterRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new IllegalArgumentException("자소서를 찾을 수 없습니다: " + id));
        return CoverLetterDto.Response.from(cl);
    }

    /* ─── 저장 ─── */
    @Transactional
    public CoverLetterDto.Response save(Long memberId, CoverLetterDto.Request dto) {
        CoverLetter cl = CoverLetter.builder()
                .memberId(memberId)
                .title(dto.getTitle())
                .companyName(dto.getCompanyName())
                .jobTitle(dto.getJobTitle())
                .content(dto.getContent())
                .build();
        return CoverLetterDto.Response.from(coverLetterRepository.save(cl));
    }

    /* ─── 수정 ─── */
    @Transactional
    public CoverLetterDto.Response update(Long id, Long memberId, CoverLetterDto.Request dto) {
        CoverLetter cl = coverLetterRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new IllegalArgumentException("자소서를 찾을 수 없습니다: " + id));
        cl.setTitle(dto.getTitle());
        cl.setCompanyName(dto.getCompanyName());
        cl.setJobTitle(dto.getJobTitle());
        cl.setContent(dto.getContent());
        cl.setAiFeedback(null); // 수정 시 기존 피드백 초기화
        return CoverLetterDto.Response.from(coverLetterRepository.save(cl));
    }

    /* ─── 삭제 ─── */
    @Transactional
    public void delete(Long id, Long memberId) {
        CoverLetter cl = coverLetterRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new IllegalArgumentException("자소서를 찾을 수 없습니다: " + id));
        coverLetterRepository.delete(cl);
    }

    /* ─── AI 피드백 생성 ─── */
    @Transactional
    public String generateFeedback(Long id, Long memberId) {
        CoverLetter cl = coverLetterRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new IllegalArgumentException("자소서를 찾을 수 없습니다: " + id));

        String prompt = buildKoreanPrompt(cl);

        try {
            String feedback = callGroqApi(prompt);
            cl.setAiFeedback(feedback);
            coverLetterRepository.save(cl);
            return feedback;
        } catch (Exception e) {
            log.error("Groq API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("AI 피드백 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /* ─── 한국어 전용 프롬프트 ─── */
    private String buildKoreanPrompt(CoverLetter cl) {
        return String.format(
            "아래 자기소개서를 분석하고 피드백을 작성해 주세요.\n\n" +
            "【지원 회사】%s\n" +
            "【지원 직무】%s\n" +
            "【자기소개서 제목】%s\n\n" +
            "【자기소개서 본문】\n%s\n\n" +
            "---\n" +
            "아래 형식에 맞춰 한국어로만 피드백을 작성해 주세요:\n\n" +
            "## ✅ 잘된 점\n" +
            "자기소개서에서 잘 작성된 부분을 3가지 이상 구체적으로 설명해 주세요.\n\n" +
            "## 🔧 개선할 점\n" +
            "부족하거나 보완이 필요한 부분을 3가지 이상 구체적으로 설명해 주세요.\n\n" +
            "## ✏️ 수정 제안\n" +
            "실제로 수정하면 좋을 문장이나 표현을 예시와 함께 제안해 주세요.\n\n" +
            "## 📊 종합 평가\n" +
            "- 점수: X / 100점\n" +
            "- 한줄 평가: 전체적인 자기소개서 수준을 한 문장으로 요약해 주세요.",
            cl.getCompanyName() != null ? cl.getCompanyName() : "미입력",
            cl.getJobTitle()    != null ? cl.getJobTitle()    : "미입력",
            cl.getTitle(),
            cl.getContent()
        );
    }

    /* ─── Groq API 호출 ─── */
    @SuppressWarnings("unchecked")
    private String callGroqApi(String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content",
            "You are a Korean career expert specializing in Korean job applications. " +
            "CRITICAL INSTRUCTION: You MUST respond ONLY in Korean (한국어). " +
            "NEVER use English, Chinese, Japanese, German, or any other language. " +
            "Every single word in your response must be written in Korean. " +
            "If you write even one word in another language, you have failed your task. " +
            "Korean only. 오직 한국어로만 작성하세요. 절대로 다른 언어를 사용하지 마세요."
        );

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(systemMsg, userMsg));
        body.put("max_tokens", 2000);
        body.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                GROQ_API_URL, HttpMethod.POST, request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) throw new RuntimeException("Groq API 응답이 비어있습니다");

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty())
            throw new RuntimeException("Groq API choices가 비어있습니다");

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
