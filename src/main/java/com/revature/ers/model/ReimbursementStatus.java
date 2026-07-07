package com.revature.ers.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Reference data: 1 = Resolved, 2 = Pending (same ids as the other status tables). */
@Entity
@Table(name = "reimbursement_status", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReimbursementStatus {

    @Id
    @Column(name = "statusid")
    private Integer statusId;

    @Column(name = "status")
    private String status;
}
