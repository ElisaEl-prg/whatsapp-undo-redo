package com.undoredo.chat;

import java.time.*;
import java.util.*;

/**
 * Demo: Undo/Redo per "Delete for everyone" (soft delete) dhe "Remove/Leave member".
 * Modeli Command: secili veprim ruan sa duhet per ta kthyer vetveten.
 */
public class ChatUndoDemo {

    // ---------- Modeli i te dhenave ----------
    enum Role { ADMIN, MEMBER }
    enum MsgState { ACTIVE, DELETED_FOR_ALL }

    static class Message {
        final int id; final String sender;
        String text; MsgState state = MsgState.ACTIVE;
        Message(int id, String sender, String text) { this.id = id; this.sender = sender; this.text = text; }
        String view() {
            return sender + ": " + (state == MsgState.ACTIVE ? text : "[Ky mesazh u fshi]");
        }
    }

    static class Chat {
        final Map<Integer, Message> byId = new HashMap<>();     // kerkim O(1)
        final List<Message> order = new ArrayList<>();          // rendi i shfaqjes
        int nextId = 1;
        Message send(String sender, String text) {
            Message m = new Message(nextId++, sender, text);
            byId.put(m.id, m); order.add(m); return m;
        }
        void print() { for (Message m : order) System.out.println("  #" + m.id + " " + m.view()); }
    }

    static class Member {
        final String id; final Role role; final int joinSeq;
        Member(String id, Role role, int joinSeq) { this.id = id; this.role = role; this.joinSeq = joinSeq; }
    }

    static class Group {
        static final int MAX_MEMBERS = 4;                        // i vogel qe te testohet "grupi plot"
        final Map<String, Member> members = new HashMap<>();
        private int seq = 0;
        void add(String id, Role r) { members.put(id, new Member(id, r, seq++)); }
        void print() {
            List<Member> l = new ArrayList<>(members.values());
            l.sort(Comparator.comparingInt(m -> m.joinSeq));
            StringBuilder sb = new StringBuilder("  Grupi: ");
            for (Member m : l) sb.append(m.id).append('(').append(m.role).append(") ");
            System.out.println(sb);
        }
    }

    // ---------- Command ----------
    /** apply/reverse kthejne null kur kryhen, ose tekstin e gabimit kur nuk lejohen. */
    interface ChatAction {
        String describe();
        boolean isNoOp();
        String apply();
        String reverse();
    }

    /** Delete for everyone me soft delete: teksti hiqet nga mesazhi dhe mbahet vetem te veprimi. */
    static class DeleteForAll implements ChatAction {
        private final Chat chat; private final int msgId; private final String actor;
        private final Duration grace; private final Clock clock;
        private String original; private Instant doneAt;

        DeleteForAll(Chat chat, int msgId, String actor, Duration grace, Clock clock) {
            this.chat = chat; this.msgId = msgId; this.actor = actor; this.grace = grace; this.clock = clock;
        }
        public String describe() { return "DELETE_ALL #" + msgId; }
        public boolean isNoOp() {
            Message m = chat.byId.get(msgId);
            return m == null || m.state == MsgState.DELETED_FOR_ALL;
        }
        public String apply() {
            Message m = chat.byId.get(msgId);
            if (m == null) return "Mesazhi nuk ekziston";
            if (!m.sender.equals(actor)) return "Vetem derguesi mund ta fshije per te gjithe";
            original = m.text;               // ruajtja e tekstit origjinal (si DELETE te editori)
            m.text = null;                   // te marresit nuk e kane me tekstin
            m.state = MsgState.DELETED_FOR_ALL;
            doneAt = clock.instant();
            return null;
        }
        public String reverse() {
            if (clock.instant().isAfter(doneAt.plus(grace))) {
                original = null;             // privatesi: pas afatit teksti hiqet pergjithmone
                return "Afati i Undo ka skaduar, teksti u hoq pergjithmone";
            }
            Message m = chat.byId.get(msgId);
            m.text = original;
            m.state = MsgState.ACTIVE;
            return null;
        }
    }

    /** Remove member (nga admini) ose Leave group (nga vete anetari). */
    static class RemoveMember implements ChatAction {
        private final Group g; private final String target, actor; private final boolean leave;
        private final Duration grace; private final Clock clock;
        private Member saved; private Instant doneAt;

        RemoveMember(Group g, String target, String actor, boolean leave, Duration grace, Clock clock) {
            this.g = g; this.target = target; this.actor = actor; this.leave = leave; this.grace = grace; this.clock = clock;
        }
        public String describe() { return (leave ? "LEAVE " : "REMOVE ") + target; }
        public boolean isNoOp() { return !g.members.containsKey(target); }

