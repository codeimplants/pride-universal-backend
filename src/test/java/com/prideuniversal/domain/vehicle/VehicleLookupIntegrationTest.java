package com.prideuniversal.domain.vehicle;

import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleLookupIntegrationTest extends AbstractIntegrationTest {

    /** A let flat with a car and two bikes, under plates no other test uses. */
    private Flat flatWithVehicles(String car, String bikes) {
        Flat flat = createFlat();
        flat.setTenant(true);
        flat.setTenantName("Test Tenant");
        flat.setTenantContact(mobileFor(nextSeq()));
        flat.setFourWheeler(true);
        flat.setFourWheelerCount(1);
        flat.setFourWheelerNumbers(car);
        flat.setTwoWheeler(true);
        flat.setTwoWheelerCount(2);
        flat.setTwoWheelerNumbers(bikes);
        return flatRepository.save(flat);
    }

    @Test
    @DisplayName("the gate finds a registered car, tenant first, however the plate is written")
    void findsTheOwner() throws Exception {
        int n = nextSeq();
        String car = "TS 09 QA " + n;            // a series nothing else in the suite registers
        Flat flat = flatWithVehicles(car, "TS09QB" + n + ", TS 09 QC " + n);

        getAs(loginAs(Role.SECURITY), "/api/vehicles/lookup?plate=ts-09-qa-" + n)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("TS09QA" + n))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].plate").value("TS09QA" + n))
                .andExpect(jsonPath("$.matches[0].kind").value("four_wheeler"))
                .andExpect(jsonPath("$.matches[0].match").value("exact"))
                .andExpect(jsonPath("$.matches[0].flat_number").value(flat.getFlatNumber()))
                .andExpect(jsonPath("$.matches[0].contacts[0].relation").value("tenant"))
                .andExpect(jsonPath("$.matches[0].contacts[0].mobile").value(flat.getTenantContact()))
                .andExpect(jsonPath("$.matches[0].contacts[1].relation").value("owner"));

        // The camera reads Q as 0; the second bike is found inside a comma-separated list.
        getAs(loginAs(Role.COMMITTEE_MEMBER), "/api/vehicles/lookup?plate=TS090C" + n)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].match").value("close"))
                .andExpect(jsonPath("$.matches[0].kind").value("two_wheeler"));
    }

    @Test
    @DisplayName("an unknown plate is an empty answer, not an error")
    void unknownPlate() throws Exception {
        getAs(loginAs(Role.SECURITY), "/api/vehicles/lookup?plate=ZZ99ZZ" + nextSeq())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(0));
    }

    @Test
    @DisplayName("the last digits are enough to find candidates")
    void partial() throws Exception {
        int n = nextSeq();
        flatWithVehicles("AP 31 RS " + n, null);
        getAs(loginAs(Role.SECURITY), "/api/vehicles/lookup?plate=RS" + n)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[*].match", hasItem("partial")));
    }

    @Test
    @DisplayName("residents cannot look up plates, and a query must say enough")
    void fenced() throws Exception {
        getAs(loginAsNewResident(), "/api/vehicles/lookup?plate=MH12AB1234").andExpect(status().isForbidden());
        getAs(loginAs(Role.SECURITY), "/api/vehicles/lookup?plate=12").andExpect(status().isBadRequest());
    }
}
