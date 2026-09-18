/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.generator;

import ch.fhnw.digibp.classroom.service.TenantTaskFilterService;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;

@Component
public class TaskFilterGenerator {

    private final TenantTaskFilterService tenantTaskFilterService;

    private final static Logger LOGGER = Logger.getLogger(TaskFilterGenerator.class.getName());

    public TaskFilterGenerator(TenantTaskFilterService tenantTaskFilterService) {
        this.tenantTaskFilterService = tenantTaskFilterService;
    }

    @PostConstruct
    public void init(){
        LOGGER.info("Synchronizing tenant task filters");
        tenantTaskFilterService.synchronizeAllTenants();
    }

}
