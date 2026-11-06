/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.endpoint;

import ch.fhnw.digibp.classroom.config.ScriptAccessPlugin;
import ch.fhnw.digibp.classroom.dto.UserDTO;
import ch.fhnw.digibp.classroom.dto.UsersDTO;
import ch.fhnw.digibp.classroom.generator.StrongPasswordGenerator;
import ch.fhnw.digibp.classroom.service.AiModelService;
import ch.fhnw.digibp.classroom.service.DeploymentService;
import ch.fhnw.digibp.classroom.service.TenantService;
import ch.fhnw.digibp.classroom.service.UserService;
import org.cibseven.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping(path = "/classroom")
public class ClassroomAPI {

    @Autowired
    private UserService userService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private AiModelService aiModelService;

    @GetMapping(path = "/ai/models/reload", produces = "application/json")
    public ResponseEntity<List<String>> reloadAiModels() {
        if (!isAdminAuthentication()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(aiModelService.reload());
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                    .body(aiModelService.models());
        }
    }

    @PutMapping(path = "/ai/models/default", consumes = "text/plain", produces = "text/plain")
    public ResponseEntity<String> setDefaultAiModel(@RequestBody String model) {
        if (!isAdminAuthentication()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(aiModelService.setDefault(model));
    }

    @GetMapping(path = "/user/showcase/generator", produces = "application/json")
    public ResponseEntity<List<String>> getUserShowcaseGenerator(@RequestParam(value = "prefix", required = false, defaultValue = "") String prefix, @RequestParam(value = "firstId") Integer firstId, @RequestParam(value = "lastId") Integer lastId, @RequestParam(value = "suffix", required = false, defaultValue = "") String suffix, @RequestParam(value = "scripting", defaultValue = "false") boolean allowScripts, @RequestParam(value = "generateStrongPassword", defaultValue = "false") boolean generateStrongPassword){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> response = new ArrayList<>();
        String id;
        for (int number=firstId; number<=lastId; number++) {
            String tenantId = prefix + number + suffix;
            try {
                id = tenantService.addTenant(tenantId, tenantId);
                assignScriptGroup(tenantId, allowScripts);
                response.add("Tenant with ID " + id + " generated.");

                createShowcaseUser(response, tenantId, "giulia", "Giulia", "Ricci", new String[]{"owner"}, generateStrongPassword);
                createShowcaseUser(response, tenantId, "martina", "Martina", "Russo", new String[]{"manager"}, generateStrongPassword);
                createShowcaseUser(response, tenantId, "sofia", "Sofia", "Conti", new String[]{"analyst"}, generateStrongPassword);
                createShowcaseUser(response, tenantId, "chiara", "Chiara", "Lombardi", new String[]{"engineer"}, generateStrongPassword);
                createShowcaseUser(response, tenantId, "beppe", "Beppe", "Ferrari", new String[]{"initiator", "assistant"}, generateStrongPassword);
                createShowcaseUser(response, tenantId, "matteo", "Matteo", "Alfonsi", new String[]{"worker", "chef"}, generateStrongPassword);
                createShowcaseUser(response, tenantId, "silvio", "Silvio", "Esposito", new String[]{"worker", "courier"}, generateStrongPassword);
            } catch (Exception e) {
                response.add("Error: Showcase user creation failed.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore()).body(response);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(response);
    }

    private void createShowcaseUser(List<String> response, String tenantId, String userId,
                                    String firstName, String lastName, String[] groups,
                                    boolean generateStrongPassword) throws Exception {
        String password = generateStrongPassword ? StrongPasswordGenerator.generate() : "password";
        String id = userService.addUser(tenantId + userId, password, tenantId + " " + firstName,
                lastName, "", groups, tenantId);
        if (id == null) {
            throw new IllegalStateException("User could not be created");
        }
        response.add("User with ID " + id + " generated."
                + (generateStrongPassword ? " Password: " + password : ""));
    }

    @PostMapping(path = "/user/{userId}/password/reset", produces = "application/json")
    public ResponseEntity<Map<String, String>> resetUserPassword(@PathVariable("userId") String userId) {
        if (!isAdminAuthentication()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore()).build();
        }
        try {
            String password = userService.resetPassword(userId);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("userId", userId, "password", password));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "User does not exist."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "Password could not be reset."));
        }
    }

    private void assignScriptGroup(String tenantId, boolean allowScripts) {
        if (!allowScripts) {
            return;
        }
        var group = identityService.createGroupQuery().groupId(ScriptAccessPlugin.SCRIPT_GROUP).singleResult();
        if (group == null) {
            group = identityService.newGroup(ScriptAccessPlugin.SCRIPT_GROUP);
            group.setName("Script execution");
            group.setType(ScriptAccessPlugin.GROUP_TYPE);
            identityService.saveGroup(group);
        } else if (!ScriptAccessPlugin.GROUP_TYPE.equals(group.getType())) {
            throw new IllegalStateException("Group '" + ScriptAccessPlugin.SCRIPT_GROUP + "' must have type SYSTEM");
        }
        identityService.createTenantGroupMembership(tenantId, ScriptAccessPlugin.SCRIPT_GROUP);
    }

