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

'use strict';

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

function notAvailableInReducedMemMode(statValue) {
  if (statValue < 0) {
    return "Not available because journal.reducedMem is set to true";
  }
  return statValue.toString();
}

function getStatsCallback(result, error) {
  if (result === null) {
    throw error;
  }
  var data = result;

  $('#version-jvm').text(data.versionStats.versionJvm);
  $('#version-adaptor-library').text(data.versionStats.versionAdaptorLibrary);
  $('#type-adaptor').text(data.versionStats.typeAdaptor);
  $('#version-adaptor').text(data.versionStats.versionAdaptor);

  $('#gaf-incremental-feed-push').attr(
      'disabled',
      !data.simpleStats.isIncrementalSupported);

  $('#gaf-num-total-doc-ids-pushed').text(
      data.simpleStats.numTotalDocIdsPushed);
  $('#gaf-num-unique-doc-ids-pushed').text(
      notAvailableInReducedMemMode(data.simpleStats.numUniqueDocIdsPushed));
  $('#gaf-num-total-gsa-requests').text(data.simpleStats.numTotalGsaRequests);
  $('#gaf-num-unique-gsa-requests').text(
      notAvailableInReducedMemMode(data.simpleStats.numUniqueGsaRequests));
  $('#gaf-num-total-non-gsa-requests').text(
      data.simpleStats.numTotalNonGsaRequests);
  $('#gaf-num-unique-non-gsa-requests').text(
      notAvailableInReducedMemMode(data.simpleStats.numUniqueNonGsaRequests));
  $('#gaf-when-started').text(String(new Date(data.simpleStats.whenStarted)));
  $('#gaf-time-resolution').text(data.simpleStats.timeResolution);

  var hadSuccessfulFullPush = Boolean(
      data.simpleStats.lastSuccessfulFullPushStart);
  $('#gaf-last-successful-full-push-start').text(
      hadSuccessfulFullPush
      ? String(new Date(data.simpleStats.lastSuccessfulFullPushStart))
      : "None yet");
  $('#gaf-last-successful-full-push-end').text(
      hadSuccessfulFullPush
      ? String(new Date(data.simpleStats.lastSuccessfulFullPushEnd))
      : "None yet");
  var curFullPushStart = data.simpleStats.currentFullPushStart;
  $('#gaf-current-full-push').text(
      curFullPushStart
      ? "Started " + String(new Date(curFullPushStart))
      : "None in progress");

  var hadSuccessfulIncrementalPush = Boolean(
      data.simpleStats.lastSuccessfulIncrementalPushStart);
  $('#gaf-last-successful-incr-push-start').text(
      hadSuccessfulIncrementalPush
      ? String(new Date(data.simpleStats.lastSuccessfulIncrementalPushStart))
      : "None yet");
  $('#gaf-last-successful-incr-push-end').text(
      hadSuccessfulIncrementalPush
      ? String(new Date(data.simpleStats.lastSuccessfulIncrementalPushEnd))
      : "None yet");
  var curIncrementalPushStart = data.simpleStats.currentIncrementalPushStart;
  $('#gaf-current-incr-push').text(
      curIncrementalPushStart
      ? "Started " + String(new Date(curIncrementalPushStart))
      : "None in progress");

  var vals = [];
  vals.push(formatChartData(data.stats[0], data.simpleStats.timeResolution));
  vals.push(formatChartData(data.stats[1], data.simpleStats.timeResolution));
  vals.push(formatChartData(data.stats[2], data.simpleStats.timeResolution));
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
      [vals[0].processingsThroughput],
      'Last Minute', ['Response'], 'Time Period',
      'Throughput (KiB/s)', null, '%#I:%M %p');
  loadChartData('gaf-throughput-chart-hour',
      [vals[1].processingsThroughput],
      'Last Hour', ['Response'], 'Time Period',
      'Throughput (KiB/s)', null, '%#I:%M %p');
  loadChartData('gaf-throughput-chart-day',
      [vals[2].processingsThroughput],
      'Last Day', ['Response'], 'Time Period',
      'Throughput (KiB/s)', null, '%#I:%M %p');
}

var xsrfToken;
var XSRF_TOKEN_HEADER_NAME = 'X-Adaptor-XSRF-Token';
var rpcRetrievingXsrfToken = false;
var pendingRpcRequests = [];

function rpc(method, params, callback) {
  // If we don't yet have an XSRF token, queue the real RPC request and issue a
  // request to retrieve the XSRF token. After obtaining the XSRF token, perform
  // the queued RPC requests.
  if (!xsrfToken) {
    pendingRpcRequests[pendingRpcRequests.length] = function() {
      rpc(method, params, callback);
    };
    if (!rpcRetrievingXsrfToken) {
      rpcRetrievingXsrfToken = true;
      $.ajax({
        type: 'POST',
        url: '../rpc',
        success: function() {
          // This should never happen.
          rpcRetrievingXsrfToken = false;
          throw 'Could not retrieve XSRF token';
        },
        error: function(xmlHttpRequest) {
          // Handle expected 409 Conflict response from server.
          rpcRetrievingXsrfToken = false;
          var token = xmlHttpRequest.getResponseHeader(XSRF_TOKEN_HEADER_NAME);
          if (!token) {
            throw 'Could not retrieve XSRF token';
          }

          xsrfToken = token;
          for (var x = 0; x < pendingRpcRequests.length; x++) {
            pendingRpcRequests[x]();
          }
        }
      });
    }
    return;
  }

  var request = {method: method, params: params, id: null};
  var headers = {};
  headers[XSRF_TOKEN_HEADER_NAME] = xsrfToken;
  $.ajax({
    accepts: 'application/json',
    contentType: 'application/json',
    data: JSON.stringify(request),
    processData: false,
    type: 'POST',
    url: '../rpc',
    headers: headers,
    success: function(data) {
      callback(data.result, data.error);
    },
    error: function(xmlHttpRequest, textStatus) {
      callback(null, textStatus);
    }
  });
}

