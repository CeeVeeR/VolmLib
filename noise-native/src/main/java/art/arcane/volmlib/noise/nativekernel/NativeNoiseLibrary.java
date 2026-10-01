package art.arcane.volmlib.noise.nativekernel;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Binds the Rust kernel once per JVM. Handles are static finals so the JIT can inline the
 * downcalls; scalar calls are "critical" to skip the native thread-state transition.
 */
final class NativeNoiseLibrary {
    static final String LIBRARY_PATH_PROPERTY = "volmlib.noise.library";
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOLS = openConfiguredLibrary(System.getProperty(LIBRARY_PATH_PROPERTY));
    private static final Linker.Option SHORT_CALL = Linker.Option.critical(false);
    private static final Linker.Option SHORT_CALL_ON_HEAP_ARRAYS = Linker.Option.critical(true);

    static final MethodHandle SIMPLEX_2D = bind("volmlib_noise_simplex_2d",
        FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE), SHORT_CALL);
    static final MethodHandle SIMPLEX_3D = bind("volmlib_noise_simplex_3d",
        FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE), SHORT_CALL);
    static final MethodHandle SIMPLEX_2D_FILL = bind("volmlib_noise_simplex_2d_fill",
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG), SHORT_CALL_ON_HEAP_ARRAYS);
    static final MethodHandle SIMPLEX_3D_FILL = bind("volmlib_noise_simplex_3d_fill",
        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG), SHORT_CALL_ON_HEAP_ARRAYS);

    static final MethodHandle PROGRAM_FILL_2D = bind("volmlib_noise_program_fill_2d", FunctionDescriptor.of(JAVA_INT,
        ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG), SHORT_CALL_ON_HEAP_ARRAYS);
    static final MethodHandle PROGRAM_FILL_3D = bind("volmlib_noise_program_fill_3d", FunctionDescriptor.of(JAVA_INT,
        ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG), SHORT_CALL_ON_HEAP_ARRAYS);

    private NativeNoiseLibrary() {
    }

    static SymbolLookup openConfiguredLibrary(String libraryPath) {
        if (libraryPath == null || libraryPath.isBlank()) {
            throw new IllegalStateException("Set -D" + LIBRARY_PATH_PROPERTY + " to the volmlib_noise shared library");
        }
        return SymbolLookup.libraryLookup(Path.of(libraryPath.trim()), Arena.global());
    }

    private static MethodHandle bind(String symbol, FunctionDescriptor descriptor, Linker.Option option) {
        MemorySegment address = SYMBOLS.find(symbol)
            .orElseThrow(() -> new IllegalStateException("Native noise library lacks symbol " + symbol));
        return LINKER.downcallHandle(address, descriptor, option);
    }
}
