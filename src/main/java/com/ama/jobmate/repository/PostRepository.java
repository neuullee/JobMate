package com.ama.jobmate.repository;

import com.ama.jobmate.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByTitleContainingOrContentContainingOrderByCreatedAtDesc(String title, String content, Pageable pageable);

    // 작성자 이름/닉네임으로 검색
    Page<Post> findByMember_NameContainingOrMember_NicknameContainingOrderByCreatedAtDesc(String name, String nickname, Pageable pageable);

    // 내가 쓴 글 (memberId로 조회)
    Page<Post> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(Long id);
}
