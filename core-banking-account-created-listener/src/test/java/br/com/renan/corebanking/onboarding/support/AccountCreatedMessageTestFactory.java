package br.com.renan.corebanking.onboarding.support;

import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedMessageDTO;
import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedPayloadDTO;

public final class AccountCreatedMessageTestFactory {
    public static final String VALID_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975";
    public static final String VALID_OWNER = "315e3cfe-f4af-4cd2-b298-a449e614349a";
    public static final String VALID_CREATED_AT = "1634874339";
    public static final String VALID_STATUS = "ENABLED";

    private AccountCreatedMessageTestFactory() {
    }

    public static AccountCreatedMessageDTO of(String id, String owner, String createdAt, String status) {
        return new AccountCreatedMessageDTO(new AccountCreatedPayloadDTO(id, owner, createdAt, status));
    }

    public static AccountCreatedMessageDTO valid() {
        return of(VALID_ID, VALID_OWNER, VALID_CREATED_AT, VALID_STATUS);
    }

    public static AccountCreatedMessageDTO withId(String id) {
        return of(id, VALID_OWNER, VALID_CREATED_AT, VALID_STATUS);
    }

    public static String validJson(String id) {
        return """
                {"account":{"id":"%s","owner":"%s","created_at":"%s","status":"%s"}}
                """.formatted(id, VALID_OWNER, VALID_CREATED_AT, VALID_STATUS);
    }
}
