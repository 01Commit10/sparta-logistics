package com.user.application.dto;

public record LoginCommand(String username, String password) {

    @Override
    public String toString() {
        return "LoginCommand[username=" + username + ", password=****]";
    }
}
