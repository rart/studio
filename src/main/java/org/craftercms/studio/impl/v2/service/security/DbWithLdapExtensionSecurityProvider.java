/*
 * Copyright (C) 2007-2018 Crafter Software Corporation. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.craftercms.studio.impl.v2.service.security;

import org.apache.commons.lang3.StringUtils;
import org.craftercms.commons.entitlements.exception.EntitlementException;
import org.craftercms.studio.api.v1.constant.DmConstants;
import org.craftercms.studio.api.v1.constant.StudioConstants;
import org.craftercms.studio.api.v1.dal.SiteFeedMapper;
import org.craftercms.studio.api.v1.exception.security.AuthenticationSystemException;
import org.craftercms.studio.api.v1.exception.security.BadCredentialsException;
import org.craftercms.studio.api.v1.exception.security.GroupAlreadyExistsException;
import org.craftercms.studio.api.v1.exception.security.UserAlreadyExistsException;
import org.craftercms.studio.api.v1.exception.security.UserNotFoundException;
import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;
import org.craftercms.studio.api.v1.service.activity.ActivityService;
import org.craftercms.studio.api.v2.dal.GroupDAO;
import org.craftercms.studio.api.v2.dal.UserDAO;
import org.craftercms.studio.api.v2.dal.UserGroup;
import org.craftercms.studio.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.AuthenticatedLdapEntryContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapEntryIdentification;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.craftercms.studio.api.v1.util.StudioConfiguration.CONFIGURATION_GLOBAL_SYSTEM_SITE;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_EMAIL;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_FIRST_NAME;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_GROUP_NAME;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_GROUP_NAME_MATCH_INDEX;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_GROUP_NAME_REGEX;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_LAST_NAME;
import static org.craftercms.studio.api.v1.util.StudioConfiguration.SECURITY_LDAP_USER_ATTRIBUTE_USERNAME;
import static org.craftercms.studio.api.v2.dal.QueryParameterNames.GROUP_NAME;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

public class DbWithLdapExtensionSecurityProvider extends DbSecurityProvider {

    private final static Logger logger = LoggerFactory.getLogger(DbWithLdapExtensionSecurityProvider.class);
    protected LdapTemplate ldapTemplate;
    protected ActivityService activityService;
    protected SiteFeedMapper siteFeedMapper;

    @Override
    public String authenticate(String username, String password)
            throws BadCredentialsException, AuthenticationSystemException, EntitlementException {

        // Mapper for user data if user is successfully authenticated
        AuthenticatedLdapEntryContextMapper<UserDAO> mapper = new AuthenticatedLdapEntryContextMapper<UserDAO>() {
            @Override
            public UserDAO mapWithContext(DirContext dirContext, LdapEntryIdentification ldapEntryIdentification) {
                try {
                    // User entry - extract attributes
                    DirContextOperations dirContextOperations =
                            (DirContextOperations)dirContext.lookup(ldapEntryIdentification.getRelativeName());
                    Attributes attributes = dirContextOperations.getAttributes();
                    String emailAttribName = studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_EMAIL);
                    String firstNameAttribName =
                            studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_FIRST_NAME);
                    String lastNameAttribName = studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_LAST_NAME);
                    String groupNameAttribName =
                            studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_GROUP_NAME);
                    Attribute emailAttrib = attributes.get(emailAttribName);
                    Attribute firstNameAttrib = attributes.get(firstNameAttribName);
                    Attribute lastNameAttrib = attributes.get(lastNameAttribName);
                    Attribute groupNameAttrib = attributes.get(groupNameAttribName);


                    UserDAO userDao = new UserDAO();
                    userDao.setActive(1);
                    userDao.setUsername(username);

                    if (emailAttrib != null && emailAttrib.get() != null) {
                        userDao.setEmail(emailAttrib.get().toString());
                    } else {
                        logger.error("No LDAP attribute " + emailAttribName + " found for username " + username +
                                     ". User will not be imported into DB.");
                        return null;
                    }
                    if (firstNameAttrib != null && firstNameAttrib.get() != null) {
                        userDao.setFirstName(firstNameAttrib.get().toString());
                    } else {
                        logger.warn("No LDAP attribute " + firstNameAttribName + " found for username " + username);
                    }
                    if (lastNameAttrib != null && lastNameAttrib.get() != null) {
                        userDao.setLastName(lastNameAttrib.get().toString());
                    } else {
                        logger.warn("No LDAP attribute " + lastNameAttribName + " found for username " + username);
                    }


                    extractGroupsFromAttribute(userDao, groupNameAttribName, groupNameAttrib);


                    return userDao;
                } catch (NamingException e) {
                    logger.error("Error getting details from LDAP for username " + username, e);

                    return null;
                }
            }
        };

        // Create ldap query to authenticate user
        LdapQuery ldapQuery = query()
                .where(studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_USERNAME)).is(username);
        UserDAO userDao;
        try {
            userDao = ldapTemplate.authenticate(ldapQuery, password, mapper);
        } catch (EmptyResultDataAccessException e) {
            logger.info("User " + username +
                        " not found with external security provider. Trying to authenticate against studio database");
            // When user not found try to authenticate against studio database
            return super.authenticate(username, password);
        } catch (CommunicationException e) {
            logger.info("Failed to connect with external security provider. " +
                        "Trying to authenticate against studio database");
            // When user not found try to authenticate against studio database
            return super.authenticate(username, password);
        } catch (AuthenticationException e) {
            logger.error("Authentication failed with the LDAP system", e);

            throw new BadCredentialsException();
        } catch (Exception e) {
            logger.error("Authentication failed with the LDAP system", e);

            throw new AuthenticationSystemException("Authentication failed with the LDAP system", e);
        }

        if (userDao != null) {
            // When user authenticated against LDAP, upsert user data into studio database
            if (super.userExists(username)) {
                try {
                    boolean success = updateUserInternal(userDao.getUsername(), userDao.getFirstName(),
                            userDao.getLastName(), userDao.getEmail());
                    if (success) {
                        ActivityService.ActivityType activityType = ActivityService.ActivityType.UPDATED;
                        Map<String, String> extraInfo = new HashMap<>();
                        extraInfo.put(DmConstants.KEY_CONTENT_TYPE, StudioConstants.CONTENT_TYPE_USER);
                        activityService.postActivity(getSystemSite(), userDao.getUsername(), userDao.getUsername(),
                                                     activityType, ActivityService.ActivitySource.API, extraInfo);
                    }
                } catch (UserNotFoundException e) {
                    logger.error("Error updating user " + username +
                                 " with data from external authentication provider", e);

                    throw new AuthenticationSystemException("Error updating user " + username +
                                                            " with data from external authentication provider", e);
                }
            } else {
                try {
                    User user = new User();
                    user.setUsername(userDao.getUsername());
                    user.setPassword(userDao.getPassword());
                    user.setEmail(userDao.getEmail());
                    user.setFirstName(userDao.getFirstName());
                    user.setExternallyManaged(true);
                    boolean success = createUser(user);
                    if (success) {
                        ActivityService.ActivityType activityType = ActivityService.ActivityType.CREATED;
                        Map<String, String> extraInfo = new HashMap<>();
                        extraInfo.put(DmConstants.KEY_CONTENT_TYPE, StudioConstants.CONTENT_TYPE_USER);
                        activityService.postActivity(getSystemSite(), user.getUsername(), user.getUsername(),
                                                     activityType, ActivityService.ActivitySource.API, extraInfo);
                    }
                } catch (UserAlreadyExistsException e) {
                    logger.error("Error adding user " + username + " from external authentication provider", e);

                    throw new AuthenticationSystemException("Error adding user " + username +
                                                            " from external authentication provider", e);
                }
            }

            for (UserGroup userGroup : userDao.getGroups()) {

                upsertUserGroup(userGroup.getGroup().getGroupName(), userDao.getUsername());

            }

            String token = createToken(userDao);
            storeSessionTicket(token);
            storeSessionUsername(username);

            return token;
        } else {
            logger.error("Failed to retrieve LDAP user details");

            throw new AuthenticationSystemException("Failed to retrieve LDAP user details");
        }
    }

    private String extractGroupNameFromAttributeValue(String groupAttributeValue) {
        Pattern pattern =
                Pattern.compile(studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_GROUP_NAME_REGEX));
        Matcher matcher = pattern.matcher(groupAttributeValue);
        if (matcher.matches()) {
            int index = Integer.parseInt(
                    studioConfiguration.getProperty(SECURITY_LDAP_USER_ATTRIBUTE_GROUP_NAME_MATCH_INDEX));

            return matcher.group(index);
        }

        return StringUtils.EMPTY;
    }

    private void extractGroupsFromAttribute(UserDAO user, String groupNameAttribName, Attribute groupNameAttrib) throws
            NamingException {
        if (groupNameAttrib != null && groupNameAttrib.size() > 0) {
            NamingEnumeration groupAttribValues = groupNameAttrib.getAll();
            while (groupAttribValues.hasMore()) {
                Object groupNameObj = groupAttribValues.next();
                if (groupNameObj != null) {
                    String groupName = extractGroupNameFromAttributeValue(groupNameObj.toString());
                    if (StringUtils.isNotEmpty(groupName)) {
                        addGroupToUser(user, groupName);
                    }
                }
            }
        } else {
            logger.debug("No LDAP attribute " + groupNameAttribName + " found for username " + user.getUsername());
        }
    }

    private void addGroupToUser(UserDAO userDao, String groupName) {
        GroupDAO group = new GroupDAO();
        group.setGroupName(groupName);
        group.setGroupDescription("Externally managed group");
        group.setOrganization(null);

        UserGroup userGroup = new UserGroup();
        userGroup.setGroup(group);
        if (userDao.getGroups() == null) {
            userDao.setGroups(new ArrayList<UserGroup>());
        }
        userDao.getGroups().add(userGroup);
    }

    protected boolean updateUserInternal(String username, String firstName, String lastName, String email)
            throws UserNotFoundException {
        if (!userExists(username)) {
            throw new UserNotFoundException();
        } else {
            Map<String, Object> params = new HashMap<>();
            params.put("username", username);
            params.put("firstname", firstName);
            params.put("lastname", lastName);
            params.put("email", email);
            params.put("externallyManaged", 1);
            userMapper.updateUser(params);
            return true;
        }
    }

    protected boolean upsertUserGroup(String groupName, String username) {

        try {
            createGroup(1, groupName, "Externally managed group - " + groupName);
        } catch (GroupAlreadyExistsException e) {
            logger.warn("Externally managed group: " + groupName + " not created. It already exists .");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(GROUP_NAME, groupName);
        GroupDAO groupDAO = groupMapper.getGroupByName(params);
        if (groupDAO != null) {
            List<String> usernames = new ArrayList<String>();
            usernames.add(username);
            boolean success = addGroupMembers(groupDAO.getId(), new ArrayList<Long>(), usernames);
            if (success){
                ActivityService.ActivityType activityType = ActivityService.ActivityType.ADD_USER_TO_GROUP;
                Map<String, String> extraInfo = new HashMap<>();
                extraInfo.put(DmConstants.KEY_CONTENT_TYPE, StudioConstants.CONTENT_TYPE_USER);
                activityService.postActivity("", "LDAP", username + " > " + groupName , activityType,
                        ActivityService.ActivitySource.API, extraInfo);
            }

        }
        return true;
    }

    public String getSystemSite() {
        return studioConfiguration.getProperty(CONFIGURATION_GLOBAL_SYSTEM_SITE);
    }

    public LdapTemplate getLdapTemplate() { return ldapTemplate; }
    public void setLdapTemplate(LdapTemplate ldapTemplate) { this.ldapTemplate = ldapTemplate; }

    public ActivityService getActivityService() { return activityService; }
    public void setActivityService(ActivityService activityService) { this.activityService = activityService; }

    public SiteFeedMapper getSiteFeedMapper() {
        return siteFeedMapper;
    }

    public void setSiteFeedMapper(SiteFeedMapper siteFeedMapper) {
        this.siteFeedMapper = siteFeedMapper;
    }
}