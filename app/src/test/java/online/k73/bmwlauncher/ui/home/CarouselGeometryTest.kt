package online.k73.bmwlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselGeometryTest {
    @Test fun center_is_flat_full_scale_opaque() {
        val t = CarouselGeometry.transformFor(0f)
        assertEquals(0f, t.rotationYDeg, 0.001f)
        assertEquals(1f, t.scale, 0.001f)
        assertEquals(1f, t.alpha, 0.001f)
        assertEquals(0f, t.translationXFraction, 0.001f)
    }
    @Test fun neighbor_is_turned_smaller_dimmer() {
        val t = CarouselGeometry.transformFor(1f)
        assertEquals(-CarouselGeometry.MAX_ROTATION, t.rotationYDeg, 0.001f)
        assertEquals(CarouselGeometry.MIN_SCALE, t.scale, 0.001f)
        assertEquals(CarouselGeometry.MIN_ALPHA, t.alpha, 0.001f)
    }
    @Test fun symmetric_in_sign() {
        val a = CarouselGeometry.transformFor(0.5f)
        val b = CarouselGeometry.transformFor(-0.5f)
        assertEquals(a.scale, b.scale, 0.001f)
        assertEquals(a.alpha, b.alpha, 0.001f)
        assertEquals(a.rotationYDeg, -b.rotationYDeg, 0.001f)
        assertEquals(a.translationXFraction, -b.translationXFraction, 0.001f)
    }
    @Test fun scale_and_alpha_decrease_monotonically() {
        assertTrue(CarouselGeometry.transformFor(0.3f).scale > CarouselGeometry.transformFor(0.7f).scale)
        assertTrue(CarouselGeometry.transformFor(0.3f).alpha > CarouselGeometry.transformFor(0.7f).alpha)
    }
    @Test fun clamps_beyond_one_tile() {
        assertEquals(CarouselGeometry.transformFor(1f).scale, CarouselGeometry.transformFor(3f).scale, 0.001f)
        assertEquals(CarouselGeometry.transformFor(1f).rotationYDeg, CarouselGeometry.transformFor(3f).rotationYDeg, 0.001f)
    }
}
