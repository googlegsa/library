// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

"use strict";

function loadChartData(chartName, data, title, labels, xlabel, ylabel, y2label,
                       dateFormat) {
  var plot = $.jqplot(chartName, data, {
      title: title,
      series: [
        {showMarker: false, label: labels[0]},
        {showMarker: false, yaxis: 'yaxis', label: labels[1]},
        {showMarker: false, yaxis: 'y2axis', label: labels[2]}],
      legend: {show: true, location: 'nw'},
      axes: {
        xaxis: {
          label: xlabel,
          renderer: $.jqplot.DateAxisRenderer,
          tickOptions: {formatString: dateFormat},
          min: data[0][0][0],
          max: data[0][data[0].length - 1][0]
        },
        yaxis: {
          label: ylabel,
          labelRenderer: $.jqplot.CanvasAxisLabelRenderer,
          min: 0
        },
        y2axis: {
          label: y2label,
          labelRenderer: $.jqplot.CanvasAxisLabelRenderer,
          min: 0
        }
      },
      highlighter: {
        show: true,
        sizeAdjust: 5
      }
  });
  return plot;
}

function formatChartData(stats, timeResolution) {
  var data = {
    responsesAvg: [],
    responsesMax: [],
    responsesCount: [],
    responsesThroughput: [],
    processingsAvg: [],
    processingsMax: [],
    processingsCount: [],
    processingsThroughput: []
  };
  $.each(stats.statData, function(key, val) {
    var time = new Date(val.time);
    // Add half of timeResolution to prevent snapshotDuration == 0 and to
    // improve average accuracy.
    var snapshotDuration = Math.min(stats.snapshotDuration,
        (stats.currentTime + timeResolution / 2) - val.time);
    if (val.requestResponsesCount != 0) {
      data.responsesAvg.push([time,
          val.requestResponsesDurationSum / val.requestResponsesCount]);
    } else {
      data.responsesAvg.push([time, 0]);
    }
    data.responsesMax.push([time, val.requestResponsesMaxDuration]);
    data.responsesCount.push([time,
        val.requestResponsesCount / (snapshotDuration / 1000)]);
    data.responsesThroughput.push([time,
        val.requestResponsesThroughput / (snapshotDuration / 1000) / 1024]);
    if (val.requestProcessingsCount != 0) {
      data.processingsAvg.push([time,
          val.requestProcessingsDurationSum / val.requestProcessingsCount]);
    } else {
      data.processingsAvg.push([time, 0]);
    }
    data.processingsMax.push([time, val.requestProcessingsMaxDuration]);
    data.processingsCount.push([time,
        val.requestProcessingsCount / (snapshotDuration / 1000)]);
    data.processingsThroughput.push([time,
        val.requestProcessingsThroughput / (snapshotDuration / 1000) / 1024]);
  });
  return data;
}

