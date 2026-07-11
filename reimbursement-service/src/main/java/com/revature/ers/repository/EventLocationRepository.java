package com.revature.ers.repository;

import com.revature.ers.model.EventLocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Locations are reference data for now; submit validates the id exists. */
public interface EventLocationRepository extends JpaRepository<EventLocation, Integer> {

    // submit() attaches this location to the created Request, and the POST response DTO
    // flattens city/state out of the postal join - fetch it with the location, not lazily
    // after the transaction has closed
    @Override
    @EntityGraph(attributePaths = "cityStatePostal")
    Optional<EventLocation> findById(Integer id);
}
