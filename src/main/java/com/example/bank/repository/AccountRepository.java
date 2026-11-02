package com.example.bank.repository;

import com.example.bank.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Loads the account with {@code SELECT ... FOR UPDATE}. Concurrent operations on the
     * same account queue up on the row lock until the current transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByAccountNumber(Long accountNumber);
}
