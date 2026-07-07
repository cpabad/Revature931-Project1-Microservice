package com.revature.ers.controller;

import com.revature.ers.dto.SubmitRequestDto;
import com.revature.ers.model.Request;
import com.revature.ers.service.RequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for reimbursement requests. Jackson serializes the whole eager graph
 * (requester, eventLocation, requestStatus) automatically; User.password stays out via
 * its @JsonIgnore.
 *
 *   GET  /requests                  -> all requests (Supervisor)
 *   GET  /requests/{id}             -> one request, or 404
 *   GET  /requests/requester/{uid}  -> a user's requests
 *   POST /requests                  -> submit for myself (requester = token subject)
 */
@RestController
@RequestMapping("/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    public ResponseEntity<List<Request>> getAll() {
        return ResponseEntity.ok(requestService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Request> getById(@PathVariable int id) {
        return requestService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/requester/{userId}")
    public ResponseEntity<List<Request>> getByRequester(@PathVariable int userId) {
        return ResponseEntity.ok(requestService.findByRequesterId(userId));
    }

    @PostMapping
    public ResponseEntity<Request> submit(@AuthenticationPrincipal Jwt jwt, @RequestBody SubmitRequestDto dto) {
        return requestService.submit(Integer.parseInt(jwt.getSubject()), dto)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                // empty = unknown event location id
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }
}
