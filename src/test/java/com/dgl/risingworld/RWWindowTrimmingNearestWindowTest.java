package com.dgl.risingworld;

import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingNearestWindowTest {
    private final RWWindowTrimming plugin = mock(RWWindowTrimming.class);
    private final Vector3f position = new Vector3f(10f, 20f, 30f);

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(plugin).findNearestWindow(any());
        when(plugin.getChunkPosition(position)).thenReturn(new Vector3i(-3, 2, 4));
    }

    @Test
    void searchesNineChunksAndReturnsNullWhenEmpty() {
        when(plugin.getConstructionElements(-3, 4)).thenReturn(new ConstructionElement[0]);
        assertNull(plugin.findNearestWindow(position));
        for (int cx = -4; cx <= -2; cx++) {
            for (int cz = 3; cz <= 5; cz++) verify(plugin).getConstructionElements(cx, cz);
        }
        verify(plugin, times(9)).getConstructionElements(anyInt(), anyInt());
    }

    @Test
    void skipsNullInvalidAndNonWindowElements() {
        ConstructionElement invalid = mock(ConstructionElement.class);
        ConstructionElement wall = candidate((byte) 1, 0f, 0f, 0f);
        when(plugin.getConstructionElements(-3, 4)).thenReturn(new ConstructionElement[]{null, invalid, wall});
        assertNull(plugin.findNearestWindow(position));
        verify(invalid, never()).getTypeID();
        verify(wall, never()).getWorldPosition();
    }

    @Test
    void selectsClosestWindowAcrossChunksUsingAllThreeCoordinates() {
        ConstructionElement first = candidate((byte) 100, 0f, 4f, 0f);
        ConstructionElement nearest = candidate((byte) 101, 1f, 1f, 1f);
        ConstructionElement farther = candidate((byte) 119, 0f, 0f, 3f);
        when(plugin.getConstructionElements(-4, 3)).thenReturn(new ConstructionElement[]{first});
        when(plugin.getConstructionElements(-2, 5)).thenReturn(new ConstructionElement[]{nearest, farther});
        assertSame(nearest, plugin.findNearestWindow(position));
    }

    @ParameterizedTest
    @CsvSource({"4.999, true", "5.0, false", "5.001, false"})
    void searchRadiusIsStrictlyLessThanFiveUnits(float distance, boolean found) {
        ConstructionElement window = candidate((byte) 100, distance, 0f, 0f);
        when(plugin.getConstructionElements(-3, 4)).thenReturn(new ConstructionElement[]{window});
        assertSame(found ? window : null, plugin.findNearestWindow(position));
    }

    @Test
    void equalDistancesKeepFirstWindow() {
        ConstructionElement first = candidate((byte) 100, 1f, 0f, 0f);
        ConstructionElement second = candidate((byte) 101, -1f, 0f, 0f);
        when(plugin.getConstructionElements(-3, 4)).thenReturn(new ConstructionElement[]{first, second});
        assertSame(first, plugin.findNearestWindow(position));
    }

    @Test
    void recognizesDefinitionTypeAndCaseInsensitiveNameOutsideIdRange() {
        ConstructionElement typed = candidate((byte) 1, 3f, 0f, 0f);
        ConstructionElement named = candidate((byte) 2, 1f, 0f, 0f);
        Constructions.ConstructionDefinition typeDefinition = mock(Constructions.ConstructionDefinition.class);
        typeDefinition.type = Constructions.Type.Window;
        Constructions.ConstructionDefinition nameDefinition = mock(Constructions.ConstructionDefinition.class);
        nameDefinition.name = "WiNdOwFrame";
        when(plugin.getConstructionDefinition((byte) 1)).thenReturn(typeDefinition);
        when(plugin.getConstructionDefinition((byte) 2)).thenReturn(nameDefinition);
        when(plugin.getConstructionElements(-3, 4)).thenReturn(new ConstructionElement[]{typed, named});
        assertSame(named, plugin.findNearestWindow(position));
        nameDefinition.name = null;
        assertSame(typed, plugin.findNearestWindow(position));
    }

    private ConstructionElement candidate(byte typeId, float dx, float dy, float dz) {
        ConstructionElement element = mock(ConstructionElement.class);
        when(element.isValid()).thenReturn(true);
        when(element.getTypeID()).thenReturn(typeId);
        when(element.getWorldPosition()).thenReturn(new Vector3f(position.x + dx, position.y + dy, position.z + dz));
        return element;
    }
}
