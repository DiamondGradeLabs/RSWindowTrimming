package com.dgl.risingworld;

import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingTrimTest {
    private final RWWindowTrimming plugin = mock(RWWindowTrimming.class);
    private final Player player = mock(Player.class);
    private final ConstructionElement window = mock(ConstructionElement.class);
    private final Vector3f approximatePosition = new Vector3f(1f, 2f, 3f);
    private final Quaternion approximateRotation = new Quaternion();
    private final Vector3f approximateSize = new Vector3f(1f, 1f, 0.2f);
    private final Vector3f actualPosition = new Vector3f(10f, 20f, 30f);
    private final Quaternion actualRotation = new Quaternion(0f, 1f, 0f, 0f);
    private final Vector3f actualSize = new Vector3f(2f, 3f, 0.4f);

    @BeforeEach
    void setUp() {
        // Run the public entry point while isolating helpers that access native game state.
        doCallRealMethod().when(plugin).trim(any(Player.class), any(Vector3f.class),
                any(Quaternion.class), any(Vector3f.class));
        when(plugin.findNearestWindow(approximatePosition)).thenReturn(window);
        when(window.getWorldPosition()).thenReturn(actualPosition);
        when(window.getRotation()).thenReturn(actualRotation);
        when(window.getScale()).thenReturn(actualSize);
    }

    @Test
    void missingWindowStopsBeforeEditingOrMessaging() {
        when(plugin.findNearestWindow(approximatePosition)).thenReturn(null);
        trim();
        verify(plugin, never()).carveTerrainOpening(any(), any(), any());
        verify(plugin, never()).trimConstructionOpening(any(), any(), any(), any());
        verifyNoInteractions(player);
    }

    @Test
    void usesCopiesOfResolvedWindowPositionRotationAndSize() {
        when(plugin.carveTerrainOpening(any(), any(), any())).thenReturn(1);
        trim();
        ArgumentCaptor<Vector3f> position = ArgumentCaptor.forClass(Vector3f.class);
        ArgumentCaptor<Quaternion> rotation = ArgumentCaptor.forClass(Quaternion.class);
        ArgumentCaptor<Vector3f> size = ArgumentCaptor.forClass(Vector3f.class);
        verify(plugin).carveTerrainOpening(position.capture(), rotation.capture(), size.capture());
        assertEquals(actualPosition, position.getValue());
        assertEquals(actualRotation, rotation.getValue());
        assertEquals(actualSize, size.getValue());
        assertNotSame(actualPosition, position.getValue());
        assertNotSame(actualRotation, rotation.getValue());
        assertNotSame(actualSize, size.getValue());
    }

    @ParameterizedTest
    @MethodSource("unusableSizes")
    void fallsBackToApproximateSizeWhenWindowScaleIsUnusable(Vector3f scale) {
        when(window.getScale()).thenReturn(scale);
        when(plugin.carveTerrainOpening(any(), any(), any())).thenReturn(1);
        trim();
        verify(plugin).carveTerrainOpening(eq(actualPosition), eq(actualRotation), same(approximateSize));
    }

    static Stream<Vector3f> unusableSizes() {
        return Stream.of(null, new Vector3f(0.05f, 3f, 0.4f),
                new Vector3f(2f, 0.05f, 0.4f), new Vector3f(2f, 3f, 0.02f));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void terrainSuccessSkipsConstructionAndReportsCount(int carved) {
        when(plugin.carveTerrainOpening(any(), any(), any())).thenReturn(carved);
        trim();
        verify(plugin, never()).trimConstructionOpening(any(), any(), any(), any());
        verify(player).sendTextMessage(successMessage(carved));
        verifyNoMoreInteractions(player);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void noTerrainFallsBackToConstructionAndReportsCount(int trimmed) {
        when(plugin.trimConstructionOpening(any(), any(), any(), any())).thenReturn(trimmed);
        trim();
        var order = inOrder(plugin);
        order.verify(plugin).carveTerrainOpening(eq(actualPosition), eq(actualRotation), eq(actualSize));
        order.verify(plugin).trimConstructionOpening(eq(actualPosition), eq(actualRotation),
                eq(actualSize), any(int[].class));
        verify(player).sendTextMessage(successMessage(trimmed));
        verifyNoMoreInteractions(player);
    }

    @Test
    void noIntersectionReportsChunkScanStatsAndOpeningSize() {
        when(plugin.trimConstructionOpening(any(), any(), any(), any())).thenAnswer(invocation -> {
            int[] stats = invocation.getArgument(3);
            stats[0] = 7;
            stats[1] = 2;
            return 0;
        });
        when(plugin.getChunkPosition(actualPosition)).thenReturn(new Vector3i(4, 0, -2));
        trim();
        verify(player).sendTextMessage("<color=#FFAA66>Auto-trim diagnostic: chunk 4,-2"
                + "; no terrain or construction blocks intersected (scanned 7, rectangular 2)"
                + "; opening 2.00 x 3.00 x 0.40.</color>");
        verifyNoMoreInteractions(player);
    }

    private void trim() {
        plugin.trim(player, approximatePosition, approximateRotation, approximateSize);
    }

    private String successMessage(int count) {
        return "<color=#77FF99>Auto-trimmed " + count + " wall block"
                + (count == 1 ? "" : "s") + " around the window.</color>";
    }
}
