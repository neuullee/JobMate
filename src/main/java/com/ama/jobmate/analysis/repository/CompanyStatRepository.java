package com.ama.jobmate.repository;

import com.ama.jobmate.entity.CompanyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyStatRepository extends JpaRepository<CompanyStat, Long> {
    Optional<CompanyStat> findByCompanyName(String companyName);
    List<CompanyStat> findByCompanyNameContaining(String keyword);
}