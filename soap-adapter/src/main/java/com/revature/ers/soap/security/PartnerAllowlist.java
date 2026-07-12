package com.revature.ers.soap.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Which user ids each partner may submit reimbursements FOR - authorization, distinct from
 * the mTLS authentication that named the partner. Bound from configuration
 * (ers.partners.allowed-user-ids.<cn>=3,5): the adapter deliberately has no database, and
 * partner onboarding already requires issuing a certificate out of band, so a config entry
 * alongside it costs nothing.
 *
 * An AUTHENTICATED partner with no entry (or an empty one) may submit for NOBODY - holding
 * a valid certificate proves who you are, never what you may do.
 */
@ConfigurationProperties(prefix = "ers.partners")
public record PartnerAllowlist(Map<String, List<Integer>> allowedUserIds) {

    public PartnerAllowlist {
        allowedUserIds = allowedUserIds == null ? Map.of() : allowedUserIds;
    }

    public boolean permits(String partnerCn, int requesterUserId) {
        return allowedUserIds.getOrDefault(partnerCn, List.of()).contains(requesterUserId);
    }
}
