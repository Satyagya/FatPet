package com.engati.data.analytics.engine.handle.metric;

import com.engati.data.analytics.engine.handle.query.factory.QueryHandlerFactory;
import com.engati.data.analytics.sdk.druid.query.DruidQueryMetaInfo;
import com.engati.data.analytics.sdk.druid.query.MultiQueryMetaInfo;
import com.engati.data.analytics.sdk.druid.query.TimeSeriesQueryMetaInfo;
import com.engati.data.analytics.sdk.druid.query.TopNQueryMetaInfo;
import com.engati.data.analytics.sdk.response.QueryResponse;
import com.engati.data.analytics.sdk.response.SimpleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PercentageMetric extends MetricHandler {

  private static final String METRIC_HANDLER_PERCENTAGE_CONTRIBUTION = "percentage_contribution";

  @Autowired
  private QueryHandlerFactory queryHandlerFactory;


  @Override
  public String getMetricName() {
    return METRIC_HANDLER_PERCENTAGE_CONTRIBUTION;
  }

  @Override
  public QueryResponse generateAndExecuteQuery(Integer botRef, Integer customerId,
      DruidQueryMetaInfo druidQueryMetaInfo, QueryResponse prevResponse) {
    List<QueryResponse> responses = new ArrayList<>();
    MultiQueryMetaInfo multiQueryMetaInfo = ((MultiQueryMetaInfo) druidQueryMetaInfo);
    String grain = null;
    List<String> timeRange = Collections.emptyList();
    QueryResponse timeSeriesResponse = new QueryResponse();
    QueryResponse topNQueryResponse = new QueryResponse();
    for (DruidQueryMetaInfo druidQuery : multiQueryMetaInfo.getMultiMetricQuery()) {
      if (druidQuery instanceof TimeSeriesQueryMetaInfo) {
        timeSeriesResponse =
            queryHandlerFactory.getQueryHandler(druidQuery.getType(), botRef, customerId)
                .generateAndExecuteQuery(botRef, customerId, druidQuery, timeSeriesResponse);
      } else if (druidQuery instanceof TopNQueryMetaInfo) {
        topNQueryResponse =
            queryHandlerFactory.getQueryHandler(druidQuery.getType(), botRef, customerId)
                .generateAndExecuteQuery(botRef, customerId, druidQuery, topNQueryResponse);
      }
    }
    return computePercentageContribution(timeSeriesResponse, topNQueryResponse,
        multiQueryMetaInfo.getMetricList().get(0));
  }

  private QueryResponse computePercentageContribution(QueryResponse timeSeriesResponse,
      QueryResponse topNQueryResponse, String metric) {
    SimpleResponse timeSeriesSimpleResponse = (SimpleResponse) timeSeriesResponse;
    SimpleResponse topNSimpleResponse = (SimpleResponse) topNQueryResponse;
    List<Map<String, Object>> topNSimpleResponseList = new ArrayList<>();
    Double overallDoubleValue;
    try {
      if (CollectionUtils.isEmpty(topNSimpleResponse.getQueryResponse())) {
        return topNSimpleResponse;
      }
      topNSimpleResponseList = topNSimpleResponse.getQueryResponse().values().iterator().next();
      overallDoubleValue = Double.valueOf(
          ((Number) timeSeriesSimpleResponse.getQueryResponse().values().iterator().next().get(0)
              .get(metric)).doubleValue());
      for (Map<String, Object> topNSingleResponse : topNSimpleResponseList) {
        String metricPercentage = metric.concat("_percentage");
        Double metricValue =
            Double.valueOf(((Number) topNSingleResponse.get(metric)).doubleValue());
        topNSingleResponse.put(metricPercentage, (metricValue / overallDoubleValue) * 100);
      }
    } catch (ArithmeticException ex) {
      log.error("Division by zero exception", ex);
      for (Map<String, Object> topNSingleResponse : topNSimpleResponseList) {
        String metricPercentage = metric.concat("_percentage");
        topNSingleResponse.put(metricPercentage, 0);
      }
    }
    return topNSimpleResponse;
  }

}