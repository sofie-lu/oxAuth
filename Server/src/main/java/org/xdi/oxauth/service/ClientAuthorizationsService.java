/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.slf4j.Logger;
import org.xdi.ldap.model.SimpleBranch;
import org.xdi.oxauth.model.ldap.ClientAuthorizations;
import org.xdi.util.StringHelper;

import com.unboundid.ldap.sdk.Filter;

/**
 * @author Javier Rojas Blum
 * @version November 30, 2016
 */
@Stateless
@Named
public class ClientAuthorizationsService {

	@Inject
    private Logger log;

    @Inject
    private LdapEntryManager ldapEntryManager;

    @Inject
    private UserService userService;

    public void addBranch(final String userInum) {
        SimpleBranch branch = new SimpleBranch();
        branch.setOrganizationalUnitName("clientAuthorizations");
        branch.setDn(getBaseDnForClientAuthorizations(userInum));

        ldapEntryManager.persist(branch);
    }

    public boolean containsBranch(final String userInum) {
        return ldapEntryManager.contains(SimpleBranch.class, getBaseDnForClientAuthorizations(userInum));
    }

    public void prepareBranch(final String userInum) {
        // Create client authorizations branch if needed
        if (!containsBranch(userInum)) {
            addBranch(userInum);
        }
    }

    public ClientAuthorizations findClientAuthorizations(String userInum, String clientId) {
        prepareBranch(userInum);

        String baseDn = getBaseDnForClientAuthorizations(userInum);
        Filter filter = Filter.createEqualityFilter("oxAuthClientId", clientId);

        List<ClientAuthorizations> entries = ldapEntryManager.findEntries(baseDn, ClientAuthorizations.class, filter);
        if (entries != null && !entries.isEmpty()) {
            // if more then one entry then it's problem, non-deterministic behavior, id must be unique
            if (entries.size() > 1) {
                log.error("Found more then one client authorization entry by client Id: {}" + clientId);
                for (ClientAuthorizations entry : entries) {
                    log.error(entry.toString());
                }
            }
            return entries.get(0);
        }

        return null;
    }

    public void add(String userInum, String clientId, Set<String> scopes) {
        prepareBranch(userInum);

        ClientAuthorizations clientAuthorizations = findClientAuthorizations(userInum, clientId);

        if (clientAuthorizations == null) {
            clientAuthorizations = new ClientAuthorizations();
            clientAuthorizations.setId(UUID.randomUUID().toString());
            clientAuthorizations.setClientId(clientId);
            clientAuthorizations.setScopes(scopes.toArray(new String[scopes.size()]));
            clientAuthorizations.setDn(getBaseDnForClientAuthorizations(clientAuthorizations.getId(), userInum));

            ldapEntryManager.persist(clientAuthorizations);
        } else if (clientAuthorizations.getScopes() != null) {
            Set<String> set = new HashSet<String>(scopes);
            set.addAll(Arrays.asList(clientAuthorizations.getScopes()));
            clientAuthorizations.setScopes(set.toArray(new String[set.size()]));

            ldapEntryManager.merge(clientAuthorizations);
        }
    }

    public String getBaseDnForClientAuthorizations(String oxId, String userInum) {
        String baseDn = getBaseDnForClientAuthorizations(userInum);
        if (StringHelper.isEmpty(oxId)) {
            return baseDn;
        }
        return String.format("oxId=%s,%s", oxId, baseDn);
    }

    public String getBaseDnForClientAuthorizations(String userInum) {
        final String userBaseDn = userService.getDnForUser(userInum); // inum=1234,ou=people,o=@!1111,o=gluu"
        return String.format("ou=clientAuthorizations,%s", userBaseDn); // "ou=clientAuthorizations,inum=1234,ou=people,o=@!1111,o=gluu"
    }

}
