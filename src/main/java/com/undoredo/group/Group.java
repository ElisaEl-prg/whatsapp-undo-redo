package com.undoredo.group;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Gjendja e grupit. Anëtarët ruhen në TreeMap sipas joinSeq,
 * kështu një anëtar i rikthyer del gjithmonë në vendin e vet.
 */
public class Group {

    /** Përdoruesi aktual i demos. */
    public static final String ME = "Ti";

    private final String name;
    private final int maxSize;
    private final TreeMap<Integer, Member> members = new TreeMap<>();
    private int nextSeq = 0;

    public Group(String name, int maxSize) {
        if (maxSize < 4) {
            throw new IllegalArgumentException("maxSize duhet të jetë të paktën 4.");
        }
        this.name = name;
        this.maxSize = maxSize;
        insert(create(ME, Role.ADMIN));
        insert(create("Ana", Role.ADMIN));
        insert(create("Bora", Role.MEMBER));
        insert(create("Klea", Role.MEMBER));
    }

    public String getName() { return name; }
    public int getMaxSize() { return maxSize; }
    public int size() { return members.size(); }
    public boolean isFull() { return members.size() >= maxSize; }

    /** Krijon një anëtar të ri me joinSeq të ri (pa e futur në grup). */
    public Member create(String memberName, Role role) {
        return new Member(nextSeq++, memberName, role);
    }

    public void insert(Member m) {
        if (contains(m.name())) {
            throw new IllegalStateException(m.name() + " është tashmë në grup.");
        }
        if (isFull()) {
            throw new IllegalStateException("Grupi është plot.");
        }
        members.put(m.joinSeq(), m);
    }

    public Member removeBySeq(int joinSeq) {
        return members.remove(joinSeq);
    }

    public Member find(String memberName) {
        if (memberName == null) return null;
        for (Member m : members.values()) {
            if (m.name().equalsIgnoreCase(memberName.trim())) return m;
        }
        return null;
    }

    public boolean contains(String memberName) {
        return find(memberName) != null;
    }

    public boolean meIsAdmin() {
        Member me = find(ME);
        return me != null && me.role() == Role.ADMIN;
    }

    public int countAdmins() {
        int n = 0;
        for (Member m : members.values()) {
            if (m.role() == Role.ADMIN) n++;
        }
        return n;
    }

    /** Anëtarët sipas radhës së hyrjes. */
    public List<Member> members() {
        return new ArrayList<>(members.values());
    }
}