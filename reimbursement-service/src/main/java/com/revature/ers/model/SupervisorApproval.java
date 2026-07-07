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
 * One supervisor's pending/decided vote on one request. Created (pending, approval=false)
 * for every direct supervisor of the requester when a request is submitted; resolved by
 * PUT /requests/{id}/approval.
 */
@Entity
@Table(name = "supervisor_approval", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approvalid")
    private Integer approvalId;

    // 2000-01-01 is the monolith's "not yet decided" sentinel; the schema only checks <= today
    @Column(name = "dateofpreviousupdate")
    private LocalDate dateOfPreviousUpdate;

    @ManyToOne
    @JoinColumn(name = "requestid")
    private Request request;

    @ManyToOne
    @JoinColumn(name = "hierarchyid")
    private Hierarchy hierarchy;

    @ManyToOne
    @JoinColumn(name = "statusid")
    private SupervisorApprovalStatus status;

    @Column(name = "approval")
    private boolean approval;
}
