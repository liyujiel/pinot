/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.core.query.pruner;

import org.apache.commons.configuration.Configuration;

import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.common.request.AggregationInfo;
import com.linkedin.pinot.common.request.BrokerRequest;
import com.linkedin.pinot.common.request.FilterQuery;
import com.linkedin.pinot.common.request.FilterQueryMap;
import com.linkedin.pinot.common.request.SelectionSort;
import com.linkedin.pinot.core.indexsegment.IndexSegment;


/**
 * An implementation of SegmentPruner.
 * Querying columns not appearing in the given segment will be pruned.
 *
 *
 */
public class DataSchemaSegmentPruner implements SegmentPruner {

  private static final String COLUMN_KEY = "column";

  @Override
  public boolean prune(IndexSegment segment, BrokerRequest brokerRequest) {
    Schema schema = segment.getSegmentMetadata().getSchema();
    // Check filtering columns
    if (!filterQueryMatchedSchema(schema, brokerRequest.getFilterQuery(), brokerRequest.getFilterSubQueryMap())) {
      return true;
    }
    // Check aggregation function columns.
    if (brokerRequest.getAggregationsInfo() != null) {
      for (AggregationInfo aggregationInfo : brokerRequest.getAggregationsInfo()) {
        if (aggregationInfo.getAggregationParams().containsKey(COLUMN_KEY)) {
          String columnName = aggregationInfo.getAggregationParams().get(COLUMN_KEY);
          if ((!aggregationInfo.getAggregationType().toLowerCase().equals("count")) && (!schema.isExisted(columnName))) {
            return true;
          }
        }
      }
      // Check groupBy columns.
      if ((brokerRequest.getGroupBy() != null) && (brokerRequest.getGroupBy().getColumns() != null)) {
        for (String columnName : brokerRequest.getGroupBy().getColumns()) {
          if (!schema.isExisted(columnName)) {
            return true;
          }
        }
      }
      return false;
    }
    // Check selection columns
    if (brokerRequest.getSelections() != null) {
      if (brokerRequest.getSelections().getSelectionColumns() != null) {
        for (String columnName : brokerRequest.getSelections().getSelectionColumns()) {
          if ((!columnName.equalsIgnoreCase("*")) && (!schema.isExisted(columnName))) {
            return true;
          }
        }
      }
      // Check columns to do sorting,
      if (brokerRequest.getSelections().getSelectionSortSequence() != null) {
        for (SelectionSort selectionOrder : brokerRequest.getSelections().getSelectionSortSequence()) {
          if (!schema.isExisted(selectionOrder.getColumn())) {
            return true;
          }
        }
      }
      return false;
    }
    // unsupported query type.
    return true;
  }

  private boolean filterQueryMatchedSchema(Schema schema, FilterQuery filterQuery, FilterQueryMap filterQueryMap) {
    if (filterQuery.getNestedFilterQueryIds() == null || filterQuery.getNestedFilterQueryIds().isEmpty()) {
      return schema.isExisted(filterQuery.getColumn());
    } else {
      for (Integer queryId : filterQuery.getNestedFilterQueryIds()) {
        FilterQuery fq = filterQueryMap.getFilterQueryMap().get(queryId);
        if (!filterQueryMatchedSchema(schema, fq, filterQueryMap)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public void init(Configuration config) {

  }

  @Override
  public String toString() {
    return "DataSchemaSegmentPruner";
  }
}
