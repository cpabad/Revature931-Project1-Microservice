package com.revature.ers.dto;

import com.revature.ers.model.CityStatePostal;
import com.revature.ers.model.EventLocation;
import com.revature.ers.model.Request;
import com.revature.ers.model.User;

import java.time.LocalDate;

/**
 * The wire shape of a reimbursement request - what a client is OWED, not what the schema
 * happens to store. Serializing the JPA entity coupled the API to the database: every mapped
 * association went on the wire (whole eager graph), any new column leaked by default, and the
 * JSON reshaped itself whenever a mapping changed. This record inverts that: fields appear on
 * the wire because someone put them here, deliberately.
 *
 * Two deliberate reshapes: the status OBJECT collapses to the string clients actually render,
 * and the location's postal-code join is flattened (city/state live inside the address here,
 * whatever the normalized schema says). The requester carries no email and no role - and could
 * never leak a password even if the users mapping someday grew one.
 */
public record RequestResponse(
        int requestId,
        double amount,
        LocalDate eventDate,
        String requestedEvent,
        String status,
        LocationResponse eventLocation,
        RequesterResponse requester) {

    /** Touches requestStatus, eventLocation(.cityStatePostal) and requester - callers must have fetched them (see the repository @EntityGraphs). */
    public static RequestResponse from(Request request) {
        return new RequestResponse(
                request.getRequestId(),
                request.getAmount(),
                request.getEventDate(),
                request.getRequestedEvent(),
                request.getRequestStatus().getStatus(),
                LocationResponse.from(request.getEventLocation()),
                RequesterResponse.from(request.getRequester()));
    }

    public record LocationResponse(int locationId, Integer streetNumber, String streetName, String city, String state) {
        static LocationResponse from(EventLocation location) {
            CityStatePostal postal = location.getCityStatePostal();
            return new LocationResponse(location.getLocationId(), location.getStreetNumber(),
                    location.getStreetName(), postal.getCity(), postal.getState());
        }
    }

    public record RequesterResponse(int userId, String username, String firstName, String lastName) {
        static RequesterResponse from(User user) {
            return new RequesterResponse(user.getUserId(), user.getUsername(), user.getFirstName(), user.getLastName());
        }
    }
}
