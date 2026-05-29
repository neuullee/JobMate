package com.ama.jobmate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class InterviewService {

    @Value("${groq.api-key}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.groq.com")
            .build();

    // 예상 질문 생성
    // 예상 질문 생성
    public String generateQuestions(String jobCategory, List<String> excludeQuestions) {

        String excludeText = "";
        if (excludeQuestions != null && !excludeQuestions.isEmpty()) {
            StringBuilder sb = new StringBuilder("\n\n[이미 출제된 질문 - 반드시 제외]\n");
            for (int i = 0; i < excludeQuestions.size(); i++) {
                sb.append((i + 1)).append(". ").append(excludeQuestions.get(i)).append("\n");
            }
            excludeText = sb.toString();
        }

        String prompt = """
            직무: %s
            
            위 직무의 실전 면접 예상 질문 5개를 한국어로만 작성하세요.%s
            
            [필수 규칙]
            1. 모든 문장을 반드시 한국어로만 작성할 것
            2. 영어 문장 절대 금지 (Spring, Java, API 같은 기술 단어만 허용)
            3. 번호와 질문만 출력, 다른 설명 없음
            4. 마크다운(*,**,#) 사용 금지
            5. 위에 나열된 기출 질문과 겹치지 않는 새로운 질문만 출력
            
            출력 형식:
            1. 질문
            2. 질문
            3. 질문
            4. 질문
            5. 질문
            """.formatted(jobCategory, excludeText);

        return callGroq(prompt);
    }

    // 답변 피드백
    public String getFeedback(String jobCategory, String question, String answer) {
        String prompt = """
            직무: %s
            면접 질문: %s
            지원자 답변: %s
            
            위 답변을 아래 형식에 맞춰 한국어로만 분석하세요.
            
            [채점 기준 - 반드시 준수]
            - 답변이 5단어 이하이거나 "답변입니다", "모릅니다" 같은 무의미한 내용이면 10점 이하
            - 질문과 관련 없는 내용이면 10점 이하
            - 핵심 개념만 나열하고 설명이 없으면 40점 이하
            - 핵심 개념과 간략한 설명이 있으면 60~70점
            - 구체적인 예시나 경험까지 포함하면 80점 이상
            - 매우 구체적이고 실무 수준의 완성도 높은 답변만 90점 이상
            
            [필수 규칙]
            1. 모든 문장을 반드시 한국어로만 작성할 것
            2. 영어 문장 절대 금지 (Spring, Java, MVC, IoC, API 같은 기술 단어만 허용)
            3. 마크다운(*,**,#) 사용 금지
            4. 아래 형식 그대로만 출력할 것
            
            [답변 점수]
            점수: 00점 / 100점
            한줄평: (한 문장 평가)
            
            [잘한 점]
            (내용)
            
            [개선할 점]
            (내용)
            
            [모범 답변 핵심]
            (내용)
            """.formatted(jobCategory, question, answer);

        return callGroq(prompt);
    }
    private String callGroq(String prompt) {
        Map<String, Object> body = Map.of(
                "model", "llama-3.3-70b-versatile",
                "max_tokens", 1024,
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "당신은 한국어 전용 IT 면접 코칭 전문가입니다. " +
                                "반드시 한국어로만 답변하세요. " +
                                "영어 문장은 절대 사용하지 마세요. " +
                                "Spring, Java, IoC, MVC, API, REST 같은 기술 용어 단어는 그대로 사용 가능합니다. " +
                                "마크다운 문법(*,**,#)은 절대 사용하지 마세요. " +
                                "요청한 형식 외의 내용은 절대 추가하지 마세요."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            Map response = webClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List choices = (List) response.get("choices");
            Map choice = (Map) choices.get(0);
            Map message = (Map) choice.get("message");
            String content = (String) message.get("content");

            // 혹시 남아있는 마크다운 제거
            content = content.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
            content = content.replaceAll("\\*([^*]+)\\*", "$1");
            content = content.replaceAll("#+\\s", "");

            return content;

        } catch (Exception e) {
            return "AI 응답 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
