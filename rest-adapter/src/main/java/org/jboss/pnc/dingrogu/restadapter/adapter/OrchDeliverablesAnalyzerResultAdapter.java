package org.jboss.pnc.dingrogu.restadapter.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.deliverablesanalyzer.dto.AnalysisReport;
import org.jboss.pnc.api.deliverablesanalyzer.dto.AnalysisResult;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.dingrogu.api.dto.adapter.OrchDeliverablesAnalyzerResultDTO;
import org.jboss.pnc.dingrogu.api.endpoint.AdapterEndpoint;
import org.jboss.pnc.dingrogu.api.endpoint.WorkflowEndpoint;
import org.jboss.pnc.dingrogu.common.TaskHelper;
import org.jboss.pnc.dingrogu.restadapter.adapter.callback.CallbackDecision;
import org.jboss.pnc.dingrogu.restadapter.client.OrchClient;
import org.jboss.pnc.rex.model.requests.StartRequest;
import org.jboss.pnc.rex.model.requests.StopRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;

@ApplicationScoped
public class OrchDeliverablesAnalyzerResultAdapter
        extends AbstractAdapter<OrchDeliverablesAnalyzerResultDTO, ResultStatus> {

    @ConfigProperty(name = "dingrogu.url")
    String dingroguUrl;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OrchClient orchClient;

    @Inject
    DeliverablesAnalyzerAdapter deliverablesAnalyzerAdapter;

    @Override
    public String getAdapterName() {
        return "orch-dela-result";
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

        // grab payload DTO
        OrchDeliverablesAnalyzerResultDTO dto = objectMapper
                .convertValue(startRequest.getPayload(), OrchDeliverablesAnalyzerResultDTO.class);

        Map<String, Object> pastResults = startRequest.getTaskResults();
        Object pastResult = pastResults.get(deliverablesAnalyzerAdapter.getRexTaskName(correlationId));
        AnalysisReport analysisReport = objectMapper.convertValue(pastResult, AnalysisReport.class);

        // generate result for Orch
        AnalysisResult result = AnalysisResult.builder()
                .operationId(dto.getOperationId())
                .results(analysisReport.getResults())
                .scratch(dto.isScratch())
                .callback(callback)
                .build();

        orchClient.submitDelAResult(dto.getOrchUrl(), result);

        return Optional.empty();
    }

    @Override
    protected Class<ResultStatus> getCallbackType() {
        return ResultStatus.class;
    }

    @Override
    protected CallbackDecision evaluate(ResultStatus r) {
        return r.isSuccess() ? CallbackDecision.ok() : CallbackDecision.fail();
    }

    @Override
    public String getNotificationEndpoint(String adapterUrl) {
        return adapterUrl + WorkflowEndpoint.DELIVERABLES_ANALYSIS_REX_NOTIFY;
    }

    /**
     * There's nothing really we can cancel about this
     * 
     * @param correlationId
     * @param stopRequest
     */
    @Override
    public void cancel(String correlationId, StopRequest stopRequest) {
    }

    /**
     * We read past results to build final request
     * 
     * @return true
     */
    @Override
    public boolean shouldGetResultsFromDependencies() {
        return true;
    }
}
