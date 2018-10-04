package com.rosklyar.cards.exception;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
public class WrongCardException extends RuntimeException {
    public WrongCardException(long cardId) {
        super("Card with id " + cardId + " is not registered in any set!");
    }
}
