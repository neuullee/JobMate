package com.ama.jobmate.repository;

import com.ama.jobmate.entity.CompanyFinance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyFinanceRepository extends JpaRepository<CompanyFinance, Long> {
    Optional<CompanyFinance> findByCompanyName(String companyName);
    Optional<CompanyFinance> findByBusinessNumber(String businessNumber);
    Optional<CompanyFinance> findByCorpCode(String corpCode);
}