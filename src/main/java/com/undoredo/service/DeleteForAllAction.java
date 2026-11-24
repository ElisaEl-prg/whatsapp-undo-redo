package com.undoredo.service;

import com.undoredo.editor.Action;
import com.undoredo.editor.ActionType;
import com.undoredo.group.ActionRefusedException;

import java.util.function.LongSupplier;

/**
 * "Delete for everyone": mesazhi mbetet në bisedë, por teksti i tij zëvendësohet
 * me një shënim. Teksti origjinal ruhet vetëm këtu, te veprimi (soft delete).
 * Undo e kthen brenda afatit; pas afatit refuzohet dhe origjinali humbet përgjithmonë.
 */
public class DeleteForAllAction extends Action {

    private final long messageId;
    private final String originalJson;
    private final String deletedJson;
    private final LongSupplier clock;
    private final long graceMillis;
    private final long createdAt;

    public DeleteForAllAction(int position, long messageId, String originalJson,
                              String deletedJson, LongSupplier clock, long graceMillis) {
        super(ActionType.DELETE, position, originalJson);
        this.messageId = messageId;
        this.originalJson = originalJson;
        this.deletedJson = deletedJson;
        this.clock = clock;
        this.graceMillis = graceMillis;
        this.createdAt = clock.getAsLong();
    }

    /** Kryerja dhe Redo: origjinali zëvendësohet me shënimin e fshirjes. */
    @Override
    public void apply(StringBuilder doc) {
        replace(doc, originalJson, deletedJson);
    }

    /** Undo: shënimi zëvendësohet me tekstin origjinal, nëse afati nuk ka skaduar. */
    @Override
    public void reverse(StringBuilder doc) {
        if (clock.getAsLong() - createdAt > graceMillis) {
            throw new ActionRefusedException("Afati i Undo ka skaduar.");
        }
        replace(doc, deletedJson, originalJson);
    }

    private static void replace(StringBuilder doc, String from, String to) {
        int index = doc.indexOf(from);
        if (index < 0) {
            throw new ActionRefusedException("Mesazhi nuk është më në bisedë.");
        }
        doc.replace(index, index + from.length(), to);
    }

    @Override
    public String describe() {
        return "DELETE_ALL mesazhi #" + messageId;
    }
}