    private List<String> createUser(String tenantId, UsersDTO users, boolean allowScripts) throws Exception {
        List<String> response = new ArrayList<>();
        String id = tenantService.addTenant(tenantId, tenantId);
        assignScriptGroup(tenantId, allowScripts);
        response.add("Tenant with ID " + id + " generated.");

        for(UserDTO user : users.getUsers()){
            ArrayList<String> groupIds = new ArrayList<>();
            for (UserDTO.GroupId groupId : user.getGroupIds()){
                groupIds.add(groupId.getGroupId());
            }
            id = userService.addUser(user.getFirstName().replaceAll("\\s", "").toLowerCase(Locale.ROOT)+user.getLastName().replaceAll("\\s", "").toLowerCase(Locale.ROOT), user.getPassword(), user.getFirstName(), user.getLastName(), "", groupIds.toArray(String[]::new), tenantId);
            response.add("User with ID " + id + " generated.");
        }
        return response;
    }

    @PostMapping(path = "/user", consumes = "application/json", produces = "application/json")
    public ResponseEntity<List<String>> postUser(@RequestParam(value = "tenant", required = false, defaultValue = "") String tenantId, @RequestBody UsersDTO users, @RequestParam(value = "scripting", defaultValue = "false") boolean allowScripts){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> response = new ArrayList<>();
        try {
            response.addAll(createUser(tenantId, users, allowScripts));
        } catch (Exception e) {
            response.add("Error: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping(path = "/user/generator", consumes = "application/json", produces = "application/json")
    public ResponseEntity<List<String>> postUserGenerator(@RequestParam(value = "prefix", required = false, defaultValue = "") String prefix, @RequestParam(value = "firstId") Integer firstId, @RequestParam(value = "lastId") Integer lastId, @RequestParam(value = "suffix", required = false, defaultValue = "") String suffix, @RequestBody UsersDTO users, @RequestParam(value = "scripting", defaultValue = "false") boolean allowScripts){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> response = new ArrayList<>();
        for (int number=firstId; number<=lastId; number++) {
            String tenantId = prefix + number + suffix;
            try {
                String id = tenantService.addTenant(tenantId, tenantId);
                assignScriptGroup(tenantId, allowScripts);
                response.add("Tenant with ID " + id + " generated.");

                for(UserDTO user : users.getUsers()){
                    ArrayList<String> groupIds = new ArrayList<>();
                    for (UserDTO.GroupId groupId : user.getGroupIds()){
                        groupIds.add(groupId.getGroupId());
                    }
                    id = userService.addUser(tenantId+user.getFirstName().replaceAll("\\s", "").toLowerCase(Locale.ROOT), user.getPassword(), tenantId+" "+user.getFirstName(), user.getLastName(), "", groupIds.toArray(String[]::new), tenantId);
                    response.add("User with ID " + id + " generated.");
                }
            } catch (Exception e) {
                response.add("Error: " + e.getMessage());
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @DeleteMapping(path = "/user/generator")
    public ResponseEntity<List<String>> deleteUserGenerator(@RequestParam(value = "prefix", required = false, defaultValue = "") String prefix, @RequestParam(value = "firstId") Integer firstId, @RequestParam(value = "lastId") Integer lastId, @RequestParam(value = "suffix", required = false, defaultValue = "") String suffix){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> response = new ArrayList<>();
        for (int number=firstId; number<=lastId; number++) {
            String tenantId = prefix + number + suffix;
            List<String> ids = userService.removeUsers(tenantId);
            for(String id : ids){
                response.add("User with ID " + id + " removed.");
            }
            try {
                tenantService.removeTenant(tenantId);
                response.add("Tenant with ID " + tenantId + " removed.");
            } catch (Exception e) {
                response.add("Error: " + e.getMessage());
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    private Map<String, UsersDTO> parseCSV(BufferedReader csvReader) throws IOException {
        String row;
        Map<String, UsersDTO> userDTOMap = new HashMap<>();
        while ((row = csvReader.readLine()) != null) {
            List<String> data = Arrays.asList(row.split(","));
            UserDTO userDTO = new UserDTO();
            userDTO.setFirstName(data.get(1));
            userDTO.setLastName(data.get(2));
            userDTO.setPassword(data.get(3));
            List<UserDTO.GroupId> groupIds = new ArrayList<>();
            for (int i = 4; i < data.size(); i++) {
                UserDTO.GroupId groupId = new UserDTO.GroupId(data.get(i));
                groupIds.add(groupId);
            }
            userDTO.setGroupIds(groupIds);
            if(userDTOMap.containsKey(data.get(0))){
                userDTOMap.get(data.get(0)).getUsers().add(userDTO);
            } else{
                UsersDTO usersDTO = new UsersDTO();
                usersDTO.getUsers().add(userDTO);
                userDTOMap.put(data.get(0), usersDTO);
            }
        }
        csvReader.close();
        return userDTOMap;
    }

    @PostMapping(path = "/user/csv", consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<List<String>> postUserCSV(@RequestPart(value="file") MultipartFile file, @RequestParam(value = "scripting", defaultValue = "false") boolean allowScripts) throws IOException{
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        Map<String, UsersDTO> usersDTOMap = parseCSV(new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)));
        List<String> response = new ArrayList<>();
        for (Map.Entry<String, UsersDTO> dtoEntry : usersDTOMap.entrySet()) {
            try {
                response.addAll(createUser(dtoEntry.getKey(), dtoEntry.getValue(), allowScripts));
            } catch (Exception e) {
                response.add("Error: " + e.getMessage());
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping(path = "/deployment/generator", consumes = "multipart/form-data")
    public ResponseEntity<List<String>> postDeploymentGenerator(@RequestParam(value = "prefix", required = false, defaultValue = "") String prefix, @RequestParam(value = "firstId") Integer firstId, @RequestParam(value = "lastId") Integer lastId, @RequestParam(value = "suffix", required = false, defaultValue = "") String suffix, @RequestParam(value = "deployment name", required = false) String deploymentName, @RequestPart(value="files") List<MultipartFile> files, @RequestParam(value = "importToModeler", defaultValue = "false") boolean importToModeler) throws IOException {
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        if(deploymentName == null) {
            deploymentName = files.get(0).getOriginalFilename();
        }
        for (int number = firstId; number <= lastId; number++) {
            if (!tenantService.tenantExists(prefix + number + suffix)) {
                return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
            }
        }
        List<String> deploymentIds = new ArrayList<>();
        for (int number=firstId; number<=lastId; number++) {
            String tenantId = prefix + number + suffix;
            deploymentIds.add(deploymentService.createTenantDeployment(tenantId, deploymentName, "ClassroomAPI by " + getCurrentUser(), files, importToModeler));
        }
        return new ResponseEntity<>(deploymentIds, HttpStatus.ACCEPTED);
    }

    @DeleteMapping(path = "/deployment/generator")
    public ResponseEntity<List<String>> deleteDeploymentGenerator(@RequestParam(value = "prefix", required = false, defaultValue = "") String prefix, @RequestParam(value = "firstId") Integer firstId, @RequestParam(value = "lastId") Integer lastId, @RequestParam(value = "suffix", required = false, defaultValue = "") String suffix){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> deploymentIds = new ArrayList<>();
        for (int number=firstId; number<=lastId; number++) {
            String tenantId = prefix + number + suffix;
            deploymentIds.addAll(deploymentService.deleteTenantDeployments(tenantId));
        }
        return new ResponseEntity<>(deploymentIds, HttpStatus.ACCEPTED);
    }

    @PostMapping(path = "/deployment", consumes = "multipart/form-data")
    public ResponseEntity<String> postDeployment(@RequestParam(value = "tenant", required = false, defaultValue = "") String tenantId, @RequestParam(value = "deployment name", required = false) String deploymentName, @RequestPart(value="files") List<MultipartFile> files, @RequestParam(value = "importToModeler", defaultValue = "false") boolean importToModeler) throws IOException {
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        if(deploymentName == null) {
            deploymentName = files.get(0).getOriginalFilename();
        }
        return new ResponseEntity<>(deploymentService.createTenantDeployment(tenantId, deploymentName, "ClassroomAPI by " + getCurrentUser(), files, importToModeler), HttpStatus.ACCEPTED);
    }

    @DeleteMapping(path = "/deployment")
    public ResponseEntity<List<String>> deleteDeployment(@RequestParam(value = "tenant", required = false, defaultValue = "") String tenantId){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> deploymentIds = deploymentService.deleteTenantDeployments(tenantId);
        return new ResponseEntity<>(deploymentIds, HttpStatus.ACCEPTED);
    }

    @DeleteMapping(path = "/deployment/old/generator")
    public ResponseEntity<List<String>> deleteDeploymentsOldGenerator(@RequestParam(value = "prefix", required = false, defaultValue = "") String prefix, @RequestParam(value = "firstId") Integer firstId, @RequestParam(value = "lastId") Integer lastId, @RequestParam(value = "suffix", required = false, defaultValue = "") String suffix){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> deploymentIds = new ArrayList<>();
        for (int number=firstId; number<=lastId; number++) {
            String tenantId = prefix + number + suffix;
            deploymentIds.addAll(deploymentService.deleteOldVersionTenantDeployments(tenantId));
        }
        return new ResponseEntity<>(deploymentIds, HttpStatus.ACCEPTED);
    }

    @DeleteMapping(path = "/deployment/old")
    public ResponseEntity<List<String>> deleteDeploymentsOld(@RequestParam(value = "tenant", required = false, defaultValue = "") String tenantId){
        if(!isAdminAuthentication()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<String> deploymentIds = deploymentService.deleteOldVersionTenantDeployments(tenantId);
        return new ResponseEntity<>(deploymentIds, HttpStatus.ACCEPTED);
    }

    private Boolean isAdminAuthentication(){
        var authentication = identityService.getCurrentAuthentication();
        return authentication != null && authentication.getGroupIds() != null
                && authentication.getGroupIds().contains("camunda-admin");
    }

    private String getCurrentUser(){
        return identityService.getCurrentAuthentication().getUserId();
    }
}
