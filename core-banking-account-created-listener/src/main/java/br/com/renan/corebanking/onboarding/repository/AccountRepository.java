package br.com.renan.corebanking.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.renan.corebanking.onboarding.model.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
