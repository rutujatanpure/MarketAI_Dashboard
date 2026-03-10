package com.marketai.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    // SECURITY FIX: @JsonIgnore prevents password hash being returned to frontend
    @JsonIgnore
    private String password;

    private String mobileNumber;

    private Role role = Role.USER;

    private boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Watchlist & preferences (used by WatchlistService) ───────────────────
    private List<String> watchlist = new ArrayList<>();

    private String currency = "INR";

    private boolean emailNotifications = true;

    // ── Constructors ──────────────────────────────────────────────────────────
    public User() {}

    public User(String username, String email, String password) {
        this.username  = username;
        this.email     = email;
        this.password  = password;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String getId()                     { return id; }
    public void   setId(String id)            { this.id = id; }

    public String getUsername()               { return username; }
    public void   setUsername(String u)       { this.username = u; }

    public String getEmail()                  { return email; }
    public void   setEmail(String e)          { this.email = e; }

    public String getPassword()               { return password; }
    public void   setPassword(String p)       { this.password = p; }

    public String getMobileNumber()           { return mobileNumber; }
    public void   setMobileNumber(String m)   { this.mobileNumber = m; }

    public Role   getRole()                   { return role; }
    public void   setRole(Role r)             { this.role = r; }

    public boolean isEnabled()                { return enabled; }
    public void    setEnabled(boolean e)      { this.enabled = e; }

    public LocalDateTime getCreatedAt()       { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }

    public List<String> getWatchlist()              { return watchlist; }
    public void         setWatchlist(List<String> w){ this.watchlist = w != null ? w : new ArrayList<>(); }

    public String getCurrency()               { return currency; }
    public void   setCurrency(String c)       { this.currency = c; }

    public boolean isEmailNotifications()              { return emailNotifications; }
    public void    setEmailNotifications(boolean en)   { this.emailNotifications = en; }
}