        private String adminCheck() {
            if (leave) return null;                       // largimi i vetes nuk kerkon admin
            Member a = g.members.get(actor);
            return (a == null || a.role != Role.ADMIN) ? "Vetem admini mund te heqe anetar" : null;
        }
        public String apply() {
            String err = adminCheck();
            if (err != null) return err;
            Member m = g.members.remove(target);
            if (m == null) return "Nuk eshte anetar i grupit";
            saved = m;                                    // ruan anetarin me rolin dhe rendin e hyrjes
            doneAt = clock.instant();
            return null;
        }
        public String reverse() {
            if (clock.instant().isAfter(doneAt.plus(grace))) return "Afati i Undo ka skaduar";
            if (g.members.containsKey(target)) return "Anetari eshte rikthyer tashme nga dikush tjeter";
            if (g.members.size() >= Group.MAX_MEMBERS) return "Grupi eshte plot";
            String err = adminCheck();
            if (err != null) return "Nuk je me admin, Undo nuk lejohet";
            g.members.put(target, saved);                 // roli dhe rendi i hyrjes rikthehen
            return null;
        }
    }

    // ---------- Dy stack-e (si te dokumenti) ----------
    static class History {
        private final Deque<ChatAction> undoStack = new ArrayDeque<>();
        private final Deque<ChatAction> redoStack = new ArrayDeque<>();
        void run(ChatAction a) {
            if (a.isNoOp()) { System.out.println("  ! Asgje per te ndryshuar: " + a.describe()); return; }
            String err = a.apply();
            if (err != null) { System.out.println("  ! " + err); return; }
            undoStack.push(a); redoStack.clear();         // veprim i ri => redo zbrazet
            System.out.println("  + " + a.describe());
        }
        void undo() {
            if (undoStack.isEmpty()) { System.out.println("  ! Nuk ka cfare te anulohet"); return; }
            ChatAction a = undoStack.pop();
            String err = a.reverse();
            if (err != null) { System.out.println("  ! UNDO refuzohet: " + err + " (veprimi hiqet nga historia)"); return; }
            redoStack.push(a); System.out.println("  < UNDO " + a.describe());
        }
        void redo() {
            if (redoStack.isEmpty()) { System.out.println("  ! Nuk ka cfare te ri-ekzekutohet"); return; }
            ChatAction a = redoStack.pop();
            String err = a.apply();
            if (err != null) { System.out.println("  ! REDO refuzohet: " + err); return; }
            undoStack.push(a); System.out.println("  > REDO " + a.describe());
        }
    }

    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    public static void main(String[] args) {
        Duration grace = Duration.ofMinutes(5);
        TestClock clock = new TestClock();

        System.out.println("=== 1. Delete for everyone ===");
        Chat chat = new Chat();
        History h = new History();
        Message m1 = chat.send("Ana", "Takimi eshte ne 10");
        chat.send("Bora", "Ok");
        h.run(new DeleteForAll(chat, m1.id, "Ana", grace, clock)); chat.print();
        h.undo(); chat.print();
        h.redo(); chat.print();
        h.undo();                                   // kthehet
        h.run(new DeleteForAll(chat, m1.id, "Bora", grace, clock));   // jo derguesi
        h.run(new DeleteForAll(chat, m1.id, "Ana", grace, clock));
        clock.advance(Duration.ofMinutes(6));
        h.undo(); chat.print();                     // afati skadoi

        System.out.println("\n=== 2. Remove / Leave member ===");
        Group g = new Group();
        g.add("Ana", Role.ADMIN); g.add("Bora", Role.MEMBER); g.add("Klea", Role.ADMIN); g.add("Drini", Role.MEMBER);
        History gh = new History();
        g.print();
        gh.run(new RemoveMember(g, "Klea", "Ana", false, grace, clock)); g.print();
        gh.undo(); g.print();                       // Klea kthehet si ADMIN, ne vendin e vet
        gh.redo(); g.print();
        gh.undo();

        System.out.println("-- Nje admin tjeter e rikthen Boren para Undo");
        gh.run(new RemoveMember(g, "Bora", "Ana", false, grace, clock));
        g.members.put("Bora", new Member("Bora", Role.MEMBER, 99));   // nje admin tjeter e shtoi ne mes
        gh.undo(); g.print();

        System.out.println("-- Grupi plot");
        gh.run(new RemoveMember(g, "Drini", "Ana", false, grace, clock));
        g.add("Elda", Role.MEMBER); g.print();
        gh.undo(); g.print();

        System.out.println("-- Jo admin nuk heq anetar");
        gh.run(new RemoveMember(g, "Klea", "Bora", false, grace, clock));
        System.out.println("-- Leave nga vete anetari");
        gh.run(new RemoveMember(g, "Bora", "Bora", true, grace, clock)); g.print();
    }
}