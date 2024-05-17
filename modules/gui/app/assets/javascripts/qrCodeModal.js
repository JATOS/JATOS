export { show }

$(".btn-qr-code").on("click", show);

// Use this function to add an event handler manually
function show() {
    const button = this;
    const url = button.parentElement.textContent.trim();

    $('#qrcode').empty();
    $('#qrCodeModal').off('shown.bs.modal');
    $('#qrCodeModalStudyLink').html(url);

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