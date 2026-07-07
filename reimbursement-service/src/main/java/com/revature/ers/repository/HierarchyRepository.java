package com.revature.ers.repository;

import com.revature.ers.model.Hierarchy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * The reporting graph. "existsByEmployee_UserId(x)" is the microservice's spelling of the
 * monolith's "findByEmployee(x).isEmpty()" top-of-chain test - an EXISTS query instead of
 * materializing the rows just to check emptiness.
 */
public interface HierarchyRepository extends JpaRepository<Hierarchy, Integer> {

    List<Hierarchy> findByEmployee_UserId(int userId);

    List<Hierarchy> findBySupervisor_UserId(int userId);

    boolean existsByEmployee_UserId(int userId);
}
