package com.revature.ers.repository;

import com.revature.ers.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to the user reference data (owned by auth-service). Only the inherited
 * findById is used - this service never creates, updates, or authenticates users.
 */
public interface UserRepository extends JpaRepository<User, Integer> {
}
