/*
 * JATOS customizations of Google sign-in. Google's library is included in main.scala.html.
 */

export { drawButton, drawUserImg }

import * as Helpers from "./helpers.js";

let initialized = false;
let buttonWidth = 300;

document.addEventListener('DOMContentLoaded', () => drawButton());
document.addEventListener("colorThemeChange", () => drawButton());

/*
 * Properly draw the Google sign-in button (using theme color and width)
 */
function drawButton(width) {
    if (typeof google !== "object") return;
    if (!initialized) {
        google.accounts.id.initialize({
            client_id: window.common.oauthGoogleClientId,
            ux_mode: "redirect",
            login_uri: window.common.realHostUrl + window.routes.SigninGoogle.signin,
            use_fedcm_for_prompt: true
        });
        initialized = true;
    }
    if (Number.isInteger(width)) buttonWidth = width;
    const googleTheme = Helpers.getTheme() === "dark" ? "filled_black" : "outline";
    google.accounts.id.renderButton(document.getElementById("signinGoogle"), {
            theme: googleTheme,
            size: "large",
            width: buttonWidth
    });
}

/*
 * Load Google user picture (if signed in by Google). Get the picture URL from the Google cookie.
 */
function drawUserImg() {
    if (!window.signedinUser || !window.signedinUser.isOauthGoogle) return
    const googlePictureUrl = window.common.googlePictureUrl;
    if (googlePictureUrl) {
        $('.google-user-img').one(); // Ignore imgage loading errors
        $('.google-user-img').attr("src", googlePictureUrl);
        $(".google-user-img").removeClass("d-none");
        $("minidenticon-svg").addClass("d-none");
    }
}
