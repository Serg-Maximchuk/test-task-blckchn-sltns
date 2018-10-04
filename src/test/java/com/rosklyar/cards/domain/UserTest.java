package com.rosklyar.cards.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
        final int cardId = 35;
        assertFalse(user.hasCard(cardId));
    }
}
