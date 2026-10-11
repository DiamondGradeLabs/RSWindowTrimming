package net.jiw.unity.runtime.utils;

/** Test-only replacement for the game runtime's native constant provider. */
public final class InternalUtils {
    private InternalUtils() { }

    public static Object getCommon(String name) {
        return switch (name) {
            case "CHUNK_SIZE_X", "CHUNK_SIZE_Z" -> 16;
            case "CHUNK_SIZE_Y" -> 64;
            case "LOD_TERRAIN_LAYERS" -> 1;
            default -> throw new IllegalArgumentException("Unexpected SDK constant: " + name);
        };
    }
}
