package com.editor;

import com.undoredo.editor.Action;
import com.undoredo.editor.ActionType;
import com.undoredo.editor.UndoRedoManager;

/**
 * Testi i strukturave të historisë: Action, undoStack/redoStack dhe addAction (hapi X).
 * Ekzekutimi: java -cp out tests.StepTest
 */
public class StepTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        // F3: Action ruan llojin, pozicionin dhe tekstin e prekur
        Action a1 = new Action(ActionType.INSERT, 0, "Pershendetje");
        check("F3 Action ruan llojin, pozicionin dhe tekstin",
                a1.getType() == ActionType.INSERT && a1.getPosition() == 0
                        && a1.getText().equals("Pershendetje"));
        check("F3 Action refuzon lloj null", rejects(() -> new Action(null, 0, "x")));
        check("F3 Action refuzon pozicion negativ",
                rejects(() -> new Action(ActionType.INSERT, -1, "x")));
        check("F3 Action refuzon tekst bosh ose null",
                rejects(() -> new Action(ActionType.INSERT, 0, ""))
                        && rejects(() -> new Action(ActionType.INSERT, 0, null)));

        // F3: apply dhe reverse janë të kundërta për të dy llojet
        StringBuilder doc = new StringBuilder("abcdef");
        Action ins = new Action(ActionType.INSERT, 3, "XY");
        ins.apply(doc);
        boolean afterInsert = doc.toString().equals("abcXYdef");
        ins.reverse(doc);
        check("F3 INSERT: apply pastaj reverse rikthen tekstin", afterInsert && doc.toString().equals("abcdef"));
        Action del = new Action(ActionType.DELETE, 1, "bc");
        del.apply(doc);
        boolean afterDelete = doc.toString().equals("adef");
        del.reverse(doc);
        check("F3 DELETE: apply pastaj reverse rikthen tekstin e fshirë", afterDelete && doc.toString().equals("abcdef"));

        // F4: dy stack-e LIFO, fillimisht bosh
        UndoRedoManager m = new UndoRedoManager();
        check("F4 stack-et fillojnë bosh", m.undoSize() == 0 && m.redoSize() == 0
                && m.peekUndo() == null && m.peekRedo() == null);

        Action a2 = new Action(ActionType.INSERT, 12, " bote");
        Action a3 = new Action(ActionType.DELETE, 12, " bote");

        m.addAction(a1);
        check("F4 pas veprimit 1 maja e undoStack është veprimi 1", m.peekUndo() == a1);
        m.addAction(a2);
        check("F4 pas veprimit 2 maja është veprimi 2 (LIFO)", m.peekUndo() == a2);
        m.addAction(a3);
        check("F4 pas veprimit 3 maja është veprimi 3 (LIFO)", m.peekUndo() == a3);
        check("F4 undoStack ka 3 veprime", m.undoSize() == 3);
        check("F4 redoStack mbetet bosh pas shtimit të veprimeve", m.redoSize() == 0 && m.peekRedo() == null);

        // F4: veprimi i ri pas Undo-së e pastron Redo-n (kjo provë ka kuptim vetëm kur Redo nuk është bosh)
        Action undone = m.popUndo();
        m.pushRedo(undone);
        check("F4 pas një Undo, redoStack ka veprimin e zhbërë", m.redoSize() == 1 && m.peekRedo() == a3
                && m.undoSize() == 2);
        Action a4 = new Action(ActionType.INSERT, 0, "X");
        m.addAction(a4);
        check("F4 addAction pas Undo-së e pastron redoStack", m.redoSize() == 0 && m.peekUndo() == a4);

        // F5: veprim null refuzohet pa ndryshuar historinë
        check("F5 veprim null refuzohet", rejects(() -> m.addAction(null)));
        check("F5 pas refuzimit historia është e pandryshuar", m.undoSize() == 3 && m.peekUndo() == a4);

        // F7: kufiri i thellësisë
        UndoRedoManager limited = new UndoRedoManager(2);
        limited.addAction(a1);
        limited.addAction(a2);
        limited.addAction(a3);
        check("F7 me maxDepth=2 historia ka vetëm 2 veprimet e fundit",
                limited.undoSize() == 2 && limited.peekUndo() == a3
                        && limited.undoActions().get(0) == a2);
        check("F7 maxDepth negativ refuzohet", rejects(() -> new UndoRedoManager(-1)));

        // F8: treguesi i memories numëron vetëm tekstin e ruajtur
        long expected = a1.getText().length() + a2.getText().length() + a4.getText().length();
        check("F8 storedChars numëron karakteret e ruajtur (" + expected + ")", m.storedChars() == expected);

        System.out.println();
        System.out.println("Rezultati: " + passed + " kaluan, " + failed + " dështuan.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static boolean rejects(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
