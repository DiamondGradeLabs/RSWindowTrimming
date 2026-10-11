package com.dgl.risingworld;

import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingWallTest {
    private final RWWindowTrimming plugin = mock(RWWindowTrimming.class);
    private final ConstructionElement wall = mock(ConstructionElement.class);
    private final Constructions.ConstructionDefinition definition = mock(Constructions.ConstructionDefinition.class);

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(plugin).isWall(nullable(ConstructionElement.class));
        when(wall.isValid()).thenReturn(true);
        when(wall.getTypeID()).thenReturn((byte) 1);
        when(plugin.getConstructionDefinition((byte) 1)).thenReturn(definition);
        definition.shapetype = Constructions.ShapeType.Default;
    }

    @Test
    void rejectsNullAndInvalidElementsBeforeDefinitionLookup() {
        assertFalse(plugin.isWall(null));
        when(wall.isValid()).thenReturn(false);
        assertFalse(plugin.isWall(wall));
        verify(plugin, never()).getConstructionDefinition(anyByte());
    }

    @Test
    void rejectsMissingDefinitionWindowsAndNonDefaultShapes() {
        when(plugin.getConstructionDefinition((byte) 1)).thenReturn(null);
        assertFalse(plugin.isWall(wall));
        when(plugin.getConstructionDefinition((byte) 1)).thenReturn(definition);
        definition.type = Constructions.Type.Window;
        assertFalse(plugin.isWall(wall));
        definition.type = null;
        definition.shapetype = null;
        assertFalse(plugin.isWall(wall));
        verify(wall, never()).getScale();
    }

    @ParameterizedTest
    @MethodSource("invalidSizes")
    void rejectsMissingShortNarrowOrThickScale(Vector3f size) {
        when(wall.getScale()).thenReturn(size);
        assertFalse(plugin.isWall(wall));
    }

    static Stream<Vector3f> invalidSizes() {
        return Stream.of(null, new Vector3f(2f, 0.249f, 0.2f),
                new Vector3f(0.249f, 2f, 0.249f), new Vector3f(1.001f, 2f, 1.001f));
    }

    @ParameterizedTest
    @MethodSource("validSizes")
    void acceptsBothWallAxesAndInclusiveSizeLimits(Vector3f size) {
        when(wall.getScale()).thenReturn(size);
        assertTrue(plugin.isWall(wall));
    }

    static Stream<Vector3f> validSizes() {
        return Stream.of(new Vector3f(2f, 2f, 0.2f), new Vector3f(0.2f, 2f, 2f),
                new Vector3f(0.25f, 0.25f, 0.25f), new Vector3f(2f, 2f, 1f));
    }
}
