package ir.h0p3.securebankapi.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ConfiguredClientIpResolver implements ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final int MAX_IP_LENGTH = 45;

    private final RateLimitProperties properties;

    public ConfiguredClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (!properties.trustForwardedHeaders()) {
            return request.getRemoteAddr();
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null) {
            return request.getRemoteAddr();
        }

        String clientIp = forwardedFor.split(",", 2)[0].trim();
        if (isIpLiteral(clientIp)) {
            return clientIp;
        }
        return request.getRemoteAddr();
    }

    private boolean isIpLiteral(String value) {
        if (value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return false;
        }
        boolean containsSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '.' || character == ':') {
                containsSeparator = true;
            } else if (Character.digit(character, 16) < 0) {
                return false;
            }
        }
        if (!containsSeparator) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
