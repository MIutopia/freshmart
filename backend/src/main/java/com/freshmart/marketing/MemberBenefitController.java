package com.freshmart.marketing;

import com.freshmart.auth.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CONSUMER')")
public class MemberBenefitController {
    private final MemberBenefitService memberBenefitService;

    public MemberBenefitController(MemberBenefitService memberBenefitService) {
        this.memberBenefitService = memberBenefitService;
    }

    @GetMapping("/api/marketing/coupons")
    public List<MemberBenefitService.CouponView> listClaimableCoupons(@AuthenticationPrincipal CurrentUser user) {
        return memberBenefitService.listClaimableCoupons(user);
    }

    @PostMapping("/api/marketing/coupons/{couponId}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void claimCoupon(@AuthenticationPrincipal CurrentUser user, @PathVariable long couponId) {
        memberBenefitService.claimCoupon(user, couponId);
    }

    @GetMapping("/api/membership")
    public MemberBenefitService.MembershipView membership(@AuthenticationPrincipal CurrentUser user) {
        return memberBenefitService.membership(user);
    }
}
