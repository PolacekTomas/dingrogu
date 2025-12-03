package org.jboss.pnc.dingrogu.restadapter.adapter.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.pnc.rex.api.CallbackEndpoint;
import org.jboss.pnc.rex.common.enums.ResponseFlag;

import java.util.Set;
import java.util.function.Function;

@ApplicationScoped
public class CallbackProcessor {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CallbackEndpoint callbackEndpoint;

    public <R> void process(
            String rexTaskName,
            Object rawObject,
            Class<R> resultType,
            Function<R, CallbackDecision> decide
    ) {
        R parsed;
        try {
            parsed = objectMapper.convertValue(rawObject, resultType);
            try {
                CallbackDecision decision = decide.apply(parsed);

                if (decision.success()) {
                    succeed(rexTaskName, parsed, decision.flags());
                } else {
                    fail(rexTaskName, parsed, decision.flags());
                }
            } catch (Exception e) {
                Log.error("Error happened in callback adapter", e);
            }
        } catch (IllegalArgumentException e) {
            // if we cannot cast object to its type, it's probably a failure
            try {
                fail(rexTaskName, rawObject);
            } catch (Exception ex) {
                Log.error("Error happened in callback adapter", ex);
            }
        }
    }

    private void succeed(String task, Object body, Set<ResponseFlag> flags) {
        callbackEndpoint.succeed(task, body, null, flags);
    }

    private void fail(String task, Object body) {
        callbackEndpoint.fail(task, body, null, null);
    }

    private void fail(String task, Object body, Set<ResponseFlag> flags) {
        callbackEndpoint.fail(task, body, null, flags);
    }
}