function startFeedPush() {
  var sending = $('#gaf-start-feed-push-sending');
  sending.show();
  rpc('startFeedPush', null, function(result, error) {
    sending.hide();
    if (result === null) {
      alert("Disconnected from adaptor.  Press the 'OK' button to return to "
            + "the login page.");
      location.reload();
      throw error !== null ? error : "Invalid response from server";
    }
    var notificationSpan = result ? $('#gaf-start-feed-push-success')
        : $('#gaf-start-feed-push-already-running');
    notificationSpan.show();
    window.setTimeout(function() {
      notificationSpan.fadeOut();
    }, 5000);
  });
}

function startIncrementalFeedPush() {
  var sending = $('#gaf-incremental-feed-push-sending');
  sending.show();
  rpc('startIncrementalFeedPush', null, function(result, error) {
    sending.hide();
    if (result === null) {
      alert("Disconnected from adaptor.  Press the 'OK' button to return to "
            + "the login page.");
      location.reload();
      throw error !== null ? error : "Invalid response from server";
    }
    var notificationSpan = result ? $('#gaf-incremental-feed-push-success')
        : $('#gaf-incremental-feed-push-already-running');
    notificationSpan.show();
    window.setTimeout(function() {
      notificationSpan.fadeOut();
    }, 5000);
  });
}

// TODO(myk): button removed - code to follow suit
function checkConfig() {
  var sending = $('#gaf-check-config-sending');
  sending.show();
  rpc('checkForUpdatedConfig', null, function(result, error) {
    sending.hide();
    if (result === null) {
      throw error !== null ? error : "Invalid response from server";
    }
    var notificationSpan = result ? $('#gaf-check-config-updated')
        : $('#gaf-check-config-not-updated');
    notificationSpan.show();
    window.setTimeout(function() {
      notificationSpan.fadeOut();
    }, 5000);
    if (result) {
      // Config was updated; auto-update displayed config.
      rpc('getConfig', null, getConfigCallback);
    }
  });
}

function encodeSensitiveValue() {
  var valuesArray = $('#gaf-sec-form').serializeArray();
  var values = {};
  for (var i = 0; i < valuesArray.length; i++) {
    values[valuesArray[i].name] = valuesArray[i].value;
  }
  var encvalue = $('#gaf-sec-encoded');
  encvalue.val("");
  var processing = $('#gaf-sec-processing');
  processing.show();
  rpc('encodeSensitiveValue', [values.secvalue, values.sectype],
      function(result, error) {
        processing.hide();
        if (result === null) {
          alert("Disconnected from adaptor.  Press the 'OK' button to return "
              + "to the login page.");
          location.reload();
          throw error !== null ? error : "Invalid response from server";
        }
        encvalue.val(result);
        encvalue.select();
      });
  // Cancel submitting of form.
  return false;
}

function getLogCallback(result, error) {
  if (result === null) {
    throw error;
  } else {
    var gafLog = $('#gaf-log');
    gafLog.empty();
    gafLog.append(document.createTextNode(result));
  }
}

function getConfigCallback(result, error) {
  if (result === null) {
    throw error;
  } else {
    var configTable = $('#gaf-config-table');
    configTable.empty();
    var keys = [];
    var key;
    for (key in result) {
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
      td.appendChild(document.createTextNode('= ' + result[key]));
      tr.appendChild(td);
      configTable.append(tr);
    }
  }
}

function getStatusesCallback(result, error) {
  if (result === null)
    throw error;

  var codeToClassMap = {
    UNAVAILABLE: 'gaf-status-unavailable',
    INACTIVE: 'gaf-status-inactive',
    NORMAL: 'gaf-status-normal',
    WARNING: 'gaf-status-warning',
    ERROR: 'gaf-status-error'
  };

  var codeToDefaultDescrMap = {
    UNAVAILABLE: 'Unavailable',
    INACTIVE: 'Status checking inactive',
    NORMAL: 'Normal',
    WARNING: 'Warning',
    ERROR: 'Error'
  };

  var statuses = result;
  var statusTable = $('#gaf-status-table');
  var curStatus, tr, td, led, message;
  for (var i = 0; i < statuses.length; i++) {
    curStatus = statuses[i];
    tr = document.createElement('tr');
    td = document.createElement('td');
    td.appendChild(document.createTextNode(curStatus.source));
    tr.appendChild(td);
    td = document.createElement('td');
    led = document.createElement('span');
    led.className = codeToClassMap[curStatus.code];
    td.appendChild(led);
    message = curStatus.message;
    if (!message) {
      message = codeToDefaultDescrMap[curStatus.code];
    }
    td.appendChild(document.createTextNode(message));
    tr.appendChild(td);
    statusTable.append(tr);
  }
}

function isEncryptionSupportedCallback(result, error) {
  if (result === null) {
    $('#gaf-sec-type-pkc').prop('disabled', true);
    $('#gaf-sec-enc-unavailable').show();
  }
}

$(document).ready(function() {
  rpc('getStatuses', null, getStatusesCallback);
  rpc('getStats', null, getStatsCallback);
  rpc('getConfig', null, getConfigCallback);
  rpc('getLog', null, getLogCallback);
  rpc('encodeSensitiveValue', ["", "ENCRYPTED"], isEncryptionSupportedCallback);
  $('#gaf-incremental-feed-push').click(startIncrementalFeedPush);
  $('#gaf-start-feed-push').click(startFeedPush);
  $('#gaf-sec-runenc').click(encodeSensitiveValue);
});
