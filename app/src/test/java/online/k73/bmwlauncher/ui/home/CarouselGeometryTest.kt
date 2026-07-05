package online.k73.bmwlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselGeometryTest {
    @Test fun center_is_flat_full_scale_opaque() {
        val t = CarouselGeometry.transformFor(0f)
        assertEquals(0f, t.rotationYDeg, 0.001f); assertEquals(1f, t.scale, 0.001f); assertEquals(1f, t.alpha, 0.001f)
    }
    @Test fun first_neighbor_matches_stops() {
        val t = CarouselGeometry.transformFor(1f)
        assertEquals(-26f, t.rotationYDeg, 0.001f); assertEquals(0.7882f, t.scale, 0.002f); assertEquals(0.55f, t.alpha, 0.001f)
    }
    @Test fun second_and_third_stops() {
        assertEquals(0.6897f, CarouselGeometry.transformFor(2f).scale, 0.002f)
        assertEquals(-38f, CarouselGeometry.transformFor(2f).rotationYDeg, 0.001f)
        assertEquals(0.16f, CarouselGeometry.transformFor(3f).alpha, 0.001f)
        assertEquals(-48f, CarouselGeometry.transformFor(3f).rotationYDeg, 0.001f)
    }
    @Test fun symmetric_in_sign() {
        val a = CarouselGeometry.transformFor(0.5f); val b = CarouselGeometry.transformFor(-0.5f)
        assertEquals(a.scale, b.scale, 0.001f); assertEquals(a.alpha, b.alpha, 0.001f); assertEquals(a.rotationYDeg, -b.rotationYDeg, 0.001f)
    }
    @Test fun interpolates_between_stops() {
        val t = CarouselGeometry.transformFor(0.5f)
        assertEquals((1f+0.55f)/2f, t.alpha, 0.001f)               // halfway stop0→stop1
        assertEquals((0f+26f)/2f, -t.rotationYDeg, 0.001f)
    }
    @Test fun monotonic_decrease() {
        assertTrue(CarouselGeometry.transformFor(0.3f).scale > CarouselGeometry.transformFor(0.7f).scale)
        assertTrue(CarouselGeometry.transformFor(0.3f).alpha > CarouselGeometry.transformFor(0.7f).alpha)
    }
    @Test fun clamps_beyond_three() {
        assertEquals(CarouselGeometry.transformFor(3f).scale, CarouselGeometry.transformFor(5f).scale, 0.001f)
        assertEquals(CarouselGeometry.transformFor(3f).rotationYDeg, CarouselGeometry.transformFor(5f).rotationYDeg, 0.001f)
    }
}
