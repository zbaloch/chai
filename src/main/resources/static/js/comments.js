// Comments: the author's Edit opens the comment in an editor right where it is; Cancel puts it back.
(function () {
    if (window.ChaiComments) return;
    window.ChaiComments = true;

    function close(comment) {
        var form = comment.querySelector('form[data-editing]');
        if (form) form.remove();
        comment.querySelector('[data-comment-body]').hidden = false;
        comment.querySelector('[data-comment-edit]').hidden = false;
    }

    document.addEventListener('click', function (e) {
        var edit = e.target.closest('[data-comment-edit]');
        if (edit) {
            var comment = edit.closest('[data-comment]');
            var template = comment && comment.querySelector('template[data-comment-edit-template]');
            if (!template || comment.querySelector('form[data-editing]')) return;
            var form = template.content.firstElementChild.cloneNode(true);
            form.dataset.editing = 'true';
            var body = comment.querySelector('[data-comment-body]');
            body.hidden = true;
            edit.hidden = true;
            body.after(form);
            var editor = form.querySelector('lexxy-editor');
            setTimeout(function () { if (editor && editor.focus) editor.focus(); }, 50);
            return;
        }
        var cancel = e.target.closest('[data-comment-edit-cancel]');
        if (cancel) close(cancel.closest('[data-comment]'));
    });

    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape') return;
        var form = e.target.closest && e.target.closest('form[data-editing]');
        if (form) close(form.closest('[data-comment]'));
    });
})();
