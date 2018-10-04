package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.AlbumSet;
import com.rosklyar.cards.domain.Card;
import com.rosklyar.cards.domain.Event;
import com.rosklyar.cards.domain.SetFinishedEvent;
import com.rosklyar.cards.domain.User;
import com.rosklyar.cards.exception.WrongCardException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultCardAssigner implements CardAssigner {

    private final List<Consumer<Event>> subscribers = new ArrayList<>();
    private final ConfigurationProvider configurationProvider;
    private final UserService userService;

    public DefaultCardAssigner(ConfigurationProvider configurationProvider,
                               UserService userService) {
        this.configurationProvider = configurationProvider;
        this.userService = userService;
    }

    @Override
    public void assignCard(long userId, long cardId) {

        final User user = userService.getUser(userId);

        if (user.hasCard(cardId)) return;

        user.addCard(cardId);

        final AlbumSet set = getAlbumSetByCard(configurationProvider.get(), cardId)
                .orElseThrow(() -> new WrongCardException(cardId));

        final boolean userHasJustCollectedWholeSet = ThinAlbumSet.of(set)
                .cardIds.containsAll(user.getCardIs());

        if (userHasJustCollectedWholeSet) notifySubscribers(new SetFinishedEvent(userId));
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

    static class ThinAlbumSet {
        final long id;
        final Set<Long> cardIds;

        ThinAlbumSet(long id, Set<Long> cardIds) {
            this.id = id;
            this.cardIds = cardIds;
        }

        static ThinAlbumSet of(AlbumSet set) {
            final Set<Long> cardIds = set.cards.stream()
                    .map(Card::getId)
                    .collect(Collectors.toSet());

            return new ThinAlbumSet(set.id, cardIds);
        }
    }
}
