package com.example.bank.api;

import com.example.bank.domain.Account;
import com.example.bank.service.BankingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AccountController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_PAGE_SIZE = 100;

    private final BankingService bank;

    AccountController(BankingService bank) {
        this.bank = bank;
    }

    @PostMapping("/accounts")
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = bank.openAccount(request.ownerName(), request.initialDeposit());
        return ResponseEntity
                .created(URI.create("/api/v1/accounts/" + account.getAccountNumber()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/accounts/{accountNumber}")
    AccountResponse get(@PathVariable long accountNumber) {
        return AccountResponse.from(bank.getAccount(accountNumber));
    }

    /** Newest entries first. */
    @GetMapping("/accounts/{accountNumber}/transactions")
    PageResponse<TransactionResponse> history(@PathVariable long accountNumber,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(
                bank.history(accountNumber, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))),
                TransactionResponse::from);
    }

    @PostMapping("/accounts/{accountNumber}/deposits")
    TransactionResponse deposit(@PathVariable long accountNumber,
                                @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(bank.deposit(accountNumber, request.amount(), key));
    }

    @PostMapping("/accounts/{accountNumber}/withdrawals")
    TransactionResponse withdraw(@PathVariable long accountNumber,
                                 @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                 @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(bank.withdraw(accountNumber, request.amount(), key));
    }

    @PostMapping("/transfers")
    TransactionResponse transfer(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                 @Valid @RequestBody TransferRequest request) {
        return TransactionResponse.from(
                bank.transfer(request.fromAccount(), request.toAccount(), request.amount(), key));
    }
}
