package com.undoredo.model;

public class ChatMessage {

    private long id;
    private String text;
    private String time;

    public ChatMessage() {
    }

    public ChatMessage(long id, String text, String time) {
        this.id = id;
        this.text = text;
        this.time = time;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}