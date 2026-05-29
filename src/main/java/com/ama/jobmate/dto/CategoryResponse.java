package com.ama.jobmate.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private Integer level;

    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();
}