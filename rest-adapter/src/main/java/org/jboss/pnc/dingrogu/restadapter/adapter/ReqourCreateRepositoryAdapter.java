package org.jboss.pnc.dingrogu.restadapter.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.dingrogu.api.dto.adapter.ReqourCreateRepositoryDTO;
import org.jboss.pnc.dingrogu.api.endpoint.AdapterEndpoint;
import org.jboss.pnc.dingrogu.api.endpoint.WorkflowEndpoint;
import org.jboss.pnc.dingrogu.common.GitUrlParser;
import org.jboss.pnc.dingrogu.common.TaskHelper;
import org.jboss.pnc.dingrogu.restadapter.adapter.callback.CallbackDecision;
import org.jboss.pnc.dingrogu.restadapter.client.ReqourClient;
import org.jboss.pnc.rex.model.requests.StartRequest;
import org.jboss.pnc.rex.model.requests.StopRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;

@ApplicationScoped
public class ReqourCreateRepositoryAdapter extends AbstractAdapter<ReqourCreateRepositoryDTO, InternalSCMCreationResponse> {

    @ConfigProperty(name = "dingrogu.url")
    String dingroguUrl;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ReqourClient reqourClient;

    @Override
    public Optional<Object> start(String correlationId, StartRequest startRequest) {
        ReqourCreateRepositoryDTO reqourCreateDTO = objectMapper
                .convertValue(startRequest.getPayload(), ReqourCreateRepositoryDTO.class);

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

        InternalSCMCreationRequest request = InternalSCMCreationRequest.builder()
                .project(getProjectName(reqourCreateDTO.getExternalUrl()))
                .callback(callback)
                .taskId(getRexTaskName(correlationId))
                .build();

        reqourClient.createRepository(reqourCreateDTO.getReqourUrl(), request);

        return Optional.empty();
    }

    @Override
    protected Class<InternalSCMCreationResponse> getCallbackType() {
        return InternalSCMCreationResponse.class;
    }

    @Override
    protected CallbackDecision evaluate(InternalSCMCreationResponse r) {
        if (r == null || r.getStatus() == InternalSCMCreationStatus.FAILED) {
            return CallbackDecision.fail();
        }
        Log.infof("Repo creation response: %s", r.toString());
        return switch (r.getCallback().getStatus()) {
            case SUCCESS -> CallbackDecision.ok();
            // TODO should FAILED status from ENV. Driver skip rollback like in other Adapters?
            case TIMED_OUT, CANCELLED, SYSTEM_ERROR, FAILED ->  CallbackDecision.fail();
        };
    }

    /**
     *
     * @param correlationId
     * @param stopRequest
     */
    @Override
    public void cancel(String correlationId, StopRequest stopRequest) {

        ReqourCreateRepositoryDTO reqourCreateDTO = objectMapper
                .convertValue(stopRequest.getPayload(), ReqourCreateRepositoryDTO.class);

        reqourClient.cancel(reqourCreateDTO.getReqourUrl(), getRexTaskName(correlationId));
    }

    @Override
    public String getNotificationEndpoint(String adapterUrl) {
        return adapterUrl + WorkflowEndpoint.REPOSITORY_CREATION_REX_NOTIFY;
    }

    @Override
    public String getAdapterName() {
        return "reqour-create-repository";
    }

    private static String getProjectName(String externalUrl) {
        return GitUrlParser.generateInternalGitRepoName(externalUrl);
    }
}
