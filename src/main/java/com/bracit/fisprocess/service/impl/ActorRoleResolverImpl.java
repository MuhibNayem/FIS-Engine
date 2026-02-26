package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.enums.ActorRole;
import com.bracit.fisprocess.service.ActorRoleResolver;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActorRoleResolverImpl implements ActorRoleResolver {

    @Override
    public ActorRole resolve(@Nullable String actorRoleHeader) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String name = authority.getAuthority();
                if ("ROLE_FIS_ADMIN".equalsIgnoreCase(name) || "FIS_ADMIN".equalsIgnoreCase(name)) {
                    return ActorRole.FIS_ADMIN;
                }
                if ("ROLE_FIS_ACCOUNTANT".equalsIgnoreCase(name) || "FIS_ACCOUNTANT".equalsIgnoreCase(name)) {
                    return ActorRole.FIS_ACCOUNTANT;
                }
                if ("ROLE_FIS_READER".equalsIgnoreCase(name) || "FIS_READER".equalsIgnoreCase(name)) {
                    return ActorRole.FIS_READER;
                }
            }
        }
        return ActorRole.fromHeader(actorRoleHeader);
    }
}
