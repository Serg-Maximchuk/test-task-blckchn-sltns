package com.rosklyar.cards.service;

import java.util.function.Consumer;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
public interface Publisher<T> {
    void subscribe(Consumer<T> eventConsumer);
}
