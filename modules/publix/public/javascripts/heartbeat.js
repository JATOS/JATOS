"use strict";
(() => {
  // src/workers/heartbeat.js
  /*!
   * heartbeat.js
   *
   * Web worker used by jatos.js to send periodic HTTP requests to the JATOS
   * server. This study-run heartbeat is separate from the channel heartbeats.
   *
   * http://www.jatos.org
   * Author Kristian Lange
   * Licensed under Apache License 2.0
   */
  var periodDefault = 6e4;
  var period;
  var url;
  var xhr;
  onmessage = function(e) {
    const studyResultUuid = e.data[0];
    period = typeof e.data[1] === "undefined" ? periodDefault : e.data[1];
    url = "../../../../" + studyResultUuid + "/heartbeat";
    if (!xhr) {
      send();
    }
  };
  function send() {
    xhr = new XMLHttpRequest();
    xhr.open("POST", url);
    xhr.setRequestHeader("Content-Type", "text/plain");
    xhr.onload = function() {
      setTimeout(function() {
        send();
      }, period);
    };
    xhr.send();
  }
})();
