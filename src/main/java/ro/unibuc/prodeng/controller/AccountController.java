package ro.unibuc.prodeng.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.CreateAccountRequest;
import ro.unibuc.prodeng.request.CreateTransactionRequest;
import ro.unibuc.prodeng.response.AccountResponse;
import ro.unibuc.prodeng.response.BalanceSheetResponse;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.AccountService;
import ro.unibuc.prodeng.service.ReportingService;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = "Authentication")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private ReportingService reportingService;

    @PostMapping
    @Operation(summary = "Create a new bank account")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable String id) {
        AccountResponse account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @GetMapping
    @Operation(summary = "Get accounts by user ID")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(@RequestParam String userId) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an account")
    public ResponseEntity<Void> deleteAccount(@PathVariable String id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transactions")
    @Operation(summary = "Create a transaction on an account")
    public ResponseEntity<TransactionResponse> createTransaction(
            @PathVariable String id,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse tx = accountService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @GetMapping("/{id}/balance-sheet")
    @Operation(summary = "Get balance sheet with running balance for an account")
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet(@PathVariable String id) {
        BalanceSheetResponse balanceSheet = reportingService.getBalanceSheet(id);
        return ResponseEntity.ok(balanceSheet);
    }
}
