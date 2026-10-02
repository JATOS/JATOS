"use strict";
(() => {
  // src/workers/heartbeat.js
  /*!
   * heartbeat.js
   *
   * Web worker used in jatos.js that sends a periodic Ajax request back to the
   * JATOS server. JATOS has two different kinds of heartbeats: this one and the
   * channel heartbeats (not here defined).
   *
   * http://www.jatos.org
   * Author Kristian Lange
   * Licensed under Apache License 2.0
   */
  var periodDefault = 6e4;
  var period;
  var url;
  var ajax;
  onmessage = function(e) {
    const studyResultUuid = e.data[0];
    period = typeof e.data[1] === "undefined" ? periodDefault : e.data[1];
    url = "../../../../" + studyResultUuid + "/heartbeat";
    if (!ajax) {
      send();
    }
  };
  function send() {
    ajax = new XMLHttpRequest();
    ajax.open("POST", url);
    ajax.setRequestHeader("Content-Type", "text/plain");
    ajax.onload = function() {
      setTimeout(function() {
        send();
      }, period);
    };
    ajax.send();
  }
})();
