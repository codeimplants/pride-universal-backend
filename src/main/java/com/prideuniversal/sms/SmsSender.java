package com.prideuniversal.sms;

/**
 * How an OTP reaches a resident's phone.
 *
 * <p>An interface with swappable implementations, chosen by the
 * {@code app.sms.provider} property. That matters practically: SMS in India
 * needs TRAI DLT registration (an approved sender ID and message template tied
 * to a registered entity), which takes days to weeks. The console
 * implementation means every other part of this system can be built and tested
 * today, and switching to real SMS later is a config change, not a code change.
 */
public interface SmsSender {

    /**
     * @param mobile 10-digit Indian mobile number, no country code
     * @param otp    the plaintext code to deliver
     * @throws SmsDeliveryException if the message could not be handed off
     */
    void sendOtp(String mobile, String otp);

    /** Identifier used for logging and health reporting. */
    String providerName();

    class SmsDeliveryException extends RuntimeException {
        public SmsDeliveryException(String message) {
            super(message);
        }

        public SmsDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
