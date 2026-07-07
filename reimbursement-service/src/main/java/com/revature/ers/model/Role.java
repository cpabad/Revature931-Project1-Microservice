package com.revature.ers.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** READ-ONLY reference copy (owned by auth-service); needed only because User carries a role. */
@Entity
@Table(name = "roles", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @Column(name = "roleid")
    private Integer roleId;

    // the column really is named "roles" (like the table) in the legacy schema
    @Column(name = "roles")
    private String role;
}
