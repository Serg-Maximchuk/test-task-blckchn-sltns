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
import java.util.function.Function;
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

        if (hasCard(userId, cardId)) return;

        final Album album = configurationProvider.get();
        final Set<AlbumSet> sets;

        if ((album == null) || ((sets = album.sets) == null)) return;

        final AlbumSet set = getAlbumSetByCard(sets, cardId)
                .orElseThrow(() -> new WrongCardException(cardId));

        final Set<Long> userCards = getUserCards(userId);
        userCards.add(cardId);

        final boolean userHasJustCollectedWholeSet = userCards.containsAll(
                extractIds(set.cards)
        );

        if (!userHasJustCollectedWholeSet) return;

        final Set<Long> completedSets = getAlreadyCompletedSets(userId);
        completedSets.add(set.id);

        publishEvent(new SetFinishedEvent(userId));

        final boolean userHasJustCollectedWholeAlbum = completedSets.containsAll(
                extractIds(sets)
        );

        if (userHasJustCollectedWholeAlbum) publishEvent(new AlbumFinishedEvent(userId));
    }

    private Set<Long> getUserCards(long userId) {
        return getOrPut(userId, userIdToCards, __ -> new HashSet<>());
    }

    private Set<Long> getAlreadyCompletedSets(long userId) {
        return getOrPut(userId, userIdToCompletedSets, __ -> new HashSet<>());
    }

    private <K, V> V getOrPut(K key, Map<K, V> warehouse, Function<K, V> source) {
        return warehouse.computeIfAbsent(key, source);
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

    boolean hasCard(long userId, long cardId) {
        return getUserCards(userId).contains(cardId);
    }

    private Optional<AlbumSet> getAlbumSetByCard(Collection<AlbumSet> sets, long cardId) {
        for (AlbumSet set : sets) {
            for (Card card : set.cards) {
                if (card.id == cardId) return Optional.of(set);
            }
        }

        return Optional.empty();
    }
}
