package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Album;
import com.rosklyar.cards.domain.Event;
import com.rosklyar.cards.domain.User;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * Created by rostyslavs on 11/21/2015.
 */
public class DefaultCardAssigner implements CardAssigner {

    private final Deque<Consumer<Event>> subscribers = new ConcurrentLinkedDeque<>();
    private final Album album;
    private final UserService userService;

    public DefaultCardAssigner(ConfigurationProvider configurationProvider,
                               UserService userService) {
        this.userService = userService;

        album = configurationProvider.get();
    }

    @Override
    public void assignCard(long userId, long cardId) {

        final User user = userService.getUser(userId);

        if (user.hasCard(cardId)) return;

        user.addCard(cardId);

    }

    @Override
    public void subscribe(Consumer<Event> consumer) {
        subscribers.push(consumer);
    }
}
