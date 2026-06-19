package br.com.renan.corebanking.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyConstants {
    public static final int SCALE = 2;

    public static final String DEFAULT_CURRENCY = "BRL";

    private MoneyConstants() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
