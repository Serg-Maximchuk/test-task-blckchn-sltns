package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.Event;

/**
 * Created by rostyslavs on 11/21/2015.
 */
public interface CardAssigner extends Publisher<Event> {
    void assignCard(long userId, long cardId);
}
