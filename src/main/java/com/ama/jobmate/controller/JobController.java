package com.ama.jobmate.controller;

import com.ama.jobmate.dto.JobMatchResult;
import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.JobPostingRepository;
import com.ama.jobmate.service.JobMatchService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobMatchService jobMatchService;
    private final JobPostingRepository jobPostingRepository;

    @Value("${groq.api-key}")
    private String groqApiKey;

    // 영어 표현 → 한국어 치환 테이블
    private static final Map<String, String> EN_TO_KO = new LinkedHashMap<>();
    static {
        EN_TO_KO.put("preferred", "우대");
        EN_TO_KO.put("preferable", "우대");
        EN_TO_KO.put("preferably", "우대하며");
        EN_TO_KO.put("required", "필수");
        EN_TO_KO.put("requirements", "자격요건");
        EN_TO_KO.put("experience", "경험");
        EN_TO_KO.put("responsibilities", "담당업무");
        EN_TO_KO.put("qualifications", "자격요건");
        EN_TO_KO.put("benefits", "복리후생");
        EN_TO_KO.put("position", "포지션");
        EN_TO_KO.put("candidate", "지원자");
        EN_TO_KO.put("candidates", "지원자");
        EN_TO_KO.put("strong", "우수한");
        EN_TO_KO.put("ability", "능력");
        EN_TO_KO.put("skills", "기술");
        EN_TO_KO.put("knowledge", "지식");
        EN_TO_KO.put("understanding", "이해도");
        EN_TO_KO.put("team", "팀");
        EN_TO_KO.put("communication", "커뮤니케이션");
        EN_TO_KO.put("collaborative", "협업");
        EN_TO_KO.put("environment", "환경");
        EN_TO_KO.put("years", "년");
        EN_TO_KO.put("year", "년");
        EN_TO_KO.put("months", "개월");
        EN_TO_KO.put("month", "개월");
        EN_TO_KO.put("developing", "개발");
        EN_TO_KO.put("development", "개발");
        EN_TO_KO.put("developer", "개발자");
        EN_TO_KO.put("engineering", "엔지니어링");
        EN_TO_KO.put("engineer", "엔지니어");
        EN_TO_KO.put("management", "관리");
        EN_TO_KO.put("manager", "매니저");
        EN_TO_KO.put("service", "서비스");
        EN_TO_KO.put("system", "시스템");
        EN_TO_KO.put("systems", "시스템");
        EN_TO_KO.put("backend", "백엔드");
        EN_TO_KO.put("frontend", "프론트엔드");
        EN_TO_KO.put("full-stack", "풀스택");
        EN_TO_KO.put("fullstack", "풀스택");
        EN_TO_KO.put("cloud", "클라우드");
        EN_TO_KO.put("platform", "플랫폼");
        EN_TO_KO.put("infrastructure", "인프라");
        EN_TO_KO.put("deployment", "배포");
        EN_TO_KO.put("maintenance", "유지보수");
        EN_TO_KO.put("additionally", "또한");
        EN_TO_KO.put("furthermore", "또한");
        EN_TO_KO.put("however", "하지만");
        EN_TO_KO.put("therefore", "따라서");
        EN_TO_KO.put("including", "포함");
        EN_TO_KO.put("such as", "예를 들어");
        EN_TO_KO.put("as well as", "및");
        EN_TO_KO.put("in addition", "추가로");
        EN_TO_KO.put("provide", "제공");
        EN_TO_KO.put("support", "지원");
        EN_TO_KO.put("ensure", "보장");
        EN_TO_KO.put("maintain", "유지");
        EN_TO_KO.put("implement", "구현");
        EN_TO_KO.put("design", "설계");
        EN_TO_KO.put("build", "개발");
        EN_TO_KO.put("work", "업무");
        EN_TO_KO.put("role", "역할");
        EN_TO_KO.put("also", "또한");
        EN_TO_KO.put("good", "우수한");
        EN_TO_KO.put("well", "원활하게");
        EN_TO_KO.put("highly", "매우");
        EN_TO_KO.put("key", "핵심");
        EN_TO_KO.put("main", "주요");
        EN_TO_KO.put("based", "기반");
        EN_TO_KO.put("using", "활용하여");
        EN_TO_KO.put("tasks", "업무");
        EN_TO_KO.put("projects", "프로젝트");
        EN_TO_KO.put("business", "비즈니스");
    }

    // 한자 및 외국어 정규식 제거
    private String removeNonKorean(String text) {
        if (text == null) return "";
        text = text.replaceAll("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]", "");
        text = text.replaceAll("[\\u3040-\\u309F\\u30A0-\\u30FF]", "");
        text = text.replaceAll("[\\u0600-\\u06FF]", "");
        text = text.replaceAll("[àáâãäåæçèéêëìíîïðñòóôõöùúûüýþÿÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ]", "");
        text = text.replaceAll("\\s{2,}", " ").trim();
        return text;
    }

    @GetMapping("/match")
    public ResponseEntity<?> getMatchedJobs(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");
        List<JobMatchResult> results = jobMatchService.getMatchedJobs(memberId);
        return ResponseEntity.ok(results);
    }

    /**
     * 배치 데이터 조회: culture_keywords, core_values, monthly_nps_data, estimated_avg_salary
     * 배치가 아직 데이터를 적재하지 않은 경우 null 값으로 반환 → 프론트에서 섹션 숨김 처리
     */
    @GetMapping("/{id}/batch-info")
    public ResponseEntity<?> getBatchInfo(@PathVariable Long id) {
        try {
            JobPosting job = jobPostingRepository.findById(id).orElse(null);
            if (job == null) return ResponseEntity.notFound().build();

            Map<String, Object> result = new LinkedHashMap<>();

            // 기업 문화 키워드 (null이면 프론트에서 섹션 숨김)
            result.put("cultureKeywords",
                isBlankOrNull(job.getCultureKeywords()) ? null : job.getCultureKeywords().trim());

            // 기업 핵심가치 (null이면 프론트에서 섹션 숨김)
            result.put("coreValues",
                isBlankOrNull(job.getCoreValues()) ? null : job.getCoreValues().trim());

            // 월별 NPS 데이터 (null이면 차트 숨김)
            result.put("monthlyNpsData",
                isBlankOrNull(job.getMonthlyNpsData()) ? null : job.getMonthlyNpsData().trim());

            // 예상 평균 연봉 (null이면 숨김)
            result.put("estimatedAvgSalary", job.getEstimatedAvgSalary());

            // 마감일 (deadline — 캘린더 연동용, 현재는 정보 제공만)
            result.put("deadline",
                isBlankOrNull(job.getDeadline()) ? null : job.getDeadline().trim());

            // 경력 요건 연수 (필터링 확장용)
            result.put("reqExperienceYears", job.getReqExperienceYears());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // JobPosting 엔티티에 해당 컬럼이 아직 없는 경우 빈 응답 반환
            // (배치가 DB에 컬럼을 추가하기 전 상황 대비)
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("cultureKeywords", null);
            empty.put("coreValues", null);
            empty.put("monthlyNpsData", null);
            empty.put("estimatedAvgSalary", null);
            empty.put("deadline", null);
            empty.put("reqExperienceYears", null);
            return ResponseEntity.ok(empty);
        }
    }

    private boolean isBlankOrNull(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 공고 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getJobById(@PathVariable Long id) {
        try {
            JobPosting job = jobPostingRepository.findById(id).orElse(null);
            if (job == null) return ResponseEntity.notFound().build();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", job.getId());
            result.put("jobId", job.getJobId());
            result.put("title", job.getTitle());
            result.put("company", job.getCompany());
            result.put("location", job.getLocation());
            result.put("jobType", job.getJobType());
            result.put("techStack", job.getTechStack());
            result.put("salary", job.getSalary());
            result.put("description", job.getDescription());
            result.put("url", job.getUrl());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 유사 공고 추천: 기술스택/직무 겹침 기준 최대 3개
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<?> getSimilarJobs(@PathVariable Long id) {
        try {
            JobPosting current = jobPostingRepository.findById(id).orElse(null);
            if (current == null) return ResponseEntity.notFound().build();

            Set<String> myStacks = Arrays.stream(
                    (current.getTechStack() != null ? current.getTechStack() : "").split(","))
                    .map(String::trim).map(String::toLowerCase)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toSet());

            String myJob = current.getNormalizedJob() != null ?
                    current.getNormalizedJob().toLowerCase() : "";

            List<Map<String, Object>> scored = jobPostingRepository
                    .findByIsValidTrueAndIsDuplicateFalseAndIsITJobTrueAndIsClosedFalse()
                    .stream()
                    .filter(j -> !j.getId().equals(id))
                    .map(job -> {
                        int score = 0;
                        Set<String> jobStacks = Arrays.stream(
                                (job.getTechStack() != null ? job.getTechStack() : "").split(","))
                                .map(String::trim).map(String::toLowerCase)
                                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
                        score += myStacks.stream().filter(jobStacks::contains).count() * 20;

                        String jobNorm = job.getNormalizedJob() != null ?
                                job.getNormalizedJob().toLowerCase() : "";
                        if (!myJob.isEmpty() && !jobNorm.isEmpty() &&
                                (myJob.contains(jobNorm) || jobNorm.contains(myJob))) score += 30;

                        if (current.getLocation() != null && job.getLocation() != null &&
                                current.getLocation().equals(job.getLocation())) score += 10;

                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("score", score);
                        m.put("id", job.getId());
                        m.put("title", job.getTitle());
                        m.put("company", job.getCompany());
                        m.put("location", job.getLocation());
                        m.put("salary", job.getSalary());
                        m.put("techStack", job.getTechStack());
                        m.put("jobType", job.getJobType());
                        m.put("url", job.getUrl());
                        return m;
                    })
                    .filter(m -> (int) m.get("score") > 0)
                    .sorted((a, b) -> (int) b.get("score") - (int) a.get("score"))
                    .limit(3)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(scored);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * AI 공고 요약: description → Groq 한 줄 요약 (한국어 강제)
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<?> getJobSummary(@PathVariable Long id) {
        try {
            JobPosting job = jobPostingRepository.findById(id).orElse(null);
            if (job == null) return ResponseEntity.notFound().build();

            String text = job.getDescription() != null ? job.getDescription() : "";
            if (text.isBlank()) return ResponseEntity.ok(Map.of("summary", ""));
            if (text.length() > 1000) text = text.substring(0, 1000);

            String prompt = "아래 채용공고를 반드시 순수 한국어로만 2~3문장 요약하세요.\n" +
                    "[절대 규칙 - 이 규칙을 어기면 무효]\n" +
                    "1. 영어 단어 사용 완전 금지. preferred→우대, required→필수, experience→경험, additionally→또한, including→포함, such as→예를 들어 처럼 반드시 한국어로 변환.\n" +
                    "2. Spring, Java, React, AWS, Docker, Kubernetes, Python, Node.js, Vue, MySQL 같은 기술 고유명사만 영어 허용.\n" +
                    "3. *, **, # 등 마크다운 기호 사용 완전 금지.\n" +
                    "4. 한국어 문장 2~3개로만 작성. 그 이상 금지.\n" +
                    "5. 스페인어, 프랑스어, 한자, 일본어 등 다른 언어 절대 사용 금지.\n\n" +
                    "[채용공고]\n" + text;

            WebClient groqClient = WebClient.builder().baseUrl("https://api.groq.com").build();
            Map<String, Object> body = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "너는 채용공고 요약 전문가다. " +
                                    "반드시 한국어로만 답해야 한다. 영어 일반 단어는 절대 사용하지 말 것. " +
                                    "preferred→우대, required→필수, experience→경험, additionally→또한, " +
                                    "including→포함, such as→예를 들어, furthermore→또한, however→하지만 으로 반드시 번역. " +
                                    "Spring, Java, React, Python, AWS, Docker, Kubernetes 같은 기술 고유명사만 영어 허용. " +
                                    "마크다운(*, **, #) 사용 절대 금지. 스페인어, 프랑스어, 한자, 일본어 절대 사용 금지. " +
                                    "순수 한국어 문장 2~3개만 출력. 이 규칙을 어기면 응답이 거부됩니다."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 200
            );

            Map response = groqClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String summary = "";
            if (response != null) {
                List choices = (List) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    summary = (String) ((Map) ((Map) choices.get(0)).get("message")).get("content");
                }
            }

            if (summary != null && !summary.isEmpty()) {
                for (Map.Entry<String, String> entry : EN_TO_KO.entrySet()) {
                    summary = summary.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b", entry.getValue());
                }
                summary = removeNonKorean(summary);
                summary = summary.replaceAll("[*#`]", "").trim();
            }

            return ResponseEntity.ok(Map.of("summary", summary != null ? summary : ""));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("summary", ""));
        }
    }
}
