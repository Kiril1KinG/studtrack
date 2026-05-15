window.showToast = function (message, type) {
    type = type || 'danger';

    var toastEl = document.getElementById('app-toast');
    if (!toastEl) {
        alert(message);
        return;
    }

    document.getElementById('toast-message').textContent = message;
    toastEl.className = 'toast align-items-center border-0 text-bg-' + type;

    bootstrap.Toast.getOrCreateInstance(toastEl, {delay: 5000}).show();
};
