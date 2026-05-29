package com.ama.jobmate.repository;

import com.ama.jobmate.entity.DartCorpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DartCorpCodeRepository extends JpaRepository<DartCorpCode, Long> {
    Optional<DartCorpCode> findByCorpCode(String corpCode);
    Optional<DartCorpCode> findByBizrNo(String bizrNo);
    Optional<DartCorpCode> findByCorpName(String corpName);
    Optional<DartCorpCode> findByCorpNameContaining(String keyword);
}