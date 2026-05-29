package com.ama.jobmate.repository;

import com.ama.jobmate.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
    Optional<MemberProfile> findByMemberId(Long memberId);

    // 전체 회원의 희망직무 목록 (중복 제거)
    @Query("SELECT DISTINCT mp.desiredJob FROM MemberProfile mp WHERE mp.desiredJob IS NOT NULL AND mp.desiredJob <> ''")
    List<String> findDistinctDesiredJobs();
}