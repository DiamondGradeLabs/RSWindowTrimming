package com.dgl.risingworld;

import net.risingworld.api.World;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RWWindowTrimmingPieceTest {
    @Test
    void missingSourceScaleStopsBeforeWorldCreation() {
        ConstructionElement source = mock(ConstructionElement.class);
        try (MockedStatic<World> world = mockStatic(World.class)) {
            RWWindowTrimming.createPiece(source, new Quaternion(), new Vector3f(4f, 4f, 0.2f),
                    true, new float[]{-2f, -1f, -2f, 2f}, 0.2f);
            world.verifyNoInteractions();
            verify(source, never()).getWorldPosition();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsPositiveDimensionsAtRotatedMidpointAndCopiesProperties(boolean widthIsX) {
        ConstructionElement source = mock(ConstructionElement.class);
        ConstructionElement created = mock(ConstructionElement.class);
        Vector3f sourceSize = widthIsX ? new Vector3f(4f, 4f, 0.2f) : new Vector3f(0.2f, 4f, 4f);
        Vector3f sourceScale = widthIsX ? new Vector3f(8f, 12f, 0.4f) : new Vector3f(0.4f, 12f, 8f);
        Vector3f position = new Vector3f(10f, 20f, 30f);
        Quaternion rotation = new Quaternion(0f, 1f, 0f, 0f);
        Vector3f surfaceOffset = new Vector3f(0.1f, 0.2f, 0.3f);
        Vector3f surfaceScale = new Vector3f(1f, 2f, 3f);
        when(source.getScale()).thenReturn(sourceScale);
        when(source.getWorldPosition()).thenReturn(position);
        when(source.getTypeID()).thenReturn((byte) 7);
        when(source.getTexture()).thenReturn(12);
        when(source.getTextureScale()).thenReturn(1.5f);
        when(source.getColor()).thenReturn(12345);
        when(source.getSurfaceOffset()).thenReturn(surfaceOffset);
        when(source.getSurfaceScale()).thenReturn(surfaceScale);
        when(source.getPlayerDbID()).thenReturn(42);
        when(source.getStrength()).thenReturn((short) 77);
        try (MockedStatic<World> world = mockStatic(World.class)) {
            world.when(() -> World.createConstructionElement(anyInt(), anyInt(), anyFloat(), anyInt(),
                    any(Vector3f.class), any(Quaternion.class), any(Vector3f.class), any(), any()))
                    .thenReturn(created);
            RWWindowTrimming.createPiece(source, rotation, sourceSize, widthIsX,
                    new float[]{-2f, -1f, -1f, 2f}, 0.2f);
            ArgumentCaptor<Vector3f> piecePosition = ArgumentCaptor.forClass(Vector3f.class);
            ArgumentCaptor<Vector3f> scale = ArgumentCaptor.forClass(Vector3f.class);
            ArgumentCaptor<Quaternion> pieceRotation = ArgumentCaptor.forClass(Quaternion.class);
            world.verify(() -> World.createConstructionElement(eq(7), eq(12), eq(1.5f), eq(12345),
                    piecePosition.capture(), pieceRotation.capture(), scale.capture(),
                    same(surfaceOffset), same(surfaceScale)));
            assertEquals(widthIsX ? new Vector3f(11.5f, 20.5f, 30f) : new Vector3f(10f, 20.5f, 31.5f),
                    piecePosition.getValue());
            Vector3f expectedScale = widthIsX ? new Vector3f(2f, 9f, 0.4f) : new Vector3f(0.4f, 9f, 2f);
            assertEquals(expectedScale.x, scale.getValue().x, 0.00001f);
            assertEquals(expectedScale.y, scale.getValue().y, 0.00001f);
            assertEquals(expectedScale.z, scale.getValue().z, 0.00001f);
            assertEquals(rotation, pieceRotation.getValue());
            assertNotSame(rotation, pieceRotation.getValue());
            verify(created).setPlayerDbID(42);
            verify(created).setStrength((short) 77);
            assertEquals(new Vector3f(10f, 20f, 30f), position);
            assertEquals(widthIsX ? new Vector3f(8f, 12f, 0.4f) : new Vector3f(0.4f, 12f, 8f), sourceScale);
        }
    }

    @Test
    void nullCreationResultDoesNotThrowOrReadOwnership() {
        ConstructionElement source = mock(ConstructionElement.class);
        when(source.getScale()).thenReturn(new Vector3f(4f, 4f, 0.2f));
        when(source.getWorldPosition()).thenReturn(new Vector3f());
        try (MockedStatic<World> world = mockStatic(World.class)) {
            assertDoesNotThrow(() -> RWWindowTrimming.createPiece(source, new Quaternion(),
                    new Vector3f(4f, 4f, 0.2f), true, new float[]{-2f, -1f, -2f, 2f}, 0.2f));
            world.verify(() -> World.createConstructionElement(anyInt(), anyInt(), anyFloat(), anyInt(),
                    any(Vector3f.class), any(Quaternion.class), any(Vector3f.class), any(), any()));
            verify(source, never()).getPlayerDbID();
            verify(source, never()).getStrength();
        }
    }
}
