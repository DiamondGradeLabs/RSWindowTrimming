package com.dgl.risingworld;

import java.util.ArrayList;
import java.util.List;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingTerrainTest {
    private final RWWindowTrimming plugin = mock(RWWindowTrimming.class);
    private final Vector3f center = new Vector3f(10f, 20f, 30f);
    private final Quaternion rotation = new Quaternion();
    private final Vector3f size = new Vector3f(1f, 1f, 0.2f);

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(plugin).carveTerrainOpening(any(), any(), any());
    }

    @Test
    void repeatedSamplesClearEachBlockOnlyOnce() {
        doAnswer(call -> {
            Vector3i chunk = call.getArgument(1);
            Vector3i block = call.getArgument(2);
            chunk.x = 4;
            chunk.y = -1;
            chunk.z = 2;
            block.x = 3;
            block.y = 5;
            block.z = 7;
            return null;
        }).when(plugin).getChunkAndBlockPosition(any(), any(), any());
        when(plugin.hasSolidTerrain(any(), any())).thenReturn(true);
        assertEquals(1, carve(size));
        verify(plugin, times(20)).getChunkAndBlockPosition(any(), any(), any());
        verify(plugin).hasSolidTerrain(new Vector3i(4, -1, 2), new Vector3i(3, 5, 7));
        verify(plugin).clearTerrainBlock(new Vector3i(4, -1, 2), new Vector3i(3, 5, 7));
    }

    @Test
    void emptyOrUnavailableTerrainIsNotEdited() {
        assertEquals(0, carve(size));
        verify(plugin).hasSolidTerrain(any(), any());
        verify(plugin, never()).clearTerrainBlock(any(), any());
    }

    @Test
    void identicalLocalBlocksInDifferentChunksAreDistinct() {
        int[] sampleIndex = {0};
        doAnswer(call -> {
            Vector3i chunk = call.getArgument(1);
            chunk.x = sampleIndex[0]++ % 2;
            return null;
        }).when(plugin).getChunkAndBlockPosition(any(), any(), any());
        when(plugin.hasSolidTerrain(any(), any())).thenReturn(true);
        assertEquals(2, carve(size));
        verify(plugin).clearTerrainBlock(new Vector3i(0, 0, 0), new Vector3i());
        verify(plugin).clearTerrainBlock(new Vector3i(1, 0, 0), new Vector3i());
    }

    @ParameterizedTest
    @CsvSource({"0.2, 1.0", "1.0, 0.2"})
    void tooSmallWidthOrHeightProducesNoSamples(float width, float height) {
        assertEquals(0, carve(new Vector3f(width, height, 0.2f)));
        verify(plugin, never()).getChunkAndBlockPosition(any(), any(), any());
    }

    @Test
    void samplesRotatedAxesAndUsesLargerHorizontalDimension() {
        List<Vector3f> samples = new ArrayList<>();
        doAnswer(call -> {
            Vector3f sample = call.getArgument(0);
            samples.add(sample.copy());
            Vector3i block = call.getArgument(2);
            block.x = samples.size();
            return null;
        }).when(plugin).getChunkAndBlockPosition(any(), any(), any());
        when(plugin.hasSolidTerrain(any(), any())).thenReturn(true, false, true);
        // A half turn around Y reverses both the width and normal axes.
        assertEquals(19, plugin.carveTerrainOpening(center, new Quaternion(0f, 1f, 0f, 0f),
                new Vector3f(0.2f, 1f, 1f)));
        assertEquals(20, samples.size());
        assertEquals(new Vector3f(10.25f, 19.75f, 30.5f), samples.get(0));
        assertEquals(new Vector3f(9.75f, 20.25f, 29.5f), samples.get(19));
        verify(plugin, times(19)).clearTerrainBlock(any(), any());
        assertEquals(new Vector3f(10f, 20f, 30f), center);
    }

    private int carve(Vector3f openingSize) {
        return plugin.carveTerrainOpening(center, rotation, openingSize);
    }
}
