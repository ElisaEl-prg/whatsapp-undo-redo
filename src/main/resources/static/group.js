/*
 * Grupi: Leave / Remove / Add me Undo dhe Redo.
 * Ngarkohet pas script.js dhe përdor funksionet e tij (apiRequest, showToast,
 * escapeHTML, refreshAll, elements). Nuk e ndryshon script.js.
 */
(function () {
    "use strict";

    const baseApiRequest = window.apiRequest;
    const baseRefreshAll = window.refreshAll;

    let group = null;

    // ---------------------------------------------------------------
    // CSS
    // ---------------------------------------------------------------
    const cssLink = document.createElement("link");
    cssLink.rel = "stylesheet";
    cssLink.href = "group.css";
    document.head.appendChild(cssLink);

    // ---------------------------------------------------------------
    // BUTONI "GRUPI" NË TOOLBAR
    // ---------------------------------------------------------------
    const groupBtn = document.createElement("button");
    groupBtn.id = "groupBtn";
    groupBtn.type = "button";
    groupBtn.className = "action-button";
    groupBtn.innerHTML =
        '<svg class="icon"><use href="#i-users"/></svg><span>Grupi</span>';
    document.querySelector(".toolbar-buttons").appendChild(groupBtn);

    // ---------------------------------------------------------------
    // BANERI KUR KE LËNË GRUPIN
    // ---------------------------------------------------------------
    const banner = document.createElement("div");
    banner.className = "group-banner";
    banner.hidden = true;
    banner.textContent =
        "Ke lënë grupin. Shtyp Undo për t'u rikthyer, ose hap Grupi.";
    elements.messageForm.parentNode.insertBefore(banner, elements.messageForm);

    // ---------------------------------------------------------------
    // DRITARJA E GRUPIT
    // ---------------------------------------------------------------
    const modal = document.createElement("div");
    modal.className = "modal-overlay";
    modal.id = "groupModal";
    modal.innerHTML = `
        <section class="history-modal group-modal">
            <header class="history-header">
                <div>
                    <span class="eyebrow">GRUPI</span>
                    <h2 id="groupTitle">Grupi</h2>
                </div>
                <button id="closeGroupBtn" type="button" class="icon-button" title="Mbyll">
                    <svg class="icon"><use href="#i-close"/></svg>
                </button>
            </header>

            <div class="group-body">
                <p id="groupSummary" class="group-summary"></p>
                <div id="groupMembers" class="group-members"></div>

                <div class="group-section">
                    <h3>Shto anëtar</h3>
                    <div class="group-input-row">
                        <input id="addMemberInput" type="text" maxlength="20"
                               placeholder="Emri i anëtarit" autocomplete="off">
                        <button id="addMemberBtn" type="button" class="small-button">Shto</button>
                    </div>
                    <small>Vetëm admini mund të shtojë ose heqë anëtarë.</small>
                </div>

                <div class="group-section">
                    <h3>Largohu</h3>
                    <button id="leaveGroupBtn" type="button" class="small-button danger-button">
                        Largohu nga grupi
                    </button>
                    <small>Pas çdo veprimi përdor Undo dhe Redo në krye të bisedës.</small>
                </div>

                <div class="group-section simulation">
                    <h3>Simulim: admin tjetër</h3>
                    <div class="group-input-row">
                        <input id="simulateInput" type="text" maxlength="20"
                               placeholder="Emri (p.sh. Bora)" autocomplete="off">
                        <button id="simulateBtn" type="button" class="small-button">Shto si admin tjetër</button>
                    </div>
                    <small>
                        Ky shtim nuk hyn në historinë e Undo. Provo: hiq dikë, shtoje
                        këtu me të njëjtin emër, pastaj Undo, dhe Undo refuzohet.
                    </small>
                </div>

                <button id="resetDemoBtn" type="button" class="small-button reset-button">
                    Rinis demon
                </button>
            </div>
        </section>
    `;
    document.body.appendChild(modal);

    const els = {
        title: modal.querySelector("#groupTitle"),
        summary: modal.querySelector("#groupSummary"),
        members: modal.querySelector("#groupMembers"),
        addInput: modal.querySelector("#addMemberInput"),
        addBtn: modal.querySelector("#addMemberBtn"),
        leaveBtn: modal.querySelector("#leaveGroupBtn"),
        simInput: modal.querySelector("#simulateInput"),
        simBtn: modal.querySelector("#simulateBtn"),
        resetBtn: modal.querySelector("#resetDemoBtn"),
        closeBtn: modal.querySelector("#closeGroupBtn")
    };

    // ---------------------------------------------------------------
    // RENDER
    // ---------------------------------------------------------------
    function formatGrace(seconds) {
        if (seconds >= 60 && seconds % 60 === 0) {
            return (seconds / 60) + " minuta";
        }
        return seconds + " sekonda";
    }

    function initials(name) {
        return String(name || "?").trim().slice(0, 2).toUpperCase();
    }

    function renderGroup() {
        if (!group) {
            return;
        }

        els.title.textContent = group.name;
        els.summary.textContent =
            group.size + " / " + group.maxSize + " anëtarë • Afati i Undo: " +
            formatGrace(group.graceSeconds);
        const previousCount = els.members.children.length;
        const keepScroll = els.members.scrollTop;
        els.members.innerHTML = "";

        group.members.forEach(member => {
            const row = document.createElement("div");
            row.className = "member-row";

            const avatar = document.createElement("div");
            avatar.className = "member-avatar" + (member.you ? " me" : "");
            avatar.textContent = initials(member.name);

            const info = document.createElement("div");
            info.className = "member-info";
            info.innerHTML =
                "<strong>" + escapeHTML(member.name) + "</strong>" +
                (member.you ? ' <span class="you-tag">ti</span>' : "");

            const badge = document.createElement("span");
            badge.className = "role-badge " + (member.role === "ADMIN" ? "admin" : "member");
            badge.textContent = member.role === "ADMIN" ? "Admin" : "Anëtar";

            row.appendChild(avatar);
            row.appendChild(info);
            row.appendChild(badge);

            if (group.youAreAdmin && !member.you) {
                const remove = document.createElement("button");
                remove.type = "button";
                remove.className = "small-button danger-button remove-member";
                remove.textContent = "Hiq";
                remove.dataset.name = member.name;
                row.appendChild(remove);
            }

            els.members.appendChild(row);
        });
        // Anëtar i ri: zbrit në fund; përndryshe mbaje pozicionin e scroll-it.
        els.members.scrollTop =
            previousCount > 0 && group.members.length > previousCount
                ? els.members.scrollHeight
                : keepScroll;
        els.leaveBtn.disabled = !group.youAreMember;
        els.addBtn.disabled = !group.youAreAdmin;
        els.addInput.disabled = !group.youAreAdmin;

        // Kur ke lënë grupin, biseda mbyllet derisa të rikthehesh.
        banner.hidden = group.youAreMember;
        elements.messageInput.disabled = !group.youAreMember;
        elements.messageInput.placeholder = group.youAreMember
            ? "Shkruaj një mesazh"
            : "Ke lënë grupin";
        elements.sendBtn.disabled = !group.youAreMember;
    }

    async function loadGroup() {
        try {
            group = await baseApiRequest("/group");
            renderGroup();
        } catch (error) {
            console.warn("Grupi nuk u ngarkua:", error);
        }
    }

    // ---------------------------------------------------------------
    // VEPRIMET
    // ---------------------------------------------------------------
    async function groupAction(endpoint, body, okMessage) {
        try {
            await baseApiRequest(endpoint, {
                method: "POST",
                body: JSON.stringify(body || {})
            });
            await window.refreshAll();
            showToast(okMessage);
            return true;
        } catch (error) {
            showToast(error.message || "Veprimi nuk u krye.");
            return false;
        }
    }

    els.leaveBtn.addEventListener("click", () => {
        groupAction("/group/leave", {}, "U largove nga grupi. Shtyp Undo për t'u rikthyer.");
    });

    els.members.addEventListener("click", event => {
        const button = event.target.closest(".remove-member");
        if (!button) {
            return;
        }
        const name = button.dataset.name;
        groupAction("/group/remove", { name }, name + " u hoq nga grupi.");
    });

    els.addBtn.addEventListener("click", async () => {
        const name = els.addInput.value.trim();
        if (await groupAction("/group/add", { name }, name + " u shtua në grup.")) {
            els.addInput.value = "";
        }
    });

    els.simBtn.addEventListener("click", async () => {
        const name = els.simInput.value.trim();
        if (await groupAction("/group/simulate-add", { name },
                "Një admin tjetër shtoi " + name + ".")) {
            els.simInput.value = "";
        }
    });

    els.resetBtn.addEventListener("click", () => {
        groupAction("/reset", {}, "Demoja u rinis.");
    });

    // ---------------------------------------------------------------
    // HAPJA DHE MBYLLJA
    // ---------------------------------------------------------------
    groupBtn.addEventListener("click", async () => {
        await loadGroup();
        modal.classList.add("open");
    });

    els.closeBtn.addEventListener("click", () => modal.classList.remove("open"));

    modal.addEventListener("click", event => {
        if (event.target === modal) {
            modal.classList.remove("open");
        }
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            modal.classList.remove("open");
        }
    });

    // ---------------------------------------------------------------
    // INTEGRIMI ME script.js (pa e ndryshuar atë)
    // ---------------------------------------------------------------

    // Çdo rifreskim i faqes rifreskon edhe grupin.
    window.refreshAll = async function () {
        await baseRefreshAll();
        await loadGroup();
    };

    // Kur Undo ose Redo refuzohet (HTTP 409), tregohet arsyeja e vërtetë
    // dhe faqja rifreskohet, sepse veprimi i refuzuar u hoq nga historia.
    window.apiRequest = async function (endpoint, options) {
        try {
            return await baseApiRequest(endpoint, options);
        } catch (error) {
            if (endpoint === "/undo" || endpoint === "/redo") {
                setTimeout(async () => {
                    await window.refreshAll();
                    showToast(error.message || "Veprimi u refuzua.");
                }, 0);
            }
            throw error;
        }
    };
    // ---------------------------------------------------------------
    // FSHIRJA E MESAZHEVE: "Fshi për mua" ose "Fshi për të gjithë"
    // ---------------------------------------------------------------
    const DELETED_MARK = "\uD83D\uDEAB";

    function closeDeleteMenus() {
        document.querySelectorAll(".delete-menu").forEach(menu => menu.remove());
    }

    async function runDelete(request, okMessage) {
        closeDeleteMenus();
        try {
            await request();
            await window.refreshAll();
            showToast(okMessage);
        } catch (error) {
            showToast(error.message || "Mesazhi nuk u fshi.");
        }
    }

    function openDeleteMenu(bubble, id) {
        closeDeleteMenus();

        const menu = document.createElement("div");
        menu.className = "delete-menu";

        const forMe = document.createElement("button");
        forMe.type = "button";
        forMe.className = "delete-for-me";
        forMe.textContent = "Fshi për mua";
        forMe.addEventListener("click", event => {
            event.stopPropagation();
            runDelete(
                () => window.apiRequest("/delete/" + encodeURIComponent(id), { method: "DELETE" }),
                "Mesazhi u fshi për ty."
            );
        });

        const forAll = document.createElement("button");
        forAll.type = "button";
        forAll.className = "delete-for-all";
        forAll.textContent = "Fshi për të gjithë";
        forAll.addEventListener("click", event => {
            event.stopPropagation();
            runDelete(
                () => window.apiRequest("/delete-all/" + encodeURIComponent(id), { method: "POST" }),
                "Mesazhi u fshi për të gjithë. Shtyp Undo për ta rikthyer."
            );
        });

        menu.appendChild(forMe);
        menu.appendChild(forAll);
        bubble.appendChild(menu);
    }

    // Kapet para listenerit të script.js, që e fshinte mesazhin menjëherë.
    elements.messagesList.addEventListener("click", event => {
        const button = event.target.closest(".delete-message");
        if (!button) {
            return;
        }
        event.stopPropagation();
        event.preventDefault();

        const bubble = button.closest(".message-bubble");
        const id = bubble && bubble.dataset.messageId;
        if (id) {
            openDeleteMenu(bubble, id);
        }
    }, true);

    document.addEventListener("click", event => {
        if (!event.target.closest(".delete-menu")) {
            closeDeleteMenus();
        }
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            closeDeleteMenus();
        }
    });

    // Mesazhet e fshira për të gjithë shfaqen me stil të veçantë dhe pa buton fshirjeje.
    function decorateDeletedMessages() {
        elements.messagesList.querySelectorAll(".message-bubble").forEach(bubble => {
            const text = bubble.querySelector(".message-text");
            if (text && text.textContent.startsWith(DELETED_MARK)) {
                bubble.classList.add("deleted-message");
                const button = bubble.querySelector(".delete-message");
                if (button) {
                    button.remove();
                }
            }
        });
    }

    new MutationObserver(decorateDeletedMessages)
        .observe(elements.messagesList, { childList: true });
    decorateDeletedMessages();
    document.addEventListener("DOMContentLoaded", loadGroup);
    if (document.readyState !== "loading") {
        loadGroup();
    }
})();