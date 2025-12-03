package org.jboss.pnc.dingrogu.restadapter.adapter;

import static org.jboss.pnc.rex.common.enums.ResponseFlag.SKIP_ROLLBACK;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.dingrogu.api.dto.adapter.ReqourAdjustDTO;
import org.jboss.pnc.dingrogu.api.endpoint.AdapterEndpoint;
import org.jboss.pnc.dingrogu.api.endpoint.WorkflowEndpoint;
import org.jboss.pnc.dingrogu.common.GitUrlParser;
import org.jboss.pnc.dingrogu.common.TaskHelper;
import org.jboss.pnc.dingrogu.restadapter.adapter.callback.CallbackDecision;
import org.jboss.pnc.dingrogu.restadapter.client.ReqourClient;
import org.jboss.pnc.rex.model.requests.StartRequest;
import org.jboss.pnc.rex.model.requests.StopRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;

@ApplicationScoped
public class ReqourAdjustAdapter extends AbstractAdapter<ReqourAdjustDTO, AdjustResponse> {

    @ConfigProperty(name = "dingrogu.url")
    String dingroguUrl;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ReqourClient reqourClient;

    private static final Logger LIVE_LOG = LoggerFactory.getLogger("org.jboss.pnc._userlog_.dingrogu");

    @Override
    public String getAdapterName() {
        return "reqour-adjust";
    }

    @Override
    public Optional<Object> start(String correlationId, StartRequest startRequest) {
        Request callback;
        try {
            callback = new Request(
                    Request.Method.POST,
                    new URI(AdapterEndpoint.getCallbackAdapterEndpoint(dingroguUrl, getAdapterName(), correlationId)),
                    TaskHelper.getHTTPHeaders(),
                    null);
        } catch (URISyntaxException e) {
            Log.error(e);
            throw new RuntimeException(e);
        }

        ReqourAdjustDTO reqourAdjustDTO = objectMapper.convertValue(startRequest.getPayload(), ReqourAdjustDTO.class);

        // Generate DTO to submit to Reqour
        InternalGitRepositoryUrl internalUrl = InternalGitRepositoryUrl.builder()
                .readonlyUrl(GitUrlParser.scmRepoURLReadOnly(reqourAdjustDTO.getScmRepoURL()))
                .readwriteUrl(reqourAdjustDTO.getScmRepoURL())
                .build();

        // Map <String, String> to <BuildConfigurationParameterKeys, String>
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = new HashMap<>();
        for (BuildConfigurationParameterKeys key : BuildConfigurationParameterKeys.values()) {
            String value = reqourAdjustDTO.getGenericParameters().get(key.name());
            if (value != null) {
                buildConfigParameters.put(key, value);
            }
        }

        AdjustRequest request = AdjustRequest.builder()
                .internalUrl(internalUrl)
                .ref(reqourAdjustDTO.getScmRevision())
                .callback(callback)
                .sync(reqourAdjustDTO.isPreBuildSyncEnabled())
                .originRepoUrl(reqourAdjustDTO.getOriginRepoURL())
                .buildConfigParameters(buildConfigParameters)
                .tempBuild(reqourAdjustDTO.isTempBuild())
                .alignmentPreference(reqourAdjustDTO.getAlignmentPreference())
                .taskId(reqourAdjustDTO.getId())
                .buildType(reqourAdjustDTO.getBuildType())
                .pncDefaultAlignmentParameters(reqourAdjustDTO.getDefaultAlignmentParams())
                .brewPullActive(reqourAdjustDTO.isBrewPullActive())
                .heartbeatConfig(startRequest.getHeartbeatConfig())
                .build();

        // Send to Reqour
        reqourClient.adjust(reqourAdjustDTO.getReqourUrl(), request);

        return Optional.empty();
    }

    @Override
    protected Class<AdjustResponse> getCallbackType() {
        return AdjustResponse.class;
    }

    @Override
    protected CallbackDecision evaluate(AdjustResponse r) {
        if (r == null || r.getCallback().getStatus() == null) {
            Log.error("Adjust response or status is null: " + r);
            return CallbackDecision.fail();
        }
        return switch (r.getCallback().getStatus()) {
            case SUCCESS -> {
                Log.infof("Adjust response: %s", r.toString());
                yield CallbackDecision.ok();
            }
            case FAILED -> CallbackDecision.fail(Set.of(SKIP_ROLLBACK)); // no rollback
            case TIMED_OUT, CANCELLED, SYSTEM_ERROR -> CallbackDecision.fail(); // with rollback (if configured)
        };
    }

    @Override
    public String getNotificationEndpoint(String adapterUrl) {
        return adapterUrl + WorkflowEndpoint.BUILD_REX_NOTIFY;
    }

    @Override
    public void cancel(String correlationId, StopRequest stopRequest) {
        ReqourAdjustDTO reqourAdjustDTO = objectMapper.convertValue(stopRequest.getPayload(), ReqourAdjustDTO.class);
        reqourClient.cancel(reqourAdjustDTO.getReqourUrl(), reqourAdjustDTO.getId());
    }

    @Override
    public boolean shouldUseHeartbeat() {
        return true;
    }
}
