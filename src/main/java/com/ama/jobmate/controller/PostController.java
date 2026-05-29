package com.ama.jobmate.controller;

import com.ama.jobmate.entity.Comment;
import com.ama.jobmate.entity.Post;
import com.ama.jobmate.service.PostService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 목록 (카테고리 필터 + 검색 + 내 글)
    @GetMapping
    @ResponseBody
    public Map<String, Object> getPosts(
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "title") String searchType,
            @RequestParam(defaultValue = "false") boolean myPosts,
            HttpSession session) {

        Pageable pageable = PageRequest.of(page, 10);
        Page<Post> posts;

        Long sessionMemberId = (Long) session.getAttribute("memberId");

        if (myPosts && sessionMemberId != null) {
            // 내가 쓴 글
            posts = postService.getPostsByMember(sessionMemberId, pageable);
        } else if (!keyword.isEmpty()) {
            // searchType에 따라 분기
            if ("author".equals(searchType)) {
                posts = postService.searchPostsByAuthor(keyword, pageable);
            } else {
                posts = postService.searchPosts(keyword, pageable);
            }
        } else {
            posts = postService.getPosts(category, pageable);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Post p : posts.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("category", p.getCategory());
            m.put("tags", p.getTags());
            m.put("viewCount", p.getViewCount());
            m.put("likeCount", p.getLikeCount());
            m.put("commentCount", p.getComments().size());
            m.put("createdAt", p.getCreatedAt().toString());
            m.put("anonymous", p.isAnonymous());
            String authorName = p.isAnonymous() ? "익명" :
                    (p.getMember().getNickname() != null ? p.getMember().getNickname() : p.getMember().getName());
            m.put("author", authorName);

            // 내 글 여부
            boolean isOwner = sessionMemberId != null && p.getMember().getId().equals(sessionMemberId);
            m.put("isOwner", isOwner);

            list.add(m);
        }

        return Map.of(
                "posts", list,
                "totalPages", posts.getTotalPages(),
                "totalElements", posts.getTotalElements(),
                "currentPage", page
        );
    }

    // 게시글 상세
    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id, HttpSession session) {
        return postService.getPost(id).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("content", p.getContent());
            m.put("category", p.getCategory());
            m.put("tags", p.getTags());
            m.put("viewCount", p.getViewCount());
            m.put("likeCount", p.getLikeCount());
            m.put("createdAt", p.getCreatedAt().toString());
            m.put("anonymous", p.isAnonymous());
            String authorName = p.isAnonymous() ? "익명" :
                    (p.getMember().getNickname() != null ? p.getMember().getNickname() : p.getMember().getName());
            m.put("author", authorName);

            Long sessionMemberId = (Long) session.getAttribute("memberId");
            boolean isOwner = sessionMemberId != null && p.getMember().getId().equals(sessionMemberId);
            m.put("isOwner", isOwner);

            boolean liked = sessionMemberId != null && postService.isLiked(p.getId(), sessionMemberId);
            m.put("liked", liked);

            return ResponseEntity.ok(m);
        }).orElse(ResponseEntity.notFound().build());
    }

    // 게시글 작성
    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));

        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String category = (String) body.getOrDefault("category", "FREE");
        String tags = (String) body.getOrDefault("tags", "");
        boolean anonymous = Boolean.parseBoolean(String.valueOf(body.getOrDefault("anonymous", false)));

        Post post = postService.createPost(memberId, title, content, category, tags, anonymous);
        return ResponseEntity.ok(Map.of("id", post.getId(), "success", true));
    }

    // 게시글 수정
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));

        try {
            String title = (String) body.get("title");
            String content = (String) body.get("content");
            String tags = (String) body.getOrDefault("tags", "");
            boolean anonymous = Boolean.parseBoolean(String.valueOf(body.getOrDefault("anonymous", false)));
            Post post = postService.updatePost(id, memberId, title, content, tags, anonymous);
            return ResponseEntity.ok(Map.of("id", post.getId(), "success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // 게시글 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));

        try {
            postService.deletePost(id, memberId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // 댓글 목록
    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long id, HttpSession session) {
        Long sessionMemberId = (Long) session.getAttribute("memberId");
        List<Comment> comments = postService.getComments(id);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Comment c : comments) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("anonymous", c.isAnonymous());
            String authorName = c.isAnonymous() ? "익명" :
                    (c.getMember().getNickname() != null ? c.getMember().getNickname() : c.getMember().getName());
            m.put("author", authorName);
            m.put("createdAt", c.getCreatedAt().toString());
            boolean isOwner = sessionMemberId != null && c.getMember().getId().equals(sessionMemberId);
            m.put("isOwner", isOwner);
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    // 댓글 작성
    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));

        String content = (String) body.get("content");
        boolean anonymous = Boolean.parseBoolean(String.valueOf(body.getOrDefault("anonymous", false)));
        Comment comment = postService.addComment(id, memberId, content, anonymous);
        return ResponseEntity.ok(Map.of("id", comment.getId(), "success", true));
    }

    // 댓글 삭제
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));

        try {
            postService.deleteComment(commentId, memberId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // 좋아요 토글
    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long id, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인 필요"));
        return ResponseEntity.ok(postService.toggleLike(id, memberId));
    }
}
