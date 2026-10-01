package art.arcane.volmlib.noise.nativekernel;

import java.util.Arrays;

/** Mirrors the status codes in rust/src/ffi.rs. */
enum NativeFillStatus {
    OK(0),
    NULL_POINTER(1),
    TOO_LONG(2),
    OUTPUT_ALIASES_INPUT(3),
    INVALID_PROGRAM(4);

    private final int code;

    NativeFillStatus(int code) {
        this.code = code;
    }

    static NativeFillStatus fromCode(int code) {
        return Arrays.stream(values()).filter(status -> status.code == code).findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown native fill status " + code));
    }

    static void requireOk(int code) {
        NativeFillStatus status = fromCode(code);
        if (status != OK) {
            throw new IllegalArgumentException("Native simplex fill refused the buffers: " + status);
        }
    }
}
