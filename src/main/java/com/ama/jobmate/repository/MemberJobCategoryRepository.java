package com.ama.jobmate.repository;

import com.ama.jobmate.entity.MemberJobCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberJobCategoryRepository extends JpaRepository<MemberJobCategory, Long> {

    List<MemberJobCategory> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);
}