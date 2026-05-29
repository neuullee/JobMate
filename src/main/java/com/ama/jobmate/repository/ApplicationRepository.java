package com.ama.jobmate.repository;

import com.ama.jobmate.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByMemberIdOrderByAppliedDateDesc(Long memberId);
}