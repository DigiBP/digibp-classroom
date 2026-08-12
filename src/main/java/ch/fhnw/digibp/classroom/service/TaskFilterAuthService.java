/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.service;

import org.cibseven.bpm.engine.AuthorizationService;
import org.cibseven.bpm.engine.FilterService;
import org.cibseven.bpm.engine.authorization.Authorization;
import org.cibseven.bpm.engine.authorization.Permission;
import org.cibseven.bpm.engine.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.cibseven.bpm.engine.authorization.Permissions.READ;
import static org.cibseven.bpm.engine.authorization.Resources.FILTER;

@Service("classroomTaskFilterAuthService")
public class TaskFilterAuthService {

    @Autowired
    private FilterService filterService;

    @Autowired
    private AuthorizationService authorizationService;

    public void createFilterAuthorization(Filter tasksFilter){
        Authorization authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GLOBAL);
        authorization.setResource(FILTER);
        authorization.addPermission(READ);
        authorization.setResourceId(tasksFilter.getId());
        authorizationService.saveAuthorization(authorization);
    }

    public void createDenyGroupAuthorization(String[] groupIds, Permission[] permissions, String filterName) {
        for (String groupId : groupIds) {
            Filter tasksFilter = filterService.createFilterQuery().filterName(filterName).singleResult();
            Authorization authorization = authorizationService.createAuthorizationQuery()
                    .groupIdIn(groupId).resourceType(FILTER).resourceId(tasksFilter.getId()).list().stream()
                    .filter(item -> item.getAuthorizationType() == Authorization.AUTH_TYPE_REVOKE)
                    .findFirst().orElse(null);
            if (authorization == null) {
                authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_REVOKE);
                authorization.setGroupId(groupId);
                authorization.setResource(FILTER);
                authorization.setResourceId(tasksFilter.getId());
            }
            for (Permission permission : permissions) {
                authorization.removePermission(permission);
            }
            authorizationService.saveAuthorization(authorization);
        }
    }

}
