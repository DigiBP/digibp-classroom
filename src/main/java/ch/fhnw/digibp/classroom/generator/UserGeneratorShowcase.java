/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.generator;

import ch.fhnw.digibp.classroom.service.GroupService;
import ch.fhnw.digibp.classroom.service.TenantService;
import ch.fhnw.digibp.classroom.service.UserService;
import org.cibseven.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;

/**
 * @author andreas.martin
 */
@Component
@DependsOn("groupGenerator")
public class UserGeneratorShowcase {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private UserService userService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private GroupService groupService;

    private final static Logger LOGGER = Logger.getLogger(UserGeneratorShowcase.class.getName());

    @PostConstruct
    public void init() {

        userService.synchronizeGroupTenantMemberships();

        if(identityService.createUserQuery().userId("giulia").singleResult() != null){
            LOGGER.info("Not creating any showcase users.");
            return;
        }

        LOGGER.info("Generating showcase users.");

        try {
            tenantService.addTenant("showcase", "Showcase");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

        try {
            userService.addUser("giulia", StrongPasswordGenerator.generate(), "Giulia", "Ricci", "", new String[]{"owner"}, "showcase");
            userService.addUser("martina", StrongPasswordGenerator.generate(), "Martina", "Russo", "", new String[]{"manager"}, "showcase");
            userService.addUser("sofia", StrongPasswordGenerator.generate(), "Sofia", "Conti", "", new String[]{"analyst"}, "showcase");
            userService.addUser("chiara", StrongPasswordGenerator.generate(), "Chiara", "Lombardi", "", new String[]{"engineer"}, "showcase");
            groupService.addWorkflowGroup("assistant", "Assistant");
            userService.addUser("beppe", StrongPasswordGenerator.generate(), "Beppe", "Ferrari", "", new String[]{"initiator", "assistant"}, "showcase");
            groupService.addWorkflowGroup("chef", "Chef");
            userService.addUser("matteo", StrongPasswordGenerator.generate(), "Matteo", "Alfonsi", "", new String[]{"worker", "chef"}, "showcase");
            groupService.addWorkflowGroup("courier", "Courier");
            userService.addUser("silvio", StrongPasswordGenerator.generate(), "Silvio", "Esposito", "", new String[]{"worker", "courier"}, "showcase");
        } catch (Exception e) {
            LOGGER.warning("Could not create showcase users.");
        }
    }
}
