package com.prideuniversal.domain.notice;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which wing-addressed items (notices, meetings) a caller is addressed by:
 * society-wide ones, plus their own flat's wing. A caller with no flat (a guard,
 * a committee member who lives elsewhere) is addressed by society-wide items only.
 *
 * <p>The wing is not in the token; it is one primary-key lookup on the flat.
 */
@Component
public class WingScopes {

    private final FlatRepository flatRepository;

    public WingScopes(FlatRepository flatRepository) {
        this.flatRepository = flatRepository;
    }

    public Set<NoticeScope> addressing(AuthPrincipal caller) {
        Set<NoticeScope> scopes = EnumSet.of(NoticeScope.BOTH);
        if (caller.flatId() != null) {
            flatRepository.findById(caller.flatId())
                    .map(Flat::getWing)
                    .flatMap(NoticeScope::forWing)
                    .ifPresent(scopes::add);
        }
        return scopes;
    }
}
