/*
 * Shows QR codes in a Modal. Uses easy.qrcode.js included in main.scala.html.
 * The Modal is defined in qrCodeModal.scala.html.
 */

export { show }

import * as Helpers from "./helpers.js";

// Add a click event listener to every ".btn-qr-code". But we need the ".off" in case this script is
// imported multiple times on the same page
$(".btn-qr-code").off("click").on("click", show);

/*
 * Use this function to add an event listener manually, e.g. for dynamically generated elements
 */
function show() {
    const button = this;
    const url = button.parentElement.textContent.trim();

    $('#qrcode').empty();
    $('#qrCodeModal').off('shown.bs.modal');
    Helpers.generateModalSubtitles("#qrCodeModal", {"Study link": url});

    $('#qrCodeModal').on('shown.bs.modal', function (e) {
        let options = {
            text: url,
            correctLevel: QRCode.CorrectLevel.H
        };
        let qrcode = new QRCode(document.getElementById("qrcode"), options);

        // It's necessary to set the canvas size because EasyQRCodeJS does it wrong
        const width = $("#qrcode").width();
        $('#qrcode canvas').width(width);
        $('#qrcode canvas').height(width);

        const studyCode = url.slice(-11);
        const filename = `jatos-qr-studylink-${studyCode}`;
        $('#qrcode_download').click(() => qrcode.download(filename));
    });
    $('#qrCodeModal').modal('show');
}