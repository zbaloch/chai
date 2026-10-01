// Chat: one live connection per tab (kept across Turbolinks visits), the "something new" dots,
// and the chat page itself — live messages, the "New" line, and @mentions in the composer.
(function () {
    if (window.ChaiChats) return;
    window.ChaiChats = true;

    var GROUP_MILLIS = 5 * 60 * 1000;

    var client = null;
    var connected = false;
    var connecting = false;
    var everConnected = false;
    var retryDelay = 1000;
    var outbox = [];
    var room = null; // the chat on screen

    function byId(id) { return document.getElementById(id); }

    function request(method, url, body) {
        var token = document.querySelector('meta[name="csrf-token"]');
        var headers = { 'Accept': 'application/json', 'X-CSRF-TOKEN': token ? token.content : '' };
        if (body) headers['Content-Type'] = 'application/json';
        return fetch(url, {
            method: method,
            credentials: 'same-origin',
            headers: headers,
            body: body ? JSON.stringify(body) : undefined
        });
    }

    // ---- The connection ----

    function connect() {
        var nav = byId('nav-chats');
        if (connected || connecting || !nav || !window.SockJS || !window.Stomp) return;
        connecting = true;
        client = Stomp.over(new SockJS(nav.dataset.wsUrl));
        client.debug = null;
        client.connect({}, function () {
            var reconnected = everConnected;
            connected = true;
            connecting = false;
            everConnected = true;
            retryDelay = 1000;
            client.subscribe('/user/queue/chats', function (frame) { onSignal(JSON.parse(frame.body)); });
            // Follow the chat before sending what was typed while offline, so it shows up here too
            joinRoom();
            while (outbox.length) client.send('/app/chat.send', {}, outbox.shift());
            if (reconnected) {
                // Messages may have come and gone while we were away
                refreshDots();
                if (room && window.Turbolinks) Turbolinks.visit(location.href, { action: 'replace' });
            }
        }, function () {
            connected = false;
            connecting = false;
            if (room) room.subscription = null;
            setTimeout(connect, retryDelay);
            retryDelay = Math.min(retryDelay * 2, 30000);
        });
    }

    // ---- Dots ----

    function setNavDot(on) {
        var dot = document.querySelector('#nav-chats [data-chat-dot]');
        if (dot) dot.hidden = !on;
    }

    function setProjectDot(projectId, on) {
        document.querySelectorAll('[data-chat-dot-project="' + projectId + '"]').forEach(function (dot) { dot.hidden = !on; });
    }

    function refreshDots() {
        var nav = byId('nav-chats');
        if (!nav || !window.fetch) return;
        fetch(nav.dataset.unreadUrl, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (unread) {
                if (!unread) return;
                setNavDot(unread.chats);
                document.querySelectorAll('[data-chat-dot-project]').forEach(function (dot) {
                    dot.hidden = unread.projects.indexOf(Number(dot.dataset.chatDotProject)) < 0;
                });
            })
            .catch(function () {});
    }

    // A quiet dot in the tab's title when something arrives while you're elsewhere
    function setTitleDot(on) {
        var title = document.title.replace(/^• /, '');
        document.title = on ? '• ' + title : title;
    }

    // Someone posted in one of your chats (never sent for your own messages or muted chats)
    function onSignal(signal) {
        if (room && room.id === String(signal.roomId)) return; // you're looking at it
        if (signal.projectId) {
            setProjectDot(signal.projectId, true);
        } else {
            setNavDot(true);
            updateListRow(signal);
        }
        if (document.hidden) setTitleDot(true);
    }

    // On the Chats page, bring the chat to the top with its dot and latest line
    function updateListRow(signal) {
        var list = byId('chat-list');
        var row = document.querySelector('[data-chat-room="' + signal.roomId + '"]');
        if (!row) {
            if ((list || byId('chat-list-empty')) && window.Turbolinks) Turbolinks.visit(location.href, { action: 'replace' });
            return;
        }
        row.querySelector('[data-row-dot]').classList.add('bg-red-500');
        var title = row.querySelector('[data-row-title]');
        title.classList.remove('font-medium', 'text-gray-700');
        title.classList.add('font-semibold', 'text-gray-900');
        row.querySelector('[data-row-preview]').textContent = signal.preview;
        row.querySelector('[data-row-when]').textContent = signal.when;
        row.parentNode.prepend(row);
    }

    // ---- The chat page ----

    function setUpRoom() {
        var el = byId('chat-room');
        if (!el) return;
        room = {
            id: el.dataset.roomId,
            me: el.dataset.me,
            canModerate: el.dataset.canModerate === 'true',
            readUrl: el.dataset.readUrl,
            deleteUrl: el.dataset.deleteUrl,
            people: JSON.parse(el.dataset.people || '[]'),
            list: byId('chat-window'),
            input: byId('chat-input'),
            mentionBox: byId('chat-mentions'),
            mention: null,
            subscription: null,
            unseen: false,
            pendingRead: false,
            readTimer: null
        };

        byId('chat-composer').addEventListener('submit', function (e) { e.preventDefault(); send(); });
        room.input.addEventListener('keydown', onComposerKey);
        room.input.addEventListener('input', function () { autosize(); updateMentions(); });
        room.input.addEventListener('click', updateMentions);
        room.input.addEventListener('blur', function () { setTimeout(closeMentions, 150); });
        room.list.addEventListener('click', onListClick);

        scrollToStart();
        if (connected) joinRoom(); else connect();
    }

    function joinRoom() {
        if (!room || !connected || room.subscription) return;
        room.subscription = client.subscribe('/topic/chat/' + room.id, function (frame) { onRoomEvent(JSON.parse(frame.body)); });
    }

    function leaveRoom() {
        if (room && room.subscription) {
            try { room.subscription.unsubscribe(); } catch (e) { /* already gone */ }
        }
        if (room) clearTimeout(room.readTimer);
        room = null;
    }

    function onRoomEvent(event) {
        if (!room) return;
        if (event.type === 'deleted') return removeMessage(event.id);
        if (event.type === 'edited') return showEdit(event);
        if (event.type !== 'message' || byId('chat_message_' + event.id)) return;

        var mine = String(event.senderId) === room.me;
        var stick = mine || nearBottom();
        if (!mine && document.hidden && !room.unseen) {
            placeNewLine();
            room.unseen = true;
        }
        appendMessage(event);
        if (stick) scrollToBottom();
        if (!mine) {
            if (document.hidden) {
                room.pendingRead = true;
                setTitleDot(true);
            } else {
                markRead();
            }
        }
    }

    function lastMessage() {
        var all = room.list.querySelectorAll('.chat-message');
        return all.length ? all[all.length - 1] : null;
    }

    function appendMessage(m) {
        var empty = byId('chat-empty');
        if (empty) empty.remove();

        var last = lastMessage();
        if (!last || last.dataset.day !== m.day) {
            var day = byId('chat-day-template').content.firstElementChild.cloneNode(true);
            day.querySelector('[data-label]').textContent = m.dayLabel;
            day.dataset.day = m.day;
            room.list.appendChild(day);
        }

        var previous = room.list.lastElementChild;
        var li = byId('chat-message-template').content.firstElementChild.cloneNode(true);
        li.id = 'chat_message_' + m.id;
        li.title = m.fullTime;
        li.dataset.senderId = m.senderId;
        li.dataset.createdAt = m.createdAt;
        li.dataset.day = m.day;
        li.dataset.continued = String(!!(previous && previous.classList.contains('chat-message')
            && previous.dataset.senderId === String(m.senderId)
            && m.createdAt - Number(previous.dataset.createdAt) < GROUP_MILLIS));
        if (m.mentionIds.map(String).indexOf(room.me) >= 0) li.classList.add('bg-amber-50');

        var avatar = li.querySelector('.chat-avatar');
        avatar.src = m.avatar;
        avatar.alt = m.senderName;
        avatar.title = m.senderName;
        li.querySelector('[data-name]').textContent = m.senderName;
        li.querySelector('[data-time]').textContent = m.time;
        li.querySelector('[data-body]').innerHTML = m.html; // escaped and formatted by the server

        var mine = String(m.senderId) === room.me;
        var edit = li.querySelector('[data-edit]');
        if (mine) edit.dataset.edit = m.id;
        else edit.remove();
        var remove = li.querySelector('[data-delete]');
        if (mine || room.canModerate) remove.dataset.delete = m.id;
        else remove.remove();

        room.list.appendChild(li);
    }

    function removeMessage(id) {
        var li = byId('chat_message_' + id);
        if (!li) return;
        var previous = li.previousElementSibling;
        var next = li.nextElementSibling;
        // The next message in the run now needs its own name and face
        if (li.dataset.continued !== 'true' && next && next.classList.contains('chat-message')) next.dataset.continued = 'false';
        li.remove();
        if (previous && previous.hasAttribute('data-day-separator') && (!next || !next.classList.contains('chat-message'))) previous.remove();
    }

    function placeNewLine() {
        var old = room.list.querySelector('[data-new-line]');
        if (old) old.remove();
        room.list.appendChild(byId('chat-new-line-template').content.firstElementChild.cloneNode(true));
    }

    function onListClick(e) {
        if (!room) return;
        var earlier = e.target.closest('[data-load-earlier] button');
        if (earlier) return loadEarlier();
        var edit = e.target.closest('[data-edit]');
        if (edit) return startEdit(byId('chat_message_' + edit.dataset.edit));
        var button = e.target.closest('[data-delete]');
        if (!button) return;
        if (!confirm('Delete this message?')) return;
        var id = button.dataset.delete;
        request('DELETE', room.deleteUrl + id)
            .then(function (r) { if (r.ok) removeMessage(id); })
            .catch(function () {});
    }

    // ---- Earlier messages ----

    // Adds the page before the oldest message shown, keeping what you're looking at in place
    function loadEarlier() {
        var item = room.list.querySelector('[data-load-earlier]');
        if (!item || item.dataset.loading) return Promise.resolve(false);
        item.dataset.loading = 'true';
        var list = room.list;
        return fetch(item.querySelector('button').dataset.url, { credentials: 'same-origin', headers: { 'Accept': 'text/html' } })
            .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); })
            .then(function (html) {
                if (!room || room.list !== list) return false;
                var page = document.createElement('template');
                page.innerHTML = html.trim();
                var firstOld = item.nextElementSibling;
                var heightBefore = list.scrollHeight;
                item.remove();
                list.insertBefore(page.content, firstOld);
                joinPages(firstOld);
                list.scrollTop += list.scrollHeight - heightBefore;
                return true;
            })
            .catch(function () { delete item.dataset.loading; return false; });
    }

    // Where an earlier page meets the one that was first: one day divider, and runs carry on
    function joinPages(firstOld) {
        if (!firstOld) return;
        var lastNew = firstOld.previousElementSibling;
        if (!lastNew || !lastNew.classList.contains('chat-message')) return;
        if (firstOld.hasAttribute('data-day-separator') && firstOld.dataset.day === lastNew.dataset.day) {
            var next = firstOld.nextElementSibling;
            firstOld.remove();
            firstOld = next;
        }
        if (firstOld && firstOld.classList.contains('chat-message')
            && firstOld.dataset.senderId === lastNew.dataset.senderId
            && Number(firstOld.dataset.createdAt) - Number(lastNew.dataset.createdAt) < GROUP_MILLIS) {
            firstOld.dataset.continued = 'true';
        }
    }

    // ---- Editing ----

    function startEdit(li) {
        if (!li || li.querySelector('[data-edit-form]')) return;
        var body = li.querySelector('[data-body]');
        var shown = body.parentNode;
        var form = byId('chat-edit-template').content.firstElementChild.cloneNode(true);
        var field = form.querySelector('textarea');
        field.value = body.innerText;
        shown.hidden = true;
        shown.after(form);
        field.focus();
        field.setSelectionRange(field.value.length, field.value.length);
        sizeTo(field);

        function close() { form.remove(); shown.hidden = false; }
        function save() {
            var text = field.value.trim();
            if (!text) return;
            if (text === body.innerText.trim()) return close();
            request('PATCH', room.deleteUrl + li.id.replace('chat_message_', ''), { content: text })
                .then(function (r) { if (r.ok) close(); })
                .catch(function () {});
        }
        form.addEventListener('submit', function (e) { e.preventDefault(); save(); });
        form.querySelector('[data-edit-cancel]').addEventListener('click', close);
        field.addEventListener('input', function () { sizeTo(field); });
        field.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { e.preventDefault(); close(); room && room.input.focus(); }
            if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); save(); }
        });
    }

    // Someone (maybe you) changed a message
    function showEdit(event) {
        var li = byId('chat_message_' + event.id);
        if (!li) return;
        li.querySelector('[data-body]').innerHTML = event.html; // escaped and formatted by the server
        li.querySelector('[data-edited]').hidden = false;
        li.classList.toggle('bg-amber-50', event.mentionIds.map(String).indexOf(room.me) >= 0);
    }

    // Your latest message, for the Up arrow in an empty composer
    function lastOwnMessage() {
        var all = room.list.querySelectorAll('.chat-message[data-sender-id="' + room.me + '"]');
        return all.length ? all[all.length - 1] : null;
    }

    function send() {
        var text = room.input.value.trim();
        if (!text) return;
        var body = JSON.stringify({ roomId: room.id, content: text });
        if (connected) client.send('/app/chat.send', {}, body);
        else { outbox.push(body); connect(); }
        room.input.value = '';
        autosize();
        closeMentions();
    }

    function markRead() {
        if (!room) return;
        room.pendingRead = false;
        clearTimeout(room.readTimer);
        var url = room.readUrl;
        room.readTimer = setTimeout(function () { request('POST', url).catch(function () {}); }, 600);
    }

    // ---- Scrolling ----

    function nearBottom() {
        var list = room.list;
        return list.scrollHeight - list.scrollTop - list.clientHeight < 120;
    }

    function scrollToBottom() {
        room.list.scrollTop = room.list.scrollHeight;
    }

    // Open at a linked message (from a notification or search), else where you left off, else the latest.
    // A linked message older than the first page loads earlier pages until it's there.
    function scrollToStart(tries) {
        var wanted = /^#chat_message_\d+$/.test(location.hash);
        var linked = wanted ? document.querySelector(location.hash) : null;
        if (wanted && !linked && (tries || 0) < 30 && room.list.querySelector('[data-load-earlier]')) {
            var list = room.list;
            return loadEarlier().then(function (loaded) { if (loaded && room && room.list === list) scrollToStart((tries || 0) + 1); });
        }
        var target = linked || room.list.querySelector('[data-new-line]');
        if (!target) return scrollToBottom();
        room.list.scrollTop += target.getBoundingClientRect().top - room.list.getBoundingClientRect().top - (linked ? room.list.clientHeight / 3 : 8);
        if (linked) {
            linked.classList.add('bg-sky-50');
            setTimeout(function () { linked.classList.remove('bg-sky-50'); }, 2000);
        }
    }

    // ---- The composer ----

    function autosize() {
        sizeTo(room.input);
    }

    function sizeTo(field) {
        field.style.height = 'auto';
        field.style.height = Math.min(field.scrollHeight, 160) + 'px';
    }

    function onComposerKey(e) {
        if (room.mention) {
            var count = room.mention.matches.length;
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                room.mention.active = (room.mention.active + (e.key === 'ArrowDown' ? 1 : count - 1)) % count;
                return renderMentions();
            }
            if (e.key === 'Enter' || e.key === 'Tab') {
                e.preventDefault();
                return pickMention(room.mention.active);
            }
            if (e.key === 'Escape') {
                e.preventDefault();
                return closeMentions();
            }
        }
        // Up in an empty composer edits your last message
        if (e.key === 'ArrowUp' && !room.input.value && lastOwnMessage()) {
            e.preventDefault();
            var own = lastOwnMessage();
            own.scrollIntoView({ block: 'nearest' });
            return startEdit(own);
        }
        // Enter sends; Shift+Enter starts a new line
        if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
            e.preventDefault();
            send();
        }
    }

    // "@ay" right before the caret → suggest people whose first or last name starts with "ay"
    function updateMentions() {
        var input = room.input;
        var before = input.value.slice(0, input.selectionStart);
        var match = /(^|\s)@([^\s@]{0,30})$/.exec(before);
        if (!match) return closeMentions();
        var query = match[2].toLowerCase();
        var matches = room.people.filter(function (person) {
            if (String(person.id) === room.me) return false;
            return !query || person.firstName.toLowerCase().indexOf(query) === 0
                || person.lastName.toLowerCase().indexOf(query) === 0
                || person.name.toLowerCase().indexOf(query) === 0;
        }).slice(0, 6);
        if (!matches.length) return closeMentions();
        room.mention = { start: before.length - match[2].length - 1, matches: matches, active: 0 };
        renderMentions();
    }

    function renderMentions() {
        var box = room.mentionBox;
        box.innerHTML = '';
        room.mention.matches.forEach(function (person, i) {
            var item = document.createElement('li');
            item.setAttribute('role', 'option');
            item.className = 'flex cursor-pointer items-center gap-x-2 px-3 py-1.5 text-sm '
                + (i === room.mention.active ? 'bg-gray-100 text-gray-900' : 'text-gray-700');
            var avatar = document.createElement('img');
            avatar.src = person.avatar;
            avatar.alt = '';
            avatar.className = 'size-5 shrink-0 rounded-full bg-gray-100';
            var name = document.createElement('span');
            name.className = 'truncate';
            name.textContent = person.name;
            item.append(avatar, name);
            item.addEventListener('mousedown', function (e) { e.preventDefault(); pickMention(i); });
            box.appendChild(item);
        });
        box.hidden = false;
    }

    function pickMention(i) {
        var person = room.mention.matches[i];
        var input = room.input;
        var insert = '@' + person.name + ' ';
        var after = input.value.slice(input.selectionStart);
        input.value = input.value.slice(0, room.mention.start) + insert + after;
        var caret = room.mention.start + insert.length;
        input.setSelectionRange(caret, caret);
        closeMentions();
        autosize();
        input.focus();
    }

    function closeMentions() {
        if (!room) return;
        room.mention = null;
        room.mentionBox.hidden = true;
        room.mentionBox.innerHTML = '';
    }

    // ---- New chat: find someone ----

    function setUpPeopleFilter() {
        var filter = byId('people-filter');
        if (!filter) return;
        filter.addEventListener('input', function () {
            var query = filter.value.trim().toLowerCase();
            document.querySelectorAll('[data-person-name]').forEach(function (row) {
                row.hidden = query && row.dataset.personName.indexOf(query) < 0;
            });
        });
        filter.addEventListener('keydown', function (e) { if (e.key === 'Enter') e.preventDefault(); });
    }

    // ---- Page life ----

    document.addEventListener('visibilitychange', function () {
        if (document.hidden) return;
        setTitleDot(false);
        if (room) {
            room.unseen = false;
            if (room.pendingRead) markRead();
        }
    });

    document.addEventListener('turbolinks:before-render', leaveRoom);

    document.addEventListener('turbolinks:load', function () {
        setTitleDot(false);
        refreshDots();
        connect();
        setUpRoom();
        setUpPeopleFilter();
    });
})();
