package com.dgl.risingworld;

import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingConstructionTest {
    private final RWWindowTrimming plugin = mock(RWWindowTrimming.class);
    private final Vector3f position = new Vector3f(10f, 20f, 30f);
    private final Quaternion rotation = new Quaternion();
    private final Vector3f size = new Vector3f(2f, 3f, 0.4f);
    private final int[] stats = {-1, -1};

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(plugin).trimConstructionOpening(any(), any(), any(), any());
        when(plugin.getChunkPosition(position)).thenReturn(new Vector3i(4, 2, -3));
    }

    @Test
    void scansAllNineNeighborChunksAndResetsStatsWhenEmpty() {
        when(plugin.getConstructionElements(4, -3)).thenReturn(new ConstructionElement[0]);
        assertEquals(0, trim());
        assertArrayEquals(new int[]{0, 0}, stats);
        for (int cx = 3; cx <= 5; cx++) {
            for (int cz = -4; cz <= -2; cz++) {
                verify(plugin).getConstructionElements(cx, cz);
            }
        }
        verify(plugin, times(9)).getConstructionElements(anyInt(), anyInt());
        verify(plugin, never()).trimWall(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void countsScannedElementsButSkipsIncompatibleWalls() {
        ConstructionElement incompatible = mock(ConstructionElement.class);
        when(plugin.getConstructionElements(4, -3))
                .thenReturn(new ConstructionElement[]{null, incompatible});
        assertEquals(0, trim());
        assertArrayEquals(new int[]{2, 0}, stats);
        verify(plugin).isWall(null);
        verify(plugin).isWall(incompatible);
        verify(plugin, never()).trimWall(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void compatibleWallWithoutIntersectionIsNotTrimmedOrReportedAsSuccess() {
        ConstructionElement wall = compatibleWall();
        when(plugin.getConstructionElements(4, -3)).thenReturn(new ConstructionElement[]{wall});
        assertEquals(0, trim());
        assertArrayEquals(new int[]{1, 1}, stats);
        verify(plugin).trimWall(wall, position, rotation, size, true);
        verify(plugin, never()).trimWall(wall, position, rotation, size, false);
    }

    @Test
    void detectsAcrossChunksBeforeTrimmingAndCountsOnlySuccessfulEdits() {
        ConstructionElement first = intersectingWall(true);
        ConstructionElement second = intersectingWall(false);
        ConstructionElement third = intersectingWall(true);
        when(plugin.getConstructionElements(3, -4)).thenReturn(new ConstructionElement[]{first});
        when(plugin.getConstructionElements(5, -2)).thenReturn(new ConstructionElement[]{second, third});
        assertEquals(2, trim());
        assertArrayEquals(new int[]{3, 3}, stats);
        var order = inOrder(plugin);
        order.verify(plugin).trimWall(first, position, rotation, size, true);
        order.verify(plugin).trimWall(second, position, rotation, size, true);
        order.verify(plugin).trimWall(third, position, rotation, size, true);
        order.verify(plugin).trimWall(first, position, rotation, size, false);
        order.verify(plugin).trimWall(second, position, rotation, size, false);
        order.verify(plugin).trimWall(third, position, rotation, size, false);
    }

    @Test
    void capsIntersectionsAt64WhileContinuingToCountScannedElements() {
        ConstructionElement[] walls = new ConstructionElement[65];
        for (int i = 0; i < 64; i++) walls[i] = intersectingWall(true);
        walls[64] = mock(ConstructionElement.class);
        ConstructionElement later = mock(ConstructionElement.class);
        when(plugin.getConstructionElements(3, -4)).thenReturn(walls);
        when(plugin.getConstructionElements(5, -2)).thenReturn(new ConstructionElement[]{later});
        assertEquals(64, trim());
        assertArrayEquals(new int[]{66, 64}, stats);
        verify(plugin, times(64)).trimWall(any(), eq(position), eq(rotation), eq(size), eq(true));
        verify(plugin, times(64)).trimWall(any(), eq(position), eq(rotation), eq(size), eq(false));
        verify(plugin, never()).isWall(walls[64]);
        verify(plugin, never()).isWall(later);
    }

    @Test
    void nonIntersectingWallsDoNotConsumeIntersectionLimit() {
        ConstructionElement nonIntersecting = compatibleWall();
        ConstructionElement intersection = intersectingWall(true);
        ConstructionElement[] walls = new ConstructionElement[66];
        java.util.Arrays.fill(walls, nonIntersecting);
        walls[65] = intersection;
        when(plugin.getConstructionElements(4, -3)).thenReturn(walls);
        assertEquals(1, trim());
        assertArrayEquals(new int[]{66, 66}, stats);
        verify(plugin, times(65)).trimWall(nonIntersecting, position, rotation, size, true);
        verify(plugin, never()).trimWall(nonIntersecting, position, rotation, size, false);
        verify(plugin).trimWall(intersection, position, rotation, size, false);
    }

    private ConstructionElement compatibleWall() {
        ConstructionElement wall = mock(ConstructionElement.class);
        when(plugin.isWall(wall)).thenReturn(true);
        return wall;
    }

    private ConstructionElement intersectingWall(boolean editSucceeds) {
        ConstructionElement wall = compatibleWall();
        when(plugin.trimWall(wall, position, rotation, size, true)).thenReturn(true);
        when(plugin.trimWall(wall, position, rotation, size, false)).thenReturn(editSucceeds);
        return wall;
    }

    private int trim() {
        return plugin.trimConstructionOpening(position, rotation, size, stats);
    }
}
