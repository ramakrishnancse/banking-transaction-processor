package com.example.bank.repository;

import com.example.bank.domain.LedgerEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountNumber(Long accountNumber, Pageable pageable);

    Optional<LedgerEntry> findByAccountNumberAndIdempotencyKey(Long accountNumber, String idempotencyKey);
}
