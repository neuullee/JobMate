package com.ama.jobmate.service;

import com.ama.jobmate.dto.JobMatchResult;
import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.entity.MemberProfile;
import com.ama.jobmate.repository.CompanyStatRepository;
import com.ama.jobmate.repository.JobPostingRepository;
import com.ama.jobmate.repository.MemberProfileRepository;
import com.ama.jobmate.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobMatchService {

    private final JobPostingRepository jobPostingRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final CompanyStatRepository companyStatRepository;
    private final MemberRepository memberRepository;

    public List<JobMatchResult> getMatchedJobs(Long memberId) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) return Collections.emptyList();
        return matchJobs(member);
    }

    public List<JobMatchResult> matchJobs(Member member) {
        MemberProfile profile = memberProfileRepository.findByMemberId(member.getId()).orElse(null);
        List<JobPosting> allJobs = jobPostingRepository.findByIsValidTrueAndIsClosedFalse()
                .stream()
                .filter(job -> {
                    // deadline 필드가 있으면 날짜 기반으로 마감 여부 추가 검증
                    // (DB에 isClosed=false로 잘못 저장된 공고 방어)
                    String dl = job.getDeadline();
                    if (dl == null || dl.isBlank()) return true;          // null이면 통과
                    if (dl.contains("상시") || dl.contains("채용시")) return true; // 상시채용 통과
                    try {
                        String normalized = dl.replaceAll("[^0-9\\-]", "");
                        java.time.LocalDate deadlineDate;
                        if (normalized.length() >= 10) {
                            deadlineDate = java.time.LocalDate.parse(normalized.substring(0, 10));
                        } else if (normalized.length() == 8) {
                            deadlineDate = java.time.LocalDate.parse(
                                normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6, 8));
                        } else {
                            return true; // 파싱 불가 → 통과
                        }
                        return !deadlineDate.isBefore(java.time.LocalDate.now());
                    } catch (Exception e) {
                        return true; // 파싱 실패 → 통과
                    }
                })
                .collect(Collectors.toList());

        if (profile == null || allJobs.isEmpty()) return Collections.emptyList();

        int weightJob      = profile.getWeightJob()      != null ? profile.getWeightJob()      : 40;
        int weightStack    = profile.getWeightStack()    != null ? profile.getWeightStack()    : 30;
        int weightLocation = profile.getWeightLocation() != null ? profile.getWeightLocation() : 20;
        int weightSalary   = profile.getWeightSalary()   != null ? profile.getWeightSalary()   : 10;

        Set<String> userStacks = parseStacks(profile.getTechStack());
        boolean userHasStacks = !userStacks.isEmpty();

        List<JobMatchResult> results = new ArrayList<>();

        for (JobPosting job : allJobs) {
            // 1. 직무 점수
            int jobScore = 0;
            String jobName = (job.getNormalizedJob() != null && !job.getNormalizedJob().isBlank())
                    ? job.getNormalizedJob()
                    : (job.getTitle() != null ? job.getTitle() : "");

            if (profile.getDesiredJob() != null && !jobName.isBlank()) {
                String userJob = profile.getDesiredJob().trim();
                if (jobName.contains(userJob) || userJob.contains(jobName)) {
                    jobScore = 100;
                } else if (isSimilarJob(userJob, jobName)) {
                    jobScore = 60;
                }
            }

            // 2. 기술스택 점수
            int stackScore = 0;
            if (userHasStacks && job.getTechStack() != null && !job.getTechStack().isBlank()) {
                Set<String> jobStacks = parseStacks(job.getTechStack());
                if (!jobStacks.isEmpty()) {
                    long matched = userStacks.stream().filter(jobStacks::contains).count();
                    stackScore = (int) Math.round((double) matched / jobStacks.size() * 100);
                }
            }

            // 3. 지역 점수 (수정됨)
            int locationScore = calcLocationScore(profile, job);

            // 4. 연봉 점수
            int salaryScore = 0;
            if (profile.getMinSalary() != null) {
                Integer jobSalary = job.getSalaryMin();
                if (jobSalary == null) {
                    jobSalary = companyStatRepository.findByCompanyName(job.getCompany())
                            .map(stat -> stat.getAvgSalary())
                            .orElse(null);
                }
                if (jobSalary != null) {
                    if (jobSalary >= profile.getMinSalary()) {
                        salaryScore = 100;
                    } else {
                        int gap = profile.getMinSalary() - jobSalary;
                        salaryScore = Math.max(0, 100 - (gap / 500));
                    }
                }
            }

            // 5. 최종 점수
            int totalScore = (int) Math.round(
                    jobScore      * (weightJob      / 100.0) +
                    stackScore    * (weightStack    / 100.0) +
                    locationScore * (weightLocation / 100.0) +
                    salaryScore   * (weightSalary   / 100.0)
            );

            int wJob      = (int) Math.round(jobScore      * (weightJob      / 100.0));
            int wStack    = (int) Math.round(stackScore    * (weightStack    / 100.0));
            int wLocation = (int) Math.round(locationScore * (weightLocation / 100.0));
            int wSalary   = (int) Math.round(salaryScore   * (weightSalary   / 100.0));

            String reason = "직무(" + weightJob + "%," + jobScore + "," + wJob + ")" +
                    "|기술스택(" + weightStack + "%," + stackScore + "," + wStack + ")" +
                    "|지역(" + weightLocation + "%," + locationScore + "," + wLocation + ")" +
                    "|연봉(" + weightSalary + "%," + salaryScore + "," + wSalary + ")";

            JobMatchResult result = new JobMatchResult(job, totalScore, reason);

            companyStatRepository.findByCompanyName(job.getCompany()).ifPresent(stat -> {
                result.setLeaveRate(stat.getLeaveRate());
                result.setEmployeeCount(stat.getEmployeeCount());
            });

            results.add(result);
        }

        results.sort(Comparator.comparingInt(JobMatchResult::getScore).reversed()
                .thenComparing(r -> r.getJob() != null ? r.getJob().getCompany() : ""));

        return results;
    }

    /**
     * 지역 점수 계산
     * 우선순위: ① 좌표 거리 계산 → ② 텍스트 매칭 (location 필드 다중 경로)
     */
    private int calcLocationScore(MemberProfile profile, JobPosting job) {

        // ① 좌표 기반 거리 계산 (preferredLat/Lng + job.lat/lng 모두 있을 때)
        boolean hasProfileCoord = profile.getPreferredLat() != null && profile.getPreferredLng() != null
                && profile.getPreferredLat() != 0.0 && profile.getPreferredLng() != 0.0;
        boolean hasJobCoord = job.getLat() != null && job.getLng() != null
                && job.getLat() != 0.0 && job.getLng() != 0.0;

        if (hasProfileCoord && hasJobCoord) {
            double distKm = calcDistKm(profile.getPreferredLat(), profile.getPreferredLng(),
                    job.getLat(), job.getLng());
            double maxKm = profile.getMaxDistanceKm() != null ? profile.getMaxDistanceKm() : 30.0;
            int score = distKm <= maxKm ? (int) Math.round((1 - distKm / maxKm) * 100) : 0;
            log.info("좌표매칭 - job:{} dist:{}km max:{}km score:{}", job.getTitle(), String.format("%.1f", distKm), maxKm, score);
            return score;
        }

        // ② 텍스트 매칭 (좌표 없을 때 fallback)
        // 공고의 지역 문자열: normalizedLocation 우선, 없으면 location 원본 사용
        String jobLocRaw = job.getNormalizedLocation();
        if (jobLocRaw == null || jobLocRaw.isBlank()) {
            jobLocRaw = job.getLocation();
        }
        if (jobLocRaw == null || jobLocRaw.isBlank()) {
            log.info("지역매칭없음(공고지역null) - job:{}", job.getTitle());
            return 0;
        }
        String jobLoc = jobLocRaw.trim();

        // 프로필의 지역 문자열: preferredAddress 우선, 없으면 location 사용
        String profileLoc = profile.getPreferredAddress();
        if (profileLoc == null || profileLoc.isBlank()) {
            profileLoc = profile.getLocation();
        }
        if (profileLoc == null || profileLoc.isBlank()) {
            log.info("지역매칭없음(프로필지역null) - job:{}", job.getTitle());
            return 0;
        }

        // 재택/원격은 항상 100점
        if (isRemoteWork(jobLoc)) {
            log.info("재택매칭 - job:{} score:100", job.getTitle());
            return 100;
        }

        // 시/도 단위 키워드 추출 후 포함 여부 비교
        String normJobLoc     = normalizeLocation(jobLoc);
        String normProfileLoc = normalizeLocation(profileLoc);

        if (!normJobLoc.isEmpty() && !normProfileLoc.isEmpty()) {
            // 양방향 contains + 공통 시도 키워드 매칭
            if (normJobLoc.contains(normProfileLoc) || normProfileLoc.contains(normJobLoc)
                    || hasSameSido(normJobLoc, normProfileLoc)) {
                log.info("텍스트매칭성공 - job:{} jobLoc:{} profileLoc:{} score:100", job.getTitle(), jobLoc, profileLoc);
                return 100;
            }
        }

        log.info("텍스트매칭실패 - job:{} normJobLoc:{} normProfileLoc:{}", job.getTitle(), normJobLoc, normProfileLoc);
        return 0;
    }

    /** 재택/원격 여부 */
    private boolean isRemoteWork(String loc) {
        if (loc == null) return false;
        String l = loc.toLowerCase();
        return l.contains("재택") || l.contains("원격") || l.contains("remote");
    }

    /**
     * 지역 문자열 정규화
     * "서울특별시 금천구" → "서울 금천구"
     * "경기도 성남시" → "경기 성남"
     */
    private String normalizeLocation(String loc) {
        if (loc == null) return "";
        return loc.trim()
                .replaceAll("특별시|광역시|특별자치시|특별자치도|자치시", "")
                .replaceAll("도(?=\\s|$)", "")
                .replaceAll("시(?=\\s)", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    /** 시/도 단위 키워드가 같은지 확인 */
    private boolean hasSameSido(String normJob, String normProfile) {
        List<String> sidoList = Arrays.asList(
                "서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산",
                "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"
        );
        for (String sido : sidoList) {
            if (normJob.contains(sido) && normProfile.contains(sido)) return true;
        }
        return false;
    }

    private Set<String> parseStacks(String techStack) {
        if (techStack == null || techStack.isBlank()) return Collections.emptySet();
        return Arrays.stream(techStack.split("[,/|\\s]+"))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private boolean isSimilarJob(String userJob, String jobText) {
        if (userJob == null || jobText == null) return false;

        Map<String, List<String>> jobGroups = new HashMap<>();
        jobGroups.put("backend", Arrays.asList(
                "backend", "백엔드", "server", "서버", "java", "spring", "node",
                "python", "django", "flask", "웹개발", "응용sw", "소프트웨어개발",
                "개발자", "웹프로그래머", "서버개발", "jsp", "php", "asp"
        ));
        jobGroups.put("frontend", Arrays.asList(
                "frontend", "프론트엔드", "프론트", "react", "vue", "angular",
                "ui개발", "퍼블리셔", "화면개발", "웹퍼블"
        ));
        jobGroups.put("fullstack", Arrays.asList(
                "fullstack", "풀스택", "full-stack", "full stack",
                "웹개발자", "웹 개발자", "웹프로그래머"
        ));
        jobGroups.put("devops", Arrays.asList(
                "devops", "데브옵스", "infra", "인프라", "cloud", "클라우드",
                "aws", "시스템운영", "sre", "운영관리"
        ));
        jobGroups.put("data", Arrays.asList(
                "data", "데이터", "ml", "ai", "machine learning", "분석",
                "빅데이터", "데이터분석", "머신러닝", "딥러닝"
        ));
        jobGroups.put("mobile", Arrays.asList(
                "mobile", "모바일", "android", "ios", "flutter", "react native",
                "앱개발", "안드로이드"
        ));
        jobGroups.put("security", Arrays.asList(
                "security", "보안", "정보보안", "soc", "취약점"
        ));
        jobGroups.put("embedded", Arrays.asList(
                "embedded", "임베디드", "펌웨어", "firmware", "iot"
        ));

        String userLow = userJob.toLowerCase();
        String jobLow  = jobText.toLowerCase();

        for (List<String> group : jobGroups.values()) {
            boolean userMatch = group.stream().anyMatch(userLow::contains);
            boolean jobMatch  = group.stream().anyMatch(jobLow::contains);
            if (userMatch && jobMatch) return true;
        }
        return false;
    }

    private double calcDistKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
                        Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
