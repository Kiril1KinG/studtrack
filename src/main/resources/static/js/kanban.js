(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initKanbanSortable(document);
    });

    document.body.addEventListener('htmx:afterSwap', function (evt) {
        var target = evt.detail && evt.detail.target;
        if (!target) return;

        if (target.id === 'kanban-board' || (target.querySelector && target.querySelector('#kanban-board'))) {
            initKanbanSortable(document);
        }
    });

    document.body.addEventListener('htmx:afterSettle', function () {
        initKanbanSortable(document);
    });

    function initKanbanSortable(root) {
        var taskLists = root.querySelectorAll ? root.querySelectorAll('.task-list') : [];
        if (!taskLists.length) return;

        taskLists.forEach(function (list) {
            if (list.dataset.sortableInitialized === 'true') return;

            new Sortable(list, {
                group: 'tasks',
                animation: 150,
                ghostClass: 'bg-light',
                onEnd: handleDragEnd
            });

            list.dataset.sortableInitialized = 'true';
        });
    }

    function handleDragEnd(evt) {
        var taskId    = evt.item.dataset.taskId;
        var projectId = evt.item.dataset.projectId;
        var newStatus = evt.to.dataset.status;
        var oldStatus = evt.from.dataset.status;
        var reviewLocked = evt.item.dataset.reviewLocked === 'true';

        if (!taskId || !projectId || !newStatus) return;

        if (newStatus === oldStatus) return;

        if (reviewLocked) {
            revertCard(evt);
            window.showToast('Задача заблокирована: идёт раунд ревью', 'warning');
            return;
        }

        fetch('/api/projects/' + projectId + '/tasks/' + taskId + '/status', {
            method: 'PATCH',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({status: newStatus})
        })
        .then(function (response) {
            if (response.ok) {
                refreshKanbanBoard(projectId);
                return;
            }

            return response.json()
                .then(function (data) {
                    revertCard(evt);
                    var msg = data.message || data.error || ('Ошибка ' + response.status);
                    window.showToast(msg, 'danger');
                })
                .catch(function () {
                    revertCard(evt);
                    window.showToast('Ошибка ' + response.status + ': ' + response.statusText, 'danger');
                });
        })
        .catch(function (err) {
            revertCard(evt);
            window.showToast('Ошибка сети: ' + err.message, 'danger');
        });
    }

    function refreshKanbanBoard(projectId) {
        if (window.htmx) {
            window.htmx.ajax('GET', '/projects/' + projectId + '/board', {
                target: '#kanban-board',
                swap: 'outerHTML'
            });
            return;
        }
        window.location.reload();
    }

    function revertCard(evt) {
        var refNode = evt.from.children[evt.oldIndex] || null;
        evt.from.insertBefore(evt.item, refNode);
    }

}());
