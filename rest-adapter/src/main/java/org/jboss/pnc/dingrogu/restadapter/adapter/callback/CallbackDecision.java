package org.jboss.pnc.dingrogu.restadapter.adapter.callback;

import org.jboss.pnc.rex.common.enums.ResponseFlag;

import java.util.Set;

public record CallbackDecision(boolean success, Set<ResponseFlag> flags) {
    public static CallbackDecision ok() {
        return new CallbackDecision(true, null);
    }
    public static CallbackDecision ok(Set<ResponseFlag> flags) {
        return new CallbackDecision(true, flags);
    }
    public static CallbackDecision fail() {
        return new CallbackDecision(false, null);
    }
    public static CallbackDecision fail(Set<ResponseFlag> flags) {
        return new CallbackDecision(false, flags);
    }
}
