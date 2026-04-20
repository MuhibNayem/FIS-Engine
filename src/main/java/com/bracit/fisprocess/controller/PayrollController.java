package com.bracit.fisprocess.controller;
import com.bracit.fisprocess.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/payroll") @RequiredArgsConstructor
public class PayrollController {
    private final PayrollService svc;
    @PostMapping("/employees") public ResponseEntity<?> registerEmployee(@RequestHeader("X-Tenant-Id") UUID t, @RequestBody com.bracit.fisprocess.dto.request.RegisterEmployeeRequestDto req) { return ResponseEntity.status(201).body(svc.registerEmployee(t, req)); }
    @PostMapping("/runs") public ResponseEntity<?> createRun(@RequestHeader("X-Tenant-Id") UUID t, @RequestHeader(value="X-Actor-Id",defaultValue="system") String p, @RequestBody com.bracit.fisprocess.dto.request.CreatePayrollRunRequestDto req) { return ResponseEntity.status(201).body(svc.createRun(t, req, p)); }
    @PostMapping("/runs/{id}/post") public ResponseEntity<?> post(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id, @RequestHeader(value="X-Actor-Id",defaultValue="system") String p) { return ResponseEntity.ok(svc.calculateAndPost(t, id, p)); }
    @GetMapping("/runs/{id}") public ResponseEntity<?> getById(@RequestHeader("X-Tenant-Id") UUID t, @PathVariable UUID id) { return ResponseEntity.ok(svc.getById(t, id)); }
    @GetMapping("/employees") public ResponseEntity<?> listEmployees(@RequestHeader("X-Tenant-Id") UUID t, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return ResponseEntity.ok(svc.listEmployees(t, org.springframework.data.domain.PageRequest.of(page, size))); }
}
