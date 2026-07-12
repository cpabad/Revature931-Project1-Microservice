package com.revature.ers.controller;

import com.revature.ers.dto.RequestResponse;
import com.revature.ers.dto.SubmitRequestDto;
import com.revature.ers.service.RequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for reimbursement requests. Every response goes out as a RequestResponse
 * DTO, mapped HERE at the boundary: the service speaks entities (domain), the controller
 * owns the wire shape. Serializing entities directly coupled the JSON to the schema -
 * every mapped association shipped, and any new column leaked by default.
 *
 *   GET  /requests                  -> a PAGE of requests (Supervisor); ?page=&size=&sort=
 *   GET  /requests/{id}             -> one request (owner or Supervisor), or 404
 *   GET  /requests/requester/{uid}  -> a user's requests (that user or Supervisor)
 *   POST /requests                  -> submit for myself (requester = token subject)
 *
 * Object-level authorization (OWASP API #1) lives HERE, not in SecurityConfig: the filter
 * chain's path rules can say "who may hit this URL shape" but not "who owns request 7" -
 * that needs the loaded entity. A request that exists but belongs to someone else returns
 * 404, not 403, so an id-walking caller cannot distinguish "not yours" from "not there"
 * (a 403 would confirm the id exists - an enumeration oracle). The by-requester route
 * returns an honest 403 on mismatch instead: the caller supplied the userId themselves,
 * so there is no existence to leak.
 */
@RestController
@RequestMapping("/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * A PAGE of requests, not the whole table. The old unbounded findAll() was fine at 4 seed
     * rows and an OOM at scale (the response, the Jackson buffer, and the eager entity graph all
     * grow with row count). Pageable turns it into ?page=&size=&sort=; the default is 20 newest
     * first, and size is HARD-CAPPED at 100 by spring.data.web.pageable.max-page-size - a client
     * asking for ?size=100000 silently gets 100, so the cap cannot be bypassed from the query.
     */
    @GetMapping
    public ResponseEntity<Page<RequestResponse>> getAll(
            @PageableDefault(size = 20, sort = "requestId", direction = Sort.Direction.DESC)
            Pageable pageable) {
        // Page.map keeps the envelope (totalElements, pageable...) and swaps the content
        return ResponseEntity.ok(requestService.findAll(pageable).map(RequestResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RequestResponse> getById(JwtAuthenticationToken auth, @PathVariable int id) {
        return requestService.findById(id)
                .filter(r -> isSupervisor(auth) || r.getRequester().getUserId() == callerId(auth))
                .map(RequestResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/requester/{userId}")
    public ResponseEntity<List<RequestResponse>> getByRequester(JwtAuthenticationToken auth, @PathVariable int userId) {
        if (userId != callerId(auth) && !isSupervisor(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(requestService.findByRequesterId(userId).stream().map(RequestResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<RequestResponse> submit(@AuthenticationPrincipal Jwt jwt, @RequestBody SubmitRequestDto dto) {
        return requestService.submit(Integer.parseInt(jwt.getSubject()), dto)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(RequestResponse.from(created)))
                // empty = unknown event location id
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    /** auth-service mints the userId into the subject claim; getName() surfaces it. */
    private static int callerId(JwtAuthenticationToken auth) {
        return Integer.parseInt(auth.getName());
    }

    private static boolean isSupervisor(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Supervisor".equals(a.getAuthority()));
    }
}
