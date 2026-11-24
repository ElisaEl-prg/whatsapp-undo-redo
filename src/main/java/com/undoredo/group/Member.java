package com.undoredo.group;

/** Anëtar i grupit. joinSeq është radha e hyrjes dhe përcakton vendin në listë. */
public record Member(int joinSeq, String name, Role role) {
}