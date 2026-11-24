package com.undoredo.editor;

/**
 * Një veprim mbi tekstin: lloji, pozicioni dhe teksti i prekur.
 * Për DELETE ruan tekstin e fshirë, kështu reverse() mund ta rikthejë.
 */
public class Action {

    private final ActionType type;
    private final int position;
    private final String text;

    public Action(ActionType type, int position, String text) {
        if (type == null) {
            throw new IllegalArgumentException("Lloji i veprimit nuk mund të jetë null.");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Pozicioni nuk mund të jetë negativ.");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Teksti nuk mund të jetë bosh ose null.");
        }
        this.type = type;
        this.position = position;
        this.text = text;
    }

    public ActionType getType() { return type; }
    public int getPosition() { return position; }
    public String getText() { return text; }

    /** Zbaton veprimin mbi dokumentin. */
    public void apply(StringBuilder doc) {
        if (type == ActionType.INSERT) {
            insert(doc);
        } else {
            delete(doc);
        }
    }

    /** Kthen veprimin mbrapsht (e kundërta e apply). */
    public void reverse(StringBuilder doc) {
        if (type == ActionType.INSERT) {
            delete(doc);
        } else {
            insert(doc);
        }
    }

    private void insert(StringBuilder doc) {
        if (position > doc.length()) {
            throw new IllegalArgumentException("Pozicioni " + position + " është jashtë dokumentit.");
        }
        doc.insert(position, text);
    }

    private void delete(StringBuilder doc) {
        int end = position + text.length();
        if (end > doc.length() || !doc.substring(position, end).equals(text)) {
            throw new IllegalArgumentException("Teksti për fshirje nuk përputhet me dokumentin.");
        }
        doc.delete(position, end);
    }

    /** Numri i karaktereve të tekstit të ruajtur në këtë veprim (për storedChars). */
    public int size() {
        return text.length();
    }

    /** Përshkrim i shkurtër për historikun (p.sh. "INSERT 87 karaktere @12"). */
    public String describe() {
        return type + " " + text.length() + " karaktere @" + position;
    }

    @Override
    public String toString() {
        return type + " @" + position + " \"" + text + "\"";
    }
}