package com.revature.ers.repository;

import com.revature.ers.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repo for users. {@code findByUsername} is a derived query: Spring reads the method
 * name and generates {@code WHERE loginusername = ?} (the User.username property maps to the
 * loginusername column). Returns Optional so "no such user" is a value, not a thrown exception -
 * the login flow treats it the same as a bad password (see AuthController).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    /** "Taken by someone else" - the profile update must not trip over the user's own current value. */
    boolean existsByUsernameAndUserIdNot(String username, int userId);

    boolean existsByEmailAndUserIdNot(String email, int userId);
}
