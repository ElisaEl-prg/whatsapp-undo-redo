package com.undoredo.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Strukturat e historisë: dy stack-e LIFO (undoStack dhe redoStack).
 * Lidhet me objektivin O3 dhe kërkesat F4, F5 dhe F7.
 *
 * Përdoret ArrayDeque (jo java.util.Stack, që është klasë e vjetër dhe e sinkronizuar).
 * push, pop dhe peek në ArrayDeque kryhen në O(1), pra kostoja e shtimit të një veprimi
 * në histori nuk varet nga gjatësia e historisë.
 */
public class UndoRedoManager {

    // Veprimet e kryera, më i fundit në krye. Undo do të marrë nga këtu.
    private final Deque<Action> undoStack = new ArrayDeque<>();

    // Veprimet e anuluara, më i fundit i anuluar në krye. Redo do të marrë nga këtu.
    private final Deque<Action> redoStack = new ArrayDeque<>();

    // Kufiri i thellësisë së historisë (F7). 0 do të thotë pa kufi.
    private final int maxDepth;

    public UndoRedoManager() {
        this(0);
    }

    public UndoRedoManager(int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth nuk mund të jetë negativ.");
        }
        this.maxDepth = maxDepth;
    }

    /**
     * Regjistron një veprim të ri të bërë nga përdoruesi.
     *
     * Rregulli kryesor (F4): një veprim i ri e pastron stack-un e Redo, sepse dega e vjetër
     * e historisë nuk mund të rikthehet më. Zbrazja kushton proporcionalisht me madhësinë e Redo-s,
     * por çdo element futet në Redo një herë dhe pastrohet një herë, ndaj kostoja amortizuar
     * për veprim mbetet konstante.
     *
     * Nëse maxDepth është vendosur (F7) dhe historia e kalon, hiqet veprimi më i vjetër.
     */
    public void addAction(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("Veprimi nuk mund të jetë null.");
        }
        undoStack.push(action);
        redoStack.clear();
        if (maxDepth > 0 && undoStack.size() > maxDepth) {
            undoStack.removeLast();
        }
    }

    // Metodat e mëposhtme përdoren nga Undo dhe Redo. Ato NUK e pastrojnë redoStack,
    // përndryshe Redo do ta shkatërronte vetë historinë e vet.

    /** Nxjerr veprimin e fundit të kryer (null nëse historia është bosh). */
    public Action popUndo() {
        return undoStack.poll();
    }

    /** Nxjerr veprimin e fundit të anuluar (null nëse nuk ka). */
    public Action popRedo() {
        return redoStack.poll();
    }

    public void pushUndo(Action action) {
        undoStack.push(action);
    }

    public void pushRedo(Action action) {
        redoStack.push(action);
    }

    public int undoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /** Veprimi i fundit i kryer, pa e hequr (null nëse historia është bosh). */
    public Action peekUndo() {
        return undoStack.peek();
    }

    /** Veprimi i fundit i anuluar, pa e hequr (null nëse nuk ka). */
    public Action peekRedo() {
        return redoStack.peek();
    }

    /** Veprimet e kryera, nga më i vjetri te më i riu (për komandën history). */
    public List<Action> undoActions() {
        List<Action> list = new ArrayList<>(undoStack); // nga më i riu te më i vjetri
        Collections.reverse(list);
        return list;
    }

    /**
     * Numri i karaktereve të tekstit të ruajtur në të dy stack-et.
     * Është treguesi i memories për krahasimin me kopjet e plota (O5, F8).
     */
    public long storedChars() {
        long total = 0;
        for (Action a : undoStack) {
            total += a.size();
        }
        for (Action a : redoStack) {
            total += a.size();
        }
        return total;
    }
}
