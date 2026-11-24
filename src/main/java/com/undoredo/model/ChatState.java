package com.undoredo.model;

import java.util.List;

public class ChatState {

    private List<ChatMessage> messages;
    private int undoSize;
    private int redoSize;
    private String lastAction;

    public ChatState() {
    }

    public ChatState(
            List<ChatMessage> messages,
            int undoSize,
            int redoSize,
            String lastAction
    ) {
        this.messages = messages;
        this.undoSize = undoSize;
        this.redoSize = redoSize;
        this.lastAction = lastAction;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public int getUndoSize() {
        return undoSize;
    }

    public void setUndoSize(int undoSize) {
        this.undoSize = undoSize;
    }

    public int getRedoSize() {
        return redoSize;
    }

    public void setRedoSize(int redoSize) {
        this.redoSize = redoSize;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }
}