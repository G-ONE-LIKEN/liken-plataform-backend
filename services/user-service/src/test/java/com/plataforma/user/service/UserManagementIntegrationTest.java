package com.plataforma.user.service;

import com.plataforma.AbstractIntegrationTest;
import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;
import com.plataforma.user.service.UserService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class UserManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @Test
    void shouldRegisterUserWithDefaultRole() {
        User saved = userService.registerUser(
                User.builder().email("test@plataforma.com").password("123456").build());
        assertNotNull(saved.getId());
        assertEquals(RoleConstants.BASIC, saved.getRole().getName());
        assertTrue(saved.isActive());
    }

    @Test
    void shouldUpdateUserFields() {
        var devRole = roleRepository.findByName(RoleConstants.DEVELOPER).orElseThrow();
        User user = userRepository.save(
                User.builder().email("old@mail.com").role(devRole).active(true).build());
        user.setEmail("new@mail.com");
        User updated = userService.updateUser(user.getId(), user);
        assertEquals("new@mail.com", updated.getEmail());
    }

    @Test
    void shouldDeactivateUser_SoftDelete() {
        var basic = roleRepository.findByName(RoleConstants.BASIC).orElseThrow();
        User user = userRepository.save(
                User.builder().role(basic).active(true).build());
        userService.deactivateUser(user.getId());
        assertFalse(userRepository.findById(user.getId()).orElseThrow().isActive());
    }

    @Test
    void shouldReturnPaginatedUsers() {
        var basic = roleRepository.findByName(RoleConstants.BASIC).orElseThrow();
        userRepository.save(User.builder().role(basic).email("a@test.com").build());
        userRepository.save(User.builder().role(basic).email("b@test.com").build());
        userRepository.save(User.builder().role(basic).email("c@test.com").build());

        Page<User> page = userService.getAllUsers(PageRequest.of(0, 2));
        assertEquals(2, page.getContent().size());
        assertEquals(3, page.getTotalElements());
    }

    @Test
    void shouldFindUserDetail() {
        var basic = roleRepository.findByName(RoleConstants.BASIC).orElseThrow();
        User user = userRepository.save(
                User.builder().role(basic).email("detail@test.com").build());
        Optional<User> found = userService.getUserById(user.getId());
        assertTrue(found.isPresent());
        assertEquals("detail@test.com", found.get().getEmail());
    }
}
