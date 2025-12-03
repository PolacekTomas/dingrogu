package org.jboss.pnc.dingrogu.restadapter.adapter;

import jakarta.inject.Inject;
import org.jboss.pnc.dingrogu.restadapter.adapter.callback.CallbackDecision;
import org.jboss.pnc.dingrogu.restadapter.adapter.callback.CallbackProcessor;

public abstract class AbstractAdapter<T, R> implements Adapter<T> {

    @Inject
    CallbackProcessor callbackProcessor;

    protected abstract Class<R> getCallbackType();

    protected abstract CallbackDecision evaluate(R response);

    @Override
    public void callback(String correlationId, Object object) {
        String taskName = getRexTaskName(correlationId);

        callbackProcessor.process(
                taskName,
                object,
                getCallbackType(),
                this::evaluate
        );
    }
}
