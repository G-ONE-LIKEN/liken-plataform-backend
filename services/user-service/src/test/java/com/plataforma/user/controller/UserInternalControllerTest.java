// services/user-service/src/test/java/com/plataforma/user/controller/UserInternalControllerTest.java
package com.plataforma.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.shared.security.GatewayHeaderAuthFilter;
import com.plataforma.user.dto.LocalUserRegistrationRequest;
import com.plataforma.user.repository.UserRepository;
import com.plataforma.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = UserInternalController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class UserInternalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean UserRepository userRepository;
    @MockBean GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    @Test
    void createLocalUser_ShouldRejectInvalidEmail() throws Exception {
        LocalUserRegistrationRequest request = new LocalUserRegistrationRequest();
        request.setEmail("invalid-email");

        mockMvc.perform(post("/internal/users/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLocalUser_ShouldRejectMissingRequiredFields() throws Exception {
        LocalUserRegistrationRequest request = new LocalUserRegistrationRequest();

        mockMvc.perform(post("/internal/users/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}