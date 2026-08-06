package ir.h0p3.securebankapi.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpResolver {

    String resolve(HttpServletRequest request);
}
