package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.AlbumFinishedEvent;
import com.rosklyar.cards.domain.AlbumSet;
import com.rosklyar.cards.domain.Card;
import com.rosklyar.cards.domain.Event;
import com.rosklyar.cards.domain.SetFinishedEvent;
import com.rosklyar.cards.exception.WrongCardException;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import static com.google.common.collect.Sets.*;
import static com.rosklyar.cards.domain.Event.Type.*;
import static java.util.concurrent.Executors.*;
import static java.util.stream.Collectors.*;
import static java.util.stream.LongStream.*;
import static org.apache.commons.lang3.RandomUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Created by rostyslavs on 11/21/2015.
 */
@ExtendWith(MockitoExtension.class)
class CardAssignerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20L);
    private final List<Long> users = range(0L, 50L).boxed().collect(toList());

    @Mock
    private ConfigurationProvider configurationProvider;

    @InjectMocks
    private DefaultCardAssigner cardAssigner;

    @RepeatedTest(100)
    void assigningCardsToUsers() {

        given(configurationProvider.get()).willReturn(
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

        final Album album = configurationProvider.get();
        final ExecutorService executorService = newFixedThreadPool(10);

        try {
            final List<Card> allCards = album.sets.stream()
                    .map(set -> set.cards)
                    .flatMap(Collection::stream)
                    .collect(toList());

            assertTimeoutPreemptively(TIMEOUT, () -> {
                while (!albumsFinished(events, album)) {
                    executorService.submit(() -> {
                        Card card = allCards.get(nextInt(0, allCards.size()));
                        Long userId = users.get(nextInt(0, users.size()));
                        cardAssigner.assignCard(userId, card.id);
                    });
                }
            });

            assertEquals(events.stream().filter(event -> event.type == ALBUM_FINISHED).count(), users.size());
            assertEquals(events.stream().filter(event -> event.type == SET_FINISHED).count(), users.size() * album.sets.size());

        } finally {
            executorService.shutdownNow();
        }
    }

    private boolean albumsFinished(List<Event> events, Album album) {
        return events.size() == users.size() + users.size() * album.sets.size();
    }

    @Test
    void assignCard_When_AllCardsInAlbumSetCollected_Expect_SetFinishedEventFired() {
        final int userId = 13;
        final int cardId = 42;

        final List<Event> events = new ArrayList<>();
        cardAssigner.subscribe(events::add);

        given(configurationProvider.get()).willReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(new Card(cardId, "Eagle"))),
                        new AlbumSet(2L, "HomoSapience", newHashSet())
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

        final List<Event> events = new ArrayList<>();
        cardAssigner.subscribe(events::add);

        given(configurationProvider.get()).willReturn(
                new Album(1L, "Animals", newHashSet(
                        new AlbumSet(1L, "Birds", newHashSet(new Card(cardId, "Eagle"))),
                        new AlbumSet(2L, "HomoSapience", newHashSet())
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

        eventsSent.forEach(cardAssigner::publishEvent);

        assertEquals(eventsReceived, eventsSent);
    }

    @Test
    void assignCards_When_UserCollectsSets_Expect_ExactEventsReceived() {
        final int userId = 13;

        final List<Event> eventsReceived = new ArrayList<>();
        cardAssigner.subscribe(eventsReceived::add);

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
                )),
                new AlbumSet(3L, "HomoSapience", newHashSet())
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

    @Test
    void assignCards_When_UserCollectsSetsAndAlbum_Expect_ExactEventsReceived() {
        final int userId = 13;

        final List<Event> eventsReceived = new ArrayList<>();
        cardAssigner.subscribe(eventsReceived::add);

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
                new SetFinishedEvent(userId),
                new AlbumFinishedEvent(userId)
        );

        assertEquals(expectedEvents, eventsReceived);
    }
}
