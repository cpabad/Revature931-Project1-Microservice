package com.revature.ers.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * The money side of a request. Note the schema shape: a reimbursement points at the
 * top-of-chain supervisor's approval (finalapprovalid), not at the request directly -
 * "which request" is reached through finalApproval.request.
 */
@Entity
@Table(name = "reimbursement", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reimbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reimbursementid")
    private Integer reimbursementId;

    @Column(name = "amount")
    private double amount;

    // 2000-01-01 sentinel until actually awarded (schema checks <= today)
    @Column(name = "dateawarded")
    private LocalDate dateAwarded;

    @ManyToOne
    @JoinColumn(name = "finalapprovalid")
    private SupervisorApproval finalApproval;

    @ManyToOne
    @JoinColumn(name = "statusid")
    private ReimbursementStatus status;
}
