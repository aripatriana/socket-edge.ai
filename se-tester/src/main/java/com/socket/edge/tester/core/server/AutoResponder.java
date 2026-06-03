package com.socket.edge.tester.core.server;

import com.socket.edge.tester.core.iso.IsoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule-based mock issuer — generates an ISO 8583 response from a request.
 * Default behavior: echo key fields, set DE39=00 (approved).
 */
public class AutoResponder {

    private static final Logger log = LoggerFactory.getLogger(AutoResponder.class);

    private final boolean enabled;
    private final int delayMs;
    private final String responseCode;

    public AutoResponder(boolean enabled, int delayMs, String responseCode) {
        this.enabled = enabled;
        this.delayMs = delayMs;
        this.responseCode = responseCode != null ? responseCode : "00";
    }

    public IsoMessage respond(IsoMessage request) throws InterruptedException {
        if (!enabled) return null;
        if (delayMs > 0) Thread.sleep(delayMs);

        String requestMti = request.getMti();
        String responseMti = toResponseMti(requestMti);
        if (responseMti == null) {
            log.warn("Cannot derive response MTI from {}", requestMti);
            return null;
        }

        IsoMessage response = new IsoMessage(responseMti);
        echoFields(request, response);
        applyResponseFields(responseMti, request, response);

        log.debug("AutoRespond {} → {} key={}:{}", requestMti, responseMti,
                request.getField(11), request.getField(37));
        return response;
    }

    private String toResponseMti(String mti) {
        if (mti == null || mti.length() != 4) return null;
        char[] c = mti.toCharArray();
        c[2] = switch (c[2]) {
            case '0' -> '1'; // 0200 → 0210
            case '2' -> '3'; // 0420 → 0430 (advice)
            default  -> c[2];
        };
        return new String(c);
    }

    private void echoFields(IsoMessage from, IsoMessage to) {
        int[] echoDE = { 2, 3, 4, 7, 11, 12, 13, 14, 18, 22, 25, 32, 37, 41, 42, 43, 49, 70 };
        for (int de : echoDE) {
            String v = from.getField(de);
            if (v != null) to.setField(de, v);
        }
    }

    private void applyResponseFields(String responseMti, IsoMessage req, IsoMessage res) {
        boolean approved = "00".equals(responseCode);
        switch (responseMti) {
            case "0110", "0210" -> {
                if (approved) res.setField(38, "123456"); // Auth ID only on approval
                res.setField(39, responseCode);
            }
            default -> res.setField(39, responseCode);
        }
    }
}