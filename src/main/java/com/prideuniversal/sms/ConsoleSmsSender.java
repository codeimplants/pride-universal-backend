package com.prideuniversal.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development sender: logs the OTP instead of sending it.
 *
 * <p>Active by default ({@code app.sms.provider=console}), so the system works
 * out of the box with no SMS account. The banner is deliberately loud - an
 * accidental deployment with this active should be obvious in the logs rather
 * than quietly handing out codes.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

    @Override
    public void sendOtp(String mobile, String otp) {
        log.warn("""

                ================= DEV SMS (NOT SENT) =================
                  To   : +91 {}
                  OTP  : {}
                  This provider only logs. Set app.sms.provider=msg91
                  with real credentials before going anywhere near
                  production.
                ======================================================
                """, mobile, otp);
    }

    @Override
    public String providerName() {
        return "console";
    }
}
