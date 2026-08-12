/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.generator;

import ch.fhnw.digibp.classroom.service.GroupService;
import ch.fhnw.digibp.classroom.service.TaskFilterAuthService;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.authorization.Permission;
import org.cibseven.bpm.engine.authorization.Permissions;
import org.cibseven.bpm.engine.authorization.Resources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;

/**
 * @author andreas.martin
 */
@Component
public class GroupGenerator {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private TaskFilterAuthService taskFilterAuthService;

    private final static Logger LOGGER = Logger.getLogger(GroupGenerator.class.getName());

    @PostConstruct
    public void init() {

        groupService.createGlobalAuthorization(Permissions.READ, Resources.AUTHORIZATION, "*");

        if(identityService.createGroupQuery().groupId("owner").singleResult() != null){
            LOGGER.info("Not creating any groups.");
        } else {
            LOGGER.info("Generating groups.");

            groupService.addWorkflowGroup("owner", "Owner");
            groupService.addWorkflowGroup("manager", "Manager");
            groupService.addWorkflowGroup("analyst", "Analyst");
            groupService.addWorkflowGroup("engineer", "Engineer");
            groupService.addWorkflowGroup("initiator", "Initiator");
            groupService.addWorkflowGroup("worker", "Worker");
        }

        groupService.createGrantGroupAuthorization(new String[]{"owner", "manager", "analyst"}, new Permission[]{Permissions.ACCESS}, Resources.APPLICATION, new String[]{"cockpit", "optimize"});
        groupService.createGrantGroupAuthorization(new String[]{"manager", "engineer"}, new Permission[]{Permissions.ALL}, Resources.APPLICATION, new String[]{"cockpit"});
        groupService.createGrantGroupAuthorization(new String[]{"analyst", "engineer"}, new Permission[]{Permissions.ALL}, Resources.APPLICATION, new String[]{"optimize"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.APPLICATION, new String[]{"admin", "modeler", "tasklist"});
        groupService.createGrantGroupAuthorization(new String[]{"initiator", "worker", "manager"}, new Permission[]{Permissions.ACCESS}, Resources.APPLICATION, new String[]{"tasklist"});

        groupService.createGrantGroupAuthorization(new String[]{"manager", "engineer", "initiator"}, new Permission[]{Permissions.ALL}, Resources.PROCESS_DEFINITION, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"manager", "engineer", "initiator"}, new Permission[]{Permissions.ALL}, Resources.DECISION_DEFINITION, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"manager", "engineer", "initiator"}, new Permission[]{Permissions.ALL}, Resources.DECISION_REQUIREMENTS_DEFINITION, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"owner", "analyst"}, new Permission[]{Permissions.READ, Permissions.READ_INSTANCE, Permissions.READ_HISTORY}, Resources.PROCESS_DEFINITION, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"worker"}, new Permission[]{Permissions.READ, Permissions.READ_TASK, Permissions.READ_INSTANCE, Permissions.READ_HISTORY, Permissions.TASK_ASSIGN, Permissions.TASK_WORK}, Resources.PROCESS_DEFINITION, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"worker"}, new Permission[]{Permissions.READ, Permissions.READ_HISTORY}, Resources.DECISION_DEFINITION, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"worker"}, new Permission[]{Permissions.READ}, Resources.DECISION_REQUIREMENTS_DEFINITION, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"manager"}, new Permission[]{Permissions.READ, Permissions.UPDATE, Permissions.DELETE}, Resources.PROCESS_INSTANCE, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"owner", "analyst"}, new Permission[]{Permissions.READ}, Resources.PROCESS_INSTANCE, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.PROCESS_INSTANCE, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"initiator"}, new Permission[]{Permissions.CREATE}, Resources.PROCESS_INSTANCE, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"manager", "engineer"}, new Permission[]{Permissions.ALL}, Resources.TASK, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"initiator", "worker"}, new Permission[]{Permissions.CREATE, Permissions.READ, Permissions.UPDATE, Permissions.TASK_ASSIGN, Permissions.TASK_WORK}, Resources.TASK, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"manager", "engineer"}, new Permission[]{Permissions.ALL}, Resources.DASHBOARD, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"owner", "analyst"}, new Permission[]{Permissions.READ}, Resources.DASHBOARD, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"owner", "manager", "analyst"}, new Permission[]{Permissions.ALL}, Resources.REPORT, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.BATCH, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.DEPLOYMENT, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.FILTER, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.HISTORIC_PROCESS_INSTANCE, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.HISTORIC_TASK, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.REPORT, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.SYSTEM, new String[]{"*"});

        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.GROUP, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.GROUP_MEMBERSHIP, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.USER, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.TENANT, new String[]{"*"});
        groupService.createGrantGroupAuthorization(new String[]{"engineer"}, new Permission[]{Permissions.ALL}, Resources.TENANT_MEMBERSHIP, new String[]{"*"});

        taskFilterAuthService.createDenyGroupAuthorization(new String[]{"initiator", "worker"}, new Permission[]{Permissions.ALL},"All Tasks");
    }

}
