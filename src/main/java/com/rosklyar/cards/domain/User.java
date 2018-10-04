package com.rosklyar.cards.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
public class User {

    public final long id;

    private final Set<Long> userCardIds = new HashSet<>();

    public User(long id) {
        this.id = id;
    }

    public boolean hasCard(long cardId) {
        return userCardIds.contains(cardId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                '}';
    }

    public void addCard(long cardId) {
        userCardIds.add(cardId);
    }
}
