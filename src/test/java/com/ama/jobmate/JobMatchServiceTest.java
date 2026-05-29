package com.ama.jobmate;

import com.ama.jobmate.dto.JobMatchResult;
import com.ama.jobmate.entity.CompanyStat;
import com.ama.jobmate.entity.JobPosting;
import com.ama.jobmate.entity.Member;
import com.ama.jobmate.entity.MemberProfile;
import com.ama.jobmate.repository.CompanyStatRepository;
import com.ama.jobmate.repository.JobPostingRepository;
import com.ama.jobmate.repository.MemberProfileRepository;
import com.ama.jobmate.repository.MemberRepository;
import com.ama.jobmate.service.JobMatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMatchServiceTest {

//    JobMatchService 개요

//    회원 없음이면 빈 결과
//    프로필 없음이면 빈 결과
//    추천 점수 계산 및 정렬
//    공고 연봉이 없을 때 CompanyStat 평균연봉 fallback 사용


    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private MemberProfileRepository memberProfileRepository;

    @Mock
    private CompanyStatRepository companyStatRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private JobMatchService jobMatchService;

    private Member member;
    private MemberProfile profile;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setId(1L);
        member.setEmail("user@test.com");

        profile = new MemberProfile();
        profile.setMember(member);
        profile.setDesiredJob("백엔드");
        profile.setTechStack("Java,Spring");
        profile.setLocation("서울");
        profile.setMinSalary(4000);
        profile.setWeightJob(40);
        profile.setWeightStack(30);
        profile.setWeightLocation(20);
        profile.setWeightSalary(10);
    }

    @Test
    void getMatchedJobs_shouldReturnEmpty_whenMemberDoesNotExist() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        List<JobMatchResult> results = jobMatchService.getMatchedJobs(1L);

        assertTrue(results.isEmpty());
    }

    @Test
    void matchJobs_shouldReturnEmpty_whenProfileDoesNotExist() {
        when(memberProfileRepository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(jobPostingRepository.findByIsValidTrueAndIsClosedFalse()).thenReturn(List.of(createBackendJob("JOB-1", "좋은회사")));

        List<JobMatchResult> results = jobMatchService.matchJobs(member);

        assertTrue(results.isEmpty());
    }

    @Test
    void matchJobs_shouldCalculateScoreAndSortBestMatchFirst() {
        JobPosting bestJob = createBackendJob("JOB-1", "좋은회사");
        bestJob.setSalaryMin(5000);

        JobPosting weakJob = new JobPosting();
        weakJob.setJobId("JOB-2");
        weakJob.setTitle("프론트엔드 개발자");
        weakJob.setCompany("다른회사");
        weakJob.setNormalizedJob("FRONTEND");
        weakJob.setTechStack("React,TypeScript");
        weakJob.setNormalizedLocation("BUSAN");
        weakJob.setSalaryMin(3200);
        weakJob.setIsValid(true);
        weakJob.setIsClosed(false);

        when(memberProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(jobPostingRepository.findByIsValidTrueAndIsClosedFalse()).thenReturn(List.of(weakJob, bestJob));
        when(companyStatRepository.findByCompanyName("좋은회사")).thenReturn(Optional.empty());
        when(companyStatRepository.findByCompanyName("다른회사")).thenReturn(Optional.empty());

        List<JobMatchResult> results = jobMatchService.matchJobs(member);

        assertEquals(2, results.size());
        assertEquals("좋은회사", results.get(0).getJob().getCompany());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
        assertTrue(results.get(0).getMatchReason().contains("직무(40%,100"));
        assertTrue(results.get(0).getMatchReason().contains("연봉(10%,100"));
    }

    @Test
    void matchJobs_shouldUseCompanyStatSalary_whenJobSalaryIsMissing() {
        JobPosting job = createBackendJob("JOB-1", "좋은회사");
        job.setSalaryMin(null);

        CompanyStat stat = new CompanyStat();
        stat.setCompanyName("좋은회사");
        stat.setAvgSalary(4500);
        stat.setLeaveRate(12.5);
        stat.setEmployeeCount(120);

        when(memberProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(jobPostingRepository.findByIsValidTrueAndIsClosedFalse()).thenReturn(List.of(job));
        when(companyStatRepository.findByCompanyName("좋은회사")).thenReturn(Optional.of(stat));

        List<JobMatchResult> results = jobMatchService.matchJobs(member);

        assertEquals(1, results.size());
        JobMatchResult result = results.get(0);
        assertTrue(result.getScore() > 0);
        assertTrue(result.getMatchReason().contains("연봉(10%,100"));
        assertEquals(12.5, result.getLeaveRate());
        assertEquals(120, result.getEmployeeCount());
    }

    private JobPosting createBackendJob(String jobId, String company) {
        JobPosting job = new JobPosting();
        job.setJobId(jobId);
        job.setTitle("백엔드 개발자");
        job.setCompany(company);
        job.setNormalizedJob("백엔드");
        job.setTechStack("Java,Spring,MySQL");
        job.setNormalizedLocation("서울");
        job.setIsValid(true);
        job.setIsClosed(false);
        return job;
    }
}
