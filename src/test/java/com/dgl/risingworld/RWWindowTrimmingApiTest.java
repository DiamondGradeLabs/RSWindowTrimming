package com.dgl.risingworld;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import net.risingworld.api.World;
import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.objects.world.Chunk;
import net.risingworld.api.objects.world.ChunkPart;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import net.risingworld.api.world.EditRestriction;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingApiTest {
    private final RWWindowTrimming plugin = new RWWindowTrimming();

    @Test
    @SuppressWarnings("unchecked")
    void constructionLookupHandlesMissingInvalidAndEmptyChunks() {
        BiFunction<Integer, Integer, Chunk> lookup = mock(BiFunction.class);
        plugin.chunkLookup = lookup;
        assertNull(plugin.getConstructionElements(-2, 3));
        Chunk chunk = mock(Chunk.class);
        when(lookup.apply(-2, 3)).thenReturn(chunk);
        assertNull(plugin.getConstructionElements(-2, 3));
        verify(chunk, never()).getAllConstructionElements();
        when(chunk.isValid()).thenReturn(true);
        assertNull(plugin.getConstructionElements(-2, 3));
        ConstructionElement[] elements = {mock(ConstructionElement.class)};
        when(chunk.getAllConstructionElements()).thenReturn(elements);
        assertSame(elements, plugin.getConstructionElements(-2, 3));
        verify(lookup, times(4)).apply(-2, 3);
    }

    @Test
    void terrainLookupHandlesMissingInvalidAirAndSolidBlocks() {
        RWWindowTrimming.ChunkPartLookup lookup = mock(RWWindowTrimming.ChunkPartLookup.class);
        plugin.chunkPartLookup = lookup;
        Vector3i chunkPosition = new Vector3i(-1, 2, 3);
        Vector3i blockPosition = new Vector3i(4, 5, 6);
        assertFalse(plugin.hasSolidTerrain(chunkPosition, blockPosition));
        ChunkPart chunk = mock(ChunkPart.class);
        when(lookup.get(-1, 2, 3)).thenReturn(chunk);
        assertFalse(plugin.hasSolidTerrain(chunkPosition, blockPosition));
        verify(chunk, never()).getTerrainID(4, 5, 6);
        when(chunk.isValid()).thenReturn(true);
        assertFalse(plugin.hasSolidTerrain(chunkPosition, blockPosition));
        when(chunk.getTerrainID(4, 5, 6)).thenReturn(9);
        assertTrue(plugin.hasSolidTerrain(chunkPosition, blockPosition));
        verify(lookup, times(4)).get(-1, 2, 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void definitionLookupForwardsTypeAndReturnsDefinitionOrNull() {
        IntFunction<Constructions.ConstructionDefinition> lookup = mock(IntFunction.class);
        plugin.definitionLookup = lookup;
        assertNull(plugin.getConstructionDefinition((byte) 7));
        Constructions.ConstructionDefinition definition = mock(Constructions.ConstructionDefinition.class);
        when(lookup.apply(7)).thenReturn(definition);
        assertSame(definition, plugin.getConstructionDefinition((byte) 7));
        verify(lookup, times(2)).apply(7);
    }

    @Test
    void coordinateWrappersHandleNegativeAndPositiveChunkBoundaries() {
        Vector3f position = new Vector3f(-0.25f, 64.5f, 16.25f);
        assertEquals(new Vector3i(-1, 1, 1), plugin.getChunkPosition(position));
        Vector3i chunk = new Vector3i();
        Vector3i block = new Vector3i();
        plugin.getChunkAndBlockPosition(position, chunk, block);
        assertEquals(new Vector3i(-1, 1, 1), chunk);
        assertEquals(new Vector3i(15, 0, 0), block);
        assertEquals(new Vector3f(-0.25f, 64.5f, 16.25f), position);
    }

    @Test
    void clearingForwardsAllCoordinatesWithSolidOnlyRestriction() {
        try (MockedStatic<World> world = mockStatic(World.class)) {
            plugin.clearTerrainBlock(new Vector3i(-1, 2, 3), new Vector3i(4, 5, 6));
            world.verify(() -> World.setTerrainData(0, -1, 2, 3, 4, 5, 6, EditRestriction.SolidOnly));
            world.verifyNoMoreInteractions();
        }
    }

    @Test
    void lifecycleLogsNormalizedPathAndGameVersion() {
        RWWindowTrimming lifecycle = spy(plugin);
        doReturn("./build/../build").when(lifecycle).getPath();
        doReturn("test-version").when(lifecycle).getGameVersion();
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output)) {
            System.setOut(capture);
            lifecycle.onEnable();
            lifecycle.onDisable();
        } finally {
            System.setOut(original);
        }
        String logs = output.toString();
        assertTrue(logs.contains("Plugin data directory: " + Path.of("build").toAbsolutePath().normalize()));
        assertTrue(logs.contains("[RWWTS] Enabled on Rising World test-version"));
        assertTrue(logs.contains("[RWWTS] Disabled on Rising World test-version"));
    }
}
