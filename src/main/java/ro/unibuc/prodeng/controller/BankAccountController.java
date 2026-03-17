package ro.unibuc.prodeng.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.CreateBankAccountRequest;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.BalanceSheetResponse;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.BankAccountService;
import ro.unibuc.prodeng.service.ReportingService;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Bank Accounts", description = "Bank account management endpoints with IBAN support")
@SecurityRequirement(name = "Authentication")
public class BankAccountController {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private ReportingService reportingService;

    @Operation(summary = "Create a new bank account",
            description = "Creates a new bank account for the authenticated user. An IBAN is automatically generated based on the country's IBAN pattern. Max 3 accounts per user, one per currency.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Bank account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request, unsupported currency, country without IBAN pattern, max accounts reached, or duplicate currency")
    })
    @PostMapping
    public ResponseEntity<BankAccountResponse> createAccount(
            @Valid @RequestBody CreateBankAccountRequest request) {
        BankAccountResponse account = bankAccountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @Operation(summary = "Get my bank accounts",
            description = "Retrieves all active bank accounts owned by the currently authenticated user (IBAN, balance, currency)")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<List<BankAccountResponse>> getMyAccounts() {
        List<BankAccountResponse> accounts = bankAccountService.getMyAccounts();
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Get all bank accounts (admin only)",
            description = "Retrieves all active bank accounts in the system with pagination")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<BankAccountResponse>> getAllAccounts(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BankAccountResponse> accounts = bankAccountService.getAllAccounts(pageable);
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Get bank account by ID (admin only)",
            description = "Retrieves a bank account by its unique identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BankAccountResponse> getAccountById(
            @Parameter(description = "Bank account ID") @PathVariable String id) {
        BankAccountResponse account = bankAccountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @Operation(summary = "Get bank account by IBAN (admin only)",
            description = "Retrieves a bank account by its IBAN")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/by-iban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BankAccountResponse> getAccountByIban(
            @Parameter(description = "IBAN") @RequestParam String iban) {
        BankAccountResponse account = bankAccountService.getAccountByIban(iban);
        return ResponseEntity.ok(account);
    }

    @Operation(summary = "Get accounts by user ID (admin only)",
            description = "Retrieves all bank accounts for a specific user, including closed (soft-deleted) accounts")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BankAccountResponse>> getAccountsByUserId(
            @Parameter(description = "User ID") @PathVariable String userId) {
        List<BankAccountResponse> accounts = bankAccountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Close bank account (admin only)",
            description = "Soft-deletes a bank account by marking it as closed")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account closed successfully"),
        @ApiResponse(responseCode = "400", description = "Account is already closed"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> closeAccount(
            @Parameter(description = "Bank account ID") @PathVariable String id) {
        bankAccountService.closeAccount(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Transfer money between bank accounts",
            description = "Transfers funds from a source bank account (owned by the current user) to a target bank account. Only same-currency transfers are supported.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transfer completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request, insufficient funds, ownership or currency validation failed"),
        @ApiResponse(responseCode = "404", description = "Source or target account not found")
    })
    @PostMapping("/transfer")
    public ResponseEntity<List<TransactionResponse>> transfer(
            @Valid @RequestBody CreateTransferRequest request) {
        List<TransactionResponse> transactions = bankAccountService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactions);
    }

    @Operation(summary = "Get balance sheet with running balance for an account",
            description = "Retrieves all transactions for the specified account with a running balance computation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance sheet retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}/balance-sheet")
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet(
            @Parameter(description = "Bank account ID") @PathVariable String id) {
        BalanceSheetResponse balanceSheet = reportingService.getBalanceSheet(id);
        return ResponseEntity.ok(balanceSheet);
    }
}
