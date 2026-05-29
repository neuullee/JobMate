package com.ama.jobmate.repository;

import com.ama.jobmate.entity.NpsCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NpsCompanyRepository extends JpaRepository<NpsCompany, Long> {
    Optional<NpsCompany> findByCompanyName(String companyName);
    Optional<NpsCompany> findByBizNo(String bizNo);
    List<NpsCompany> findByCompanyNameContaining(String keyword);
}