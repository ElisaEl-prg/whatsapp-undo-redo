
const API = "/api/chat";

const elements = {
    messagesList: document.getElementById("messagesList"),
    messagesArea: document.getElementById("messagesArea"),
    messageForm: document.getElementById("messageForm"),
    messageInput: document.getElementById("messageInput"),
    sendBtn: document.getElementById("sendBtn"),

    undoBtn: document.getElementById("undoBtn"),
    redoBtn: document.getElementById("redoBtn"),
    historyBtn: document.getElementById("historyBtn"),

    undoCount: document.getElementById("undoCount"),
    redoCount: document.getElementById("redoCount"),
    undoStat: document.getElementById("undoStat"),
    redoStat: document.getElementById("redoStat"),
    storedChars: document.getElementById("storedChars"),

    undoStackVisual: document.getElementById("undoStackVisual"),
    redoStackVisual: document.getElementById("redoStackVisual"),

    lastAction: document.getElementById("lastAction"),

    historyModal: document.getElementById("historyModal"),
    historyList: document.getElementById("historyList"),
    closeHistoryBtn: document.getElementById("closeHistoryBtn"),

    welcomeMessage: document.getElementById("welcomeMessage"),
    toast: document.getElementById("toast"),

    contactSearch: document.getElementById("contactSearch"),
    chatTitle: document.getElementById("chatTitle")
};

let currentMessages = [];
let currentHistory = [];
let toastTimeout;

// --------------------------------------------------
// FUNKSIONET E HELP
// --------------------------------------------------

function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => {
        const entities = {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#039;"
        };

        return entities[character];
    });
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.classList.add("show");

    clearTimeout(toastTimeout);

    toastTimeout = setTimeout(() => {
        elements.toast.classList.remove("show");
    }, 2500);
}

function getMessageId(message) {
    return message.id ?? message.messageId ?? message.message_id;
}

function getMessageText(message) {
    return message.text ?? message.content ?? message.message ?? "";
}

