package com.ama.jobmate.controller;

import com.ama.jobmate.dto.CategoryResponse;
import com.ama.jobmate.entity.Category;
import com.ama.jobmate.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public List<CategoryResponse> getCategories(@RequestParam String type) {

        List<Category> all = categoryRepository.findByTypeOrderByLevelAscIdAsc(type);

        Map<Long, CategoryResponse> map = new HashMap<>();
        List<CategoryResponse> roots = new ArrayList<>();

        // 1. 전부 DTO로 변환
        for (Category c : all) {
            map.put(c.getId(),
                    CategoryResponse.builder()
                            .id(c.getId())
                            .name(c.getName())
                            .level(c.getLevel())
                            .build());
        }

        // 2. 트리 구성
        for (Category c : all) {
            CategoryResponse current = map.get(c.getId());

            if (c.getParent() == null) {
                roots.add(current);
            } else {
                CategoryResponse parent = map.get(c.getParent().getId());
                if (parent != null) {
                    parent.getChildren().add(current);
                }
            }
        }

        return roots;
    }
}