package com.revature.ers.service;

import com.revature.ers.dto.SubmitRequestDto;
import com.revature.ers.model.EventLocation;
import com.revature.ers.model.Hierarchy;
import com.revature.ers.model.Reimbursement;
import com.revature.ers.model.Request;
import com.revature.ers.model.SupervisorApproval;
import com.revature.ers.model.User;
import com.revature.ers.repository.EventLocationRepository;
import com.revature.ers.repository.HierarchyRepository;
import com.revature.ers.repository.ReimbursementRepository;
import com.revature.ers.repository.ReimbursementStatusRepository;
import com.revature.ers.repository.RequestRepository;
import com.revature.ers.repository.RequestStatusRepository;
import com.revature.ers.repository.SupervisorApprovalRepository;
import com.revature.ers.repository.SupervisorApprovalStatusRepository;
import com.revature.ers.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class RequestService {

    private static final int PENDING = 2;
    /** The monolith's "not yet decided" sentinel date, kept for schema/data parity. */
    private static final LocalDate NOT_YET_DECIDED = LocalDate.of(2000, 1, 1);

    private final RequestRepository requestRepository;
    private final RequestStatusRepository requestStatusRepository;
    private final EventLocationRepository eventLocationRepository;
    private final UserRepository userRepository;
    private final HierarchyRepository hierarchyRepository;
    private final SupervisorApprovalRepository supervisorApprovalRepository;
    private final SupervisorApprovalStatusRepository supervisorApprovalStatusRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final ReimbursementStatusRepository reimbursementStatusRepository;

    public RequestService(RequestRepository requestRepository,
                          RequestStatusRepository requestStatusRepository,
                          EventLocationRepository eventLocationRepository,
                          UserRepository userRepository,
                          HierarchyRepository hierarchyRepository,
                          SupervisorApprovalRepository supervisorApprovalRepository,
                          SupervisorApprovalStatusRepository supervisorApprovalStatusRepository,
                          ReimbursementRepository reimbursementRepository,
                          ReimbursementStatusRepository reimbursementStatusRepository) {
        this.requestRepository = requestRepository;
        this.requestStatusRepository = requestStatusRepository;
        this.eventLocationRepository = eventLocationRepository;
        this.userRepository = userRepository;
        this.hierarchyRepository = hierarchyRepository;
        this.supervisorApprovalRepository = supervisorApprovalRepository;
        this.supervisorApprovalStatusRepository = supervisorApprovalStatusRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.reimbursementStatusRepository = reimbursementStatusRepository;
    }

    /**
     * Persists a new request and fans out its approval chain: one pending vote per direct
     * supervisor of the requester, plus the pending reimbursement record hung off the
     * top-of-chain supervisor's vote. Ported from the monolith's RequestService.submitRequest,
     * minus its insert-then-re-find hack - save() hands back the generated id directly - and
     * wrapped in one transaction instead of one-per-write. Empty = unknown event location (-> 400).
     */
    @Transactional
    public Optional<Request> submit(int requesterUserId, SubmitRequestDto dto) {
        Optional<EventLocation> location = eventLocationRepository.findById(dto.eventLocationId());
        if (location.isEmpty()) {
            return Optional.empty();
        }
        // Unknown requester is unreachable over REST (the JWT subject always exists) but very
        // reachable via the Kafka intake, where a SOAP partner asserts the id - fail soft.
        Optional<User> requesterFound = userRepository.findById(requesterUserId);
        if (requesterFound.isEmpty()) {
            return Optional.empty();
        }
        User requester = requesterFound.get();
        Request request = requestRepository.save(new Request(
                null,
                dto.amount(),
                dto.eventDate(),
                location.get(),
                dto.requestedEvent(),
                requester,
                requestStatusRepository.findById(PENDING).orElseThrow()));

        for (Hierarchy h : hierarchyRepository.findByEmployee_UserId(requesterUserId)) {
            SupervisorApproval approval = supervisorApprovalRepository.save(new SupervisorApproval(
                    null, NOT_YET_DECIDED, request, h,
                    supervisorApprovalStatusRepository.findById(PENDING).orElseThrow(), false));
            if (!hierarchyRepository.existsByEmployee_UserId(h.getSupervisor().getUserId())) {
                reimbursementRepository.save(new Reimbursement(
                        null, request.getAmount(), NOT_YET_DECIDED, approval,
                        reimbursementStatusRepository.findById(PENDING).orElseThrow()));
            }
        }
        return Optional.of(request);
    }

    public Page<Request> findAll(Pageable pageable) {
        return requestRepository.findAll(pageable);
    }

    public Optional<Request> findById(int id) {
        return requestRepository.findById(id);
    }

    public List<Request> findByRequesterId(int userId) {
        return requestRepository.findByRequester_UserId(userId);
    }
}
