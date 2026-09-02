package com.prideuniversal.domain.flat;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.domain.flat.dto.FlatDtos.FlatDetail;
import com.prideuniversal.domain.flat.dto.FlatDtos.FlatRequest;
import com.prideuniversal.domain.flat.dto.FlatDtos.FlatSummary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The reference CRUD slice. Every other domain should follow this shape.
 *
 * <h2>Where the rules live</h2>
 * Authorisation is on the <em>service</em>, not the controller. A controller is
 * one of several possible entry points — a scheduled job, another service, a
 * future GraphQL layer — and a rule enforced only at the edge is a rule that
 * the next entry point silently skips. {@code @PreAuthorize} here is checked
 * whichever way the method is reached.
 *
 * <p>{@code SecurityConfig}'s URL rules still exist and still matter. They are
 * a coarse outer fence; these are the actual decision. Defence in depth means
 * a mistake in one is not a breach.
 *
 * <h2>Transactions</h2>
 * Reads are {@code readOnly = true}: it lets Hibernate skip dirty-checking the
 * loaded entities and lets Postgres route the transaction accordingly. Writes
 * get a normal transaction so an exception rolls the whole operation back.
 */
@Service
public class FlatService {

    /** Roles allowed to change the flat register. */
    private static final String CAN_EDIT = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY')";

    /** Deactivating a flat is a bigger deal than editing one. */
    private static final String CAN_DEACTIVATE = "hasAnyRole('ADMIN','CHAIRMAN')";

    private final FlatRepository flatRepository;

    public FlatService(FlatRepository flatRepository) {
        this.flatRepository = flatRepository;
    }

    // =====================================================================
    // Reads
    // =====================================================================

    /**
     * The society directory. Any authenticated user, summary view only.
     *
     * @param wing optional filter; null or blank returns every active flat.
     */
    @Transactional(readOnly = true)
    public List<FlatSummary> directory(String wing) {
        List<Flat> flats = (wing == null || wing.isBlank())
                ? flatRepository.findAllByActiveTrueOrderByWingAscFlatNumberAsc()
                : flatRepository.findAllByWingAndActiveTrueOrderByFlatNumberAsc(normaliseWing(wing));

        return flats.stream().map(FlatSummary::from).toList();
    }

    /**
     * The full record, including contact numbers and vehicle registrations.
     *
     * <p>Committee members see any flat; a resident sees only their own. Note
     * the 403 is raised <em>after</em> the lookup rather than by filtering the
     * query, so a resident probing for valid ids cannot tell "does not exist"
     * apart from "not yours" — both are refusals, but only one of them would
     * confirm that a flat id is real.
     */
    @Transactional(readOnly = true)
    public FlatDetail detail(UUID id) {
        Flat flat = findOrThrow(id);

        if (!AuthPrincipal.current().canAccessFlat(flat.getId())) {
            throw ApiException.forbidden("You can only view your own flat's full details.");
        }
        return FlatDetail.from(flat);
    }

    // =====================================================================
    // Writes
    // =====================================================================

    @PreAuthorize(CAN_EDIT)
    @Transactional
    public FlatDetail create(FlatRequest request) {
        String wing = normaliseWing(request.wing());
        String number = request.flatNumber().trim();

        if (flatRepository.existsByWingAndFlatNumber(wing, number)) {
            throw ApiException.conflict("Flat " + wing + "-" + number + " already exists.");
        }

        Flat flat = new Flat();
        apply(request, flat, wing, number);
        return FlatDetail.from(flatRepository.save(flat));
    }

