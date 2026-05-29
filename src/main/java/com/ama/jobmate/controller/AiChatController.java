package com.ama.jobmate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    @Value("${groq.api-key}")
    private String groqApiKey;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body) {
        try {
            String userSystem = (String) body.getOrDefault("system", "");
            List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");

            if (messages == null || messages.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "messages가 없습니다."));
            }

            // 강화된 한국어 전용 시스템 프롬프트
            String systemPrompt =
                "당신은 한국어 전용 면접 코치 AI입니다.\n\n" +
                "【절대 규칙 - 반드시 준수】\n" +
                "1. 모든 답변은 반드시 한국어로만 작성하세요.\n" +
                "2. 영어, 스페인어, 중국어, 일본어, 한자 등 어떤 외국어도 절대 사용하지 마세요.\n" +
                "3. 기술 용어(API, HTTP, SSL 등 영어 약어)는 허용하지만, 일반 단어는 반드시 한국어로 쓰세요.\n" +
                "4. 외국어 단어가 나오려 하면 즉시 한국어로 바꾸세요.\n" +
                "5. 이 규칙을 어기면 답변이 무효 처리됩니다.\n\n" +
                userSystem;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama-3.3-70b-versatile");
            requestBody.put("max_tokens", 1024);
            requestBody.put("temperature", 0.3);

            List<Map<String, String>> fullMessages = new ArrayList<>();
            fullMessages.add(Map.of("role", "system", "content", systemPrompt));
            fullMessages.addAll(messages);
            requestBody.put("messages", fullMessages);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.groq.com/openai/v1/chat/completions",
                request,
                Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            // 후처리: 외국어 제거
            content = postProcess(content);

            return ResponseEntity.ok(Map.of("text", content));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "AI 응답 실패: " + e.getMessage()));
        }
    }

    private String postProcess(String text) {
        if (text == null) return "";

        // 한자 제거
        text = text.replaceAll("[\\u4e00-\\u9fff]+", "");
        // 일본어 히라가나/가타카나 제거
        text = text.replaceAll("[\\u3040-\\u30ff]+", "");
        // 아랍어 제거
        text = text.replaceAll("[\\u0600-\\u06ff]+", "");

        // 자주 나오는 외국어 단어 한국어로 치환
        Map<String, String> replacements = new LinkedHashMap<>();
        // 스페인어
        replacements.put("conocer",    "이해하다");
        replacements.put("también",    "또한");
        replacements.put("para",       "위해");
        replacements.put("pero",       "하지만");
        replacements.put("como",       "처럼");
        replacements.put("con",        "함께");
        replacements.put("del",        "의");
        replacements.put("una",        "하나의");
        replacements.put("los",        "그것들");
        // 프랑스어
        replacements.put("avec",       "함께");
        replacements.put("pour",       "위해");
        replacements.put("dans",       "안에서");
        replacements.put("être",       "이다");
        replacements.put("vous",       "당신");
        replacements.put("est",        "이다");
        // 영어 일반단어 (기술 용어는 유지)
        replacements.put("\\bthe\\b",  "");
        replacements.put("\\band\\b",  "그리고");
        replacements.put("\\bor\\b",   "또는");
        replacements.put("\\bof\\b",   "의");
        replacements.put("\\bin\\b",   "에서");
        replacements.put("\\bis\\b",   "이다");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replaceAll("(?i)" + entry.getKey(), entry.getValue());
        }

        // 연속 공백/빈 줄 정리
        text = text.replaceAll(" {2,}", " ");
        text = text.replaceAll("\\n{3,}", "\n\n").trim();

        return text;
    }
}
