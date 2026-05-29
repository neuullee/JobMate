package com.ama.jobmate.service;

import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final JobPostingRepository jobPostingRepository;

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .build();

    public boolean existsByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            return false;
        }
        return jobPostingRepository.findByJobId(jobId).isPresent();
    }

    public int importFromCsv(MultipartFile file) throws Exception {

        int savedCount = 0;
        int skippedCount = 0;

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
        );

        String line;
        boolean isFirstLine = true;
        List<JobPosting> toSave = new ArrayList<>();
        Set<String> seenJobIds = new HashSet<>();

        while ((line = reader.readLine()) != null) {

            if (isFirstLine) {
                isFirstLine = false;
                continue;
            }

            if (line.trim().isEmpty()) continue;

            try {
                String[] cols = parseCsvLine(line);

                // 현재 UiPath CSV 컬럼 순서 기준:
                // 0 JobId
                // 1 Rank
                // 2 Score
                // 3 Title
                // 4 Company
                // 5 URL
                // 6 Deadline
                // 7 TechStack
                // 8 JobLevel
                // 9 IsRemote
                // 10 AI_Summary
                // 11 AI_Reason
                // 12 StackScore
                // 13 LevelScore
                // 14 RemoteScore
                // 15 FullDescription
                // 16 Location
                if (cols.length < 17) {
                    log.warn("컬럼 수 부족 스킵: {}", line);
                    skippedCount++;
                    continue;
                }

                String jobId           = getCol(cols, 0);
                String rank            = getCol(cols, 1);   // 현재 직접 저장에 안 씀
                String scoreOrSalary   = getCol(cols, 2);   // 기존 salary 컬럼에 임시 저장
                String title           = getCol(cols, 3);
                String company         = getCol(cols, 4);
                String url             = getCol(cols, 5);
                String deadline        = getCol(cols, 6);
                String techStack       = getCol(cols, 7);
                String jobLevel        = getCol(cols, 8);
                String isRemote        = getCol(cols, 9);
                String aiSummary       = getCol(cols, 10);
                String aiReason        = getCol(cols, 11);
                String stackScore      = getCol(cols, 12);
                String levelScore      = getCol(cols, 13);
                String remoteScore     = getCol(cols, 14);
                String fullDescription = getCol(cols, 15);
                String location        = getCol(cols, 16);

                if (jobId.isEmpty()) {
                    log.warn("jobId 없음 스킵: {}", line);
                    skippedCount++;
                    continue;
                }

                if (url.isEmpty()) {
                    log.warn("URL 없음 스킵: {}", line);
                    skippedCount++;
                    continue;
                }

                // DB에 이미 있는 공고면 스킵
                if (jobPostingRepository.findByJobId(jobId).isPresent()) {
                    log.info("중복 jobId 스킵(DB): {}", jobId);
                    skippedCount++;
                    continue;
                }

                // 이번 업로드 CSV 내부에서 같은 jobId가 또 나오면 스킵
                if (seenJobIds.contains(jobId)) {
                    log.info("중복 jobId 스킵(CSV): {}", jobId);
                    skippedCount++;
                    continue;
                }
                seenJobIds.add(jobId);

                JobPosting job = new JobPosting();

                // 원본값 저장
                job.setJobId(jobId);
                job.setTitle(title);
                job.setCompany(company);
                job.setUrl(url);
                job.setDescription(deadline);     // 현재 deadline 임시 저장
                job.setDeadline(deadline);        // deadline 필드에도 정상 저장
                job.setTechStack(techStack);
                job.setJobType(jobLevel);
                job.setSalary(scoreOrSalary);     // 현재 score를 salary 자리에 임시 저장
                job.setLocation(location);

                // fullDescription에 AI 요약/사유도 같이 붙여서 보존
                String mergedDescription = fullDescription;
                if (!isBlank(aiSummary) || !isBlank(aiReason)) {
                    mergedDescription = safe(fullDescription)
                            + "\n[AI Summary] " + safe(aiSummary)
                            + "\n[AI Reason] " + safe(aiReason)
                            + "\n[StackScore] " + safe(stackScore)
                            + "\n[LevelScore] " + safe(levelScore)
                            + "\n[RemoteScore] " + safe(remoteScore);
                }
                job.setFullDescription(mergedDescription);

                // 재택 여부 보완
                if ("Y".equalsIgnoreCase(isRemote)) {
                    if (isBlank(job.getLocation())) {
                        job.setLocation("재택/원격");
                    } else if (!job.getLocation().contains("재택") && !job.getLocation().contains("원격")) {
                        job.setLocation(job.getLocation() + " / 재택/원격");
                    }
                }

                // 정제/검증/중복처리
                applyNormalization(job);
                applyValidation(job);
                applyDuplicate(job);

                if (Boolean.FALSE.equals(job.getIsValid())) {
                    skippedCount++;
                    log.warn("불량 데이터 제외: {} / 사유: {}", job.getTitle(), job.getInvalidReason());
                    continue;
                }

                toSave.add(job);
                savedCount++;

            } catch (Exception e) {
                log.warn("파싱 실패 스킵: {} | 오류: {}", line, e.getMessage());
                skippedCount++;
            }
        }

        reader.close();

        if (!toSave.isEmpty()) {
            jobPostingRepository.saveAll(toSave);
        }

        log.info("CSV 임포트 완료 - 저장: {}건, 스킵: {}건", savedCount, skippedCount);
        return savedCount;
    }

    private void applyNormalization(JobPosting job) {
        String mergedText = safe(job.getTitle()) + " " + safe(job.getTechStack()) + " " + safe(job.getFullDescription());

        job.setNormalizedJob(normalizeJob(mergedText));
        job.setNormalizedLocation(normalizeLocation(job.getLocation(), job.getFullDescription()));
        job.setNormalizedJobType(normalizeJobType(job.getJobType()));
        job.setNormalizedTechStack(normalizeTechStack(job.getTechStack(), job.getFullDescription()));

        int[] salaryRange = parseSalaryRange(job.getSalary(), job.getFullDescription());
        job.setSalaryMin(salaryRange[0] > 0 ? salaryRange[0] : null);
        job.setSalaryMax(salaryRange[1] > 0 ? salaryRange[1] : null);

        job.setIsITJob(isITJob(mergedText));
        job.setIsClosed(isClosed(job.getDescription(), job.getFullDescription()));

        // 좌표 변환 (카카오 API)
        if ((job.getLat() == null || job.getLat() == 0.0) && !isBlank(job.getNormalizedLocation())) {
            double[] coords = geocodeAddress(job.getNormalizedLocation());
            if (coords != null) {
                job.setLat(coords[0]);
                job.setLng(coords[1]);
                log.info("좌표 변환 성공 - location:{} lat:{} lng:{}", job.getNormalizedLocation(), coords[0], coords[1]);
            }
        }

        log.info("정규화 완료 - title: {}, normalizedJob: {}, normalizedTechStack: {}, isITJob: {}",
                job.getTitle(), job.getNormalizedJob(), job.getNormalizedTechStack(), job.getIsITJob());
    }

    /**
     * 카카오 REST API로 주소 → 좌표 변환
     * 실패 시 null 반환 (점수 계산은 텍스트 매칭 fallback으로 처리됨)
     */
    private double[] geocodeAddress(String address) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", address)
                            .queryParam("size", 1)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) return null;

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode documents = root.path("documents");

            if (documents.isArray() && documents.size() > 0) {
                JsonNode first = documents.get(0);
                double lat = first.path("y").asDouble(0);
                double lng = first.path("x").asDouble(0);
                if (lat != 0 && lng != 0) return new double[]{lat, lng};
            }
        } catch (Exception e) {
            log.warn("좌표 변환 실패 - address:{} error:{}", address, e.getMessage());
        }
        return null;
    }

    private void applyValidation(JobPosting job) {
        job.setIsValid(true);
        job.setInvalidReason(null);

        if (isBlank(job.getJobId())) {
            invalidate(job, "jobId 없음");
            return;
        }

        if (isBlank(job.getTitle())) {
            invalidate(job, "제목 없음");
            return;
        }

        if (isBlank(job.getCompany())) {
            invalidate(job, "회사명 없음");
            return;
        }

        if (isBlank(job.getUrl())) {
            invalidate(job, "URL 없음");
            return;
        }

        if (Boolean.FALSE.equals(job.getIsITJob())) {
            invalidate(job, "IT 공고 아님");
            return;
        }

        if (isBlank(job.getNormalizedJob())) {
            invalidate(job, "직무 정규화 실패");
            return;
        }

        String content = safe(job.getFullDescription()).trim();
        if (!content.isEmpty() && content.length() < 20) {
            invalidate(job, "상세내용 너무 짧음");
            return;
        }

        if (Boolean.TRUE.equals(job.getIsClosed())) {
            invalidate(job, "마감 공고");
            return;
        }

        // 파트타임/단시간/알바/인턴 제외
        String titleLower = safe(job.getTitle()).toLowerCase(Locale.ROOT);
        if (containsAny(titleLower,
                "파트타임", "파트 타임", "단시간", "알바", "아르바이트",
                "part time", "parttime", "part-time",
                "인턴", "intern", "실습", "견습")) {
            invalidate(job, "파트타임/알바/인턴 공고");
            return;
        }

        // 근무 형태가 파트타임인 경우 (jobType 필드)
        String jobTypeLower = safe(job.getNormalizedJobType()).toLowerCase(Locale.ROOT);
        if (containsAny(jobTypeLower, "파트", "단시간", "알바", "아르바이트")) {
            invalidate(job, "파트타임 근무형태");
            return;
        }
    }

    private void applyDuplicate(JobPosting job) {
        String duplicateKey = buildDuplicateKey(job);
        job.setDuplicateGroupKey(duplicateKey);

        boolean duplicate;
        if (job.getId() == null) {
            duplicate = jobPostingRepository.existsByDuplicateGroupKey(duplicateKey);
        } else {
            duplicate = jobPostingRepository.existsByDuplicateGroupKeyAndIdNot(duplicateKey, job.getId());
        }

        job.setIsDuplicate(duplicate);
    }

    private String buildDuplicateKey(JobPosting job) {
        String company = normalizeTextForKey(job.getCompany());
        String title = normalizeTextForKey(job.getTitle());
        String location = normalizeTextForKey(job.getNormalizedLocation());
        String role = normalizeTextForKey(job.getNormalizedJob());

        return company + "|" + title + "|" + location + "|" + role;
    }

    private String normalizeJob(String text) {
        String t = safe(text).toLowerCase(Locale.ROOT);

        if (containsAny(t, "풀스택", "fullstack", "full-stack")) return "FULLSTACK";
        if (containsAny(t, "백엔드", "backend", "back-end", "server", "서버", "api", "spring", "java")) return "BACKEND";
        if (containsAny(t, "프론트", "frontend", "front-end", "react", "vue", "javascript", "typescript")) return "FRONTEND";
        if (containsAny(t, "클라우드", "cloud", "aws", "azure", "gcp")) return "CLOUD";
        if (containsAny(t, "데브옵스", "devops", "infra", "infrastructure", "kubernetes", "docker", "ci/cd")) return "DEVOPS";
        if (containsAny(t, "데이터", "data engineer", "data analyst", "etl", "bigquery")) return "DATA";
        if (containsAny(t, "ai", "ml", "machine learning", "딥러닝", "llm", "인공지능")) return "AI";
        if (containsAny(t, "앱", "android", "ios", "flutter", "react native")) return "MOBILE";
        if (containsAny(t, "보안", "security")) return "SECURITY";
        if (containsAny(t, "dba", "database administrator", "데이터베이스")) return "DBA";

        return "";
    }

    private String normalizeLocation(String location, String fullDescription) {
        String text = (safe(location) + " " + safe(fullDescription)).toLowerCase(Locale.ROOT);

        if (containsAny(text, "재택", "원격", "remote", "hybrid")) return "REMOTE";
        if (containsAny(text, "서울", "seoul")) return "SEOUL";
        if (containsAny(text, "경기", "판교", "성남", "수원", "용인", "고양", "시흥", "부천", "안양")) return "GYEONGGI";
        if (containsAny(text, "인천")) return "INCHEON";
        if (containsAny(text, "부산")) return "BUSAN";
        if (containsAny(text, "대전")) return "DAEJEON";
        if (containsAny(text, "대구")) return "DAEGU";
        if (containsAny(text, "광주")) return "GWANGJU";
        if (containsAny(text, "울산")) return "ULSAN";
        if (containsAny(text, "전국")) return "NATIONWIDE";

        return "";
    }

    private String normalizeJobType(String jobType) {
        String t = safe(jobType).toLowerCase(Locale.ROOT);

        if (containsAny(t, "정규직", "full time", "full-time")) return "FULL_TIME";
        if (containsAny(t, "계약직", "contract")) return "CONTRACT";
        if (containsAny(t, "인턴", "intern")) return "INTERN";
        if (containsAny(t, "프리랜서", "freelancer")) return "FREELANCER";

        return "";
    }

    private String normalizeTechStack(String techStack, String fullDescription) {
        String text = (safe(techStack) + " " + safe(fullDescription)).toLowerCase(Locale.ROOT);
        List<String> stacks = new ArrayList<>();

        addIfContains(stacks, text, "JAVA", "java");
        addIfContains(stacks, text, "SPRING", "spring");
        addIfContains(stacks, text, "SPRING_BOOT", "spring boot", "springboot");
        addIfContains(stacks, text, "PYTHON", "python");
        addIfContains(stacks, text, "JAVASCRIPT", "javascript");
        addIfContains(stacks, text, "TYPESCRIPT", "typescript");
        addIfContains(stacks, text, "REACT", "react");
        addIfContains(stacks, text, "VUE", "vue");
        addIfContains(stacks, text, "NODEJS", "node", "nodejs");
        addIfContains(stacks, text, "MYSQL", "mysql");
        addIfContains(stacks, text, "MARIADB", "mariadb");
        addIfContains(stacks, text, "POSTGRESQL", "postgresql", "postgres");
        addIfContains(stacks, text, "ORACLE", "oracle");
        addIfContains(stacks, text, "AWS", "aws");
        addIfContains(stacks, text, "AZURE", "azure");
        addIfContains(stacks, text, "GCP", "gcp", "google cloud");
        addIfContains(stacks, text, "DOCKER", "docker");
        addIfContains(stacks, text, "KUBERNETES", "kubernetes", "k8s");
        addIfContains(stacks, text, "JPA", "jpa", "hibernate");
        addIfContains(stacks, text, "QUERYDSL", "querydsl");
        addIfContains(stacks, text, "REDIS", "redis");
        addIfContains(stacks, text, "MONGODB", "mongodb");
        addIfContains(stacks, text, "GIT", "git");
        addIfContains(stacks, text, "LINUX", "linux");
        addIfContains(stacks, text, "PHP", "php");

        return String.join(",", stacks);
    }

    private int[] parseSalaryRange(String salary, String fullDescription) {
        // salary 컬럼이 점수값(3자리 이하 숫자)이면 무시
        String salaryText = safe(salary).trim();
        boolean isSalaryMeaningful = salaryText.contains("만원") || salaryText.contains("~")
                || salaryText.contains("억") || salaryText.matches(".*\\d{4,}.*");

        String text = (isSalaryMeaningful ? salaryText : "") + " " + safe(fullDescription);
        text = text.replace(",", "").replace(" ", "");

        // 범위 패턴: 3000~5000 또는 3000만~5000만
        Matcher rangeMatcher = Pattern.compile("(\\d{4,5})[~\\-](\\d{4,5})").matcher(text);
        if (rangeMatcher.find()) {
            int min = parseInt(rangeMatcher.group(1));
            int max = parseInt(rangeMatcher.group(2));
            if (min >= 1000 && max >= 1000) return new int[]{min, max};
        }

        // 단일 연봉: 최소 1000만원 이상인 것만
        Matcher singleMatcher = Pattern.compile("(\\d{4,5})만?원?").matcher(text);
        while (singleMatcher.find()) {
            int value = parseInt(singleMatcher.group(1));
            if (value >= 1000 && value <= 30000) return new int[]{value, value};
        }

        return new int[]{0, 0};
    }
    private boolean isITJob(String text) {
        String t = safe(text).toLowerCase(Locale.ROOT);

        // 명확히 IT 직군 아닌 키워드가 있으면 제외
        if (containsAny(t,
                "상담", "콜센터", "고객센터", "텔레마케터",
                "운전", "배달", "택배", "물류", "창고", "생산직", "제조",
                "간호", "의료", "요양", "복지", "사회복지",
                "교사", "강사", "튜터",
                "경리", "회계", "총무", "인사담당",
                "4.5h", "4.5시간", "단시간 계약",
                // 추가 비IT 키워드
                "마케터", "마케팅", "영업", "세일즈", "sales",
                "디자이너", "사운드", "음향", "영상편집", "촬영",
                "logistics", "물류관리", "무역", "수출입",
                "화장품", "뷰티", "패션", "의류",
                "요리사", "셰프", "조리", "식품",
                "건축", "토목", "기계", "전기", "설비", "시설관리",
                "cctv 유지", "카메라 설치",
                "각 부문별", "전 직군", "전직군")) {
            return false;
        }

        return containsAny(
                t,
                "개발", "developer", "engineer", "backend", "frontend", "fullstack",
                "java", "spring", "react", "vue", "python", "node",
                "cloud", "aws", "azure", "gcp", "devops", "docker", "kubernetes",
                "data", "ai", "ml", "security", "dba", "ios", "android", "php"
        );
    }

    private boolean isClosed(String description, String fullDescription) {
        String text = (safe(description) + " " + safe(fullDescription)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "마감", "채용종료", "closed")) return true;

        // deadline 날짜 형식이면 현재 날짜와 비교
        String dl = safe(description).trim();
        if (!dl.isBlank() && !dl.equals("상시채용") && !dl.contains("상시") && !dl.contains("채용시")) {
            try {
                String normalized = dl.replaceAll("[^0-9\\-]", "");
                if (normalized.length() >= 10) {
                    java.time.LocalDate deadlineDate = java.time.LocalDate.parse(normalized.substring(0, 10));
                    if (deadlineDate.isBefore(java.time.LocalDate.now())) return true;
                } else if (normalized.length() == 8) {
                    String formatted = normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6, 8);
                    java.time.LocalDate deadlineDate = java.time.LocalDate.parse(formatted);
                    if (deadlineDate.isBefore(java.time.LocalDate.now())) return true;
                }
            } catch (Exception ignored) {
                // 파싱 실패 시 무시
            }
        }
        return false;
    }

    private void invalidate(JobPosting job, String reason) {
        job.setIsValid(false);
        job.setInvalidReason(reason);
    }

    private void addIfContains(List<String> list, String text, String code, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                if (!list.contains(code)) {
                    list.add(code);
                }
                return;
            }
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractRecIdx(String url) {
        Pattern pattern = Pattern.compile("rec_idx=(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return "saramin_" + matcher.group(1);
        }
        return "";
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private String getCol(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return "";
        return cols[idx] == null ? "" : cols[idx].trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeTextForKey(String value) {
        return Normalizer.normalize(safe(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9가-힣_]", "");
    }

    public long countAllJobs() {
        return jobPostingRepository.count();
    }
    public List<String> getAllJobIds() {
        return jobPostingRepository.findAll()
                .stream()
                .map(JobPosting::getJobId)
                .filter(id -> id != null && !id.trim().isEmpty())
                .toList();
    }
}