package com.reelcipe.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ApplicationRoleValidator implements ApplicationRunner {

    private final String role;

    public ApplicationRoleValidator(@Value("${app.role:}") String role) {
        this.role = role;
    }

    @Override
    public void run(ApplicationArguments args) {
        ApplicationRole.parse(role);
    }
}
