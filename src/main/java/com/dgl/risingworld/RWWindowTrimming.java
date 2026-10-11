package com.dgl.risingworld;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import net.risingworld.api.objects.world.Chunk;
import net.risingworld.api.objects.world.ChunkPart;

import net.risingworld.api.Plugin;
import net.risingworld.api.events.Listener;
import net.risingworld.api.World;
import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;

/**
 * RWWindowTrimming is a plugin for the Rising World game that allows players to trim windows in the game world based on approximate position, rotation, and size.
 * 
 * @author Adam C. Guthrie
 */
public final class RWWindowTrimming extends Plugin implements Listener {

    private static final float FRAME_OVERLAP = 0.06f;
    private static final int MAX_INTERSECTIONS = 64;

    // Native lookups are injectable so wrapper logic can run outside the game.
    BiFunction<Integer, Integer, Chunk> chunkLookup = World::getChunk;
    IntFunction<Constructions.ConstructionDefinition> definitionLookup = Definitions::getConstructionDefinition;
    ChunkPartLookup chunkPartLookup = World::getChunkPart;

    interface ChunkPartLookup {
        ChunkPart get(int x, int y, int z);
    }
    
    /**
     * Called when the plugin is enabled. Initializes the plugin and prints relevant debug information.
     * 
     */
    @Override
    public void onEnable() {
        Path pluginPath = Path.of(getPath()).toAbsolutePath().normalize();
        debug("Starting plugin initialization");
        debug("Plugin data directory: " + pluginPath);


        System.out.println("[RWWTS] Enabled on Rising World " + getGameVersion());
    }


    @Override
    public void onDisable() {
        debug("Disabling plugin");
        System.out.println("[RWWTS] Disabled on Rising World " + getGameVersion());
    }

    /**
     * Prints a debug message to the console with a specific prefix indicating it is from the RWWTS plugin.
     * 
     * @param message The debug message to be printed.
     */
    private static void debug(String message) {
        System.out.println("[RWWTS/DEBUG] " + message);
    }

