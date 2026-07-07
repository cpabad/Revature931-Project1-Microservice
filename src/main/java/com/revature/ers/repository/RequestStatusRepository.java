package com.revature.ers.repository;

import com.revature.ers.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reference data (1 = Resolved, 2 = Pending); findById is all the services need. */
public interface RequestStatusRepository extends JpaRepository<RequestStatus, Integer> {
}
