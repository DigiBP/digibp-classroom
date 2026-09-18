/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.service;

import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.identity.Group;
import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.bpm.engine.identity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service("classroomUserService")
public class UserService {
    @Autowired
    private IdentityService identityService;

    private final static Logger LOGGER = Logger.getLogger(UserService.class.getName());

    public String addUser(String userId, String password, String firstName, String lastName, String email) throws Exception {
        return addUser(userId, password, firstName, lastName, email, null, null);
    }

    public String addUser(String userId, String password, String firstName, String lastName, String email, String[] groupIds) throws Exception {
        return addUser(userId, password, firstName, lastName, email, groupIds, null);
    }

    public String addUser(String userId, String password, String firstName, String lastName, String email, String[] groupIds, String tenantId) throws Exception {
        if (identityService.isReadOnly()) {
            LOGGER.severe("Identity service provider is Read Only, could not create user.");
            return null;
        }
        if (identityService.createUserQuery().userId(userId).count() > 0) {
            throw new Exception("User " + userId + " already exists, could not create that user again.");
        }
        User user = identityService.newUser(userId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword(password);
        user.setEmail(email);
        identityService.saveUser(user);
        if(groupIds!=null){
            addUserToGroup(userId, groupIds);
        }
        if(tenantId!=null){
            addUserToTenant(userId, tenantId);
            if (groupIds != null) {
                addGroupsToTenant(groupIds, tenantId);
            }
        }
        return user.getId();
    }

    public void addUserToGroup(String userId, String[] groupIds) throws Exception {
        if (identityService.createUserQuery().userId(userId)==null) {
            throw new Exception("User " + userId + " does not exist, could not create group membership.");
        }
        for (String groupId : groupIds) {
            identityService.createMembership(userId, groupId);
        }
    }

    public void addUserToTenant(String userId, String tenantId) throws Exception {
        if (identityService.createUserQuery().userId(userId)==null) {
            throw new Exception("User " + userId + " does not exist, could not create tenant membership.");
        }
        identityService.createTenantUserMembership(tenantId, userId);
    }

    public void addGroupsToTenant(String[] groupIds, String tenantId) {
        for (String groupId : groupIds) {
            if (identityService.createGroupQuery().groupId(groupId).count() > 0
                    && identityService.createGroupQuery().groupId(groupId).memberOfTenant(tenantId).count() == 0) {
                identityService.createTenantGroupMembership(tenantId, groupId);
            }
        }
    }

    public void synchronizeGroupTenantMemberships() {
        for (Tenant tenant : identityService.createTenantQuery().list()) {
            for (User user : identityService.createUserQuery().memberOfTenant(tenant.getId()).list()) {
                List<Group> groups = identityService.createGroupQuery().groupMember(user.getId()).list();
                addGroupsToTenant(groups.stream().map(Group::getId).toArray(String[]::new), tenant.getId());
            }
        }
    }

    public List<String> removeUsers(String tenantId){
        List<User> users = identityService.createUserQuery().memberOfTenant(tenantId).list();
        List<String> userIds = new ArrayList<>();
        for(User user : users){
            userIds.add(user.getId());
            removeUser(user.getId());
        }
        return userIds;
    }

    public String removeUser(String userId){
        identityService.deleteUser(userId);
        return userId;
    }
}
