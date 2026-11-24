package com.undoredo.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.undoredo.editor.Action;
import com.undoredo.editor.ActionType;
import com.undoredo.editor.UndoRedoManager;
import com.undoredo.group.ActionRefusedException;
import com.undoredo.group.Group;
import com.undoredo.group.Member;
import com.undoredo.group.MemberAction;
import com.undoredo.group.Role;
import com.undoredo.model.ChatMessage;
import com.undoredo.model.ChatState;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class ChatService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final String GROUP_NAME = "Design Team";
    private static final int GROUP_MAX_SIZE = 5;
    /** Teksti që shfaqet në vend të mesazhit të fshirë për të gjithë (fillon me emoji 🚫). */
    private static final String DELETED_PREFIX = "\uD83D\uDEAB";
    private static final String DELETED_TEXT = DELETED_PREFIX + " Ky mesazh u fshi";
    private final ObjectMapper objectMapper;
    private final StringBuilder document;
    private final List<String> history = new ArrayList<>();
    private final LongSupplier clock = System::currentTimeMillis;
    private final long graceMillis;
    private final Map<Long, String> messageTimes = new HashMap<>();
    private UndoRedoManager undoRedoManager;
    private Group group;
    private long nextMessageId;
    private String lastAction;

    public ChatService(ObjectMapper objectMapper,
                       @Value("${chat.undo-grace-seconds:300}") long graceSeconds) {
        this.objectMapper = objectMapper;
        this.graceMillis = graceSeconds * 1000L;
        this.undoRedoManager = new UndoRedoManager();
        this.document = new StringBuilder("[]");
        this.group = new Group(GROUP_NAME, GROUP_MAX_SIZE);
        this.nextMessageId = 1;
        this.lastAction = "Asnjë veprim";
    }

    // SEND MESSAGE
    public synchronized ChatState sendMessage(String text) {

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Mesazhi nuk mund të jetë bosh."
            );
        }

        if (group.find(Group.ME) == null) {
            throw new IllegalArgumentException(
                    "Nuk je më anëtar i grupit, ndaj nuk mund të dërgosh mesazhe."
            );
        }

        String time = LocalTime.now().format(TIME_FORMAT);

        ChatMessage message = new ChatMessage(
                nextMessageId,
                text,
                time
        );

        try {
            List<ChatMessage> messages = getMessages();

            String messageJson =
                    objectMapper.writeValueAsString(message);

            int position = document.length() - 1;

            String insertion;

            if (messages.isEmpty()) {
                insertion = messageJson;
            } else {
                insertion = "," + messageJson;
            }

            Action action = new Action(
                    ActionType.INSERT,
                    position,
                    insertion
            );

            action.apply(document);
            undoRedoManager.addAction(action);
            messageTimes.put(nextMessageId, time);
            nextMessageId++;

            lastAction = "INSERT - " + message.getText();
            history.add(lastAction);

            return getState();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Gabim gjatë dërgimit të mesazhit.",
                    e
            );
        }
    }

    // DELETE MESSAGE
    public synchronized ChatState deleteMessage(long messageId) {

        try {
            List<ChatMessage> messages = getMessages();

            ChatMessage messageToDelete = null;

            for (ChatMessage message : messages) {
                if (message.getId() == messageId) {
                    messageToDelete = message;
                    break;
                }
            }

            if (messageToDelete == null) {
                throw new IllegalArgumentException(
                        "Mesazhi nuk u gjet."
                );
            }

            String messageJson =
                    objectMapper.writeValueAsString(messageToDelete);

            int position = document.indexOf(messageJson);

            if (position < 0) {
                throw new IllegalStateException(
                        "Pozicioni i mesazhit nuk u gjet."
                );
            }

            int start = position;
            int length = messageJson.length();

            if (position + length < document.length() - 1
                    && document.charAt(position + length) == ',') {

                length++;

            } else if (position > 1
                    && document.charAt(position - 1) == ',') {

                start--;
                length++;
            }

            String deletedText =
                    document.substring(start, start + length);

            Action action = new Action(
                    ActionType.DELETE,
                    start,
                    deletedText
            );

            action.apply(document);
            undoRedoManager.addAction(action);

            lastAction = "DELETE - " + messageToDelete.getText();
            history.add(lastAction);

            return getState();

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Gabim gjatë fshirjes së mesazhit.",
                    e
            );
        }
    }
    // DELETE FOR EVERYONE (soft delete, me Undo dhe Redo)
    public synchronized ChatState deleteForEveryone(long messageId) {

        ChatMessage target = null;

        for (ChatMessage message : getMessages()) {
            if (message.getId() == messageId) {
                target = message;
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Mesazhi nuk u gjet.");
        }

        if (target.getText().startsWith(DELETED_PREFIX)) {
            throw new IllegalArgumentException("Mesazhi është fshirë tashmë për të gjithë.");
        }

        try {
            String time = messageTimes.getOrDefault(
                    messageId, LocalTime.now().format(TIME_FORMAT));

            String originalJson = objectMapper.writeValueAsString(target);
            String deletedJson = objectMapper.writeValueAsString(
                    new ChatMessage(messageId, DELETED_TEXT, time));

            int position = document.indexOf(originalJson);

            if (position < 0) {
                throw new IllegalStateException("Pozicioni i mesazhit nuk u gjet.");
            }

            DeleteForAllAction action = new DeleteForAllAction(
                    position, messageId, originalJson, deletedJson, clock, graceMillis);

            action.apply(document);
            undoRedoManager.addAction(action);

            lastAction = "DELETE_ALL - mesazhi #" + messageId + " u fshi për të gjithë";
            history.add(lastAction);

            return getState();

        } catch (IllegalStateException e) {
            throw e;

        } catch (Exception e) {
            throw new RuntimeException("Gabim gjatë fshirjes për të gjithë.", e);
        }
    }
    // UNDO
    public synchronized ChatState undo() {

        Action action = undoRedoManager.popUndo();

        if (action == null) {
            lastAction = "UNDO - nuk ka veprime për të zhbërë";
            history.add(lastAction);

            return getState();
        }

        try {
            action.reverse(document);

            undoRedoManager.pushRedo(action);

            lastAction = "UNDO - " + action.describe();
            history.add(lastAction);

            return getState();

        } catch (ActionRefusedException e) {

            // Veprimi hiqet nga historia: nuk kthehet në stack, që të mos bllokojë të tjerët.
            lastAction = "UNDO refuzuar - " + e.getMessage();
            history.add(lastAction);

            throw e;

        } catch (RuntimeException e) {

            undoRedoManager.pushUndo(action);

            throw new RuntimeException(
                    "Undo dështoi.",
                    e
            );
        }
    }

    // REDO
    public synchronized ChatState redo() {

        Action action = undoRedoManager.popRedo();

        if (action == null) {
            lastAction = "REDO - nuk ka veprime për të rikthyer";
            history.add(lastAction);

            return getState();
        }

        try {
            action.apply(document);

            undoRedoManager.pushUndo(action);

            lastAction = "REDO - " + action.describe();
            history.add(lastAction);

            return getState();

        } catch (ActionRefusedException e) {

            lastAction = "REDO refuzuar - " + e.getMessage();
            history.add(lastAction);

            throw e;

        } catch (RuntimeException e) {

            undoRedoManager.pushRedo(action);

            throw new RuntimeException(
                    "Redo dështoi.",
                    e
            );
        }
    }

    // ---------------------------------------------------------------
    // GRUPI
    // ---------------------------------------------------------------

    // LEAVE GROUP
    public synchronized Map<String, Object> leaveGroup() {
        MemberAction action = MemberAction.leave(group, clock, graceMillis);
        perform(action, "LEAVE - " + Group.ME + " u largua nga grupi");
        return getGroupView();
    }

    // REMOVE MEMBER
    public synchronized Map<String, Object> removeMember(String name) {
        MemberAction action = MemberAction.remove(group, name, clock, graceMillis);
        perform(action, "REMOVE - " + action.getMember().name() + " u hoq nga grupi");
        return getGroupView();
    }

    // ADD MEMBER (veprim i Ti-së si admin, mund të zhbëhet)
    public synchronized Map<String, Object> addMember(String name) {
        MemberAction action = MemberAction.add(group, name, clock, graceMillis);
        perform(action, "ADD - " + action.getMember().name() + " u shtua në grup");
        return getGroupView();
    }

    // Simulon një admin tjetër që shton dikë. Nuk hyn në historinë e Undo,
    // por mund ta bëjë një Undo të vjetër të pamundur (anëtari rikthyer ose grup plot).
    public synchronized Map<String, Object> simulateOtherAdminAdds(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 20) {
            throw new IllegalArgumentException("Emri duhet të ketë 1 deri në 20 karaktere.");
        }
        if (group.contains(clean)) {
            throw new IllegalArgumentException(clean + " është tashmë në grup.");
        }
        if (group.isFull()) {
            throw new IllegalArgumentException("Grupi është plot.");
        }
        group.insert(group.create(clean, Role.MEMBER));
        lastAction = "SISTEMI - një admin tjetër shtoi " + clean;
        history.add(lastAction);
        return getGroupView();
    }

    private void perform(MemberAction action, String label) {
        action.apply(document); // dokumenti nuk përdoret nga veprimet e grupit
        undoRedoManager.addAction(action);
        lastAction = label;
        history.add(lastAction);
    }

    // GROUP VIEW
    public synchronized Map<String, Object> getGroupView() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Member m : group.members()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("joinSeq", m.joinSeq());
            row.put("name", m.name());
            row.put("role", m.role().name());
            row.put("you", m.name().equalsIgnoreCase(Group.ME));
            rows.add(row);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", group.getName());
        view.put("members", rows);
        view.put("size", group.size());
        view.put("maxSize", group.getMaxSize());
        view.put("youAreMember", group.find(Group.ME) != null);
        view.put("youAreAdmin", group.meIsAdmin());
        view.put("graceSeconds", graceMillis / 1000L);
        return view;
    }

    // RESET (për ta nisur demon nga e para)
    public synchronized ChatState reset() {
        document.setLength(0);
        document.append("[]");
        undoRedoManager = new UndoRedoManager();
        group = new Group(GROUP_NAME, GROUP_MAX_SIZE);
        history.clear();
        messageTimes.clear();
        nextMessageId = 1;
        lastAction = "Asnjë veprim";
        return getState();
    }

    // GET CURRENT STATE
    public synchronized ChatState getState() {

        return new ChatState(
                getMessages(),
                undoRedoManager.undoSize(),
                undoRedoManager.redoSize(),
                lastAction
        );
    }

    // GET MESSAGES
    private List<ChatMessage> getMessages() {

        try {
            return objectMapper.readValue(
                    document.toString(),
                    new TypeReference<List<ChatMessage>>() {
                    }
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Dokumenti i chat-it nuk është në format të vlefshëm.",
                    e
            );
        }
    }

    // GET DOCUMENT
    public synchronized String getDocument() {
        return document.toString();
    }

    // GET UNDO SIZE
    public synchronized int getUndoSize() {
        return undoRedoManager.undoSize();
    }

    // GET REDO SIZE
    public synchronized int getRedoSize() {
        return undoRedoManager.redoSize();
    }

    // GET STORED CHARACTERS
    public synchronized long getStoredChars() {
        return undoRedoManager.storedChars();
    }

    // GET COMPLETE HISTORY
    public synchronized List<String> getHistory() {
        return new ArrayList<>(history);
    }
}