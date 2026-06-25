package com.revature.ers.dto;

/**
 * Inbound /login body. A Java record = immutable carrier with a canonical constructor, accessors
 * (username()/password()), equals/hashCode/toString auto-generated - the idiomatic Java 17 DTO,
 * and Jackson binds JSON straight into it. NOT a JPA entity: this never touches the database, it
 * only ferries the two submitted fields.
 */
public record LoginRequest(String username, String password) {
}
