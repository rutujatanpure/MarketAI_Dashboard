package com.marketai.dashboard.service;

import com.marketai.dashboard.model.User;
import com.marketai.dashboard.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WatchlistService {

    private final UserRepository userRepository;

    public WatchlistService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<String> addSymbol(String email, String symbol) {
        User user = findUser(email);
        String upper = symbol.toUpperCase();
        if (!user.getWatchlist().contains(upper)) {
            user.getWatchlist().add(upper);
            userRepository.save(user);
        }
        return user.getWatchlist();
    }

    public List<String> removeSymbol(String email, String symbol) {
        User user = findUser(email);
        user.getWatchlist().remove(symbol.toUpperCase());
        userRepository.save(user);
        return user.getWatchlist();
    }

    public List<String> getWatchlist(String email) {
        return findUser(email).getWatchlist();
    }

    public User updateCurrency(String email, String currency) {
        User user = findUser(email);
        user.setCurrency(currency.toUpperCase());
        return userRepository.save(user);
    }

    public User updateNotificationPreference(String email, boolean enabled) {
        User user = findUser(email);
        user.setEmailNotifications(enabled);
        return userRepository.save(user);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}