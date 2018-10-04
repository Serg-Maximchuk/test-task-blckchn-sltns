package com.rosklyar.cards.domain;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
public class AlbumFinishedEvent extends Event {

    public AlbumFinishedEvent(long userId) {
        super(userId, Type.ALBUM_FINISHED);
    }
}
