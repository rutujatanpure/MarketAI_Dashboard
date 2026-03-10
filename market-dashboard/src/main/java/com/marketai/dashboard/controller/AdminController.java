package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.User;
import com.marketai.dashboard.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
// NOTE: Do NOT add @PreAuthorize at class level — it can interfere with
// Spring's CORS pre-flight (OPTIONS) requests before auth is established.
// Security is already enforced by SecurityConfig: .requestMatchers("/api/admin/**").hasRole("ADMIN")
// The method-level @PreAuthorize below is kept as a second layer but moved to each method.
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // GET /api/admin/users — returns all users
    // Password field is included in User model — consider adding @JsonIgnore on password
    // for security. Frontend only needs: id, username, email, mobileNumber, role, enabled, createdAt
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    // DELETE /api/admin/users/{id}
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // PUT /api/admin/users/{id}/toggle — enable/disable user
    @PutMapping("/users/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> toggleUserStatus(@PathVariable String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setEnabled(!user.isEnabled());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    // GET /api/admin/stats — user count summary
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<User> all = userRepository.findAll();
        long total    = all.size();
        long active   = all.stream().filter(User::isEnabled).count();
        long disabled = total - active;
        return ResponseEntity.ok(Map.of(
                "totalUsers",    total,
                "activeUsers",   active,
                "disabledUsers", disabled
        ));
    }
}