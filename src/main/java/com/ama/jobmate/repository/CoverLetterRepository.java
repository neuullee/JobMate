package com.ama.jobmate.repository;

import com.ama.jobmate.entity.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    // 특정 회원의 자소서 전체 (최신순)
    List<CoverLetter> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 특정 회원의 특정 자소서 조회 (보안: memberId 검증)
    java.util.Optional<CoverLetter> findByIdAndMemberId(Long id, Long memberId);
}
