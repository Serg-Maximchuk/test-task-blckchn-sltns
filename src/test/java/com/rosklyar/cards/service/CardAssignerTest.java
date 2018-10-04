package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.AlbumFinishedEvent;
import com.rosklyar.cards.domain.AlbumSet;
import com.rosklyar.cards.domain.Card;
import com.rosklyar.cards.domain.Event;
import com.rosklyar.cards.domain.SetFinishedEvent;
import com.rosklyar.cards.domain.User;
import com.rosklyar.cards.exception.WrongCardException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import static com.google.common.collect.Sets.newHashSet;
import static com.rosklyar.cards.domain.Event.Type.*;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static java.util.stream.LongStream.range;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Created by rostyslavs on 11/21/2015.
 */
@ExtendWith(MockitoExtension.class)
class CardAssignerTest { // actually it should be DefaultCardAssignerTest

    private final List<Long> users = range(0L, 10L).boxed().collect(toList());

    @Mock
    private ConfigurationProvider configurationProvider;

    @Mock
    private UserService userService;

    @InjectMocks
    private DefaultCardAssigner cardAssigner;

    @Disabled
    @Test
    void assigningCardsToUsers() {

        when(configurationProvider.get()).thenReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(
                                new Card(1L, "Eagle"),
                                new Card(2L, "Cormorant"),
                                new Card(3L, "Sparrow"),
                                new Card(4L, "Raven")
                        )),
                        new AlbumSet(2L, "Fish", newHashSet(
                                new Card(5L, "Salmon"),
                                new Card(6L, "Mullet"),
                                new Card(7L, "Bream"),
                                new Card(8L, "Marline")
                        ))
                ))
        );

        final List<Event> events = new CopyOnWriteArrayList<>();
        cardAssigner.subscribe(events::add);

        Album album = configurationProvider.get();
        ExecutorService executorService = newFixedThreadPool(10);
        final List<Card> allCards = album.sets.stream().map(set -> set.cards).flatMap(Collection::stream).collect(toList());

        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2L), () -> {
            while (!albumsFinished(events, album)) {
                executorService.submit(() -> {
                    Card card = allCards.get(nextInt(0, allCards.size()));
                    Long userId = users.get(nextInt(0, users.size()));
                    cardAssigner.assignCard(userId, card.id);
                });
            }
        });

        assert events.stream().filter(event -> event.type == ALBUM_FINISHED).count() == users.size();
        assert events.stream().filter(event -> event.type == SET_FINISHED).count() == users.size() * album.sets.size();
    }

    private boolean albumsFinished(List<Event> events, Album album) {
        return events.size() == users.size() + users.size() * album.sets.size();
    }

    @Test
    void assignCard_When_NoCardsAssignedYet_Expect_CardAssigned() {
        final int userId = 13;
        final int cardId = 42;
        final User user = new User(userId);

        given(userService.getUser(userId)).willReturn(user);

        given(configurationProvider.get()).willReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(new Card(cardId, "Eagle")))
                ))
        );

        assertFalse(user.hasCard(cardId));

        cardAssigner.assignCard(userId, cardId);

        assertTrue(user.hasCard(cardId));
    }

    @Test
    void getAlbumSetByCard_When_CardAndSetExist_Expect_CorrectSetReturned() {
        final int cardId = 42;

        final AlbumSet expectedSet = new AlbumSet(1L, "Birds", newHashSet(
                new Card(cardId, "Eagle"),
                new Card(2L, "Cormorant"),
                new Card(3L, "Sparrow"),
                new Card(4L, "Raven")
        ));
        final AlbumSet wrongSet = new AlbumSet(2L, "Fish", newHashSet(
                new Card(5L, "Salmon"),
                new Card(6L, "Mullet"),
                new Card(7L, "Bream"),
                new Card(8L, "Marline")
        ));
        final Album album = new Album(1L, "Animals", newHashSet(expectedSet, wrongSet));

        final Optional<AlbumSet> foundAlbumSet = cardAssigner.getAlbumSetByCard(album, cardId);

        assertTrue(foundAlbumSet.isPresent());
        assertEquals(expectedSet, foundAlbumSet.get());
    }

    @Test
    void getAlbumSetByCard_When_CardIsNotInAnySet_Expect_EmptyOptional() {
        final int cardId = 42;

        final AlbumSet wrongSet = new AlbumSet(2L, "Fish", newHashSet(
                new Card(5L, "Salmon"),
                new Card(6L, "Mullet"),
                new Card(7L, "Bream"),
                new Card(8L, "Marline")
        ));
        final Album album = new Album(1L, "Animals", newHashSet(wrongSet));

        final Optional<AlbumSet> foundAlbumSet = cardAssigner.getAlbumSetByCard(album, cardId);

        assertFalse(foundAlbumSet.isPresent());
    }

    @Test
    void assignCard_When_AllCardsInAlbumSetCollected_Expect_SetFinishedEventFired() {
        final int userId = 13;
        final int cardId = 42;
        final User user = new User(userId);

        final List<Event> events = new ArrayList<>();
        cardAssigner.subscribe(events::add);

        given(userService.getUser(userId)).willReturn(user);

        given(configurationProvider.get()).willReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(new Card(cardId, "Eagle")))
                ))
        );

        cardAssigner.assignCard(userId, cardId);

        assertEquals(1, events.size());

        final Event event = events.get(0);

        assertEquals(SET_FINISHED, event.type);
    }

    @Test
    void assignCards_When_AllCardsInAlbumSetCollectedAndThenOneAssignedAgain_Expect_EventPublishedOnce() {
        final int userId = 13;
        final int cardId = 42;
        final User user = new User(userId);

        final List<Event> events = new ArrayList<>();
        cardAssigner.subscribe(events::add);

        given(userService.getUser(userId)).willReturn(user);

        given(configurationProvider.get()).willReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(new Card(cardId, "Eagle")))
                ))
        );

        cardAssigner.assignCard(userId, cardId);
        cardAssigner.assignCard(userId, cardId);

        assertEquals(1, events.size());

        final Event event = events.get(0);

        assertEquals(SET_FINISHED, event.type);
    }

    @Test
    void assignCard_When_CardIsNotInAnySet_Expect_CorrectExceptionThrown() {
        final int userId = 13;
        final int cardId = 42;
        final User user = new User(userId);

        given(userService.getUser(userId)).willReturn(user);

        given(configurationProvider.get()).willReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(new Card(1, "Eagle")))
                ))
        );

        assertThrows(WrongCardException.class, () -> cardAssigner.assignCard(userId, cardId));
    }

    @Test
    void notifySubscribers_When_ThereIsOneSubscriber_Expect_Notified() {
        final int userId = 13;

        final List<Event> eventsSent = Arrays.asList(
                new SetFinishedEvent(userId),
                new AlbumFinishedEvent(userId)
        );

        final List<Event> eventsReceived = new ArrayList<>();
        cardAssigner.subscribe(eventsReceived::add);

        eventsSent.forEach(cardAssigner::notifySubscribers);

        assertTrue(eventsReceived.containsAll(eventsSent));
    }

    @Test
    void assignCards_When_UserCollectsSets_Expect_ExactEventsReceived() {
        final int userId = 13;
        final User user = new User(userId);

        final List<Event> eventsReceived = new ArrayList<>();
        cardAssigner.subscribe(eventsReceived::add);

        given(userService.getUser(userId)).willReturn(user);

        final Album album = new Album(1L, "Animals", newHashSet(
                new AlbumSet(1L, "Birds", newHashSet(
                        new Card(1L, "Eagle"),
                        new Card(2L, "Cormorant"),
                        new Card(3L, "Sparrow"),
                        new Card(4L, "Raven")
                )),
                new AlbumSet(2L, "Fish", newHashSet(
                        new Card(5L, "Salmon"),
                        new Card(6L, "Mullet"),
                        new Card(7L, "Bream"),
                        new Card(8L, "Marline")
                ))
        ));

        given(configurationProvider.get()).willReturn(album);

        album.sets.stream()
                .map(set -> set.cards)
                .flatMap(Collection::stream)
                .map(Card::getId)
                .forEach(cardId -> cardAssigner.assignCard(userId, cardId));

        final List<Event> expectedEvents = Arrays.asList(
                new SetFinishedEvent(userId),
                new SetFinishedEvent(userId)
        );

        assertEquals(expectedEvents, eventsReceived);
    }
}
