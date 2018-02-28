// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountsImpl implements Accounts {
  private static final Logger log = LoggerFactory.getLogger(AccountsImpl.class);

  private final AccountResolver resolver;
  private final AccountCache byId;
  private final GroupCache groupCache;
  private final GroupMembers groupMembers;

  @Inject
  public AccountsImpl(
      AccountResolver resolver,
      AccountCache byId,
      GroupCache groupCache,
      GroupMembers groupMembers) {
    this.resolver = resolver;
    this.byId = byId;
    this.groupCache = groupCache;
    this.groupMembers = groupMembers;
  }

  @Override
  public Set<Account.Id> find(String nameOrEmailOrGroup) {
    if (nameOrEmailOrGroup.startsWith(GROUP_PREFIX)) {
      return findAccountsInGroup(nameOrEmailOrGroup.substring(GROUP_PREFIX.length()));
    }
    return findUserOrEmail(nameOrEmailOrGroup);
  }

  private Set<Id> findAccountsInGroup(String groupNameOrUUID) {
    Optional<InternalGroup> group =
        groupCache
            .get(new AccountGroup.NameKey(groupNameOrUUID))
            .map(Optional::of)
            .orElse(groupCache.get(new AccountGroup.UUID(groupNameOrUUID)));

    if (!group.isPresent()) {
      log.warn("Group {} was not found", groupNameOrUUID);
      return Collections.emptySet();
    }

    try {
      return groupMembers
          .listAccounts(group.get().getGroupUUID(), null)
          .stream()
          .map(Account::getId)
          .collect(Collectors.toSet());
    } catch (NoSuchProjectException | IOException e) {
      log.error("Unable to list accounts in group " + group, e);
      return Collections.emptySet();
    }
  }

  private Set<Account.Id> findUserOrEmail(String nameOrEmail) {
    try {
      Set<Id> accountIds = resolver.findAll(nameOrEmail);
      if (accountIds.isEmpty()) {
        log.warn("User '{}' does not resolve to any account.", nameOrEmail);
        return accountIds;
      }

      Set<Id> fulllyMatchedAccountIds =
          accountIds
              .stream()
              .filter(id -> isFullMatch(id, nameOrEmail))
              .collect(Collectors.toSet());
      if (fulllyMatchedAccountIds.isEmpty()) {
        log.warn(
            "User '{}' resolves to {} accounts {}, but does not correspond to any them",
            nameOrEmail,
            accountIds.size(),
            accountIds);
        return fulllyMatchedAccountIds;
      }

      return accountIds;
    } catch (OrmException | IOException | ConfigInvalidException e) {
      log.error("Error trying to resolve user " + nameOrEmail, e);
      return Collections.emptySet();
    }
  }

  private boolean isFullMatch(Account.Id id, String nameOrEmail) {
    Optional<AccountState> account = byId.get(id);
    return account.isPresent() && Objects.toString(account.get().getAccount().getFullName(), "")
            .trim()
            .equalsIgnoreCase(nameOrEmail)
        || account
            .get()
            .getExternalIds()
            .stream()
            .anyMatch(
                extId ->
                    getSchemeRest(extId.key().scheme(), extId.key().get())
                        .trim()
                        .equalsIgnoreCase(nameOrEmail));
  }

  private String getSchemeRest(String scheme, String key) {
    return null != scheme ? key.substring(scheme.length() + 1) : key;
  }
}
