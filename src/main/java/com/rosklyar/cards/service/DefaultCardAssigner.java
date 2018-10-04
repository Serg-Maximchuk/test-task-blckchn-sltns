package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.AlbumFinishedEvent;
import com.rosklyar.cards.domain.AlbumSet;
import com.rosklyar.cards.domain.Card;
import com.rosklyar.cards.domain.Event;
import com.rosklyar.cards.domain.Identifiable;
import com.rosklyar.cards.domain.SetFinishedEvent;
import com.rosklyar.cards.domain.User;
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
    private final UserService userService;

    private final Map<Long, Set<Long>> userToCompletedSets = new HashMap<>();

    public DefaultCardAssigner(ConfigurationProvider configurationProvider,
                               UserService userService) {
        this.configurationProvider = Objects.requireNonNull(
                configurationProvider,
                "configuration provider is required to be non-null"
        );
        this.userService = Objects.requireNonNull(
                userService, "UserService is required to be non-null"
        );
    }

    @Override
    public void assignCard(long userId, long cardId) {

        final User user = userService.getUser(userId);

        if (user.hasCard(cardId)) return;

        final Album album = configurationProvider.get();
        final AlbumSet set = getAlbumSetByCard(album, cardId)
                .orElseThrow(() -> new WrongCardException(cardId));

        user.addCard(cardId);

        final boolean userHasJustCollectedWholeSet = user.getCardIs().containsAll(
                extractIds(set.cards)
        );

        if (!userHasJustCollectedWholeSet) return;

        final Set<Long> completedSets = Optional.ofNullable(userToCompletedSets.get(userId))
                .orElseGet(() -> {
                    final Set<Long> empty = new HashSet<>(album.sets.size());
                    userToCompletedSets.put(userId, empty);
                    return empty;
                });

        completedSets.add(set.id);

        notifySubscribers(new SetFinishedEvent(userId));

        final boolean userHasJustCollectedWholeAlbum = completedSets.containsAll(
                extractIds(album.sets)
        );

        if (userHasJustCollectedWholeAlbum) notifySubscribers(new AlbumFinishedEvent(userId));
    }

    void notifySubscribers(Event event) {
        for (Consumer<Event> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    Optional<AlbumSet> getAlbumSetByCard(Album album, long cardId) {

        for (AlbumSet set : album.sets) {
            for (Card card : set.cards) {
                if (card.id == cardId) return Optional.of(set);
            }
        }

        return Optional.empty();
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
