package ro.unibuc.prodeng.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.AdminService;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = "Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/transactions/search")
    @Operation(summary = "Search transactions globally with filters (admin only)")
    public ResponseEntity<List<TransactionResponse>> searchTransactions(
            @RequestBody TransactionSearchRequest request) {
        List<TransactionResponse> results = adminService.searchTransactions(request);
        return ResponseEntity.ok(results);
    }
}
