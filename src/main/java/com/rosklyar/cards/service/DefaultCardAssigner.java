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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultCardAssigner implements CardAssigner {

    private static final Function<Long, Set<Long>> setFactory = __ -> new HashSet<>();

    private final List<Consumer<Event>> subscribers = new ArrayList<>();

    private final Map<Long, Set<Long>> userIdToCompletedSets = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> userIdToCards = new ConcurrentHashMap<>();

    private final ConfigurationProvider configurationProvider;

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

        if (!hasJustCollectedAlbumSet(userId, cardId, set)) return;

        publishEvent(new SetFinishedEvent(userId));

        if (hasJustCollectedWholeAlbum(userId, set.id, sets)) {
            publishEvent(new AlbumFinishedEvent(userId));
        }
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

    private boolean hasJustCollectedAlbumSet(long userId, long cardId, AlbumSet set) {
        return hasJustCollectedAll(userIdToCards, userId, cardId, set.cards);
    }

    private boolean hasJustCollectedWholeAlbum(long userId, long setId, Set<? extends Identifiable<Long>> sets) {
        return hasJustCollectedAll(userIdToCompletedSets, userId, setId, sets);
    }

    private boolean hasJustCollectedAll(Map<Long, Set<Long>> register,
                                        long key, long newVal,
                                        Set<? extends Identifiable<Long>> identifiable) {

        final Set<Long> collected = register.computeIfAbsent(key, setFactory);

        synchronized (collected) {
            return collected.add(newVal) && collected.containsAll(extractIds(identifiable));
        }
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
