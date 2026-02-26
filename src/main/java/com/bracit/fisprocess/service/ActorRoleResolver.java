package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.ActorRole;
import org.jspecify.annotations.Nullable;

public interface ActorRoleResolver {
    ActorRole resolve(@Nullable String actorRoleHeader);
}
