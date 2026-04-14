document.addEventListener('DOMContentLoaded', function() {
    // Инициализация drag-and-drop для всех колонок задач
    const taskLists = document.querySelectorAll('.task-list');
    taskLists.forEach(list => {
        new Sortable(list, {
            group: 'tasks',
            animation: 150,
            ghostClass: 'bg-light',
            onEnd: function(evt) {
                const taskId = evt.item.dataset.taskId;
                const newStatus = evt.to.dataset.status;
                if (!taskId || !newStatus) return;

                fetch(`/api/tasks/${taskId}/status`, {
                    method: 'PATCH',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({status: newStatus})
                }).then(response => {
                    if (!response.ok) {
                        alert('Не удалось изменить статус задачи');
                        // Можно откатить перемещение, но для простоты оставим так
                    }
                });
            }
        });
    });
});