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

/**
 * One supervisor->employee edge of the reporting graph (the monolith's "Hierarchy").
 * A user with no row as employee is a top-of-chain supervisor; an employee may have
 * several supervisors (the seed has exactly that), which is why request approval
 * fans out to multiple rows.
 */
@Entity
@Table(name = "employee_supervisor_jt", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hierarchyid")
    private Integer hierarchyId;

    @ManyToOne
    @JoinColumn(name = "useridsupervisor")
    private User supervisor;

    @ManyToOne
    @JoinColumn(name = "useridemployee")
    private User employee;
}
