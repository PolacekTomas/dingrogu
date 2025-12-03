package org.jboss.pnc.dingrogu.restadapter.adapter;

import static org.jboss.pnc.rex.common.enums.ResponseFlag.SKIP_ROLLBACK;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.causeway.dto.push.BuildPushRequest;
import org.jboss.pnc.api.causeway.dto.push.PushResult;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.dingrogu.api.client.AuthorizationClientHttpFactory;
import org.jboss.pnc.dingrogu.api.client.CausewayProducer;
import org.jboss.pnc.dingrogu.api.dto.adapter.BrewPushDTO;
import org.jboss.pnc.dingrogu.api.endpoint.AdapterEndpoint;
import org.jboss.pnc.dingrogu.api.endpoint.WorkflowEndpoint;
import org.jboss.pnc.dingrogu.restadapter.adapter.callback.CallbackDecision;
import org.jboss.pnc.rex.model.requests.StartRequest;
import org.jboss.pnc.rex.model.requests.StopRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;

@ApplicationScoped
public class CausewayBuildPushAdapter extends AbstractAdapter<BrewPushDTO, PushResult> {

    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "dingrogu.url")
    String dingroguUrl;

    @Inject
    CausewayProducer causewayProducer;

    @Inject
    AuthorizationClientHttpFactory authorizationClientHttpFactory;

    @jakarta.inject.Inject
    public CausewayBuildPushAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAdapterName() {
        return "causeway-brew-push";
    }

    @Override
    public Optional<Object> start(String correlationId, StartRequest startRequest) {
        BrewPushDTO brewPushDTO = objectMapper.convertValue(startRequest.getPayload(), BrewPushDTO.class);

        String callbackUrl = AdapterEndpoint.getCallbackAdapterEndpoint(dingroguUrl, getAdapterName(), correlationId);
        Request callback = new Request(Request.Method.POST, URI.create(callbackUrl));

        BuildPushRequest request = BuildPushRequest.builder()
                .id(correlationId)
                .buildId(brewPushDTO.getBuildId())
                .tagPrefix(brewPushDTO.getTagPrefix())
                .reimport(brewPushDTO.isReimport())
                .username(brewPushDTO.getUsername())
                .callback(callback)
                .heartbeat(startRequest.getHeartbeatConfig())
                .build();

        Log.infof("Causeway request: %s", request);
        causewayProducer.getCauseway(brewPushDTO.getCausewayUrl()).importBuild(request);
        return Optional.empty();
    }

    @Override
    protected Class<PushResult> getCallbackType() {
        return PushResult.class;
    }

    @Override
    protected CallbackDecision evaluate(PushResult r) {
        if (r == null || r.getResult() == null) {
            Log.error("Build Push response or status is null: " + r);
            return CallbackDecision.fail();
        }
        return switch (r.getResult()) {
            case SUCCESS -> CallbackDecision.ok();
            case FAILED -> CallbackDecision.fail(Set.of(SKIP_ROLLBACK)); // no rollback
            case TIMED_OUT, CANCELLED, SYSTEM_ERROR -> CallbackDecision.fail(); // with rollback (if configured)
        };
    }

    @Override
    public void cancel(String correlationId, StopRequest stopRequest) {
    }

    @Override
    public String getNotificationEndpoint(String adapterUrl) {
        return adapterUrl + WorkflowEndpoint.BREW_PUSH_REX_NOTIFY;
    }

    @Override
    public boolean shouldUseHeartbeat() {
        return true;
    }
}
