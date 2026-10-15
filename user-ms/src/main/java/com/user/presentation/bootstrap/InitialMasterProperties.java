package com.user.presentation.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yaml initial-master.* (값은 .env의 MASTER_*) */
@ConfigurationProperties(prefix = "initial-master")
public record InitialMasterProperties(String username, String password, String name, String slackId) {

    @Override
    public String toString() {
        return "InitialMasterProperties[username=" + username + ", name=" + name + ", slackId=" + slackId + "]";
    }
}
