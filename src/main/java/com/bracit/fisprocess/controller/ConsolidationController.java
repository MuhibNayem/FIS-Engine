package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.service.ConsolidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/consolidation") @RequiredArgsConstructor
public class ConsolidationController {
    private final ConsolidationService svc;
    @PostMapping("/groups") public ResponseEntity<?> createGroup(@RequestHeader("X-Tenant-Id") UUID t, @RequestBody com.bracit.fisprocess.dto.request.CreateConsolidationGroupRequestDto req) { return ResponseEntity.status(201).body(svc.createGroup(t, req)); }
    @PostMapping("/groups/{id}/members") public ResponseEntity<?> addMember(@PathVariable UUID id, @RequestBody com.bracit.fisprocess.dto.request.AddConsolidationMemberRequestDto req) { return ResponseEntity.status(201).body(svc.addMember(id, req)); }
    @PostMapping("/groups/{id}/run") public ResponseEntity<?> run(@PathVariable UUID id, @RequestParam String period) { return ResponseEntity.ok(svc.run(id, period)); }
    @GetMapping("/groups/{id}") public ResponseEntity<?> getGroup(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.getGroup(t, id)); }
    @GetMapping("/groups") public ResponseEntity<?> listGroups(@RequestHeader("X-Tenant-Id") UUID t, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return ResponseEntity.ok(svc.listGroups(t, org.springframework.data.domain.PageRequest.of(page, size))); }
}
