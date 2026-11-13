package com.prideuniversal.domain.marketplace;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.marketplace.dto.ListingDtos.ListingRequest;
import com.prideuniversal.domain.marketplace.dto.ListingDtos.ListingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The residents' marketplace. Anyone signed in reads and posts; a listing belongs
 * to its poster, who (or the committee, for moderation) may edit it, mark it sold
 * or remove it. A giveaway is always free; a free listing always has price 0.
 */
@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final FlatRepository flatRepository;

    public ListingService(ListingRepository listingRepository, FlatRepository flatRepository) {
        this.listingRepository = listingRepository;
        this.flatRepository = flatRepository;
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> list(ListingType type, ListingCategory category, boolean mine) {
        List<Listing> listings = mine
                ? listingRepository.postedBy(AuthPrincipal.current().userId())
                : listingRepository.active(type, category);
        return listings.stream().map(ListingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ListingResponse get(UUID id) {
        return ListingResponse.from(findLive(id));
    }

    @Transactional
    public ListingResponse create(ListingRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        Listing l = new Listing();
        l.setPostedById(caller.userId());
        if (caller.flatId() != null) {
            flatRepository.findById(caller.flatId()).ifPresent(f -> {
                l.setWing(f.getWing());
                l.setFlatNumber(f.getFlatNumber());
            });
        }
        apply(request, l, caller);
        l.setStatus(ListingStatus.ACTIVE);
        return ListingResponse.from(listingRepository.save(l));
    }

    @Transactional
    public ListingResponse update(UUID id, ListingRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        Listing l = findLive(id);
        requireOwnerOrCommittee(caller, l);
        Versions.requireCurrent(l.getVersion(), request.version(), "listing");
        apply(request, l, caller);
        if (request.status() != null) {
            l.setStatus(request.status());
        }
        return ListingResponse.from(listingRepository.saveAndFlush(l));
    }

    @Transactional
    public void remove(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        Listing l = findLive(id);
        requireOwnerOrCommittee(caller, l);
        l.setStatus(ListingStatus.REMOVED);
        listingRepository.save(l);
    }

    // ---------------------------------------------------------------------

    private Listing findLive(UUID id) {
        return listingRepository.findById(id)
                .filter(l -> l.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> ApiException.notFound("Listing", id));
    }

    private static void requireOwnerOrCommittee(AuthPrincipal caller, Listing l) {
        if (!caller.isManagement() && !Objects.equals(caller.userId(), l.getPostedById())) {
            throw ApiException.forbidden("Only the person who posted this listing can change it.");
        }
    }

    private static void apply(ListingRequest r, Listing l, AuthPrincipal caller) {
        ListingType type = r.listingType() == null ? ListingType.SELL : r.listingType();
        boolean free = type == ListingType.GIVE_AWAY || r.isFree();
        l.setTitle(r.title().trim());
        l.setDescription(Text.blankToNull(r.description()));
        l.setCategory(r.category());
        l.setListingType(type);
        l.setFree(free);
        l.setPrice(free || r.price() == null ? BigDecimal.ZERO : r.price());

        String name = Text.blankToNull(r.contactName());
        String number = Text.blankToNull(r.contactNumber());
        if (l.getContactName() == null || name != null) {
            l.setContactName(name != null ? name : caller.displayName());
        }
        if (l.getContactNumber() == null || number != null) {
            l.setContactNumber(number != null ? number : caller.mobile());
        }
    }
}
