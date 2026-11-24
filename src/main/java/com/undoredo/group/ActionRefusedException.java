package com.undoredo.group;

/** Undo ose Redo u refuzua sepse kushtet nuk plotësohen më. Veprimi hiqet nga historia. */
public class ActionRefusedException extends RuntimeException {
    public ActionRefusedException(String message) {
        super(message);
    }
}