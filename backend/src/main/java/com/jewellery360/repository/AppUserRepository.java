package com.jewellery360.repository;

import com.jewellery360.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    List<AppUser> findAllByUsernameIgnoreCaseAndDeletedFalse(String username);
    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<AppUser> findByEmailIgnoreCaseAndDeletedFalse(String email);
    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<AppUser> findByPhoneAndDeletedFalse(String phone);
    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<AppUser> findByUsernameIgnoreCaseAndDeletedFalse(String username);
    Optional<AppUser> findByCompanyIdAndUsernameIgnoreCaseAndDeletedFalse(Long companyId, String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByUsernameIgnoreCaseAndDeletedFalse(String username);
    @Query("""
            select u from AppUser u
            left join fetch u.company c
            left join fetch u.branch b
            where u.deleted = false
              and u.company.id = :companyId
            order by u.username asc
            """)
@EntityGraph(attributePaths = {"company", "branch"})
    List<AppUser> findByCompanyIdAndDeletedFalse(@Param("companyId") Long companyId);

    @Query("""
            select u from AppUser u
            left join fetch u.company c
            left join fetch u.branch b
            where u.deleted = false
            order by u.username asc
            """)
    @EntityGraph(attributePaths = {"company", "branch"})
    List<AppUser> findAllByDeletedFalseOrderByUsernameAsc();

    @Query("""
            select u from AppUser u
            left join fetch u.company c
            left join fetch u.branch b
            where u.deleted = false
              and (lower(u.username) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or coalesce(u.phone, '') like concat('%', :search, '%')
                   or lower(u.role) like lower(concat('%', :search, '%'))
                   or lower(coalesce(c.name, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(b.name, '')) like lower(concat('%', :search, '%')))
            order by u.username asc
            """)
@EntityGraph(attributePaths = {"company", "branch"})
    List<AppUser> searchAllActive(@Param("search") String search);

    @Query("""
            select u from AppUser u
            left join fetch u.company c
            left join fetch u.branch b
            where u.deleted = false
              and u.company.id = :companyId
              and (lower(u.username) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or coalesce(u.phone, '') like concat('%', :search, '%')
                   or lower(u.role) like lower(concat('%', :search, '%'))
                   or lower(coalesce(b.name, '')) like lower(concat('%', :search, '%')))
            order by u.username asc
            """)
@EntityGraph(attributePaths = {"company", "branch"})
    List<AppUser> searchCompanyActive(@Param("companyId") Long companyId, @Param("search") String search);
    long countByCompanyIdAndRoleNotAndDeletedFalse(Long companyId, AppRole role);
}
