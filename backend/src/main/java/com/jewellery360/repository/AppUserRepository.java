package com.jewellery360.repository;

import com.jewellery360.domain.AppRole;
import com.jewellery360.domain.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    List<AppUser> findAllByUsernameIgnoreCaseAndDeletedFalse(String username);

    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<AppUser> findByEmailIgnoreCaseAndDeletedFalse(String email);

    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<AppUser> findByPhoneAndDeletedFalse(String phone);

    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<AppUser> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    Optional<AppUser> findByCompanyIdAndUsernameIgnoreCaseAndDeletedFalse(
            Long companyId,
            String username
    );

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByUsernameIgnoreCaseAndDeletedFalse(String username);

    @EntityGraph(attributePaths = {"company", "branch"})
    @Query("""
            select u
            from AppUser u
            where u.deleted = false
              and u.company.id = :companyId
            order by u.username asc
            """)
    List<AppUser> findByCompanyIdAndDeletedFalse(
            @Param("companyId") Long companyId
    );

    @EntityGraph(attributePaths = {"company", "branch"})
    @Query("""
            select u
            from AppUser u
            where u.deleted = false
            order by u.username asc
            """)
    List<AppUser> findAllByDeletedFalseOrderByUsernameAsc();

    @EntityGraph(attributePaths = {"company", "branch"})
    @Query("""
            select u
            from AppUser u
            where u.deleted = false
              and (
                    lower(u.username) like lower(concat('%', :search, '%'))
                    or lower(u.email) like lower(concat('%', :search, '%'))
                    or coalesce(u.phone, '') like concat('%', :search, '%')
                    or lower(u.role) like lower(concat('%', :search, '%'))
                    or lower(u.company.name) like lower(concat('%', :search, '%'))
                    or lower(u.branch.name) like lower(concat('%', :search, '%'))
                  )
            order by u.username asc
            """)
    List<AppUser> searchAllActive(
            @Param("search") String search
    );

    @EntityGraph(attributePaths = {"company", "branch"})
    @Query("""
            select u
            from AppUser u
            where u.deleted = false
              and u.company.id = :companyId
              and (
                    lower(u.username) like lower(concat('%', :search, '%'))
                    or lower(u.email) like lower(concat('%', :search, '%'))
                    or coalesce(u.phone, '') like concat('%', :search, '%')
                    or lower(u.role) like lower(concat('%', :search, '%'))
                    or lower(u.branch.name) like lower(concat('%', :search, '%'))
                  )
            order by u.username asc
            """)
    List<AppUser> searchCompanyActive(
            @Param("companyId") Long companyId,
            @Param("search") String search
    );

    long countByCompanyIdAndRoleNotAndDeletedFalse(
            Long companyId,
            AppRole role
    );
}