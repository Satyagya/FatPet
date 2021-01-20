package com.engati.data.analytics.engine.responseGenerator;

import com.google.gson.JsonArray;

import java.util.List;
import java.util.Map;

public interface DruidResponseParser {

  List<List<Map<String, String>>> convertJsonToMap(JsonArray response);
}
