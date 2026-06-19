package br.com.renan.corebanking.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.renan.corebanking.onboarding.entity.AccountEntity;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
}
