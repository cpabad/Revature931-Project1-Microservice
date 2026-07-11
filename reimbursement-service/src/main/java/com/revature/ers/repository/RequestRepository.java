package com.revature.ers.repository;

import com.revature.ers.model.EventLocation;
import com.revature.ers.model.Request;
import com.revature.ers.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The whole monolith RequestRepositoryImpl (~185 lines of Session/try/catch/commit/rollback
 * per method) collapses to this. findById/findAll/save/delete come from JpaRepository; the
 * custom finders below are derived from method names by Spring Data:
 *   - findByRequester                          -> WHERE requesteruserid = ?
 *   - findByRequesterAndRequestStatus_StatusId -> WHERE requesteruserid = ? AND statusid = ?
 *       (statusId 2 = pending, 1 = resolved - same as the monolith's two finders)
 *   - findByEventDateAndEventLocationAndRequester -> the natural-key lookup after insert
 */
@Repository
public interface RequestRepository extends JpaRepository<Request, Integer> {

    /*
     * The DTO fetch plan: associations are LAZY on the entity (the default costs nothing),
     * and each read that feeds a RequestResponse declares the graph it needs, so a page
     * comes back in ONE joined query instead of 1 + 3-per-row (N+1). The fetch decision
     * lives at the query, where it is visible - not on the entity, where EAGER silently
     * applied to every query in the service.
     */

    @Override
    @EntityGraph(attributePaths = {"requester", "requestStatus", "eventLocation", "eventLocation.cityStatePostal"})
    Page<Request> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"requester", "requestStatus", "eventLocation", "eventLocation.cityStatePostal"})
    Optional<Request> findById(Integer id);

    List<Request> findByRequester(User requester);

    // navigates Request.requester.userId -> WHERE requesteruserid = ? (handy for a REST path id)
    @EntityGraph(attributePaths = {"requester", "requestStatus", "eventLocation", "eventLocation.cityStatePostal"})
    List<Request> findByRequester_UserId(int userId);

    List<Request> findByRequesterAndRequestStatus_StatusId(User requester, int statusId);

    Optional<Request> findByEventDateAndEventLocationAndRequester(
            LocalDate eventDate, EventLocation eventLocation, User requester);
}
