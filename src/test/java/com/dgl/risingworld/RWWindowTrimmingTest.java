package com.dgl.risingworld;

import java.lang.reflect.Method;
import net.risingworld.api.World;
import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RWWindowTrimmingTest {
    // Avoid initializing the Plugin superclass against the native game runtime.
    private final RWWindowTrimming plugin = mock(RWWindowTrimming.class);

    @ParameterizedTest
    @ValueSource(ints = {99, 100, 110, 119, 120, 255})
    void recognizesOnlyWindowTypeRangeWithoutDefinition(int typeId) throws Exception {
        ConstructionElement element = mock(ConstructionElement.class);
        when(element.getTypeID()).thenReturn((byte) typeId);
        assertEquals(typeId >= 100 && typeId <= 119, isWindow(element, null));
    }

    @Test
    void recognizesWindowDefinitionTypeAndName() throws Exception {
        ConstructionElement element = mock(ConstructionElement.class);
        Constructions.ConstructionDefinition definition = mock(Constructions.ConstructionDefinition.class);
        definition.type = Constructions.Type.Window;
        assertTrue(isWindow(element, definition));
        definition.type = null;
        definition.name = "WiNdOwFrame";
        assertTrue(isWindow(element, definition));
        definition.name = "wall";
        assertFalse(isWindow(element, definition));
    }

    @Test
    void detectionDoesNotEditWorld() throws Exception {
        ConstructionElement wall = wall();
        try (MockedStatic<World> world = mockStatic(World.class)) {
            assertTrue(trimWall(wall, new Vector3f(), true));
            verify(wall, never()).destroy(anyBoolean());
            verify(wall, never()).setScale(anyFloat(), anyFloat(), anyFloat());
            world.verifyNoInteractions();
        }
    }

    @Test
    void rejectsOpeningBeyondWallDepthOrWidth() throws Exception {
        ConstructionElement wall = wall();
        try (MockedStatic<World> world = mockStatic(World.class)) {
            assertFalse(trimWall(wall, new Vector3f(0f, 0f, 2f), false));
            assertFalse(trimWall(wall, new Vector3f(5f, 0f, 0f), false));
            verify(wall, never()).destroy(anyBoolean());
            world.verifyNoInteractions();
        }
    }

    @Test
    void rejectsMissingWallScale() throws Exception {
        ConstructionElement wall = mock(ConstructionElement.class);
        assertFalse(trimWall(wall, new Vector3f(), false));
        verify(wall, never()).destroy(anyBoolean());
    }

    @Test
    void trimmingCreatesFourPiecesAndCopiesOwnershipAndStrength() throws Exception {
        ConstructionElement wall = wall();
        ConstructionElement created = mock(ConstructionElement.class);
        try (MockedStatic<World> world = mockStatic(World.class)) {
            world.when(() -> World.createConstructionElement(anyInt(), anyInt(), anyFloat(), anyInt(),
                    any(Vector3f.class), any(Quaternion.class), any(Vector3f.class),
                    nullable(Vector3f.class), nullable(Vector3f.class))).thenReturn(created);
            assertTrue(trimWall(wall, new Vector3f(), false));
            world.verify(() -> World.createConstructionElement(anyInt(), anyInt(), anyFloat(), anyInt(),
                    any(Vector3f.class), any(Quaternion.class), any(Vector3f.class),
                    nullable(Vector3f.class), nullable(Vector3f.class)), times(4));
            verify(created, times(4)).setPlayerDbID(wall.getPlayerDbID());
            verify(created, times(4)).setStrength(wall.getStrength());
            verify(wall).setScale(0.001f, 0.001f, 0.001f);
            verify(wall).destroy(true);
        }
    }

    @Test
    void trimsZAxisWallAndRejectsNegligibleHeightOverlap() {
        ConstructionElement wall = wall();
        when(wall.getScale()).thenReturn(new Vector3f(0.2f, 4f, 4f));
        doCallRealMethod().when(plugin).trimWall(any(), any(), any(), any(), anyBoolean());
        try (MockedStatic<World> world = mockStatic(World.class)) {
            assertTrue(plugin.trimWall(wall, new Vector3f(), new Quaternion(), new Vector3f(1f, 1f, 1f), false));
            verify(wall).destroy(true);
            assertFalse(plugin.trimWall(wall, new Vector3f(), new Quaternion(), new Vector3f(1f, 0.12f, 1f), true));
        }
    }

    @Test
    void openingCoveringEntireWallLeavesNoPieces() {
        ConstructionElement wall = wall();
        doCallRealMethod().when(plugin).trimWall(any(), any(), any(), any(), anyBoolean());
        try (MockedStatic<World> world = mockStatic(World.class)) {
            assertTrue(plugin.trimWall(wall, new Vector3f(), new Quaternion(), new Vector3f(8f, 8f, 1f), false));
            world.verifyNoInteractions();
            verify(wall).destroy(true);
        }
    }

    @Test
    void fullHeightOpeningCreatesOnlySidePieces() {
        ConstructionElement wall = wall();
        doCallRealMethod().when(plugin).trimWall(any(), any(), any(), any(), anyBoolean());
        try (MockedStatic<World> world = mockStatic(World.class)) {
            assertTrue(plugin.trimWall(wall, new Vector3f(), new Quaternion(), new Vector3f(1f, 8f, 0.2f), false));
            world.verify(() -> World.createConstructionElement(anyInt(), anyInt(), anyFloat(), anyInt(),
                    any(Vector3f.class), any(Quaternion.class), any(Vector3f.class), any(), any()), times(2));
            verify(wall).destroy(true);
        }
    }

    private ConstructionElement wall() {
        ConstructionElement wall = mock(ConstructionElement.class);
        when(wall.getScale()).thenReturn(new Vector3f(4f, 4f, 0.2f));
        when(wall.getWorldPosition()).thenReturn(new Vector3f());
        when(wall.getRotation()).thenReturn(new Quaternion());
        return wall;
    }

    private boolean isWindow(ConstructionElement element,
                             Constructions.ConstructionDefinition definition) throws Exception {
        Method method = RWWindowTrimming.class.getDeclaredMethod("isWindow",
                ConstructionElement.class, Constructions.ConstructionDefinition.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, element, definition);
    }

    private boolean trimWall(ConstructionElement wall, Vector3f position, boolean detectOnly) throws Exception {
        doCallRealMethod().when(plugin).trimWall(any(), any(), any(), any(), anyBoolean());
        return plugin.trimWall(wall, position, new Quaternion(),
                new Vector3f(1f, 1f, 0.2f), detectOnly);
    }
}
