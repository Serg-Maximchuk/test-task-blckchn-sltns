package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.AlbumFinishedEvent;
import com.rosklyar.cards.domain.AlbumSet;
import com.rosklyar.cards.domain.Card;
import com.rosklyar.cards.domain.Event;
import com.rosklyar.cards.domain.Identifiable;
import com.rosklyar.cards.domain.SetFinishedEvent;
import com.rosklyar.cards.exception.WrongCardException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultCardAssigner implements CardAssigner {

    private final List<Consumer<Event>> subscribers = new ArrayList<>();
    private final ConfigurationProvider configurationProvider;

    private final Map<Long, Set<Long>> userIdToCompletedSets = new HashMap<>();
    private final Map<Long, Set<Long>> userIdToCards = new HashMap<>();

    public DefaultCardAssigner(ConfigurationProvider configurationProvider) {
        this.configurationProvider = Objects.requireNonNull(
                configurationProvider,
                "configuration provider is required to be non-null"
        );
    }

    @Override
    public void assignCard(long userId, long cardId) {

        final Set<AlbumSet> sets = getAlbumSets();
        if (sets == null) return;

        final AlbumSet set = getAlbumSetByCard(sets, cardId)
                .orElseThrow(() -> new WrongCardException(cardId));

        synchronized (userIdToCards) {
            Set<Long> userCards = userIdToCards.computeIfAbsent(userId, k -> new HashSet<>());
            if (!userCards.add(cardId)) return;
            if (!hasJustCollectedAlbumSet(userId, set.cards)) return;
        }

        publishEvent(new SetFinishedEvent(userId));

        synchronized (userIdToCompletedSets) {
            if (!hasJustCollectedWholeAlbum(userId, set.id, sets)) return;
        }

        publishEvent(new AlbumFinishedEvent(userId));
    }

    private Set<AlbumSet> getAlbumSets() {
        final Album album = configurationProvider.get();
        return (album == null) ? null : album.sets;
    }

    private Optional<AlbumSet> getAlbumSetByCard(Collection<AlbumSet> sets, long cardId) {
        for (AlbumSet set : sets) {
            for (Card card : set.cards) {
                if (card.id == cardId) return Optional.of(set);
            }
        }

        return Optional.empty();
    }

    private boolean hasJustCollectedWholeAlbum(long userId, long setId, Set<? extends Identifiable<Long>> sets) {
        final Set<Long> collected = userIdToCompletedSets.computeIfAbsent(userId, __ -> new HashSet<>());
        collected.add(setId);
        return collected.containsAll(extractIds(sets));
    }

    private boolean hasJustCollectedAlbumSet(long userId, Set<? extends Identifiable<Long>> cards) {
        return userIdToCards.get(userId).containsAll(extractIds(cards));
    }

    void publishEvent(Event event) {
        for (Consumer<Event> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    @Override
    public void subscribe(Consumer<Event> consumer) {
        subscribers.add(consumer);
    }

    private Set<Long> extractIds(Collection<? extends Identifiable<Long>> identifiable) {
        return identifiable.stream()
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
    }
}
