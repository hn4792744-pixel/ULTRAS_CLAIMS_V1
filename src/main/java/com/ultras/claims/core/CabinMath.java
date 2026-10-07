package com.ultras.claims.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts deposited items into protection time. Pure so it can be unit tested. */
public final class CabinMath {
    private CabinMath() {
    }

    /** minimum = items needed for one step, seconds = protection added per step. */
    public record Rule(int minimum, long seconds) {
        public Rule {
            minimum = Math.max(1, minimum);
            seconds = Math.max(0, seconds);
        }
    }

    /** consumed: key -> items to remove; seconds: total protection gained. */
    public record Result(Map<String, Integer> consumed, long seconds) {
        public boolean isEmpty() {
            return consumed.isEmpty();
        }
    }

    public static Result compute(Map<String, Rule> rules, Map<String, Integer> deposited) {
        return compute(rules, deposited, Long.MAX_VALUE);
    }

    /** Same, but never grants more than {@code maxSeconds} in total (items beyond the cap stay unconsumed). */
    public static Result compute(Map<String, Rule> rules, Map<String, Integer> deposited, long maxSeconds) {
        Map<String, Integer> consumed = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, Integer> e : deposited.entrySet()) {
            Rule rule = rules.get(e.getKey());
            if (rule == null || e.getValue() == null || e.getValue() < rule.minimum()) {
                continue;
            }
            int steps = e.getValue() / rule.minimum();
            if (rule.seconds() > 0 && maxSeconds != Long.MAX_VALUE) {
                long room = Math.max(0, maxSeconds - total);
                steps = (int) Math.min(steps, room / rule.seconds());
            }
            if (steps <= 0) {
                continue;
            }
            consumed.put(e.getKey(), steps * rule.minimum());
            total += (long) steps * rule.seconds();
        }
        return new Result(consumed, total);
    }
}
