package com.marketai.dashboard.service;

import com.marketai.dashboard.dto.RegisterRequest;
import com.marketai.dashboard.model.User;
import com.marketai.dashboard.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Async
    public CompletableFuture<List<User>> registerUsersBulk(List<RegisterRequest> requests) {
        List<User> users = requests.stream().map(req -> {
            if (!req.getPassword().equals(req.getConfirmPassword())) {
                throw new IllegalArgumentException("Passwords do not match for " + req.getEmail());
            }
            User user = new User();
            user.setUsername(req.getUsername());
            user.setEmail(req.getEmail());
            user.setMobileNumber(req.getMobileNumber());
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            return user;
        }).collect(Collectors.toList());

        return CompletableFuture.completedFuture(userRepository.saveAll(users));
    }
}
