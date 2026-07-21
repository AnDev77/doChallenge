package com.fitmeet.member.infrastructure;

import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMemberRepository extends JpaRepository<Member, Long>, MemberRepository {

    @Override
    Optional<Member> findByEmail(String email);

    @Override
    boolean existsByEmail(String email);
}
