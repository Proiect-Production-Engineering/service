package ro.unibuc.prodeng.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import ro.unibuc.prodeng.request.AccountSearchRequest;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.AdminService;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = "Authentication")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/transactions/search")
    @Operation(summary = "Search transactions globally with filters, pagination (admin only)")
    public ResponseEntity<Page<TransactionResponse>> searchTransactions(
            @RequestBody TransactionSearchRequest request) {
        Page<TransactionResponse> results = adminService.searchTransactions(request);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/accounts/search")
    @Operation(summary = "Search accounts by IBAN or owner name with pagination (admin only)")
    public ResponseEntity<Page<BankAccountResponse>> searchAccounts(
            @RequestBody AccountSearchRequest request) {
        Page<BankAccountResponse> results = adminService.searchAccounts(request);
        return ResponseEntity.ok(results);
    }
}
