package com.digitalgrader.users;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record User(long id, String email, @JsonIgnore String passwordHash, String fullName, Role role) {
    public Profile profile() { return new Profile(id, email, fullName, role); }
    public record Profile(long id, String email, String fullName, Role role) {}
}
