package com.revature.ers.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * READ-ONLY reference copy of the user, deliberately trimmed. auth-service OWNS users/roles;
 * this service only needs "who is this requester" for its graph, so the mapping has no
 * password column at all - the credential physically cannot leak from this service because
 * it is never loaded. Each service mapping only the columns it needs is the shared-database
 * spelling of a service boundary (the next step would be separate databases).
 */
@Entity
@Table(name = "users", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "userid")
    private Integer userId;

    @Column(name = "loginusername")
    private String username;

    @Column(name = "firstname")
    private String firstName;

    @Column(name = "lastname")
    private String lastName;

    @Column(name = "email")
    private String email;

    @ManyToOne
    @JoinColumn(name = "roles")
    private Role role;
}
