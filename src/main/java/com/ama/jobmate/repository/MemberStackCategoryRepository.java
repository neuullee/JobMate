package com.ama.jobmate.repository;

import com.ama.jobmate.entity.MemberStackCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberStackCategoryRepository extends JpaRepository<MemberStackCategory, Long> {

    List<MemberStackCategory> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);
}