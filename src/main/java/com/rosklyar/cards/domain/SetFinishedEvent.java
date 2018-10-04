package com.rosklyar.cards.domain;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
public class SetFinishedEvent extends Event {

    public SetFinishedEvent(long userId) {
        super(userId, Type.SET_FINISHED);
    }

}
