package com.undoredo.group;

import com.undoredo.editor.Action;
import com.undoredo.editor.ActionType;

import java.util.function.LongSupplier;

/**
 * Veprim mbi grupin (Leave, Remove, Add) që hyn në të njëjtin undoStack
 * me veprimet mbi mesazhet. Nuk përdor dokumentin e tekstit: apply dhe reverse
 * ndryshojnë objektin Group. Kur kushtet nuk plotësohen, hedh ActionRefusedException.
 */
public class MemberAction extends Action {

    public enum Kind { LEAVE, REMOVE, ADD }

    private final Kind kind;
    private final Group group;
    private final Member member;
    private final LongSupplier clock;
    private final long graceMillis;
    private final long createdAt;

    private MemberAction(Kind kind, Group group, Member member, LongSupplier clock, long graceMillis) {
        super(kind == Kind.ADD ? ActionType.INSERT : ActionType.DELETE, member.joinSeq(), member.name());
        this.kind = kind;
        this.group = group;
        this.member = member;
        this.clock = clock;
        this.graceMillis = graceMillis;
        this.createdAt = clock.getAsLong();
    }

    // ---------- fabrikat (validojnë para se të krijojnë veprimin) ----------

    public static MemberAction leave(Group g, LongSupplier clock, long graceMillis) {
        Member me = g.find(Group.ME);
        if (me == null) {
            throw new IllegalArgumentException("Nuk je anëtar i grupit.");
        }
        MemberAction a = new MemberAction(Kind.LEAVE, g, me, clock, graceMillis);
        failIfProblem(a.removeProblem());
        return a;
    }

    public static MemberAction remove(Group g, String name, LongSupplier clock, long graceMillis) {
        Member target = g.find(name);
        if (target == null) {
            throw new IllegalArgumentException("Anëtari nuk u gjet.");
        }
        if (target.name().equalsIgnoreCase(Group.ME)) {
            throw new IllegalArgumentException("Për veten përdor \"Largohu nga grupi\".");
        }
        MemberAction a = new MemberAction(Kind.REMOVE, g, target, clock, graceMillis);
        failIfProblem(a.removeProblem());
        return a;
    }

    public static MemberAction add(Group g, String name, LongSupplier clock, long graceMillis) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 20) {
            throw new IllegalArgumentException("Emri duhet të ketë 1 deri në 20 karaktere.");
        }
        failIfProblem(addProblem(g, clean));
        return new MemberAction(Kind.ADD, g, g.create(clean, Role.MEMBER), clock, graceMillis);
    }

    private static void failIfProblem(String problem) {
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    // ---------- kushtet ----------

    private static String addProblem(Group g, String name) {
        if (!g.meIsAdmin()) return "Duhet të jesh admin për të shtuar anëtarë.";
        if (g.contains(name)) return name + " është tashmë në grup.";
        if (g.isFull()) return "Grupi është plot.";
        return null;
    }

    private boolean memberIsInGroup() {
        Member current = group.find(member.name());
        return current != null && current.joinSeq() == member.joinSeq();
    }

    private String removeProblem() {
        if (!memberIsInGroup()) return member.name() + " nuk është më në grup.";
        if (kind == Kind.REMOVE && !group.meIsAdmin()) {
            return "Duhet të jesh admin për të hequr anëtarë.";
        }
        if (member.role() == Role.ADMIN && group.countAdmins() == 1 && group.size() > 1) {
            return "Nuk mund të largohet admini i fundit.";
        }
        return null;
    }

    private String undoProblem() {
        if (clock.getAsLong() - createdAt > graceMillis) {
            return "Afati i Undo ka skaduar.";
        }
        if (kind == Kind.ADD) {
            if (!memberIsInGroup()) return member.name() + " nuk është më në grup.";
            if (!group.meIsAdmin()) return "Nuk je admin, s'mund ta zhbësh shtimin.";
            return null;
        }
        if (group.contains(member.name())) {
            return member.name() + " është rikthyer tashmë nga dikush tjetër.";
        }
        if (group.isFull()) return "Grupi është plot.";
        if (kind == Kind.REMOVE && !group.meIsAdmin()) {
            return "Nuk je më admin, s'mund ta rikthesh anëtarin.";
        }
        return null;
    }

    private static void refuseIfProblem(String problem) {
        if (problem != null) {
            throw new ActionRefusedException(problem);
        }
    }

    // ---------- apply (kryerja dhe Redo) dhe reverse (Undo) ----------

    @Override
    public void apply(StringBuilder ignored) {
        if (kind == Kind.ADD) {
            refuseIfProblem(addProblem(group, member.name()));
            group.insert(member);
        } else {
            refuseIfProblem(removeProblem());
            group.removeBySeq(member.joinSeq());
        }
    }

    @Override
    public void reverse(StringBuilder ignored) {
        refuseIfProblem(undoProblem());
        if (kind == Kind.ADD) {
            group.removeBySeq(member.joinSeq());
        } else {
            group.insert(member);
        }
    }

    public Kind getKind() { return kind; }
    public Member getMember() { return member; }

    @Override
    public String describe() {
        return kind + " " + member.name() + " (" + member.role() + ")";
    }
}