function getMessageTime(message) {
    const value = message.timestamp ?? message.createdAt ?? message.time;

    if (!value) {
        return new Date().toLocaleTimeString("sq-AL", {
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return String(value);
    }

    return date.toLocaleTimeString("sq-AL", {
        hour: "2-digit",
        minute: "2-digit"
    });
}

function getArray(data) {
    if (Array.isArray(data)) {
        return data;
    }

    if (Array.isArray(data?.messages)) {
        return data.messages;
    }

    if (Array.isArray(data?.history)) {
        return data.history;
    }

    if (Array.isArray(data?.actions)) {
        return data.actions;
    }

    return [];
}

async function apiRequest(endpoint, options = {}) {
    const response = await fetch(`${API}${endpoint}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });

    if (!response.ok) {
        let errorMessage = `Gabim HTTP: ${response.status}`;

        try {
            const errorData = await response.json();
            errorMessage = errorData.message || errorData.error || errorMessage;
        } catch {
            // Përgjigjja nuk ishte JSON.
        }

        throw new Error(errorMessage);
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("application/json")) {
        return response.json();
    }

    return response.text();
}

// --------------------------------------------------
// NGARKO BISEDEN NGA BACKEND-I
// --------------------------------------------------

async function loadChat() {
    try {
        const data = await apiRequest("/state");

        currentMessages = getArray(data);

        renderMessages(currentMessages);

        updateCounters({
            undoSize: data.undoSize ?? data.undoCount ?? 0,
            redoSize: data.redoSize ?? data.redoCount ?? 0,
            lastAction: data.lastAction
        });

        await loadStatistics();

    } catch (error) {
        console.error("Gabim gjatë ngarkimit të bisedës:", error);
        showToast("Nuk u arrit të ngarkohej biseda.");
    }
}

// --------------------------------------------------
// RINDERTO MESAZHET
// --------------------------------------------------

function renderMessages(messages) {
    elements.messagesList.innerHTML = "";

    elements.welcomeMessage.style.display =
        messages.length === 0 ? "block" : "none";

    messages.forEach(message => {
        const id = getMessageId(message);
        const text = getMessageText(message);
        const time = getMessageTime(message);

        const row = document.createElement("div");
        row.className = "message-row";

        const bubble = document.createElement("div");
        bubble.className = "message-bubble";

        const messageText = document.createElement("p");
        messageText.className = "message-text";
        messageText.textContent = text;

        const deleteButton = document.createElement("button");
        deleteButton.className = "delete-message";
        deleteButton.type = "button";
        deleteButton.title = "Fshi mesazhin";
        deleteButton.setAttribute("aria-label", "Fshi mesazhin");

        deleteButton.innerHTML = `
            <svg class="icon">
                <use href="#i-close"></use>
            </svg>
        `;

        deleteButton.addEventListener("click", () => {
            deleteMessage(id);
        });

        const meta = document.createElement("div");
        meta.className = "message-meta";

        meta.innerHTML = `
            <span>${escapeHTML(time)}</span>
            <svg class="icon">
                <use href="#i-check-double"></use>
            </svg>
        `;

        bubble.appendChild(messageText);

        // ID përdoret për të lidhur mesazhin me veprimin DELETE.
        bubble.dataset.messageId = id ?? "";

        if (id !== undefined && id !== null) {
            bubble.appendChild(deleteButton);
        }

        bubble.appendChild(meta);
        row.appendChild(bubble);

        elements.messagesList.appendChild(row);
    });

    elements.messagesArea.scrollTop =
        elements.messagesArea.scrollHeight;
}

// --------------------------------------------------
// DERGO MESAZHIN
// --------------------------------------------------

elements.messageForm.addEventListener("submit", async event => {
    event.preventDefault();

    const text = elements.messageInput.value.trim();

    if (!text) {
        return;
    }

    elements.sendBtn.disabled = true;

    try {
        await apiRequest("/send", {
            method: "POST",
            body: JSON.stringify({ text })
        });

        elements.messageInput.value = "";

        await refreshAll();

        elements.messageInput.focus();

    } catch (error) {
        console.error("Gabim gjatë dërgimit:", error);
        showToast("Mesazhi nuk u dërgua.");
    } finally {
        elements.sendBtn.disabled = false;
    }
});

// --------------------------------------------------
// FSHI MESAZHIN
// --------------------------------------------------

async function deleteMessage(id) {
    if (id === undefined || id === null || id === "") {
        showToast("ID e mesazhit nuk u gjet.");
        return;
    }

    try {
        await apiRequest(`/delete/${encodeURIComponent(id)}`, {
            method: "DELETE"
        });

        await refreshAll();

        showToast("Mesazhi u fshi.");

    } catch (error) {
        console.error("Gabim gjatë fshirjes:", error);
        showToast("Mesazhi nuk u fshi.");
    }
}

// --------------------------------------------------
// UNDO
// --------------------------------------------------

elements.undoBtn.addEventListener("click", async () => {
    try {
        await apiRequest("/undo", {
            method: "POST"
        });

        await refreshAll();

        showToast("Undo u krye me sukses.");

    } catch (error) {
        console.error("Undo error:", error);
        showToast("Undo nuk mund të kryhet.");
    }
});

// --------------------------------------------------
// REDO
// --------------------------------------------------

elements.redoBtn.addEventListener("click", async () => {
    try {
        await apiRequest("/redo", {
            method: "POST"
        });

        await refreshAll();

        showToast("Redo u krye me sukses.");

    } catch (error) {
        console.error("Redo error:", error);
        showToast("Redo nuk mund të kryhet.");
    }
});

// --------------------------------------------------
// PERDITSO UNDO DHE REDON E VEPRIMEVE
// --------------------------------------------------

function updateCounters(data) {
    const undoSize = Number(data.undoSize ?? 0);
    const redoSize = Number(data.redoSize ?? 0);

    elements.undoCount.textContent = undoSize;
    elements.redoCount.textContent = redoSize;

    elements.undoStat.textContent = undoSize;
    elements.redoStat.textContent = redoSize;

    elements.undoBtn.disabled = undoSize === 0;
    elements.redoBtn.disabled = redoSize === 0;

    if (data.lastAction !== undefined && data.lastAction !== null) {
        elements.lastAction.textContent =
            typeof data.lastAction === "string"
                ? data.lastAction
                : JSON.stringify(data.lastAction);
    } else if (undoSize === 0 && redoSize === 0) {
        elements.lastAction.textContent = "Ende nuk ka veprime";
    }

    renderStackPlaceholder(
        elements.undoStackVisual,
        undoSize,
        "undo"
    );

    renderStackPlaceholder(
        elements.redoStackVisual,
        redoSize,
        "redo"
    );
}

// Shfaq numrin real të veprimeve.
// Nuk shpik përmbajtjen e Stack, pasi backend-i aktual
// ekspozon vetëm numrin e elementeve.

function renderStackPlaceholder(container, count, type) {
    if (count === 0) {
        container.innerHTML = `
            <div class="empty-stack">Nuk ka veprime</div>
        `;
        return;
    }

    const label = type === "undo"
        ? "veprim(e) për Undo"
        : "veprim(e) për Redo";

    container.innerHTML = `
        <div class="stack-item ${type === "redo" ? "redo-item" : ""}">
            <span>${count} ${label}</span>
            <small>LIFO</small>
        </div>
    `;
}

// --------------------------------------------------
// STATISTIKAT E BISEDES
// --------------------------------------------------

async function loadStatistics() {
    try {
        const data = await apiRequest("/stats");

        const storedChars =
            data.storedChars ??
            data.storedCharacters ??
            data.characterCount ??
            0;

        elements.storedChars.textContent = storedChars;

        updateCounters({
            undoSize: data.undoSize ?? data.undoCount ?? 0,
            redoSize: data.redoSize ?? data.redoCount ?? 0,
            lastAction: data.lastAction
        });

    } catch (error) {
        console.warn("Statistikat nuk u ngarkuan:", error);
    }
}

// --------------------------------------------------
// HISTORY
// --------------------------------------------------

async function loadHistory() {
    elements.historyList.innerHTML = `
        <div class="empty-history">Duke ngarkuar historikun...</div>
    `;

    try {
        const data = await apiRequest("/history");

        currentHistory = getArray(data);

        renderHistory(currentHistory);

    } catch (error) {
        console.error("Gabim gjatë ngarkimit të historikut:", error);

        elements.historyList.innerHTML = `
            <div class="empty-history">
                Nuk u arrit të ngarkohej historiku.
            </div>
        `;
    }
}

function renderHistory(history) {
    elements.historyList.innerHTML = "";

    if (!history.length) {
        elements.historyList.innerHTML = `
            <div class="empty-history">
                Nuk ka ende veprime në historik.
            </div>
        `;
        return;
    }

    [...history].reverse().forEach((item, index) => {
        const entry = document.createElement("div");
        entry.className = "history-entry";

        const actionText =
            typeof item === "string"
                ? item
                : item.description ??
                  item.action ??
                  item.type ??
                  item.message ??
                  JSON.stringify(item);

        const time =
            typeof item === "object"
                ? item.timestamp ?? item.time ?? ""
                : "";

        const marker = document.createElement("div");
        marker.className = "history-marker";
        marker.textContent = String(history.length - index);

        const content = document.createElement("div");

        const paragraph = document.createElement("p");
        paragraph.textContent = actionText;

        const small = document.createElement("small");
        small.textContent = time
            ? String(time)
            : "Veprim i regjistruar";

        content.appendChild(paragraph);
        content.appendChild(small);

        entry.appendChild(marker);
        entry.appendChild(content);

        elements.historyList.appendChild(entry);
    });
}

// Open history
elements.historyBtn.addEventListener("click", async () => {
    elements.historyModal.classList.add("open");
    await loadHistory();
});

// Close history
elements.closeHistoryBtn.addEventListener("click", () => {
    elements.historyModal.classList.remove("open");
});

// Close when clicking outside the modal
elements.historyModal.addEventListener("click", event => {
    if (event.target === elements.historyModal) {
        elements.historyModal.classList.remove("open");
    }
});

// --------------------------------------------------
// KERKIMI I KONTAKTEVE
// --------------------------------------------------

elements.contactSearch.addEventListener("input", event => {
    const searchValue = event.target.value.toLowerCase();

    document.querySelectorAll(".chat-item").forEach(item => {
        const contactName =
            item.dataset.contact.toLowerCase();

        item.style.display =
            contactName.includes(searchValue) ? "flex" : "none";
    });
});

// --------------------------------------------------
// DEMO E SEKSIONIT TE KONTAKTEVE
// --------------------------------------------------

document.querySelectorAll(".chat-item").forEach(item => {
    item.addEventListener("click", () => {
        document.querySelectorAll(".chat-item").forEach(chat => {
            chat.classList.remove("active");
        });

        item.classList.add("active");

        const contact = item.dataset.contact;

        elements.chatTitle.textContent = contact;

        // Backend-i aktual ruan një bisedë të vetme.
        // Kontaktet e tjera janë vetëm elemente demonstrimi.
        if (contact !== "John Doe") {
            showToast("Kjo është vetëm një bisedë demonstrimi.");
        }
    });
});

// --------------------------------------------------
// TABI I FILTRAVE
// --------------------------------------------------

document.querySelectorAll(".filter-tab").forEach(tab => {
    tab.addEventListener("click", () => {
        document.querySelectorAll(".filter-tab").forEach(button => {
            button.classList.remove("active");
        });

        tab.classList.add("active");

        // Filtrat janë vetëm për pamjen demo.
        // Nuk ndryshojnë të dhënat e backend-it.
    });
});

// --------------------------------------------------
// RIFRESKO TE GJITHA TE DHENAT
// --------------------------------------------------

async function refreshAll() {
    await loadChat();
    await loadHistoryIfOpen();
}

async function loadHistoryIfOpen() {
    if (elements.historyModal.classList.contains("open")) {
        await loadHistory();
    }
}

// --------------------------------------------------
// SHORTCUT E TASTIERES
// --------------------------------------------------

document.addEventListener("keydown", async event => {
    const isMac = navigator.platform.toUpperCase().includes("MAC");
    const modifier = isMac ? event.metaKey : event.ctrlKey;

    if (!modifier) {
        return;
    }

    const activeElement = document.activeElement;

    if (
        activeElement === elements.messageInput ||
        activeElement === elements.contactSearch
    ) {
        return;
    }

    if (event.key.toLowerCase() === "z" && !event.shiftKey) {
        event.preventDefault();

        if (!elements.undoBtn.disabled) {
            elements.undoBtn.click();
        }
    }

    if (
        event.key.toLowerCase() === "y" ||
        (event.key.toLowerCase() === "z" && event.shiftKey)
    ) {
        event.preventDefault();

        if (!elements.redoBtn.disabled) {
            elements.redoBtn.click();
        }
    }
});

// --------------------------------------------------
// INICIALIZIMI
// --------------------------------------------------

document.addEventListener("DOMContentLoaded", async () => {
    await loadChat();
    await loadHistory();
});

