package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.AlbumSet;
import com.rosklyar.cards.domain.Card;
import com.rosklyar.cards.domain.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import static com.google.common.collect.Sets.newHashSet;
import static com.rosklyar.cards.domain.Event.Type.*;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static java.util.stream.LongStream.range;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.mockito.Mockito.when;

/**
 * Created by rostyslavs on 11/21/2015.
 */
@ExtendWith(MockitoExtension.class)
class CardAssignerTest {

    private final List<Long> users = range(0L, 10L).boxed().collect(toList());

    @Mock
    private ConfigurationProvider configurationProvider;

    private CardAssigner cardAssigner = new DefaultCardAssigner();

    @BeforeEach
    void configure() {
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
    }

    @Test
    void assigningCardsToUsers() {
        final List<Event> events = new CopyOnWriteArrayList<>();
        cardAssigner.subscribe(events::add);

        Album album = configurationProvider.get();
        ExecutorService executorService = newFixedThreadPool(10);
        final List<Card> allCards = album.sets.stream().map(set -> set.cards).flatMap(Collection::stream).collect(toList());

        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(20L), () -> {
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
}