function processData(data) {
  $('#gaf-numTotalDocIdsPushed').html(data.simpleStats.numTotalDocIdsPushed);
  $('#gaf-numUniqueDocIdsPushed').html(data.simpleStats.numUniqueDocIdsPushed);
  $('#gaf-numTotalGsaRequests').html(data.simpleStats.numTotalGsaRequests);
  $('#gaf-numUniqueGsaRequests').html(data.simpleStats.numUniqueGsaRequests);
  $('#gaf-numTotalNonGsaRequests').html(
      data.simpleStats.numTotalNonGsaRequests);
  $('#gaf-numUniqueNonGsaRequests').html(
      data.simpleStats.numUniqueNonGsaRequests);
  $('#gaf-whenStarted').html(String(new Date(data.simpleStats.whenStarted)));
  $('#gaf-log').html(data.log);

  var configTable = $('#gaf-config-table');
  var keys = [];
  var key;
  for (key in data.config) {
    keys.push(key);
  }
  keys.sort();
  var tr, td, i;
  for (i = 0; i < keys.length; i++) {
    key = keys[i];
    tr = document.createElement('tr');
    td = document.createElement('td');
    td.appendChild(document.createTextNode(key));
    tr.appendChild(td);
    td = document.createElement('td');
    td.appendChild(document.createTextNode('= ' + data.config[key]));
    tr.appendChild(td);
    configTable.append(tr);
  }

  var vals = [];
  vals.push(formatChartData(data.stats[0], data.simpleStats.timeResolution));
  vals.push(formatChartData(data.stats[1], data.simpleStats.timeResolution));
  vals.push(formatChartData(data.stats[2], data.simpleStats.timeResolution));
  loadChartData('gaf-responses-chart-minute',
      [vals[0].responsesAvg, vals[0].responsesMax, vals[0].responsesCount],
      'Last Minute', ['Average', 'Max', 'Rate'], 'Time Period',
      'Duration (ms)', 'Requests/s', '%#I:%M:%S %p');
  loadChartData('gaf-responses-chart-hour',
      [vals[1].responsesAvg, vals[1].responsesMax, vals[1].responsesCount],
      'Last Hour', ['Average', 'Max', 'Rate'], 'Time Period',
      'Duration (ms)', 'Requests/s', '%#I:%M %p');
  loadChartData('gaf-responses-chart-day',
      [vals[2].responsesAvg, vals[2].responsesMax, vals[2].responsesCount],
      'Last Day', ['Average', 'Max', 'Rate'], 'Time Period',
      'Duration (ms)', 'Requests/s', '%#I:%M %p');
  loadChartData('gaf-processings-chart-minute',
      [vals[0].processingsAvg, vals[0].processingsMax,
          vals[0].processingsCount],
      'Last Minute', ['Average', 'Max', 'Rate'], 'Time Period',
      'Duration (ms)', 'Requests/s', '%#I:%M:%S %p');
  loadChartData('gaf-processings-chart-hour',
      [vals[1].processingsAvg, vals[1].processingsMax,
          vals[1].processingsCount],
      'Last Hour', ['Average', 'Max', 'Rate'], 'Time Period',
      'Duration (ms)', 'Requests/s', '%#I:%M %p');
  loadChartData('gaf-processings-chart-day',
      [vals[2].processingsAvg, vals[2].processingsMax,
          vals[2].processingsCount],
      'Last Day', ['Average', 'Max', 'Rate'], 'Time Period',
      'Duration (ms)', 'Requests/s', '%#I:%M %p');
  loadChartData('gaf-throughput-chart-minute',
      [vals[0].processingsThroughput, vals[0].responsesThroughput],
      'Last Minute', ['Processing', 'Response'], 'Time Period',
      'Throughput (KiB/s)', null, '%#I:%M %p');
  loadChartData('gaf-throughput-chart-hour',
      [vals[1].processingsThroughput, vals[1].responsesThroughput],
      'Last Hour', ['Processing', 'Response'], 'Time Period',
      'Throughput (KiB/s)', null, '%#I:%M %p');
  loadChartData('gaf-throughput-chart-day',
      [vals[2].processingsThroughput, vals[2].responsesThroughput],
      'Last Day', ['Processing', 'Response'], 'Time Period',
      'Throughput (KiB/s)', null, '%#I:%M %p');
}

function rpc(method, params, id, callback) {
  var request = {method: method, params: params, id: id};
  $.ajax({
    accepts: 'application/json',
    contentType: 'application/json',
    data: JSON.stringify(request),
    processData: false,
    type: 'POST',
    url: '/rpc',
    success: function(data) {
      callback(data.result, data.error);
    },
  });
}

function startFeedPush() {
  var sending = $('#gaf-start-feed-push-sending');
  sending.show();
  rpc('startFeedPush', null, null, function(result, error) {
    sending.hide();
    var notificationSpan;
    if (result !== null) {
      notificationSpan = $('#gaf-start-feed-push-success');
      notificationSpan.text('Feed push started');
    } else {
      if (error === null) {
        error = 'Invalid response from server';
      }
      notificationSpan = $('#gaf-start-feed-push-error');
      notificationSpan.text(error);
    }
    notificationSpan.show();
    window.setTimeout(function() {
      notificationSpan.fadeOut();
    }, 5000);
  });
}

$(document).ready(function() {
  $.getJSON('/stat', processData);
  $('#gaf-start-feed-push').click(startFeedPush);
});
