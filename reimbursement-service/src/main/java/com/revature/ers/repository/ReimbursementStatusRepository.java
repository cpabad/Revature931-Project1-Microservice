package com.revature.ers.repository;

import com.revature.ers.model.ReimbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reference data (1 = Resolved, 2 = Pending); findById is all the services need. */
public interface ReimbursementStatusRepository extends JpaRepository<ReimbursementStatus, Integer> {
}
