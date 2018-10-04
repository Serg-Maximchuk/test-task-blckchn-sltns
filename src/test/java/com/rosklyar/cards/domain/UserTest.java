package com.rosklyar.cards.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
@ExtendWith(MockitoExtension.class)
class UserTest {

    private User user = new User(42L);

    @BeforeEach
    void setUp() {
        user = new User(42L);
    }

    @Test
    void hasCard_When_NoCardsAssignedYet_Expect_False() {
        final int cardId = 0;
        assertFalse(user.hasCard(cardId));
    }

    @Test
    void addCard_When_NoCardsAssignedBefore_Expect_Added() {
        final int cardId0 = 0;
        final int cardId1 = 1;

        user.addCard(cardId0);

        assertTrue(user.hasCard(cardId0));

        user.addCard(cardId1);

        assertTrue(user.hasCard(cardId1));
    }
}
