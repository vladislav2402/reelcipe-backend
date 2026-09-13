package com.reelcipe.auth;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("!production")
public class FixtureUserInitializer {

    public static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final UserRepository users;

    public FixtureUserInitializer(UserRepository users) {
        this.users = users;
    }

    @PostConstruct
    void ensureFixtureUsers() {
        saveIfMissing(ALICE_ID, "Fixture Alice");
        saveIfMissing(BOB_ID, "Fixture Bob");
    }

    private void saveIfMissing(UUID id, String displayName) {
        if (users.findById(id).isEmpty()) {
            Instant now = Instant.now();
            users.save(new User(id, displayName, UserStatus.ACTIVE, now, now));
        }
    }
}
