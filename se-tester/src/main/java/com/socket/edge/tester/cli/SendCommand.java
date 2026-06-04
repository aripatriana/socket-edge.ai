package com.socket.edge.tester.cli;

import com.socket.edge.tester.core.client.IsoClient;
import com.socket.edge.tester.core.iso.IsoMessage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Command(
    name        = "send",
    description = "Send a single ISO 8583 transaction manually and print full request/response.",
    mixinStandardHelpOptions = true
)
public class SendCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target host")
    private String host;

    @Option(names = {"-p", "--port"}, required = true, description = "Target port")
    private int port;

    @Option(names = {"--mti"}, defaultValue = "0200",
            description = "Message type indicator (default: 0200)")
    private String mti;

    @Option(names = {"-f", "--field"},
            description = "Set field value: -f DE=VALUE (e.g. -f 3=000000 -f 4=000000001000). " +
                          "DE11 and DE37 are auto-generated if omitted.",
            arity = "0..*")
    private List<String> fields = new ArrayList<>();

    @Option(names = {"-n", "--repeat"}, defaultValue = "1",
            description = "Number of times to send (default: 1)")
    private int repeat;

    @Option(names = {"--interval-ms"}, defaultValue = "0",
            description = "Interval between repeats in ms (default: 0)")
    private long intervalMs;

    @Option(names = {"--header-bytes"}, defaultValue = "4",
            description = "Frame header size: 2=SE-Core, 4=standalone (default: 4)")
    private int headerBytes;

    @Option(names = {"--timeout-ms"}, defaultValue = "5000",
            description = "Response timeout in ms (default: 5000)")
    private long timeoutMs;

    @Option(names = {"--no-defaults"},
            description = "Disable default field population — only fields from -f are sent")
    private boolean noDefaults;

    private static final AtomicLong TX_COUNTER = new AtomicLong(1);

    private static final DateTimeFormatter DTF_TRANS = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter DTF_TIME  = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTF_DATE  = DateTimeFormatter.ofPattern("MMdd");

    @Override
    public Integer call() throws Exception {
        IsoClient client = new IsoClient();
        try {
            client.connect(host, port, 5000, headerBytes);
        } catch (Exception e) {
            System.err.printf("[send] Cannot connect to %s:%d — %s%n", host, port, e.getMessage());
            return 1;
        }

        int failures = 0;
        for (int i = 0; i < repeat; i++) {
            if (i > 0 && intervalMs > 0) Thread.sleep(intervalMs);
            boolean ok = doSend(client, i + 1);
            if (!ok) failures++;
        }

        client.disconnect();
        return failures > 0 ? 1 : 0;
    }

    private boolean doSend(IsoClient client, int seq) {
        long txId = TX_COUNTER.getAndIncrement();
        IsoMessage request = buildMessage(txId);

        if (repeat > 1) {
            System.out.printf("%n── TX #%d %s%n", seq, "─".repeat(55 - String.valueOf(seq).length()));
        }

        printMessage("REQUEST", request);

        long sentMs = System.currentTimeMillis();
        IsoMessage response = null;
        String errorMsg = null;

        try {
            response = client.send(request, timeoutMs);
        } catch (TimeoutException e) {
            errorMsg = "TIMEOUT after " + timeoutMs + "ms";
        } catch (Exception e) {
            errorMsg = "ERROR: " + e.getMessage();
        }

        long latencyMs = System.currentTimeMillis() - sentMs;

        if (response != null) {
            printMessage("RESPONSE", response);
        }

        printResult(response, errorMsg, latencyMs);
        return response != null;
    }

    // ── message builder ───────────────────────────────────────────────────────

    private IsoMessage buildMessage(long txId) {
        String stan = String.format("%06d", (txId % 999999) + 1);
        String rrn  = String.format("%012d", txId % 1_000_000_000_000L);
        LocalDateTime now = LocalDateTime.now();

        IsoMessage msg = new IsoMessage(mti);

        if (!noDefaults) {
            // sensible defaults for a 0200 — caller can override via -f
            msg.setField(2,  "4000123456789010");
            msg.setField(3,  "000000");
            msg.setField(4,  "000000000100");
            msg.setField(7,  now.format(DTF_TRANS));
            msg.setField(11, stan);
            msg.setField(12, now.format(DTF_TIME));
            msg.setField(13, now.format(DTF_DATE));
            msg.setField(22, "051");
            msg.setField(37, rrn);
            msg.setField(41, "SESEND01");
            msg.setField(42, "SE_SEND_TEST   ");
            msg.setField(49, "360");
        }

        // apply user-supplied fields (override defaults)
        for (String spec : fields) {
            int eq = spec.indexOf('=');
            if (eq < 1) {
                System.err.println("[send] Ignoring invalid field spec (expected DE=VALUE): " + spec);
                continue;
            }
            try {
                int de = Integer.parseInt(spec.substring(0, eq).trim());
                String value = spec.substring(eq + 1);
                msg.setField(de, value);
            } catch (NumberFormatException e) {
                System.err.println("[send] Ignoring invalid DE number in: " + spec);
            }
        }

        // auto-generate DE11/DE37 if still absent (noDefaults mode)
        if (!msg.hasField(11)) msg.setField(11, stan);
        if (!msg.hasField(37)) msg.setField(37, rrn);

        return msg;
    }

    // ── output formatting ─────────────────────────────────────────────────────

    private static void printMessage(String label, IsoMessage msg) {
        String line = "─".repeat(58);
        System.out.println("── " + label + " " + line.substring(label.length() + 3));
        System.out.printf("  %-8s: %s%n", "MTI", msg.getMti());
        for (Map.Entry<Integer, String> e : msg.getFields().entrySet()) {
            int de = e.getKey();
            String value = maskIfPan(de, e.getValue());
            System.out.printf("  DE%03d   : %s%n", de, value);
        }
    }

    private static void printResult(IsoMessage response, String errorMsg, long latencyMs) {
        System.out.println("── RESULT " + "─".repeat(52));
        if (response != null) {
            String rc = response.getField(39);
            String status = "00".equals(rc) ? "APPROVED" : (rc != null ? "DECLINED (RC=" + rc + ")" : "SUCCESS");
            System.out.printf("  %-10s: %s%n", "Status", status);
            if (rc != null) System.out.printf("  %-10s: %s%n", "RC (DE039)", rc);
            String authCode = response.getField(38);
            if (authCode != null) System.out.printf("  %-10s: %s%n", "Auth (DE038)", authCode.strip());
        } else {
            System.out.printf("  %-10s: %s%n", "Status", errorMsg);
        }
        System.out.printf("  %-10s: %d ms%n", "Latency", latencyMs);
        System.out.println();
    }

    private static String maskIfPan(int de, String v) {
        if (de == 2 && v != null && v.length() >= 13)
            return v.substring(0, 6) + "******" + v.substring(v.length() - 4);
        return v;
    }
}
