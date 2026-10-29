package com.zero.assistant.core

import com.zero.assistant.OrganicOrbGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganicOrbGeometryTest {
    @Test fun meshIsDeterministicAndBounded() {
        val first = OrganicOrbGeometry.create(4)
        val second = OrganicOrbGeometry.create(4)
        assertTrue(first.vertices.contentEquals(second.vertices))
        assertTrue(first.edges.contentEquals(second.edges))
        assertTrue(first.vertices.size >= 7_600 && first.vertices.size <= 8_000)
        assertTrue(first.edges.size <= 20_000)
        assertEquals(48 * 4, first.particles.size)
    }

    @Test fun everyEdgeIndexIsWithinVertexRange() {
        val mesh = OrganicOrbGeometry.create(4)
        val vertexCount = mesh.vertices.size / 3
        for (index in mesh.edges) assertTrue(index >= 0 && index < vertexCount)
    }
}
