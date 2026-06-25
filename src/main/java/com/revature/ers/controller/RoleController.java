package com.revature.ers.controller;

import com.revature.ers.model.Role;
import com.revature.ers.service.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST entry point. @RestController + @GetMapping replace the monolith's FrontController
 * servlet + the giant switch in RequestHelper; Jackson serializes the returned object to
 * JSON automatically (no manual ObjectMapper).
 *
 * GET /roles -> [{"roleId":1,"role":"Supervisor"}, {"roleId":2,"role":"Employee"}]
 */
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    // Mechanism #2 - method security. Same role check as the path rule on /requests, but expressed
    // as an annotation right next to the handler. Enforced because SecurityConfig has
    // @EnableMethodSecurity. An authenticated non-Supervisor reaching here gets 403 (the method is
    // never entered); an UNauthenticated caller is already stopped at 401 by the filter chain.
    @PreAuthorize("hasRole('Supervisor')")
    @GetMapping
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(roleService.findAll());
    }
}
