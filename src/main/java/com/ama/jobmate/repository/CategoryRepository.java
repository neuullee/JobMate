package com.ama.jobmate.repository;

import com.ama.jobmate.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByTypeOrderByLevelAscIdAsc(String type);

    List<Category> findByTypeAndLevelOrderByIdAsc(String type, Integer level);
}