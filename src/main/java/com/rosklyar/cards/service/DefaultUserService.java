package com.rosklyar.cards.service;

import com.rosklyar.cards.domain.User;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serhii Maksymchuk from Ubrainians for imCode
 * 04.10.18.
 */
public class DefaultUserService implements UserService {

    private final Map<Long, User> userIdToUser = new HashMap<>();

    @Override
    public User getUser(long id) {
        User user = userIdToUser.get(id);

        if (user == null) {
            user = new User(id);
            userIdToUser.put(id, user);
        }

        return user;
    }
}
