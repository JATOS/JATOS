import jquery from "jquery";
import "./index.js";

/** @deprecated Compatibility API for existing studies; omitted from slim builds. */
window.jatos.jQuery = jquery;
jquery.ajaxSetup({cache: true});