    /**
     * Updates a flat, refusing the write if the client's copy is stale.
     *
     * <h3>Two layers of optimistic locking, on purpose</h3>
     * The explicit version comparison below catches the common case — a
     * committee member editing a screen someone else already saved — and gives
     * a clean 409 before any work is done.
     *
     * <p>It is not sufficient on its own: two requests can both read version 3,
     * both pass this check, and only then race to write. The second one is
     * stopped by Hibernate's own {@code @Version} guard at flush time, which
     * raises {@code ObjectOptimisticLockingFailureException} — a subclass of
     * the exception thrown here, so both paths surface identically as 409.
     * The check is the friendly early exit; the entity's version column is the
     * actual guarantee.
     */
    @PreAuthorize(CAN_EDIT)
    @Transactional
    public FlatDetail update(UUID id, FlatRequest request) {
        Flat flat = findOrThrow(id);

        if (request.version() == null) {
            throw ApiException.badRequest(
                    "version is required when updating a flat. Re-fetch the flat and send the "
                            + "version it returned.");
        }
        if (!Objects.equals(flat.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException(
                    "Flat " + id + " was modified by someone else");
        }

        String wing = normaliseWing(request.wing());
        String number = request.flatNumber().trim();

        // Renaming onto another flat's identity is a conflict, but renaming a
        // flat to the number it already has is not.
        flatRepository.findByWingAndFlatNumber(wing, number)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Flat " + wing + "-" + number + " already exists.");
                });

        apply(request, flat, wing, number);

        // saveAndFlush, not save. Hibernate increments @Version at flush time,
        // which without this happens at commit - after the DTO below has
        // already been built. The response would then echo the version the
        // client sent, while the database moved on. The client's next update
        // would send that stale version and get a 409 it could never escape:
        // the API would be telling it a lie about the row's current state.
        return FlatDetail.from(flatRepository.saveAndFlush(flat));
    }

    /**
     * Soft delete.
     *
     * <p>Flats are never physically deleted: maintenance bills, complaints and
     * payment history all reference them, and a hard delete would either
     * cascade away financial records or fail on a foreign key. Deactivating
     * removes it from the directory while leaving the ledger intact.
     */
    @PreAuthorize(CAN_DEACTIVATE)
    @Transactional
    public void deactivate(UUID id) {
        Flat flat = findOrThrow(id);
        if (!flat.isActive()) {
            return;   // idempotent: deactivating twice is not an error
        }
        flat.setActive(false);
        flatRepository.save(flat);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private Flat findOrThrow(UUID id) {
        return flatRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Flat", id));
    }

    /**
     * Copies the request onto the entity.
     *
     * <p>Field-by-field rather than a reflective bean-copy. Verbose, but a
     * copier that maps everything by name will happily overwrite {@code id} or
     * {@code version} the day someone adds them to the request record. What is
     * writable through the API should be legible in one place.
     */
    private void apply(FlatRequest request, Flat flat, String wing, String number) {
        requireTenantNamed(request);

        flat.setWing(wing);
        flat.setFlatNumber(number);
        flat.setFloor(request.floor());
        flat.setAreaSqft(request.areaSqft());

        flat.setOwnerName(request.ownerName().trim());
        flat.setOwnerContact(blankToNull(request.ownerContact()));
        flat.setOwnerAlternateContact(blankToNull(request.ownerAlternateContact()));

        flat.setTenant(request.isTenant());
        flat.setTenantName(blankToNull(request.tenantName()));
        flat.setTenantContact(blankToNull(request.tenantContact()));
        flat.setStaying(request.isStaying());

        flat.setTwoWheeler(request.twoWheeler());
        flat.setTwoWheelerCount(request.twoWheelerCount());
        flat.setTwoWheelerNumbers(blankToNull(request.twoWheelerNumbers()));
        flat.setFourWheeler(request.fourWheeler());
        flat.setFourWheelerCount(request.fourWheelerCount());
        flat.setFourWheelerNumbers(blankToNull(request.fourWheelerNumbers()));
        flat.setPermanentParking(request.permanentParking());

        flat.setShareCertificateNumber(blankToNull(request.shareCertificateNumber()));
        flat.setMembershipDate(request.membershipDate());
        flat.setNotes(blankToNull(request.notes()));
    }

    /**
     * Mirrors the {@code ck_flat_tenant_named} CHECK constraint so the caller
     * gets a 400 naming the field, rather than the database's 409.
     * The constraint stays as the backstop — this is a nicer message, not the
     * guarantee.
     */
    private void requireTenantNamed(FlatRequest request) {
        if (request.isTenant() && blankToNull(request.tenantName()) == null) {
            throw ApiException.badRequest(
                    "A flat marked as tenanted must have a tenant name.");
        }
    }

    /** Empty strings from web forms are absent values, not values. */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String normaliseWing(String wing) {
        return wing.trim().toUpperCase();
    }
}
