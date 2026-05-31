package com.plataforma.user.repository;

import com.plataforma.rbac.model.Role;
import com.plataforma.user.model.DeveloperStatus;
import com.plataforma.user.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSubject(String googleSubject);
    boolean existsByDni(String dni);
    boolean existsByDniAndIdNot(String dni, Long id);
    boolean existsByRole(Role role);
    long countByRole_Name(String roleName);
    Page<User> findByRole_Name(String roleName, Pageable pageable);
    Page<User> findByRole_NameAndDeveloperStatus(String roleName, DeveloperStatus developerStatus, Pageable pageable);
}
