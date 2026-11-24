package app.florsent.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualChipGridMathTest {

    // Pixel 10 Pro card content at density 500: about 870 px wide, 31 px gaps.
    private val width = 870
    private val gap = 31

    @Test
    fun `three short options share one row in equal cells`() {
        assertEquals(3, EqualChipGridMath.columns(count = 3, width = width, gap = gap, widest = 250))
        assertEquals(269, EqualChipGridMath.cellWidth(width, gap, 3))
    }

    @Test
    fun `three options too wide for thirds stack one per line instead of 2 plus 1`() {
        val widest = 300 // e.g. "20 分鐘" at the largest font size
        assertEquals(1, EqualChipGridMath.columns(count = 3, width = width, gap = gap, widest = widest))
    }

    @Test
    fun `four options too wide for quarters make a 2 by 2 grid`() {
        val widest = 300
        val columns = EqualChipGridMath.columns(count = 4, width = width, gap = gap, widest = widest)
        assertEquals(2, columns)
        assertTrue(EqualChipGridMath.cellWidth(width, gap, columns) >= widest)
    }

    @Test
    fun `a label wider than half falls back to one per line`() {
        assertEquals(1, EqualChipGridMath.columns(count = 3, width = width, gap = gap, widest = 600))
        assertEquals(width, EqualChipGridMath.cellWidth(width, gap, 1))
    }

    @Test
    fun `even a label wider than the whole row gets a full line, not zero columns`() {
        assertEquals(1, EqualChipGridMath.columns(count = 3, width = width, gap = gap, widest = 2_000))
    }

    @Test
    fun `every cell is wide enough for the widest label whenever more than one column is used`() {
        for (widest in 50..900 step 7) {
            val columns = EqualChipGridMath.columns(3, width, gap, widest)
            if (columns > 1) assertTrue(EqualChipGridMath.cellWidth(width, gap, columns) >= widest)
            val cells = EqualChipGridMath.cellWidth(width, gap, columns) * columns + gap * (columns - 1)
            assertTrue("fits the row", cells <= width)
        }
    }
}
