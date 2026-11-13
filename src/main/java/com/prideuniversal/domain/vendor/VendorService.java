package com.prideuniversal.domain.vendor;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.vendor.dto.VendorDtos.VendorRequest;
import com.prideuniversal.domain.vendor.dto.VendorDtos.VendorResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The vendor directory. Anyone signed in reads it and may recommend a vendor;
 * a recommendation starts unverified until the committee verifies it (the Base44
 * form let anyone tick "verified"). The committee, or whoever added a vendor, may
 * edit or remove it. Removal deactivates.
 */
@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final FlatRepository flatRepository;

    public VendorService(VendorRepository vendorRepository, FlatRepository flatRepository) {
        this.vendorRepository = vendorRepository;
        this.flatRepository = flatRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> list(ServiceType type, String q) {
        String term = Text.blankToNull(q);
        return vendorRepository.search(type, term == null ? "" : term).stream().map(VendorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse get(UUID id) {
        return VendorResponse.from(findActive(id));
    }

    @Transactional
    public VendorResponse create(VendorRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        Vendor v = new Vendor();
        v.setAddedById(caller.userId());
        v.setAddedByName(caller.displayName());
        if (caller.flatId() != null) {
            flatRepository.findById(caller.flatId())
                    .ifPresent(f -> v.setAddedByFlat(f.getWing() + "-" + f.getFlatNumber()));
        }
        apply(request, v, caller.isManagement());
        return VendorResponse.from(vendorRepository.save(v));
    }

    @Transactional
    public VendorResponse update(UUID id, VendorRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        Vendor v = findActive(id);
        requireCanEdit(caller, v);
        Versions.requireCurrent(v.getVersion(), request.version(), "vendor");
        apply(request, v, caller.isManagement());
        return VendorResponse.from(vendorRepository.saveAndFlush(v));
    }

    @Transactional
    public void deactivate(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        Vendor v = findActive(id);
        requireCanEdit(caller, v);
        v.setActive(false);
        vendorRepository.save(v);
    }

    // ---------------------------------------------------------------------

    private Vendor findActive(UUID id) {
        return vendorRepository.findById(id).filter(Vendor::isActive)
                .orElseThrow(() -> ApiException.notFound("Vendor", id));
    }

    private static void requireCanEdit(AuthPrincipal caller, Vendor v) {
        if (!caller.isManagement() && !Objects.equals(caller.userId(), v.getAddedById())) {
            throw ApiException.forbidden("Only the committee or the person who added this vendor can change it.");
        }
    }

    private static void apply(VendorRequest r, Vendor v, boolean committee) {
        v.setName(r.name().trim());
        v.setServiceType(r.serviceType());
        v.setContact(r.contact().trim());
        v.setAlternateContact(Text.blankToNull(r.alternateContact()));
        v.setDescription(Text.blankToNull(r.description()));
        v.setAvailability(Text.blankToNull(r.availability()));
        v.setRating(r.rating() == null ? null : r.rating().setScale(1, RoundingMode.HALF_UP));
        if (committee && r.approved() != null) {
            v.setApproved(r.approved());
        }
    }
}
