package com.ama.jobmate.service;

import com.ama.jobmate.entity.*;
import com.ama.jobmate.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final MemberRepository memberRepository;

    // 게시글 목록
    public Page<Post> getPosts(String category, Pageable pageable) {
        if (category == null || category.isEmpty() || category.equals("ALL")) {
            return postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return postRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
    }

    // 제목+내용 검색
    public Page<Post> searchPosts(String keyword, Pageable pageable) {
        return postRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(keyword, keyword, pageable);
    }

    // 작성자 이름/닉네임으로 검색
    public Page<Post> searchPostsByAuthor(String keyword, Pageable pageable) {
        return postRepository.findByMember_NameContainingOrMember_NicknameContainingOrderByCreatedAtDesc(
                keyword, keyword, pageable);
    }

    // 내가 쓴 글
    public Page<Post> getPostsByMember(Long memberId, Pageable pageable) {
        return postRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    // 게시글 상세 (조회수 증가)
    @Transactional
    public Optional<Post> getPost(Long id) {
        postRepository.incrementViewCount(id);
        return postRepository.findById(id);
    }

    // 게시글 작성
    @Transactional
    public Post createPost(Long memberId, String title, String content, String category, String tags, boolean anonymous) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));
        Post post = new Post();
        post.setMember(member);
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.setTags(tags);
        post.setAnonymous(anonymous);
        return postRepository.save(post);
    }

    // 게시글 수정
    @Transactional
    public Post updatePost(Long postId, Long memberId, String title, String content, String tags, boolean anonymous) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));
        if (!post.getMember().getId().equals(memberId)) throw new IllegalArgumentException("권한 없음");
        post.setTitle(title);
        post.setContent(content);
        post.setTags(tags);
        post.setAnonymous(anonymous);
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long postId, Long memberId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));
        if (!post.getMember().getId().equals(memberId)) throw new IllegalArgumentException("권한 없음");
        postRepository.delete(post);
    }

    // 댓글 작성
    @Transactional
    public Comment addComment(Long postId, Long memberId, String content, boolean anonymous) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setMember(member);
        comment.setContent(content);
        comment.setAnonymous(anonymous);
        return commentRepository.save(comment);
    }

    // 댓글 삭제
    @Transactional
    public void deleteComment(Long commentId, Long memberId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글 없음"));
        if (!comment.getMember().getId().equals(memberId)) throw new IllegalArgumentException("권한 없음");
        commentRepository.delete(comment);
    }

    // 좋아요 토글
    @Transactional
    public Map<String, Object> toggleLike(Long postId, Long memberId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음"));

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndMemberId(postId, memberId);
        boolean liked;
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.setLikeCount(post.getLikeCount() - 1);
            liked = false;
        } else {
            PostLike like = new PostLike();
            like.setPost(post);
            like.setMember(member);
            postLikeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            liked = true;
        }
        postRepository.save(post);
        return Map.of("liked", liked, "likeCount", post.getLikeCount());
    }

    // 댓글 목록
    public List<Comment> getComments(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
    }

    // 좋아요 여부
    public boolean isLiked(Long postId, Long memberId) {
        return postLikeRepository.existsByPostIdAndMemberId(postId, memberId);
    }
}
