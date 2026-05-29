package com.ama.jobmate.repository;

import com.ama.jobmate.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);

  //아이디 찾기용
    Optional<Member> findFirstByNameAndPhone(String name, String phone);
    Optional<Member> findFirstByNameAndBirthDate(String name, LocalDate birthDate);
}