    /**
     * Finds the Nearest Window to the specified approximate position.
     * 
     * @param nearPosition The approximate position to search for the nearest window.
     * @return The nearest window construction element, or null if none is found.
     */
    ConstructionElement findNearestWindow(Vector3f nearPosition) {

        // Get the center chunk of the approximate position.
        Vector3i centerChunk = getChunkPosition(nearPosition);
        ConstructionElement nearest = null; // Initialize the nearest window element to null.
        float nearestDistanceSquared = 25f; // Set the initial search 5 units away (squared distance).

        // Search for the nearest window element within the surrounding chunks.
        for (int cx = centerChunk.x - 1; cx <= centerChunk.x + 1; cx++) {
            for (int cz = centerChunk.z - 1; cz <= centerChunk.z + 1; cz++) {
                ConstructionElement[] elements = getConstructionElements(cx, cz);
                if (elements == null) continue;
                for (ConstructionElement element : elements) {
                    if (element == null || !element.isValid()) continue;
                    Constructions.ConstructionDefinition definition =
                            getConstructionDefinition(element.getTypeID());
                    if (!isWindow(element, definition)) continue;
                    Vector3f candidate = element.getWorldPosition();
                    float dx = candidate.x - nearPosition.x;
                    float dy = candidate.y - nearPosition.y;
                    float dz = candidate.z - nearPosition.z;
                    float distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared;
                        nearest = element;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Determines whether the specified construction element is a window.
     * 
     * @param element The construction element to check.
     * @param definition The construction definition of the element.
     * @return True if the element is a window, false otherwise.
     */
    private static boolean isWindow(ConstructionElement element, Constructions.ConstructionDefinition definition) {
        int typeId = Byte.toUnsignedInt(element.getTypeID()); // Get the unsigned type ID of the construction element.

        // If the element is between the lowest minimum and highest maximum type IDs for windows, consider it a window.
        return typeId >= 100 && typeId <= 119
            || definition != null && (definition.type == Constructions.Type.Window
            || definition.name != null
            && definition.name.toLowerCase(Locale.US).startsWith("window"));
    }

    /**
     * Carve the terrain when there is a window intersecting with it.
     * 
     * @param center The center position of the window opening.
     * @param rotation The rotation of the window opening.
     * @param size The size of the window opening.
     * @return The number of terrain blocks carved.
     */
    int carveTerrainOpening(Vector3f center, Quaternion rotation, Vector3f size) {

        float width = Math.max(size.x, size.z);
        Vector3f widthAxis = rotation.mult(Vector3f.RIGHT).normalize();
        Vector3f normalAxis = rotation.mult(Vector3f.FORWARD).normalize();

        Set<String> editedBlocks = new HashSet<>();
        int carved = 0;
        for (float x = -width * 0.5f + 0.25f; x < width * 0.5f; x += 0.5f) {
            for (float y = -size.y * 0.5f + 0.25f; y < size.y * 0.5f; y += 0.5f) {
                for (float z = -0.5f; z <= 0.5f; z += 0.25f) {
                    Vector3f sample = center.add(widthAxis.mult(x)).add(Vector3f.UP.mult(y))
                            .add(normalAxis.mult(z));
                    Vector3i chunk = new Vector3i();
                    Vector3i block = new Vector3i();
                    getChunkAndBlockPosition(sample, chunk, block);
                    String key = chunk.x + "," + chunk.y + "," + chunk.z + ":"
                            + block.x + "," + block.y + "," + block.z;
                    // Multiple sample points can land in the same terrain block.
                    if (!editedBlocks.add(key)) continue;
                    if (!hasSolidTerrain(chunk, block)) continue;
                    // Terrain ID zero clears the block; restrict the edit to solid terrain.
                    clearTerrainBlock(chunk, block);
                    carved++;
                }
            }
        }
        return carved;
    }

    /**
     * Trims the construction opening at the specified position with the given rotation and size.
     * Updates the provided stats array with the number of scanned and compatible walls.
     *
     * @param position The position of the construction opening.
     * @param openingRotation The rotation of the construction opening.
     * @param size The size of the construction opening.
     * @param stats An array to store the number of scanned and compatible walls.
     * @return The number of walls trimmed.
     */

    int trimConstructionOpening(Vector3f position, Quaternion openingRotation,
                                        Vector3f size, int[] stats) {

        Vector3i openingChunk = getChunkPosition(position);

        List<ConstructionElement> intersecting = new ArrayList<>();
        int scanned = 0;
        int compatible = 0;

        for (int cx = openingChunk.x - 1; cx <= openingChunk.x + 1; cx++) {
            for (int cz = openingChunk.z - 1; cz <= openingChunk.z + 1; cz++) {
                ConstructionElement[] elements = getConstructionElements(cx, cz);
                if (elements == null) continue;
                for (ConstructionElement wall : elements) {
                    scanned++;
                    if (intersecting.size() >= MAX_INTERSECTIONS || !isWall(wall)) continue;
                    compatible++;
                    if (trimWall(wall, position, openingRotation, size, true)) intersecting.add(wall);
                }
            }
        }
        int trimmed = 0;

        for (ConstructionElement wall : intersecting) {
            if (trimWall(wall, position, openingRotation, size, false)) trimmed++;
        }

        stats[0] = scanned;
        stats[1] = compatible;
        return trimmed;
    }

    /**
     * Adds a new piece to the list of wall pieces if it meets the minimum size requirements.
     *
     * @param pieces The list of wall pieces.
     * @param minW The minimum width of the piece.
     * @param maxW The maximum width of the piece.
     * @param minY The minimum height of the piece.
     * @param maxY The maximum height of the piece.
     */
    private static void addPiece(List<float[]> pieces, float minW, float maxW,
                                 float minY, float maxY) {
        if (maxW - minW < 0.08f || maxY - minY < 0.08f) return;
        pieces.add(new float[]{minW, maxW, minY, maxY});
    }

    /**
     * Creates a new wall piece based on the specified dimensions and adds it to the scene.
     *
     * @param source The original wall element.
     * @param rotation The rotation of the wall.
     * @param sourceSize The size of the original wall.
     * @param widthIsX Whether the wall's width is along the X axis.
     * @param piece The dimensions of the new piece [minW, maxW, minY, maxY].
     * @param thickness The thickness of the wall.
     */
    static void createPiece(ConstructionElement source, Quaternion rotation,
                                    Vector3f sourceSize, boolean widthIsX,
                                    float[] piece, float thickness) {
        
        // Get the scale of the source wall element, if null return.
        Vector3f sourceScale = source.getScale();
        if (sourceScale == null) return;

        // Get the offsets. 
        float centerW = (piece[0] + piece[1]) * 0.5f;
        float centerY = (piece[2] + piece[3]) * 0.5f;
        Vector3f offset = widthIsX ? new Vector3f(centerW, centerY, 0f)
                : new Vector3f(0f, centerY, centerW);

        // Rotate each new piece with local wall rotation and calculate its position and size.
        Vector3f position = source.getWorldPosition().add(rotation.mult(offset));
        float pieceWidth = piece[1] - piece[0];
        float pieceHeight = piece[3] - piece[2];
        Vector3f pieceSize = widthIsX ? new Vector3f(pieceWidth, pieceHeight, thickness)
                : new Vector3f(thickness, pieceHeight, pieceWidth);

        // Apply the scale as a proportion of the source wall's size.
        Vector3f scale = new Vector3f(sourceScale.x * pieceSize.x / sourceSize.x,
        sourceScale.y * pieceSize.y / sourceSize.y,
        sourceScale.z * pieceSize.z / sourceSize.z);

        // Create the new pieces in the world with the calculated position, rotation, and scale and texture.
        ConstructionElement created = World.createConstructionElement(source.getTypeID(), source.getTexture(),
        source.getTextureScale(), source.getColor(), position, rotation.copy(), scale,
        source.getSurfaceOffset(), source.getSurfaceScale());
        if (created != null) {
            created.setPlayerDbID(source.getPlayerDbID());
            created.setStrength(source.getStrength());
        }
    }

    /**
     * Checks if the given construction element is a valid wall.
     *
     * @param element The construction element to check.
     * @return True if the element is a valid wall, false otherwise.
     */
    boolean isWall(ConstructionElement element) {
        
        // Ensure the element is non-null and valid.
        if (element == null || !element.isValid()) return false;

        // Ensure the element is a default-shaped wall.
        Constructions.ConstructionDefinition definition = getConstructionDefinition(element.getTypeID());
        if (definition == null || definition.type == Constructions.Type.Window
                || definition.shapetype != Constructions.ShapeType.Default) return false;

        // Ensue the size is valid.
        Vector3f size = element.getScale();
        return size != null && size.y >= 0.25f && Math.max(size.x, size.z) >= 0.25f
                && Math.min(size.x, size.z) <= 1f;
    }

    boolean trimWall(ConstructionElement wall, Vector3f openingPosition, Quaternion openingRotation, Vector3f openingSize, boolean detectOnly) {
        // Get the size of the wall element, and return if false if wall size is null.
        Vector3f wallSize = wall.getScale();
        if(wallSize == null) return false;

        // Initialize wall properties.
        wallSize = wall.getScale();
        Quaternion rotation = wall.getRotation().copy();
        boolean widthIsX = wallSize.x >= wallSize.z; // Determine if the wall's width is along the X axis.
        float wallWidth = widthIsX ? wallSize.x : wallSize.z; 
        float thickness = widthIsX ? wallSize.z : wallSize.x;
        Quaternion inverseWallRotation = rotation.inverse();

        // Convert to wall-local coordinates.
        Vector3f local = inverseWallRotation.mult(openingPosition.subtract(wall.getWorldPosition()));
        Vector3f openingRight = inverseWallRotation.mult(openingRotation.mult(Vector3f.RIGHT));
        Vector3f openingForward = inverseWallRotation.mult(openingRotation.mult(Vector3f.FORWARD));
        float localWidth = widthIsX ? local.x : local.z;
        float localDepth = widthIsX ? local.z : local.x;

        // Calculate the projected widths and depths of the opening relative to the wall's local axes.
        float rightWidth = Math.abs(widthIsX ? openingRight.x : openingRight.z);
        float forwardWidth = Math.abs(widthIsX ? openingForward.x : openingForward.z);
        float rightDepth = Math.abs(widthIsX ? openingRight.z : openingRight.x);
        float forwardDepth = Math.abs(widthIsX ? openingForward.z : openingForward.x);

        // Calculate the half-width and half-depth of the opening relative to the wall's local axes.
        float openingHalfWidth = rightWidth * openingSize.x * 0.5f
            + forwardWidth * openingSize.z * 0.5f;
        float openingHalfDepth = rightDepth * openingSize.x * 0.5f
                + forwardDepth * openingSize.z * 0.5f;

        // Reject openings that do not reach the panel, allowing a small depth tolerance.
        if (Math.abs(localDepth) > thickness * 0.5f + openingHalfDepth + 0.05f) return false;

        // Calculate the bounding box of the wall in its local coordinates.
        float minW = -wallWidth * 0.5f;
        float maxW = wallWidth * 0.5f;
        float minY = -wallSize.y * 0.5f;
        float maxY = wallSize.y * 0.5f;

        // Calculate the cut boundaries for the opening, ensuring they stay within the wall's bounds and account for the frame overlap.
        float cutMinW = Math.max(minW, localWidth - openingHalfWidth + FRAME_OVERLAP);
        float cutMaxW = Math.min(maxW, localWidth + openingHalfWidth - FRAME_OVERLAP);
        float cutMinY = Math.max(minY, local.y - openingSize.y * 0.5f + FRAME_OVERLAP);
        float cutMaxY = Math.min(maxY, local.y + openingSize.y * 0.5f - FRAME_OVERLAP);

        // Ignore negligible overlaps; detectOnly stops before any world changes.
        if (cutMaxW - cutMinW < 0.08f || cutMaxY - cutMinY < 0.08f) return false;
        
        // Handle the detection-only case. If detectOnly is true return early without making any changes.
        if (detectOnly) return true;  

        // Build the list of wall pieces that remain after cutting out the opening.
        List<float[]> pieces = new ArrayList<>(4);
        addPiece(pieces, minW, cutMinW, minY, maxY);
        addPiece(pieces, cutMaxW, maxW, minY, maxY);
        addPiece(pieces, cutMinW, cutMaxW, minY, cutMinY);
        addPiece(pieces, cutMinW, cutMaxW, cutMaxY, maxY);

        // Create the trimmed pieces
        for (float[] piece : pieces) createPiece(wall, rotation, wallSize, widthIsX, piece, thickness);

        // Remove the original wall piece after creating the trimmed pieces.
        wall.setScale(0.001f, 0.001f, 0.001f);
        wall.destroy(true);
        return true;
    }

    /**
     * Trims the specified window for the given player based on the approximate position, rotation, and size.
     *
     * @param player The player performing the trim action.
     * @param approximatePosition The approximate position of the window.
     * @param approximateRotation The approximate rotation of the window.
     * @param approximateSize The approximate size of the window.
     */
    public void trim(Player player, Vector3f approximatePosition, Quaternion approximateRotation, Vector3f approximateSize) {
        
        Vector3f position = approximatePosition;
        Quaternion rotation = approximateRotation;
        Vector3f openingSize = approximateSize;

        // Once the window is placed in the game world, find the nearest window element to the approximate position.
        ConstructionElement window = findNearestWindow(approximatePosition);

        if(window == null) { //Ensure a window is found.
            debug("No suitable window found near the approximate position.");
            return;
        }

        // Get the actual position, rotation, and size of the window element in the game world.
        position = window.getWorldPosition().copy();
        rotation = window.getRotation().copy();
        Vector3f actualSize = window.getScale();

        // Ensure the actual size is reasonable before using it to update the opening size.
        if (actualSize != null && actualSize.x > 0.05f
                && actualSize.y > 0.05f && actualSize.z > 0.02f) {
            openingSize = actualSize.copy();
        }
        debug("Resolved placed window " + window.getGlobalID() + " at " + position
                + " with size " + openingSize);

        // Carve the terrain opening based on the resolved position, rotation, and opening size.
        int carved = carveTerrainOpening(position, rotation, openingSize);

        int[] stats = new int[2];

        // If this returns non zero, construction trimming is skipped.
        // Issue #6 was created to research this.
        int constructionBlocks = 0;
         if (carved == 0) {
            constructionBlocks = trimConstructionOpening(position, rotation, openingSize, stats);
        }

        // There really isn't a case where both would be non-zero.
        int total = carved + constructionBlocks;

        if (total > 0) {
            player.sendTextMessage("<color=#77FF99>Auto-trimmed " + total + " wall block"
                    + (total == 1 ? "" : "s") + " around the window.</color>");
        } else {
            Vector3i chunk = getChunkPosition(position);
            player.sendTextMessage("<color=#FFAA66>Auto-trim diagnostic: chunk "
                    + chunk.x + "," + chunk.z
                    + "; no terrain or construction blocks intersected (scanned " + stats[0]
                    + ", rectangular " + stats[1] + "); opening "
                    + String.format(Locale.US, "%.2f x %.2f x %.2f",
                    openingSize.x, openingSize.y, openingSize.z) + ".</color>");
        }
    }

    Vector3i getChunkPosition(Vector3f position) {
        return Utils.ChunkUtils.getChunkPosition(position);
    }

    void getChunkAndBlockPosition(Vector3f sample, Vector3i chunk, Vector3i block) {
        Utils.ChunkUtils.getChunkAndBlockPosition(sample, chunk, block);
    }

    boolean hasSolidTerrain(Vector3i chunk, Vector3i block) {
        var chunkPart = chunkPartLookup.get(chunk.x, chunk.y, chunk.z);
        return chunkPart != null && chunkPart.isValid()
                && chunkPart.getTerrainID(block.x, block.y, block.z) != 0;
    }

    void clearTerrainBlock(Vector3i chunk, Vector3i block) {
        World.setTerrainData(0, chunk.x, chunk.y, chunk.z, block.x, block.y, block.z,
                net.risingworld.api.world.EditRestriction.SolidOnly);
    }

    Constructions.ConstructionDefinition getConstructionDefinition(byte typeId) {
        return definitionLookup.apply(typeId);
    }

    ConstructionElement[] getConstructionElements(int cx, int cz) {
        var chunk = chunkLookup.apply(cx, cz);
        return chunk == null || !chunk.isValid() ? null : chunk.getAllConstructionElements();
    }
} 
