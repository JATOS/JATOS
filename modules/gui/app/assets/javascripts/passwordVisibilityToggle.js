export { setPasswordVisibility }

$(".password-visibility-toggle").off("click").on("click", () => {
    const isVisible = $("input[name='newPassword']").attr('type') === "text";
    setPasswordVisibility(!isVisible);
});

function setPasswordVisibility(visible) {
    if (visible) {
        $("input[name='password']").attr('type', 'text');
        $("input[name='newPassword']").attr('type', 'text');
        $("input[name='newPasswordRepeat']").attr('type', 'text');
        $(".password-visibility-toggle i").addClass("bi-eye-fill");
        $(".password-visibility-toggle i").removeClass("bi-eye-slash-fill");
    } else {
        $("input[name='password']").attr('type', 'password');
        $("input[name='newPassword']").attr('type', 'password');
        $("input[name='newPasswordRepeat']").attr('type', 'password');
        $(".password-visibility-toggle i").removeClass("bi-eye-fill");
        $(".password-visibility-toggle i").addClass("bi-eye-slash-fill");
    }
}