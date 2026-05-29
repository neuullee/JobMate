package com.ama.jobmate.repository;

import com.ama.jobmate.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    boolean existsByJobId(String jobId);

    Optional<JobPosting> findByJobId(String jobId);

    List<JobPosting> findByIsValidTrueAndIsDuplicateFalseAndIsITJobTrue();

    List<JobPosting> findByIsValidTrueAndIsDuplicateFalseAndIsITJobTrueAndIsClosedFalse();

    // JobMatchService용: 마감 안 된 유효 공고 전체 조회
    List<JobPosting> findByIsValidTrueAndIsClosedFalse();

    boolean existsByDuplicateGroupKeyAndIdNot(String duplicateGroupKey, Long id);

    boolean existsByDuplicateGroupKey(String duplicateGroupKey);

    List<JobPosting> findByCompanyAndIsClosedFalse(String companyName);
}
