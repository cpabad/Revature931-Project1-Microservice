package com.revature.ers.repository;

import com.revature.ers.model.EventLocation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Locations are reference data for now; submit validates the id exists. */
public interface EventLocationRepository extends JpaRepository<EventLocation, Integer> {
}
