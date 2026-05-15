document.body.addEventListener('htmx:configRequest', function (evt) {
    var tokenMeta  = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    if (tokenMeta && headerMeta) {
        evt.detail.headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
});
